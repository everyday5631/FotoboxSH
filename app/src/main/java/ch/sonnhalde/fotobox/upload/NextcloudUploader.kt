package ch.sonnhalde.fotobox.upload

import ch.sonnhalde.fotobox.wcm.WcmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Laedt ein Foto per WebDAV in eine Nextcloud und erstellt einen oeffentlichen Freigabe-Link (OCS Share API).
 * Anmeldung mit Benutzername + App-Passwort (Nextcloud: Einstellungen -> Sicherheit -> Neues App-Passwort).
 */
class NextcloudUploader(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun upload(config: WcmConfig, fileName: String, jpeg: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = config.ncUrl.trim().ifBlank { error("Nextcloud-Adresse fehlt") }.toHttpUrl()
                val user = config.ncUser.trim().ifBlank { error("Nextcloud-Benutzer fehlt") }
                val auth = Credentials.basic(user, config.ncPassword)
                val folder = config.ncFolder.trim().trim('/').split('/').filter { it.isNotBlank() }

                fun davUrl(segments: List<String>) = base.newBuilder().apply {
                    addPathSegments("remote.php/dav/files")
                    addPathSegment(user)
                    segments.forEach { addPathSegment(it) }
                }.build()

                // 1) Ordner anlegen (jede Ebene; 405 = existiert schon)
                for (i in folder.indices) {
                    val req = Request.Builder().url(davUrl(folder.take(i + 1))).header("Authorization", auth)
                        .method("MKCOL", null).build()
                    http.newCall(req).execute().use { r ->
                        if (r.code == 401) error("Anmeldung abgelehnt (Benutzer/App-Passwort prüfen)")
                        if (r.code !in listOf(201, 405)) error("Ordner konnte nicht angelegt werden (HTTP ${r.code})")
                    }
                }

                // 2) Datei hochladen
                val put = Request.Builder().url(davUrl(folder + fileName)).header("Authorization", auth)
                    .put(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
                http.newCall(put).execute().use { r ->
                    if (r.code == 401) error("Anmeldung abgelehnt (Benutzer/App-Passwort prüfen)")
                    if (r.code !in listOf(201, 204)) error("Upload fehlgeschlagen (HTTP ${r.code})")
                }

                // 3) Oeffentlichen Link erstellen (shareType 3, nur lesen)
                val sharePath = "/" + (folder + fileName).joinToString("/")
                val shareUrl = base.newBuilder().addPathSegments("ocs/v2.php/apps/files_sharing/api/v1/shares")
                    .addQueryParameter("format", "json").build()
                val form = FormBody.Builder().add("path", sharePath).add("shareType", "3").add("permissions", "1").build()
                val share = Request.Builder().url(shareUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(form).build()
                http.newCall(share).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) error("Freigabe-Link fehlgeschlagen (HTTP ${r.code}); sind öffentliche Links in Nextcloud erlaubt?")
                    JSONObject(text).getJSONObject("ocs").getJSONObject("data").getString("url")
                }
            }
        }
}
