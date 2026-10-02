package ch.sonnhalde.fotobox

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.sonnhalde.fotobox.kiosk.Kiosk
import ch.sonnhalde.fotobox.photo.PhotoComposer
import ch.sonnhalde.fotobox.print.IppPrinter
import ch.sonnhalde.fotobox.qr.QrCode
import ch.sonnhalde.fotobox.upload.NextcloudUploader
import ch.sonnhalde.fotobox.upload.SharePointUploader
import ch.sonnhalde.fotobox.upload.UploadQueue
import ch.sonnhalde.fotobox.wcm.WcmClient
import ch.sonnhalde.fotobox.wcm.WcmConfig
import ch.sonnhalde.fotobox.wcm.WcmSettings
import ch.sonnhalde.fotobox.wcm.WcmStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    data object Printing : PrintState
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
)

const val MAX_COPIES = 3

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = WcmSettings(app)
    private val client = WcmClient()
    private val uploader = SharePointUploader()
    private val nextcloud = NextcloudUploader()
    private val queue = UploadQueue(app)
    private val printer = IppPrinter(app)
    /** Gemeinsamer Einstieg fuer beide Upload-Ziele. */
    private suspend fun uploadTo(config: WcmConfig, name: String, jpeg: ByteArray): Result<String> =
        if (config.uploadTarget == "nextcloud") nextcloud.upload(config, name, jpeg) else uploader.upload(config.flowUrl, name, jpeg)

    private fun uploadConfigured(c: WcmConfig) =
        if (c.uploadTarget == "nextcloud") c.ncUrl.isNotBlank() && c.ncUser.isNotBlank() && c.ncPassword.isNotBlank() else c.flowUrl.isNotBlank()

    private var photoJpeg: ByteArray? = null
    private var digitalJpeg: ByteArray? = null

    private val _state = MutableStateFlow(UiState(config = settings.load(), retail = Kiosk.isEnabled(app), queueCount = UploadQueue(app).count()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { checkConnection() }

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
        photoJpeg = null
        digitalJpeg = null
        _state.update { it.copy(retail = false, screen = Screen.Overview, photo = null, digital = null, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, message = null) }
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

    fun startBackgroundFile() = File(getApplication<Application>().filesDir, "start_bg.jpg")

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings, message = null) }
    fun closeSettings() = _state.update { it.copy(screen = Screen.Overview, printerTest = null, message = null) }

    fun checkPin(pin: String) = pin == Kiosk.pin(getApplication())

    fun setCopies(n: Int) = _state.update { it.copy(copies = n.coerceIn(1, MAX_COPIES)) }

    fun backToHome() {
        // Upload fehlgeschlagen und Kunde geht weiter: Foto in die Warteschlange, nichts geht verloren.
        digitalJpeg?.let { if (_state.value.upload is UploadState.Failed) { queue.add(it); refreshQueue() } }
        photoJpeg = null
        digitalJpeg = null
        _state.update {
            it.copy(screen = Screen.Welcome, photo = null, digital = null, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, savedUri = null, message = null)
        }
    }

    fun showError(text: String) {
        _state.update { it.copy(screen = Screen.Welcome, message = text) }
        // Meldung nach einigen Sekunden ausblenden, damit der Startbildschirm sauber bleibt.
        viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            _state.update { if (it.message == text) it.copy(message = null) else it }
        }
    }

    fun onPhotoCaptured(file: File) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.Default) {
                runCatching {
                    val upright = decodeUpright(file)
                    val text = _state.value.config.bannerText
                    val print = PhotoComposer.compose(getApplication(), upright, text)
                    val digital = PhotoComposer.composeDigital(upright, text)
                    fun jpeg(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                    Triple(print, digital, jpeg(print) to jpeg(digital))
                }
            }
            file.delete()
            prepared.fold(
                onSuccess = { (bmp, digital, jpegs) ->
                    photoJpeg = jpegs.first
                    digitalJpeg = jpegs.second
                    _state.update {
                        it.copy(screen = Screen.Result, photo = bmp, digital = digital, upload = UploadState.Idle, print = PrintState.Idle, copies = 1, qrBitmap = null, savedUri = null, message = null)
                    }
                    uploadPhoto()
                    if (_state.value.config.autoPrint) printPhoto()
                },
                onFailure = { e -> showError("Foto konnte nicht verarbeitet werden: ${e.message}") },
            )
        }
    }

    fun uploadPhoto() {
        val jpeg = digitalJpeg ?: return
        val config = _state.value.config
        if (!uploadConfigured(config)) {
            _state.update { it.copy(upload = UploadState.Failed("Upload ist nicht eingerichtet (Setup → Konfiguration).")) }
            return
        }
        _state.update { it.copy(upload = UploadState.Uploading) }
        viewModelScope.launch {
            val result = uploadTo(config, "foto-${System.currentTimeMillis()}.jpg", jpeg)
            result.fold(
                onSuccess = { url ->
                    val qr = withContext(Dispatchers.Default) { QrCode.generate(url) }
                    _state.update { it.copy(upload = UploadState.Done(url), qrBitmap = qr, qrInput = url) }
                },
                onFailure = { e ->
                    // Hat der Kunde den Ergebnis-Bildschirm schon verlassen, geht das Foto in die Warteschlange.
                    if (_state.value.screen != Screen.Result) { queue.add(jpeg); refreshQueue() }
                    if (_state.value.screen == Screen.Result) {
                        _state.update { it.copy(upload = UploadState.Failed(e.message ?: "Upload fehlgeschlagen")) }
                    }
                },
            )
        }
    }

    /** Direktdruck per IPP; bei Fehler zeigt die Oberflaeche den Android-Druckdialog als Ausweg an. */
    fun printPhoto() {
        val jpeg = photoJpeg ?: return
        val bmp = _state.value.photo ?: return
        _state.update { it.copy(print = PrintState.Printing) }
        viewModelScope.launch {
            val img = withContext(Dispatchers.Default) { encodeForPrint(bmp, _state.value.config.printRotation, jpeg) }
            val result = printer.printJpeg(_state.value.config, img.bytes, img.width, img.height, _state.value.copies)
            _state.update {
                it.copy(print = result.fold(
                    onSuccess = { text -> PrintState.Done(text) },
                    onFailure = { e -> PrintState.Failed(e.message ?: "Drucken fehlgeschlagen") },
                ))
            }
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
                val bmp = PhotoComposer.composeDigital(PhotoComposer.preview(getApplication(), ""), config.bannerText.ifBlank { "Test" }, 900)
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
                val bmp = PhotoComposer.preview(getApplication(), _state.value.config.bannerText.ifBlank { "Testdruck" }, width = 1800)
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
