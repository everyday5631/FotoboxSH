package ch.sonnhalde.fotobox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ch.sonnhalde.fotobox.ui.Actions
import ch.sonnhalde.fotobox.ui.MainScreen
import ch.sonnhalde.fotobox.ui.SonnhaldeTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            SonnhaldeTheme {
                val state by vm.state.collectAsState()
                MainScreen(
                    state = state,
                    actions = Actions(
                        onConfigChange = vm::updateConfig,
                        onCheckConnection = vm::checkConnection,
                        onQrInput = vm::setQrInput,
                        onGenerate = vm::generateQr,
                        onDownload = vm::downloadQr,
                        onOpenCamera = vm::openCamera,
                        onPhotoCaptured = vm::onPhotoCaptured,
                        onCameraError = vm::showError,
                        onRetryUpload = vm::uploadPhoto,
                        onHome = vm::backToHome,
                    ),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::receiveSharedText)
        }
    }
}
