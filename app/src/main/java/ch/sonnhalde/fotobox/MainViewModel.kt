package ch.sonnhalde.fotobox

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.sonnhalde.fotobox.qr.QrCode
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

data class UiState(
    val config: WcmConfig = WcmConfig(),
    val status: WcmStatus = WcmStatus.Unknown,
    val qrInput: String = "",
    val qrBitmap: Bitmap? = null,
    val savedUri: Uri? = null,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = WcmSettings(app)
    private val client = WcmClient()

    private val _state = MutableStateFlow(UiState(config = settings.load()))
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
