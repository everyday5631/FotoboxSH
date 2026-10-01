package ch.sonnhalde.fotobox.wcm

import android.content.Context

/** Einstellungen (WCMPlus-Drucker und SharePoint-Upload), lokal auf dem Geraet gespeichert. */
data class WcmConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    /** Pfad, der fuer den Verbindungstest per GET aufgerufen wird. */
    val statusPath: String = "/",
    /** Optionaler Bearer-Token; leer = keine Authentifizierung. */
    val token: String = "",
    /** HTTPS-Link des Power-Automate-Flows, der Fotos in SharePoint ablegt (leer = kein Upload). */
    val flowUrl: String = "",
    /** Optionale IPP-Adresse des Druckers (z. B. ipp://192.168.4.1:631/ipp/print); leer = automatisch suchen. */
    val printerUri: String = "",
    /** Foto nach der Aufnahme sofort drucken (ohne Tippen auf «Drucken»). */
    val autoPrint: Boolean = false,
    /** Frontkamera (Selfie) statt Rueckkamera verwenden. */
    val useFrontCamera: Boolean = true,
) {
    companion object {
        /** Standardadresse des WCMPlus-Portals (laut Anleitung 192.168.4.1). */
        const val DEFAULT_BASE_URL = "http://192.168.4.1"
    }
}

class WcmSettings(context: Context) {
    private val prefs = context.getSharedPreferences("wcmplus", Context.MODE_PRIVATE)

    fun load() = WcmConfig(
        baseUrl = prefs.getString("baseUrl", WcmConfig.DEFAULT_BASE_URL).orEmpty(),
        statusPath = prefs.getString("statusPath", "/").orEmpty(),
        token = prefs.getString("token", "").orEmpty(),
        flowUrl = prefs.getString("flowUrl", "").orEmpty(),
        printerUri = prefs.getString("printerUri", "").orEmpty(),
        autoPrint = prefs.getBoolean("autoPrint", false),
        useFrontCamera = prefs.getBoolean("useFrontCamera", true),
    )

    fun save(config: WcmConfig) {
        prefs.edit()
            .putString("baseUrl", config.baseUrl.trim())
            .putString("statusPath", config.statusPath.trim())
            .putString("token", config.token.trim())
            .putString("flowUrl", config.flowUrl.trim())
            .putString("printerUri", config.printerUri.trim())
            .putBoolean("autoPrint", config.autoPrint)
            .putBoolean("useFrontCamera", config.useFrontCamera)
            .apply()
    }
}
