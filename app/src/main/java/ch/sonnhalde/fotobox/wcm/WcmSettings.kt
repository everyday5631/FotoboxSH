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
    /** Text im Banner unten rechts auf dem Foto (max. 28 Zeichen), z. B. «Personalfest 2027». */
    val bannerText: String = "",
    /** Countdown vor der Aufnahme in Sekunden. */
    val timerSeconds: Int = 3,
    /** Ueberschrift und Knopf-Text auf dem Startbildschirm des Retail-Modus. */
    /** Kamera: Belichtungskorrektur (Index) und Zoom, wie bei UpReach «Camera settings». */
    val exposureIndex: Int = 0,
    val zoomRatio: Float = 1f,
    /** Druckformat: IPP-Papiername (leer = automatisch «4x6»), Skalierung, Ausrichtung und Drehung des Bildes. */
    val printMedia: String = "",
    val printScaling: String = "fit",      // fit | fill | auto (Drucker entscheidet)
    val printOrientation: String = "auto", // auto | landscape | portrait | none
    val printRotation: Int = 0,            // 0 | 90 | 180 | 270 Grad vor dem Senden
    /** Upload-Ziel: «flow» (Power Automate/SharePoint) oder «nextcloud». */
    val uploadTarget: String = "flow",
    val ncUrl: String = "",
    val ncUser: String = "",
    val ncPassword: String = "",
    val ncFolder: String = "Fotobox",
    val startTitle: String = "Ein Moment für Sie und Ihre Liebsten",
    val startButton: String = "jetzt starten",
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
        bannerText = prefs.getString("bannerText", "").orEmpty(),
        timerSeconds = prefs.getInt("timerSeconds", 3),
        exposureIndex = prefs.getInt("exposureIndex", 0),
        zoomRatio = prefs.getFloat("zoomRatio", 1f),
        printMedia = prefs.getString("printMedia", "").orEmpty(),
        printScaling = prefs.getString("printScaling", "fit").orEmpty(),
        printOrientation = prefs.getString("printOrientation", "auto").orEmpty(),
        printRotation = prefs.getInt("printRotation", 0),
        uploadTarget = prefs.getString("uploadTarget", "flow").orEmpty(),
        ncUrl = prefs.getString("ncUrl", "").orEmpty(),
        ncUser = prefs.getString("ncUser", "").orEmpty(),
        ncPassword = prefs.getString("ncPassword", "").orEmpty(),
        ncFolder = prefs.getString("ncFolder", "Fotobox").orEmpty(),
        startTitle = prefs.getString("startTitle", WcmConfig().startTitle).orEmpty(),
        startButton = prefs.getString("startButton", WcmConfig().startButton).orEmpty(),
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
            .putString("bannerText", config.bannerText.take(28))
            .putInt("timerSeconds", config.timerSeconds)
            .putInt("exposureIndex", config.exposureIndex)
            .putFloat("zoomRatio", config.zoomRatio)
            .putString("printMedia", config.printMedia.trim())
            .putString("printScaling", config.printScaling)
            .putString("printOrientation", config.printOrientation)
            .putInt("printRotation", config.printRotation)
            .putString("uploadTarget", config.uploadTarget)
            .putString("ncUrl", config.ncUrl.trim())
            .putString("ncUser", config.ncUser.trim())
            .putString("ncPassword", config.ncPassword)
            .putString("ncFolder", config.ncFolder.trim().trim('/'))
            .putString("startTitle", config.startTitle.take(60))
            .putString("startButton", config.startButton.take(24))
            .apply()
    }
}
