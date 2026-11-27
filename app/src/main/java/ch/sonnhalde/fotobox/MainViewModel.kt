package ch.sonnhalde.fotobox

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.sonnhalde.fotobox.kiosk.Kiosk
import ch.sonnhalde.fotobox.photo.BackgroundLibrary
import ch.sonnhalde.fotobox.photo.BackgroundRemover
import ch.sonnhalde.fotobox.photo.BannerStyle
import ch.sonnhalde.fotobox.photo.HistoryEntry
import ch.sonnhalde.fotobox.photo.PhotoHistory
import ch.sonnhalde.fotobox.photo.PhotoComposer
import ch.sonnhalde.fotobox.print.IppPrinter
import ch.sonnhalde.fotobox.print.PrinterNetwork
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import ch.sonnhalde.fotobox.qr.QrCode
import ch.sonnhalde.fotobox.upload.NextcloudUploader
import ch.sonnhalde.fotobox.upload.SharePointUploader
import ch.sonnhalde.fotobox.upload.UploadQueue
import ch.sonnhalde.fotobox.wcm.WcmClient
import ch.sonnhalde.fotobox.wcm.WcmConfig
import ch.sonnhalde.fotobox.wcm.WcmSettings
import ch.sonnhalde.fotobox.wcm.WcmStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Splash -> Overview (Setup) <-> Settings; Welcome/Start/Result gehoeren zum Retail-Modus (Kiosk). */
enum class Screen { Splash, Overview, Settings, Welcome, Start, Result }

sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Done(val url: String) : UploadState
    data class Failed(val reason: String) : UploadState
}

sealed interface PrintState {
    data object Idle : PrintState
    /** stage: aktueller Schritt, z. B. «Verbinde mit Drucker-WLAN …». */
    data class Printing(val stage: String = "Foto wird gedruckt …") : PrintState
    data class Done(val text: String) : PrintState
    data class Failed(val reason: String) : PrintState
}

data class UiState(
    val config: WcmConfig = WcmConfig(),
    val status: WcmStatus = WcmStatus.Unknown,
    val screen: Screen = Screen.Splash,
    /** Druck-Version (3:2 mit Logo-Banner) und digitale Version (mit Text-Sticker, fuer QR/Download). */
    val photo: Bitmap? = null,
    val digital: Bitmap? = null,
    val upload: UploadState = UploadState.Idle,
    val print: PrintState = PrintState.Idle,
    /** Retail-Modus aktiv: Kiosk fuer Gaeste (Foto, Timer, Drucken, QR). Sonst Setup-Modus. */
    val retail: Boolean = false,
    val copies: Int = 1,
    /** Zaehler, der sich beim Wechsel des Startbildschirm-Hintergrunds erhoeht (zum Neuladen). */
    val bgVersion: Int = 0,
    /** Zaehler, der sich erhoeht, wenn Foto-Hintergruende hinzukommen oder geloescht werden (Vorschaubilder neu laden). */
    val backgroundsVersion: Int = 0,
    /** Hintergrund, den der Gast beim Fotografieren gewaehlt hat («» = Original); null = Standard aus den Einstellungen. */
    val guestBackgroundId: String? = null,
    /** Hinweis zum Foto-Hintergrund auf dem Ergebnis-Bildschirm, z. B. wenn die Freistellung nicht geklappt hat. */
    val backgroundNote: String? = null,
    /** Fotos, die noch nicht nach SharePoint hochgeladen werden konnten. */
    val queueCount: Int = 0,
    val printerTest: String? = null,
    /** Ergebnis des Upload-Tests im Setup (Text und QR-Code zum Link). */
    val uploadTestText: String? = null,
    val uploadTestQr: Bitmap? = null,
    /** Gefundene Druckwarteschlangen des WCMPlus (eine pro Papierformat, z. B. QW410-4x6), zum Auswaehlen im Setup. */
    val printerQueues: List<String> = emptyList(),
    val qrInput: String = "",
    val qrBitmap: Bitmap? = null,
    val savedUri: Uri? = null,
    val message: String? = null,
    /** Text, solange nach dem Ausloesen das Foto verarbeitet wird (null = nichts in Arbeit); der Kamera-Bildschirm zeigt ihn an. */
    val processing: String? = null,
    /** Foto im Verlauf, das gerade auf dem Ergebnis-Bildschirm steht (null = keins); Druck und Upload melden sich damit zurueck. */
    val historyId: String? = null,
    /** Abzuege, die fuer das angezeigte Foto schon gedruckt wurden; pro Foto sind insgesamt hoechstens [MAX_COPIES] moeglich. */
    val printedCopies: Int = 0,
    /** Foto-Verlauf (neueste zuerst) fuer den Reiter «Fotos» im Setup. */
    val history: List<HistoryEntry> = emptyList(),
)

const val MAX_COPIES = 3
private const val TAG = "Fotobox"
/** Wiederholungen nach einem fehlgeschlagenen Druck, je mit nur einem Abzug (Papier ist knapp, keine doppelten Mengen). */
private const val PRINT_RETRIES = 2
private const val PRINT_RETRY_PAUSE_MS = 3_000L

private class Prepared(val print: Bitmap, val digital: Bitmap, val digitalJpeg: ByteArray, val entry: HistoryEntry)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = WcmSettings(app)
    private val client = WcmClient()
    private val uploader = SharePointUploader()
    private val nextcloud = NextcloudUploader()
    private val queue = UploadQueue(app)
    private val printer = IppPrinter(app)
    private val printerNetwork = PrinterNetwork(app)
    private val backgrounds = BackgroundLibrary(app)
    /** Gemeinsamer Einstieg fuer beide Upload-Ziele. */
    private suspend fun uploadTo(config: WcmConfig, name: String, jpeg: ByteArray): Result<String> =
        if (config.uploadTarget == "nextcloud") nextcloud.upload(config, name, jpeg) else uploader.upload(config.flowUrl, name, jpeg)

    private fun uploadConfigured(c: WcmConfig) =
        if (c.uploadTarget == "nextcloud") c.ncUrl.isNotBlank() && c.ncUser.isNotBlank() && c.ncPassword.isNotBlank() else c.flowUrl.isNotBlank()

    private val history = PhotoHistory(app)
    /** Druckauftraege laufen nacheinander: der WLAN-Wechsel zum Drucker vertraegt keine zwei gleichzeitig. */
    private val printLock = Mutex()
    private var digitalJpeg: ByteArray? = null

    private val _state = MutableStateFlow(UiState(config = settings.load(), retail = Kiosk.isEnabled(app), queueCount = UploadQueue(app).count()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        checkConnection()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.recoverInterrupted() }
            refreshHistory()
            // Auftraege, die vor einem Neustart noch warteten, gehen nicht verloren: aeltester zuerst.
            val waiting = withContext(Dispatchers.IO) { history.list().filter { it.printStatus == "pending" }.reversed() }
            for (e in waiting) enqueuePrint(e.id, e.copies)
            // Alte Fotos auch dann entfernen, wenn lange kein neues Foto gemacht wird.
            while (true) {
                kotlinx.coroutines.delay(30 * 60_000L)
                refreshHistory()
            }
        }
    }

    fun updateConfig(config: WcmConfig) {
        settings.save(config)
        _state.update { it.copy(config = config) }
        checkConnection()
    }

    /** Speichert Einstellungen ohne Verbindungstest (z. B. Banner-Text beim Tippen). */
    fun updateConfigQuiet(config: WcmConfig) {
        settings.save(config)
        _state.update { it.copy(config = config) }
    }

    fun checkConnection() {
        _state.update { it.copy(status = WcmStatus.Checking) }
        viewModelScope.launch {
            var status = client.checkConnection(_state.value.config)
            if (status is WcmStatus.Offline) {
                // WCMPlus haengt oft im lokalen WLAN (andere IP als 192.168.4.1): per mDNS suchen wie Mopria.
                val found = printer.discover(_state.value.config).getOrNull()
                if (found != null) status = WcmStatus.Online(200, via = printer.foundHost())
            }
            _state.update { it.copy(status = status) }
        }
    }

    // ---- Foto-Ablauf: Kamera -> Ergebnis (Upload, QR, Druck) ----

    fun finishSplash() = _state.update {
        if (it.screen != Screen.Splash) it else it.copy(screen = if (it.retail) Screen.Welcome else Screen.Overview)
    }

    fun startRetail() = _state.update { it.copy(retail = true, screen = Screen.Welcome, message = null) }

    /** «jetzt starten»: Kamera mit Live-Bild. */
    fun startCapture() = _state.update { it.copy(screen = Screen.Start, message = null) }

    fun exitRetail() {
        digitalJpeg = null
        _state.update { it.copy(retail = false, screen = Screen.Overview, historyId = null, printedCopies = 0, photo = null, digital = null, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, message = null) }
    }

    // ---- Startbildschirm-Hintergrund (Bild aus der Galerie) ----

    fun setStartBackground(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                    val bmp = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Bild nicht lesbar")
                    startBackgroundFile().outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                }.isSuccess
            }
            _state.update { it.copy(bgVersion = it.bgVersion + 1, message = if (ok) "Hintergrundbild gespeichert." else "Hintergrundbild konnte nicht geladen werden.") }
        }
    }

    fun clearStartBackground() {
        startBackgroundFile().delete()
        _state.update { it.copy(bgVersion = it.bgVersion + 1, message = "Hintergrundbild entfernt.") }
    }

    // ---- Banner-Logo (PNG oder JPG aus der Galerie, wird als PNG gespeichert) ----

    fun setBannerLogo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
                    val bmp = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Bild nicht lesbar")
                    PhotoComposer.logoFile(getApplication()).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.isSuccess
            }
            _state.update { it.copy(message = if (ok) "Logo gespeichert." else "Logo konnte nicht geladen werden.") }
        }
    }

    fun clearBannerLogo() {
        PhotoComposer.logoFile(getApplication()).delete()
        _state.update { it.copy(message = "Standard-Logo wird wieder verwendet.") }
    }

    // ---- Foto-Hintergrund (Personen freistellen und vor ein gewaehltes Bild setzen) ----

    fun addBackground(uri: Uri) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { backgrounds.add(uri) }
            if (id != null) {
                // Neues Bild gleich auswaehlen.
                updateConfigQuiet(_state.value.config.copy(backgroundId = id))
            }
            _state.update { it.copy(backgroundsVersion = it.backgroundsVersion + 1, message = if (id != null) "Hintergrund hinzugefügt und ausgewählt." else "Bild konnte nicht geladen werden.") }
        }
    }

    /** Gast waehlt beim Fotografieren einen Hintergrund (gilt bis zurueck zum Startbildschirm). */
    fun selectBackground(id: String) = _state.update { it.copy(guestBackgroundId = id) }

    fun deleteBackground(id: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { backgrounds.delete(id) }
            if (ok && _state.value.config.backgroundId == id) updateConfigQuiet(_state.value.config.copy(backgroundId = ""))
            _state.update { it.copy(backgroundsVersion = it.backgroundsVersion + 1, message = if (ok) "Hintergrund gelöscht." else null) }
        }
    }

    /**
     * Setzt die Personen auf dem Foto vor den gewaehlten Hintergrund. Ohne Auswahl, bei fehlendem Bild oder wenn die
     * Erkennung scheitert, bleibt das Originalfoto: ein Gast verliert nie sein Foto.
     */
    private suspend fun applyBackground(photo: Bitmap, backgroundId: String, diagnostics: Boolean, useHairModel: Boolean): Bitmap {
        backgroundNote = null
        if (backgroundId.isBlank()) {
            if (diagnostics) backgroundNote = "Hintergrund: keiner gewählt (Original)."
            return photo
        }
        val label = backgrounds.all().firstOrNull { it.id == backgroundId }?.label ?: backgroundId
        val background = backgrounds.load(backgroundId)
        if (background == null) {
            Log.w(TAG, "Hintergrund $backgroundId nicht lesbar")
            backgroundNote = "Hintergrund konnte nicht geladen werden, es gilt das Originalfoto."
            return photo
        }
        return runCatching { BackgroundRemover.replace(getApplication<Application>(), photo, background, useHairModel) }.fold(
            onSuccess = { r ->
                Log.i(TAG, "Hintergrund ersetzt, Personenanteil %.3f".format(r.personShare))
                // Fast nichts oder fast alles als Person erkannt: fuer die Fehlersuche sichtbar machen.
                backgroundNote = when {
                    r.personShare < 0.01f -> "Keine Person erkannt: das Foto zeigt nur den Hintergrund."
                    r.personShare > 0.97f -> "Der Hintergrund wurde nicht erkannt (ganzes Bild als Person): das Foto bleibt fast unverändert."
                    else -> if (diagnostics) "Hintergrund «$label»: ${(r.personShare * 100).toInt()} % des Bildes als Person erkannt, Freistellung ${"%.1f".format(r.millis / 1000f)} s." else null
                }
                r.image
            },
            onFailure = { e ->
                Log.e(TAG, "Hintergrund ersetzen fehlgeschlagen", e)
                backgroundNote = "Hintergrund konnte nicht ersetzt werden (${e.javaClass.simpleName}: ${e.message}), es gilt das Originalfoto."
                photo
            },
        )
    }

    /** Ergebnis des letzten Hintergrund-Schritts (wird beim Anzeigen des Ergebnisses in den Zustand uebernommen). */
    private var backgroundNote: String? = null

    fun startBackgroundFile() = File(getApplication<Application>().filesDir, "start_bg.jpg")

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings, message = null) }
    fun closeSettings() = _state.update { it.copy(screen = Screen.Overview, printerTest = null, message = null) }

    fun checkPin(pin: String) = pin == Kiosk.pin(getApplication())

    fun setCopies(n: Int) = _state.update { it.copy(copies = n.coerceIn(1, maxOf(1, MAX_COPIES - it.printedCopies))) }

    fun backToHome() {
        // Upload fehlgeschlagen und Kunde geht weiter: Foto in die Warteschlange, nichts geht verloren.
        digitalJpeg?.let { if (_state.value.upload is UploadState.Failed) { queue.add(it); refreshQueue() } }
        digitalJpeg = null
        _state.update {
            it.copy(screen = Screen.Welcome, processing = null, historyId = null, printedCopies = 0, guestBackgroundId = null, backgroundNote = null, photo = null, digital = null, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, savedUri = null, message = null)
        }
    }

    fun showError(text: String) {
        _state.update { it.copy(screen = Screen.Welcome, message = text, processing = null) }
        // Meldung nach einigen Sekunden ausblenden, damit der Startbildschirm sauber bleibt.
        viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            _state.update { if (it.message == text) it.copy(message = null) else it }
        }
    }

    fun onPhotoCaptured(file: File) {
        // Rueckmeldung fuer den Gast: das Foto ist gemacht, jetzt wird es verarbeitet (dauert einige Sekunden).
        _state.update { it.copy(processing = "Ihr Foto wird vorbereitet …") }
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.Default) {
                runCatching {
                    val captured = decodeUpright(file)
                    val config = _state.value.config
                    val backgroundId = if (config.backgroundGuestChoice) _state.value.guestBackgroundId ?: config.backgroundId else config.backgroundId
                    if (backgroundId.isNotBlank()) _state.update { it.copy(processing = "Sie werden freigestellt und vor Ihren Hintergrund gesetzt …") }
                    val upright = applyBackground(captured, backgroundId, config.backgroundDiagnostics, config.backgroundHairModel)
                    _state.update { it.copy(processing = "Ihr Foto wird fertiggestellt …") }
                    val style = BannerStyle.from(config)
                    val print = PhotoComposer.compose(getApplication(), upright, style)
                    val digital = PhotoComposer.composeDigital(getApplication(), upright, style)
                    fun jpeg(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                    val digitalBytes = jpeg(digital)
                    // Zuerst auf den Speicher: Druck und erneutes Drucken lesen das Foto von dort, es geht nicht mehr verloren.
                    val entry = history.add(jpeg(print), digitalBytes)
                    history.prune(config.historyCount, config.historyHours)
                    Prepared(print, digital, digitalBytes, entry)
                }
            }
            file.delete()
            prepared.fold(
                onSuccess = { p ->
                    digitalJpeg = p.digitalJpeg
                    _state.update {
                        it.copy(screen = Screen.Result, processing = null, historyId = p.entry.id, printedCopies = 0, backgroundNote = backgroundNote, photo = p.print, digital = p.digital, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, savedUri = null, message = null)
                    }
                    refreshHistory()
                    val upload = startUpload()
                    // Beim Wechsel ins Drucker-WLAN zuerst hochladen (braucht Internet), dann drucken.
                    if (_state.value.config.autoPrint) {
                        viewModelScope.launch { if (printerWifiActive()) upload?.join(); printPhoto() }
                    }
                },
                onFailure = { e -> showError("Foto konnte nicht verarbeitet werden: ${e.message}") },
            )
        }
    }

    fun uploadPhoto() { startUpload() }

    private fun startUpload(): Job? {
        val jpeg = digitalJpeg ?: return null
        val id = _state.value.historyId
        val config = _state.value.config
        if (!uploadConfigured(config)) {
            _state.update { it.copy(upload = UploadState.Failed("Upload ist nicht eingerichtet (Setup → Konfiguration).")) }
            if (id != null) markUpload(id, "failed", "")
            return null
        }
        _state.update { it.copy(upload = UploadState.Uploading) }
        return viewModelScope.launch {
            val result = uploadTo(config, "foto-${System.currentTimeMillis()}.jpg", jpeg)
            // Nur der Bildschirm dieses Fotos darf den Zustand aendern: ein spaet fertiger Upload darf dem naechsten Gast keinen fremden QR-Code zeigen.
            result.fold(
                onSuccess = { url ->
                    val qr = withContext(Dispatchers.Default) { QrCode.generate(url) }
                    if (_state.value.historyId == id) _state.update { it.copy(upload = UploadState.Done(url), qrBitmap = qr, qrInput = url) }
                    if (id != null) markUpload(id, "done", url)
                },
                onFailure = { e ->
                    // Hat der Kunde den Ergebnis-Bildschirm schon verlassen, geht das Foto in die Warteschlange.
                    if (_state.value.historyId != id) { queue.add(jpeg); refreshQueue() }
                    else _state.update { it.copy(upload = UploadState.Failed(e.message ?: "Upload fehlgeschlagen")) }
                    if (id != null) markUpload(id, "failed", "")
                },
            )
        }
    }

    private fun markUpload(id: String, status: String, url: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.update(id) { it.copy(uploadStatus = status, uploadUrl = url) } }
            refreshHistory()
        }
    }

    /** Direktdruck per IPP; bei Fehler zeigt die Oberflaeche den Android-Druckdialog als Ausweg an. */
    private fun printerWifiActive(c: WcmConfig = _state.value.config) = c.printerWifiSwitch && c.printerWifiSsid.isNotBlank()

    /** Verbindet voruebergehend mit dem Drucker-WLAN, druckt und gibt es wieder frei (Android kehrt ins Internet-WLAN zurueck). */
    private suspend fun printViaPrinterWifi(config: WcmConfig, img: PrintImage, copies: Int, stage: (String) -> Unit): Result<String> {
        stage("Verbinde mit Drucker-WLAN … (bitte ggf. «Verbinden» bestätigen)")
        val result = printerNetwork.use(config.printerWifiSsid, config.printerWifiPassword) { network ->
            stage("Foto wird gedruckt …")
            val client = OkHttpClient.Builder().socketFactory(network.socketFactory)
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
            // Eigene Instanz: ohne mDNS, feste Adresse des WCMPlus im Drucker-WLAN.
            IppPrinter(getApplication(), useMdns = false, http = client)
                .printJpeg(config, img.bytes, img.width, img.height, copies).getOrThrow()
        }
        stage("Zurück ins Internet-WLAN …")
        kotlinx.coroutines.delay(2500)
        return result
    }

    /** «DRUCKEN» auf dem Ergebnis-Bildschirm (und Auto-Druck): das aktuelle Foto mit der gewaehlten Zahl Abzuege. */
    fun printPhoto() {
        val s = _state.value
        val id = s.historyId ?: return
        if (s.print is PrintState.Printing) return // Doppeltippen startet keinen zweiten Auftrag
        val remaining = MAX_COPIES - s.printedCopies
        if (remaining <= 0) return // alle erlaubten Abzuege dieses Fotos sind gedruckt
        // Sofort (noch vor dem Speichern) auf «laeuft» stellen, damit auch sehr schnelle Taps nichts Zweites starten.
        _state.update { it.copy(print = PrintState.Printing("Druckauftrag wartet …")) }
        enqueuePrint(id, minOf(s.copies, remaining))
    }

    /** Erneut drucken aus dem Foto-Verlauf (Setup). */
    fun printEntry(id: String, copies: Int) {
        val busy = _state.value.history.firstOrNull { it.id == id }?.printStatus in listOf("pending", "printing")
        if (busy) { _state.update { it.copy(message = "Für dieses Foto läuft schon ein Druckauftrag.") }; return }
        enqueuePrint(id, copies)
        _state.update { it.copy(message = "Druckauftrag gesendet (bei Fehlern bis zu $PRINT_RETRIES Wiederholungen mit je 1 Abzug).") }
    }

    /** Legt den Auftrag dauerhaft ab (Status «pending» im Verlauf) und fuehrt ihn aus, sobald kein anderer Druck laeuft. */
    private fun enqueuePrint(id: String, copies: Int) {
        val n = copies.coerceIn(1, MAX_COPIES)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.update(id) { it.copy(copies = n, printStatus = "pending", printDetail = "Auftrag wartet") } }
            refreshHistory()
            if (_state.value.historyId == id) _state.update { it.copy(print = PrintState.Printing("Druckauftrag wartet …")) }
            printLock.withLock { runPrintJob(id, n) }
        }
    }

    /**
     * Druckt das Foto aus dem Verlauf. Schlaegt der erste Versuch fehl, folgen bis zu [PRINT_RETRIES] Wiederholungen mit je
     * einem Abzug (nicht noch einmal die ganze Menge). Das Ergebnis steht im Verlauf; der Ergebnis-Bildschirm zeigt es nur,
     * solange dieses Foto noch angezeigt wird.
     */
    private suspend fun runPrintJob(id: String, copies: Int) {
        val config = _state.value.config
        fun ui(change: (UiState) -> UiState) { if (_state.value.historyId == id) _state.update(change) }
        fun stage(text: String) = ui { it.copy(print = PrintState.Printing(text)) }
        suspend fun finish(ok: Boolean, detail: String, sent: Int = 0) {
            withContext(Dispatchers.IO) {
                history.update(id) { it.copy(printStatus = if (ok) "done" else "failed", printDetail = detail, printedAt = System.currentTimeMillis()) }
            }
            ui {
                val printed = it.printedCopies + sent
                it.copy(
                    print = if (ok) PrintState.Done(detail) else PrintState.Failed(detail),
                    printedCopies = printed,
                    copies = it.copies.coerceIn(1, maxOf(1, MAX_COPIES - printed)),
                )
            }
            refreshHistory()
        }

        val jpeg = withContext(Dispatchers.IO) { history.printFile(id).takeIf { it.exists() }?.readBytes() }
        if (jpeg == null) { finish(false, "Das Foto ist nicht mehr gespeichert."); return }
        withContext(Dispatchers.IO) { history.update(id) { it.copy(printStatus = "printing", printDetail = "Druck läuft") } }
        refreshHistory()
        stage("Foto wird gedruckt …")

        val useWifi = printerWifiActive(config)
        if (useWifi) {
            // Upload braucht Internet: erst fertig werden lassen, bevor das WLAN gewechselt wird.
            kotlinx.coroutines.withTimeoutOrNull(30_000) { _state.first { it.upload !is UploadState.Uploading } }
        }
        val img = withContext(Dispatchers.Default) { encodeJpegForPrint(jpeg, config.printRotation) }

        val attempts = 1 + PRINT_RETRIES
        var result: Result<String> = Result.failure(IllegalStateException("kein Versuch"))
        var attempt = 0
        var sent = 0
        while (attempt < attempts) {
            attempt++
            val n = if (attempt == 1) copies else 1
            if (attempt > 1) {
                stage("Neuer Versuch $attempt von $attempts … (1 Abzug)")
                kotlinx.coroutines.delay(PRINT_RETRY_PAUSE_MS)
            }
            result = if (useWifi) printViaPrinterWifi(config, img, n, ::stage)
            else printer.printJpeg(config, img.bytes, img.width, img.height, n)
            if (result.isSuccess) { sent = n; break }
            Log.w(TAG, "Druck Versuch $attempt von $attempts fehlgeschlagen: ${result.exceptionOrNull()?.message}")
        }
        result.fold(
            onSuccess = { text ->
                finish(true, if (attempt == 1) text else "$text (nach Versuch $attempt, nur 1 Abzug; gewünscht waren $copies)", sent)
            },
            onFailure = { e ->
                finish(false, "${e.message ?: "Drucken fehlgeschlagen"} (nach $attempts Versuchen)")
            },
        )
        if (useWifi) refreshQueue()
    }

    /** Setup-Test: ins Drucker-WLAN wechseln, Warteschlangen suchen, zurueck. */
    fun testPrinterWifi() {
        val config = _state.value.config
        if (!printerWifiActive(config)) { _state.update { it.copy(printerTest = "Bitte Drucker-WLAN-Name (SSID) eintragen und den Schalter einschalten.") }; return }
        _state.update { it.copy(printerTest = "Verbinde mit «${config.printerWifiSsid}» … (bitte ggf. «Verbinden» bestätigen)") }
        viewModelScope.launch {
            val result = printerNetwork.use(config.printerWifiSsid, config.printerWifiPassword) { network ->
                val client = OkHttpClient.Builder().socketFactory(network.socketFactory)
                    .connectTimeout(5, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
                IppPrinter(getApplication(), useMdns = false, http = client).discoverAll(config).getOrThrow()
            }
            val text = result.fold(
                onSuccess = { list -> "✓ Drucker-WLAN erreichbar. Gefundene Druckwarteschlangen:\n" + list.joinToString("\n") { f -> "• ${f.queueName}" } },
                onFailure = { e -> "✗ ${e.message}" },
            )
            _state.update { it.copy(printerTest = text) }
        }
    }

    fun testPrinter() {
        _state.update { it.copy(printerTest = "Suche Drucker …") }
        viewModelScope.launch {
            val result = printer.discoverAll(_state.value.config)
            val all = result.getOrNull().orEmpty()
            _state.update { it.copy(printerQueues = all.map { f -> f.queueName }) }
            val text = result.fold(
                onSuccess = { list ->
                    val used = printer.pick(list, _state.value.config)
                    "Gefundene Druckwarteschlangen:\n" + list.joinToString("\n") { f -> "• ${f.queueName}  (${f.ippUri})" } +
                        "\nVerwendet wird: ${used.queueName}\n" +
                        listOf("printer-state", "printer-state-reasons", "document-format-supported")
                            .joinToString("\n") { k -> "$k: " + used.info.attrs[k].orEmpty().joinToString(", ").take(300) }
                },
                onFailure = { e -> e.message ?: "Fehler" },
            )
            _state.update { it.copy(printerTest = text) }
        }
    }

    /** Upload-Test mit Platzhalterfoto; zeigt Link + QR-Code (zum Gegenpruefen mit dem Handy) oder den genauen Fehler. */
    fun testUpload() {
        val config = _state.value.config
        if (!uploadConfigured(config)) {
            _state.update { it.copy(uploadTestText = "✗ Upload ist nicht eingerichtet: bitte die Felder oben ausfüllen.", uploadTestQr = null) }
            return
        }
        _state.update { it.copy(uploadTestText = "Test läuft …", uploadTestQr = null) }
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) {
                val style = BannerStyle.from(config).let { if (it.textLeft.isBlank() && it.textRight.isBlank()) it.copy(textRight = "Test") else it }
                val bmp = PhotoComposer.previewDigital(getApplication(), style, 900)
                ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            }
            val result = uploadTo(config, "test-${System.currentTimeMillis()}.jpg", jpeg)
            result.fold(
                onSuccess = { url ->
                    val qr = withContext(Dispatchers.Default) { QrCode.generate(url, 600) }
                    _state.update {
                        it.copy(uploadTestText = "✓ Upload erfolgreich\n$url\nQR-Code mit dem Handy scannen: er öffnet das Testfoto.", uploadTestQr = qr)
                    }
                },
                onFailure = { e -> _state.update { it.copy(uploadTestText = "✗ ${e.message ?: "Upload fehlgeschlagen"}", uploadTestQr = null) } },
            )
        }
    }

    class PrintImage(val bytes: ByteArray, val width: Int, val height: Int)

    /** Druck-Bild nach Einstellung drehen und als JPEG kodieren (mit Bildmassen fuer das PDF). */
    private fun encodeForPrint(bmp: Bitmap, rotation: Int, original: ByteArray?): PrintImage {
        if (rotation % 360 == 0 && original != null) return PrintImage(original, bmp.width, bmp.height)
        val rotated = if (rotation % 360 == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val jpeg = ByteArrayOutputStream().also { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        return PrintImage(jpeg, rotated.width, rotated.height)
    }

    /** Wie [encodeForPrint], aber ab dem gespeicherten JPEG des Verlaufs. */
    private fun encodeJpegForPrint(jpeg: ByteArray, rotation: Int): PrintImage {
        if (rotation % 360 == 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            return PrintImage(jpeg, bounds.outWidth, bounds.outHeight)
        }
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: error("Bild nicht lesbar")
        return encodeForPrint(bmp, rotation, null)
    }

    // ---- Foto-Verlauf (Setup, Reiter «Fotos») ----

    /** Raeumt nach Anzahl und Alter auf und liest den Verlauf neu ein. */
    fun refreshHistory() {
        viewModelScope.launch {
            val config = _state.value.config
            val list = withContext(Dispatchers.IO) {
                history.prune(config.historyCount, config.historyHours)
                history.list()
            }
            _state.update { it.copy(history = list) }
        }
    }

    fun reuploadEntry(id: String) {
        val config = _state.value.config
        if (!uploadConfigured(config)) { _state.update { it.copy(message = "Upload ist nicht eingerichtet.") }; return }
        viewModelScope.launch {
            _state.update { it.copy(message = "Foto wird hochgeladen …") }
            val jpeg = withContext(Dispatchers.IO) { history.digitalFile(id).takeIf { it.exists() }?.readBytes() }
            if (jpeg == null) { _state.update { it.copy(message = "Das Foto ist nicht mehr gespeichert.") }; return@launch }
            uploadTo(config, "foto-${System.currentTimeMillis()}.jpg", jpeg).fold(
                onSuccess = { url ->
                    withContext(Dispatchers.IO) { history.update(id) { it.copy(uploadStatus = "done", uploadUrl = url) } }
                    _state.update { it.copy(message = "Hochgeladen. Der neue QR-Code steht bei diesem Foto.") }
                },
                onFailure = { e ->
                    withContext(Dispatchers.IO) { history.update(id) { it.copy(uploadStatus = "failed") } }
                    _state.update { it.copy(message = "Upload fehlgeschlagen: ${e.message ?: "unbekannt"}") }
                },
            )
            refreshHistory()
        }
    }

    fun exportEntry(id: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { history.exportToDownloads(id) } }
            _state.update {
                it.copy(message = result.fold(
                    onSuccess = { n -> "$n Dateien im Ordner «Downloads/Fotobox» gespeichert." },
                    onFailure = { e -> "Exportieren fehlgeschlagen: ${e.message}" },
                ))
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.delete(id) }
            refreshHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            // Wartende oder laufende Druckauftraege bleiben (der Druck ist Pflicht).
            withContext(Dispatchers.IO) {
                history.list().filter { it.printStatus != "pending" && it.printStatus != "printing" }.forEach { history.delete(it.id) }
            }
            refreshHistory()
        }
    }

    // ---- Upload-Warteschlange ----

    fun refreshQueue() = _state.update { it.copy(queueCount = queue.count()) }

    fun retryQueue() {
        val config = _state.value.config
        if (!uploadConfigured(config)) { _state.update { it.copy(message = "Upload ist nicht eingerichtet.") }; return }
        viewModelScope.launch {
            val files = queue.files()
            var sent = 0
            for (f in files) {
                val ok = uploadTo(config, f.name, f.readBytes()).isSuccess
                if (!ok) break
                f.delete(); sent++
            }
            _state.update { it.copy(queueCount = queue.count(), message = "$sent von ${files.size} Fotos gesendet.") }
        }
    }

    fun clearQueue() {
        queue.clear()
        refreshQueue()
    }

    /** Testdruck mit Platzhalterfoto und Banner, um Drucker und Layout zu pruefen. */
    fun testPrint() {
        _state.update { it.copy(printerTest = "Testdruck wird gesendet …") }
        viewModelScope.launch {
            val img = withContext(Dispatchers.Default) {
                val style = BannerStyle.from(_state.value.config).let { if (it.textLeft.isBlank() && it.textRight.isBlank()) it.copy(textRight = "Testdruck") else it }
                val bmp = PhotoComposer.preview(getApplication(), style, width = 1800)
                encodeForPrint(bmp, _state.value.config.printRotation, null)
            }
            val result = printer.printJpeg(_state.value.config, img.bytes, img.width, img.height, copies = 1)
            _state.update { it.copy(printerTest = result.fold({ it }, { e -> e.message ?: "Testdruck fehlgeschlagen" })) }
        }
    }

    // ---- Setup ----

    fun setPin(pin: String) {
        if (pin.length >= 4) Kiosk.setPin(getApplication(), pin)
        _state.update { it.copy(message = if (pin.length >= 4) "PIN gespeichert." else "PIN braucht mindestens 4 Zeichen.") }
    }

    private fun decodeUpright(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Bild nicht lesbar")
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }

    // ---- QR-Code aus manuell eingefuegtem SharePoint-Link ----

    fun setQrInput(text: String) =
        _state.update { it.copy(qrInput = text, qrBitmap = null, savedUri = null, message = null) }

    /** Wird aufgerufen, wenn ein Link per "Teilen" (z. B. aus SharePoint) uebergeben wird. */
    fun receiveSharedText(text: String) {
        setQrInput(text.trim())
        generateQr()
    }

    fun generateQr() {
        val text = _state.value.qrInput.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(message = "Bitte zuerst einen SharePoint-Link einfügen.") }
            return
        }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { QrCode.generate(text) }
            _state.update { it.copy(qrBitmap = bmp, savedUri = null, message = null) }
        }
    }

    fun downloadQr() {
        val bmp = _state.value.qrBitmap ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { QrCode.saveToDownloads(getApplication(), bmp) }
            }
            _state.update {
                result.fold(
                    onSuccess = { uri -> it.copy(savedUri = uri, message = "QR-Code im Ordner «Downloads» gespeichert.") },
                    onFailure = { e -> it.copy(message = "Speichern fehlgeschlagen: ${e.message}") },
                )
            }
        }
    }
}
