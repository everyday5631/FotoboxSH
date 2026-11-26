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
    /** Text im Banner unten links (max. 28 Zeichen). */
    val bannerTextLeft: String = "",
    /** Textfarbe im Banner: black | gold | navy | rainbow. */
    val bannerColor: String = "navy",
    /** Deckkraft des Banner-Hintergrunds in Prozent (100 = deckend, 0 = durchsichtig). */
    val bannerAlpha: Int = 100,
    /** Banner auch auf der digitalen Version (QR-Code/Download) einblenden. */
    val bannerDigital: Boolean = true,
    /** Countdown vor der Aufnahme in Sekunden. */
    val timerSeconds: Int = 3,
    /** Ueberschrift und Knopf-Text auf dem Startbildschirm des Retail-Modus. */
    /** Kamera: Belichtungskorrektur (Index) und Zoom, wie bei UpReach «Camera settings». */
    val exposureIndex: Int = 0,
    val zoomRatio: Float = 1f,
    /** Druckformat: IPP-Papiername (leer = automatisch «4x6»), Skalierung, Ausrichtung und Drehung des Bildes. */
    val printMedia: String = "",
    /** Gewuenschte Druckwarteschlange (Teil des Namens, z. B. «4x6»); leer = automatisch 4x6. */
    val printerQueue: String = "",
    /** Zum Drucken automatisch ins Drucker-WLAN (WCMPlus-Hotspot) wechseln und danach zurueck ins Internet-WLAN. */
    val printerWifiSwitch: Boolean = false,
    val printerWifiSsid: String = "",
    val printerWifiPassword: String = "dnp12345",
    val printerWifiHost: String = "192.168.4.1",
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
        bannerTextLeft = prefs.getString("bannerTextLeft", "").orEmpty(),
        bannerColor = prefs.getString("bannerColor", "navy").orEmpty(),
        bannerAlpha = prefs.getInt("bannerAlpha", 100),
        bannerDigital = prefs.getBoolean("bannerDigital", true),
        timerSeconds = prefs.getInt("timerSeconds", 3),
        exposureIndex = prefs.getInt("exposureIndex", 0),
        zoomRatio = prefs.getFloat("zoomRatio", 1f),
        printMedia = prefs.getString("printMedia", "").orEmpty(),
        printerQueue = prefs.getString("printerQueue", "").orEmpty(),
        printerWifiSwitch = prefs.getBoolean("printerWifiSwitch", false),
        printerWifiSsid = prefs.getString("printerWifiSsid", "").orEmpty(),
        printerWifiPassword = prefs.getString("printerWifiPassword", "dnp12345").orEmpty(),
        printerWifiHost = prefs.getString("printerWifiHost", "192.168.4.1").orEmpty(),
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
            .putString("bannerTextLeft", config.bannerTextLeft.take(28))
            .putString("bannerColor", config.bannerColor)
            .putInt("bannerAlpha", config.bannerAlpha.coerceIn(0, 100))
            .putBoolean("bannerDigital", config.bannerDigital)
            .putInt("timerSeconds", config.timerSeconds)
            .putInt("exposureIndex", config.exposureIndex)
            .putFloat("zoomRatio", config.zoomRatio)
            .putString("printMedia", config.printMedia.trim())
            .putString("printerQueue", config.printerQueue.trim())
            .putBoolean("printerWifiSwitch", config.printerWifiSwitch)
            .putString("printerWifiSsid", config.printerWifiSsid.trim())
            .putString("printerWifiPassword", config.printerWifiPassword)
            .putString("printerWifiHost", config.printerWifiHost.trim())
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
