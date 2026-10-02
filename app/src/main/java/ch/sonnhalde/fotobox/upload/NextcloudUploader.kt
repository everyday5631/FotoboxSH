package ch.sonnhalde.fotobox.upload

import ch.sonnhalde.fotobox.wcm.WcmConfig
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit

/**
 * Laedt ein Foto per WebDAV in eine Nextcloud und erstellt einen oeffentlichen Freigabe-Link (OCS Share API).
 * Anmeldung mit Benutzername + App-Passwort (Nextcloud: Einstellungen -> Sicherheit -> Neues App-Passwort).
 *
 * Jeder Schritt meldet bei Fehlern, was genau schiefging («2/4 Anmeldung: …»), damit sich Probleme vor Ort
 * ohne Logs eingrenzen lassen.
 */
class NextcloudUploader(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private class StepError(step: String, message: String) : Exception("$step: $message")

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
                    val form = FormBody.Builder().add("path", sharePath).add("shareType", "3").add("permissions", "1").build()
                    val share = Request.Builder().url(shareUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(form).build()
                    http.newCall(share).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        val ocs = runCatching { JSONObject(text).getJSONObject("ocs") }.getOrNull()
                        if (!r.isSuccessful) {
                            val detail = ocs?.optJSONObject("meta")?.optString("message").orEmpty()
                            throw StepError(s4, "HTTP ${r.code}" + (if (detail.isNotBlank()) " – $detail" else "") +
                                " (Ist «Teilen per Link» in Nextcloud erlaubt?)")
                        }
                        ocs?.optJSONObject("data")?.optString("url")?.takeIf { it.startsWith("http") }
                            ?: throw StepError(s4, "Antwort enthält keinen Link")
                    }
                }
            }
        }

    private fun checkAuth(step: String, code: Int) {
        if (code == 401) throw StepError(step, "Anmeldung abgelehnt – Benutzername und **App-Passwort** prüfen (nicht das normale Passwort)")
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
