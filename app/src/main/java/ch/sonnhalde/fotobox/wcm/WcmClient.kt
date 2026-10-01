package ch.sonnhalde.fotobox.wcm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface WcmStatus {
    data object Unknown : WcmStatus
    data object Checking : WcmStatus
    data class Online(val httpCode: Int) : WcmStatus
    data class Offline(val reason: String) : WcmStatus
}

/**
 * Minimaler Client fuer WCMPlus. Die konkreten API-Endpunkte von WCMPlus sind nicht
 * festgelegt; deshalb gibt es bewusst nur einen Verbindungstest gegen einen
 * konfigurierbaren Pfad. Weitere Aufrufe (z. B. Druckauftrag) hier ergaenzen,
 * sobald die WCMPlus-Schnittstellendokumentation vorliegt.
 */
class WcmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun checkConnection(config: WcmConfig): WcmStatus = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trim().trimEnd('/')
        val path = config.statusPath.trim().let { if (it.startsWith("/")) it else "/$it" }
        val url = (base + path).toHttpUrlOrNull()
            ?: return@withContext WcmStatus.Offline("Ungültige Adresse: $base$path")

        val request = Request.Builder().url(url).get().apply {
            if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}")
        }.build()

        try {
            http.newCall(request).execute().use { response ->
                if (response.code in 200..399) WcmStatus.Online(response.code)
                else WcmStatus.Offline("WCMPlus antwortet mit HTTP ${response.code}")
            }
        } catch (e: Exception) {
            WcmStatus.Offline(e.message ?: e.javaClass.simpleName)
        }
    }
}
