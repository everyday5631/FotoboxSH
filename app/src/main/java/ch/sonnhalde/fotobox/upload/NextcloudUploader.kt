package ch.sonnhalde.fotobox.upload

import android.util.Log
import ch.sonnhalde.fotobox.wcm.WcmConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Laedt ein Foto per WebDAV in eine Nextcloud und erstellt einen oeffentlichen Freigabe-Link (OCS Share API).
 * Anmeldung mit Benutzername + App-Passwort (Nextcloud: Einstellungen -> Sicherheit -> Neues App-Passwort).
 *
 * Jeder Schritt meldet bei Fehlern, was genau schiefging («2/4 Anmeldung: …»), damit sich Probleme vor Ort
 * ohne Logs eingrenzen lassen.
 *
 * Gueltigkeit des Links: Nextcloud kennt beim Ablauf einer Freigabe nur ganze **Tage** (`expireDate` = Datum), keine Stunden.
 * Die Frist [WcmConfig.ncShareHours] wird deshalb auf ein Datum umgerechnet, das **nicht frueher** als die gewuenschte
 * Dauer endet (siehe [expireDate]). Stundengenau wird es nur mit [WcmConfig.ncDeleteExpired]: dann loescht die App
 * eigene Fotos (`foto-<ms>.jpg`, `test-<ms>.jpg`) im Upload-Ordner, sobald sie aelter als die Frist sind.
 */
class NextcloudUploader(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private class StepError(step: String, message: String) : Exception("$step: $message")

    /** Aufraeumen laeuft im Hintergrund, damit der QR-Code nicht darauf warten muss. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupRunning = AtomicBoolean(false)

    suspend fun upload(config: WcmConfig, fileName: String, jpeg: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = normalize(config.ncUrl)
                val user = config.ncUser.trim().ifBlank { throw StepError("Eingabe", "Benutzername fehlt") }
                if (config.ncPassword.isBlank()) throw StepError("Eingabe", "App-Passwort fehlt")
                val auth = Credentials.basic(user, config.ncPassword)
                val folder = config.ncFolder.trim().trim('/').split('/').filter { it.isNotBlank() }

                fun davUrl(segments: List<String>) = base.newBuilder().apply {
                    addPathSegments("remote.php/dav/files")
                    addPathSegment(user)
                    segments.forEach { addPathSegment(it) }
                }.build()

                // 1) Ist der Server erreichbar und eine Nextcloud?
                val s1 = "1/4 Verbindung"
                step(s1, base) {
                    val req = Request.Builder().url(base.newBuilder().addPathSegment("status.php").build()).get().build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw StepError(s1, "HTTP ${r.code} – Adresse prüfen (nur die Hauptadresse, ohne /index.php oder /login)")
                        val status = try { JSONObject(r.body?.string().orEmpty()) } catch (e: JSONException) {
                            throw StepError(s1, "Die Adresse antwortet, ist aber keine Nextcloud")
                        }
                        if (!status.optBoolean("installed", true)) throw StepError(s1, "Nextcloud ist nicht installiert/eingerichtet")
                        if (status.optBoolean("maintenance", false)) throw StepError(s1, "Nextcloud ist im Wartungsmodus")
                    }
                }

                // 2) Anmeldung + Ordner anlegen (jede Ebene; 405 = existiert schon)
                val s2 = "2/4 Anmeldung/Ordner"
                step(s2, base) {
                    if (folder.isEmpty()) {
                        // Nur Anmeldung pruefen
                        val req = Request.Builder().url(davUrl(emptyList())).header("Authorization", auth).method("PROPFIND", null).header("Depth", "0").build()
                        http.newCall(req).execute().use { r -> checkAuth(s2, r.code) }
                    }
                    for (i in folder.indices) {
                        val req = Request.Builder().url(davUrl(folder.take(i + 1))).header("Authorization", auth).method("MKCOL", null).build()
                        http.newCall(req).execute().use { r ->
                            checkAuth(s2, r.code)
                            if (r.code !in listOf(201, 405)) throw StepError(s2, "Ordner «${folder[i]}» konnte nicht angelegt werden (HTTP ${r.code})")
                        }
                    }
                }

                // 3) Datei hochladen
                val s3 = "3/4 Upload"
                step(s3, base) {
                    val put = Request.Builder().url(davUrl(folder + fileName)).header("Authorization", auth)
                        .put(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
                    http.newCall(put).execute().use { r ->
                        checkAuth(s3, r.code)
                        if (r.code == 507) throw StepError(s3, "Kein Speicherplatz mehr (Quota des Benutzers voll)")
                        if (r.code !in listOf(201, 204)) throw StepError(s3, "HTTP ${r.code}")
                    }
                }

                // 4) Oeffentlichen Link erstellen (shareType 3, nur lesen)
                val s4 = "4/4 Freigabe-Link"
                step(s4, base) {
                    val sharePath = "/" + (folder + fileName).joinToString("/")
                    val shareUrl = base.newBuilder().addPathSegments("ocs/v2.php/apps/files_sharing/api/v1/shares")
                        .addQueryParameter("format", "json").build()
                    val form = FormBody.Builder().add("path", sharePath).add("shareType", "3").add("permissions", "1")
                        .apply { expireDate(config.ncShareHours)?.let { add("expireDate", it) } }.build()
                    val share = Request.Builder().url(shareUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(form).build()
                    http.newCall(share).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        val ocs = runCatching { JSONObject(text).getJSONObject("ocs") }.getOrNull()
                        if (!r.isSuccessful) {
                            val detail = ocs?.optJSONObject("meta")?.optString("message").orEmpty()
                            throw StepError(s4, "HTTP ${r.code}" + (if (detail.isNotBlank()) " – $detail" else "") +
                                " (Ist «Teilen per Link» in Nextcloud erlaubt? Ablaufdatum ${expireDate(config.ncShareHours) ?: "keines"})")
                        }
                        // Zur Kontrolle im Logcat: wann laeuft der Link laut Server wirklich ab?
                        Log.i(TAG, "Nextcloud-Freigabe laeuft ab: ${ocs?.optJSONObject("data")?.optString("expiration").orEmpty().ifBlank { "nie (kein Ablaufdatum)" }}")
                        ocs?.optJSONObject("data")?.optString("url")?.takeIf { it.startsWith("http") }
                            ?: throw StepError(s4, "Antwort enthält keinen Link")
                    }
                }
            }.onSuccess { cleanupAsync(config) }
        }

    /**
     * Ablaufdatum («YYYY-MM-DD») fuer die Freigabe oder `null` (= ohne Ablauf, [hours] <= 0).
     *
     * Nextcloud rechnet nur in ganzen Tagen. Damit der Link **nie frueher** ablaeuft als gewuenscht (auch falls der Server den Tag
     * um 00:00 statt um 23:59 enden laesst), wird auf den Tag **nach** dem Zieldatum aufgerundet; mindestens morgen, weil Nextcloud
     * ein Ablaufdatum von heute als «in der Vergangenheit» ablehnen kann. Der Link kann dadurch bis zu etwa einen Tag laenger gelten
     * als eingestellt; genau wird die Frist nur mit [WcmConfig.ncDeleteExpired].
     * Nicht gegen einen echten Server geprueft: das echte Ablaufdatum steht im Logcat (`Nextcloud-Freigabe laeuft ab`).
     */
    internal fun expireDate(hours: Int, now: LocalDateTime = LocalDateTime.now()): String? {
        if (hours <= 0) return null
        return now.plusHours(hours.toLong()).toLocalDate().plusDays(1).toString()
    }

    private fun cleanupAsync(config: WcmConfig) {
        if (!config.ncDeleteExpired || config.ncShareHours <= 0) return
        if (!cleanupRunning.compareAndSet(false, true)) return
        cleanupScope.launch {
            try {
                runCatching { deleteExpired(config) }.onFailure { Log.w(TAG, "Nextcloud-Aufraeumen fehlgeschlagen: ${it.message}") }
            } finally {
                cleanupRunning.set(false)
            }
        }
    }

    /**
     * Loescht eigene Fotos im Upload-Ordner, die aelter als die Frist sind. Nur Dateien, die die App selbst so benennt
     * (`foto-<ms>.jpg`, `test-<ms>.jpg`; das Alter steht im Namen), nur direkt im konfigurierten Ordner und nie im Hauptverzeichnis.
     * Nextcloud legt geloeschte Dateien zunaechst in den Papierkorb des Benutzers.
     */
    private fun deleteExpired(config: WcmConfig) {
        val base = normalize(config.ncUrl)
        val user = config.ncUser.trim()
        val folder = config.ncFolder.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (user.isBlank() || config.ncPassword.isBlank() || folder.isEmpty()) return
        val auth = Credentials.basic(user, config.ncPassword)

        fun davUrl(segments: List<String>) = base.newBuilder().apply {
            addPathSegments("remote.php/dav/files")
            addPathSegment(user)
            segments.forEach { addPathSegment(it) }
        }.build()

        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml".toMediaType())
        val listing = Request.Builder().url(davUrl(folder)).header("Authorization", auth).header("Depth", "1")
            .method("PROPFIND", body).build()
        val xml = http.newCall(listing).execute().use { r -> if (r.code == 207) r.body?.string().orEmpty() else return }

        val limitMs = config.ncShareHours * 3_600_000L
        val now = System.currentTimeMillis()
        var deleted = 0
        for (m in HREF.findAll(xml)) {
            val name = runCatching { URLDecoder.decode(m.groupValues[1].trimEnd('/').substringAfterLast('/'), "UTF-8") }.getOrNull() ?: continue
            val takenAt = OWN_FILE.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            if (now - takenAt <= limitMs) continue
            val del = Request.Builder().url(davUrl(folder + name)).header("Authorization", auth).delete().build()
            http.newCall(del).execute().use { r -> if (r.code in listOf(204, 404)) deleted++ }
            if (deleted >= MAX_DELETES_PER_RUN) break
        }
        if (deleted > 0) Log.i(TAG, "Nextcloud: $deleted abgelaufene Fotos geloescht")
    }

    private companion object {
        const val TAG = "Fotobox"
        const val MAX_DELETES_PER_RUN = 50
        val HREF = Regex("<[A-Za-z0-9]*:?href>([^<]*)</[A-Za-z0-9]*:?href>")
        /** Dateien, die diese App selbst anlegt: Name enthaelt die Aufnahmezeit in Millisekunden. */
        val OWN_FILE = Regex("(?:foto|test)-(\\d{10,13})\\.jpg")
    }

    private fun checkAuth(step: String, code: Int) {
        if (code == 401) throw StepError(step, "Anmeldung abgelehnt (HTTP 401) – Benutzername (Konto-ID, nicht Anzeigename) und Passwort prüfen. Bei aktivierter Zwei-Faktor-Anmeldung ist ein App-Passwort nötig (Nextcloud → Einstellungen → Sicherheit → Neues App-Passwort)")
        if (code == 403) throw StepError(step, "Zugriff verboten (HTTP 403) – Rechte des Benutzers prüfen")
    }

    /** Netzwerkfehler eines Schritts mit verstaendlicher Meldung versehen. */
    private fun <T> step(name: String, base: HttpUrl, block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw StepError(name, "keine Verbindung zu ${base.host} – hat das Tablet Internet und stimmt die Adresse? (${e.javaClass.simpleName}: ${e.message})")
        }

    private fun normalize(raw: String): HttpUrl {
        var s = raw.trim().ifBlank { throw StepError("Eingabe", "Nextcloud-Adresse fehlt") }
        if (!s.startsWith("http", ignoreCase = true)) s = "https://$s"
        s = s.trimEnd('/').removeSuffix("/index.php")
        return s.toHttpUrlOrNull() ?: throw StepError("Eingabe", "Nextcloud-Adresse ungültig: $raw")
    }
}
