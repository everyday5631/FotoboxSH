package ch.sonnhalde.fotobox.upload

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Laedt ein Foto ueber einen Power-Automate-Flow (Trigger «Beim Empfang einer HTTP-Anfrage») nach SharePoint.
 *
 * Vertrag mit dem Flow:
 *  - Anfrage:  POST JSON {"fileName": "foto-123.jpg", "contentBase64": "<JPEG als Base64>"}
 *  - Antwort:  JSON {"url": "https://<tenant>.sharepoint.com/..."}  (Freigabe-Link zur Datei)
 * Siehe README fuer den Aufbau des Flows.
 */
class SharePointUploader(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun upload(flowUrl: String, fileName: String, jpeg: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("fileName", fileName)
                    .put("contentBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(flowUrl.trim()).post(body).build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("Upload fehlgeschlagen (HTTP ${response.code})")
                    parseUrl(text) ?: error("Antwort des Flows enthält keinen Link («url»)")
                }
            }
        }

    private fun parseUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("http")) return trimmed
        return runCatching { JSONObject(trimmed).optString("url") }.getOrNull()?.takeIf { it.startsWith("http") }
    }
}
