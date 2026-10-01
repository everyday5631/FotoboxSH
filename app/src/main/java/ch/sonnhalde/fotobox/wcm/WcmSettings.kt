package ch.sonnhalde.fotobox.wcm

import android.content.Context

/** Verbindungseinstellungen zu WCMPlus, lokal auf dem Geraet gespeichert. */
data class WcmConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    /** Pfad, der fuer den Verbindungstest per GET aufgerufen wird. */
    val statusPath: String = "/",
    /** Optionaler Bearer-Token; leer = keine Authentifizierung. */
    val token: String = "",
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:8080"
    }
}

class WcmSettings(context: Context) {
    private val prefs = context.getSharedPreferences("wcmplus", Context.MODE_PRIVATE)

    fun load() = WcmConfig(
        baseUrl = prefs.getString("baseUrl", WcmConfig.DEFAULT_BASE_URL).orEmpty(),
        statusPath = prefs.getString("statusPath", "/").orEmpty(),
        token = prefs.getString("token", "").orEmpty(),
    )

    fun save(config: WcmConfig) {
        prefs.edit()
            .putString("baseUrl", config.baseUrl.trim())
            .putString("statusPath", config.statusPath.trim())
            .putString("token", config.token.trim())
            .apply()
    }
}
