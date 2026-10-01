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
import ch.sonnhalde.fotobox.print.IppPrinter
import ch.sonnhalde.fotobox.qr.QrCode
import ch.sonnhalde.fotobox.upload.SharePointUploader
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

enum class Screen { Home, Camera, Result }

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
    val screen: Screen = Screen.Home,
    val photo: Bitmap? = null,
    val upload: UploadState = UploadState.Idle,
    val print: PrintState = PrintState.Idle,
    /** Verwaltungsbereich (Einstellungen, QR aus Link, Kiosk beenden) nach PIN-Eingabe sichtbar. */
    val adminUnlocked: Boolean = false,
    val kioskOn: Boolean = true,
    val printerTest: String? = null,
    val qrInput: String = "",
    val qrBitmap: Bitmap? = null,
    val savedUri: Uri? = null,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = WcmSettings(app)
    private val client = WcmClient()
    private val uploader = SharePointUploader()
    private val printer = IppPrinter()
    private var photoJpeg: ByteArray? = null

    private val _state = MutableStateFlow(UiState(config = settings.load(), kioskOn = Kiosk.isEnabled(app)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { checkConnection() }

    fun updateConfig(config: WcmConfig) {
        settings.save(config)
        _state.update { it.copy(config = config) }
        checkConnection()
    }

    fun checkConnection() {
        _state.update { it.copy(status = WcmStatus.Checking) }
        viewModelScope.launch {
            val status = client.checkConnection(_state.value.config)
            _state.update { it.copy(status = status) }
        }
    }

    // ---- Foto-Ablauf: Kamera -> Ergebnis (Upload, QR, Druck) ----

    fun openCamera() = _state.update { it.copy(screen = Screen.Camera, message = null) }

    fun backToHome() {
        photoJpeg = null
        _state.update {
            it.copy(screen = Screen.Home, photo = null, upload = UploadState.Idle, print = PrintState.Idle, qrBitmap = null, savedUri = null, message = null)
        }
    }

    fun showError(text: String) = _state.update { it.copy(screen = Screen.Home, message = text) }

    fun onPhotoCaptured(file: File) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.Default) {
                runCatching {
                    val bmp = decodeUpright(file)
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    bmp to out.toByteArray()
                }
            }
            file.delete()
            prepared.fold(
                onSuccess = { (bmp, jpeg) ->
                    photoJpeg = jpeg
                    _state.update {
                        it.copy(screen = Screen.Result, photo = bmp, upload = UploadState.Idle, print = PrintState.Idle, qrBitmap = null, savedUri = null, message = null)
                    }
                    uploadPhoto()
                },
                onFailure = { e -> showError("Foto konnte nicht verarbeitet werden: ${e.message}") },
            )
        }
    }

    fun uploadPhoto() {
        val jpeg = photoJpeg ?: return
        val flowUrl = _state.value.config.flowUrl
        if (flowUrl.isBlank()) {
            _state.update { it.copy(upload = UploadState.Failed("Kein Upload-Link eingestellt (Einstellungen).")) }
            return
        }
        _state.update { it.copy(upload = UploadState.Uploading) }
        viewModelScope.launch {
            val result = uploader.upload(flowUrl, "foto-${System.currentTimeMillis()}.jpg", jpeg)
            result.fold(
                onSuccess = { url ->
                    val qr = withContext(Dispatchers.Default) { QrCode.generate(url) }
                    _state.update { it.copy(upload = UploadState.Done(url), qrBitmap = qr, qrInput = url) }
                },
                onFailure = { e -> _state.update { it.copy(upload = UploadState.Failed(e.message ?: "Upload fehlgeschlagen")) } },
            )
        }
    }

    /** Direktdruck per IPP; bei Fehler zeigt die Oberflaeche den Android-Druckdialog als Ausweg an. */
    fun printPhoto() {
        val jpeg = photoJpeg ?: return
        val landscape = (_state.value.photo?.let { it.width >= it.height }) ?: true
        _state.update { it.copy(print = PrintState.Printing) }
        viewModelScope.launch {
            val result = printer.printJpeg(_state.value.config, jpeg, landscape)
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
            val result = printer.discover(_state.value.config)
            val text = result.fold(
                onSuccess = { f ->
                    "Gefunden: ${f.ippUri}\n" + listOf("printer-name", "printer-state", "printer-state-reasons", "document-format-supported", "media-supported")
                        .joinToString("\n") { k -> "$k: " + f.info.attrs[k].orEmpty().joinToString(", ").take(300) }
                },
                onFailure = { e -> e.message ?: "Fehler" },
            )
            _state.update { it.copy(printerTest = text) }
        }
    }

    // ---- Verwaltungsbereich / Kiosk ----

    fun unlockAdmin(pin: String): Boolean {
        val ok = pin == Kiosk.pin(getApplication())
        if (ok) _state.update { it.copy(adminUnlocked = true) }
        return ok
    }

    fun lockAdmin() = _state.update { it.copy(adminUnlocked = false, printerTest = null) }

    fun setPin(pin: String) {
        if (pin.length >= 4) Kiosk.setPin(getApplication(), pin)
        _state.update { it.copy(message = if (pin.length >= 4) "PIN gespeichert." else "PIN braucht mindestens 4 Zeichen.") }
    }

    fun kioskChanged(on: Boolean) = _state.update { it.copy(kioskOn = on) }

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
        _state.update { it.copy(screen = Screen.Home) }
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
