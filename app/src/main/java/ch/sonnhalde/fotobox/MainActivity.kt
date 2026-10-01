package ch.sonnhalde.fotobox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ch.sonnhalde.fotobox.kiosk.Kiosk
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
                        onSplashDone = vm::finishSplash,
                        onPhotoCaptured = vm::onPhotoCaptured,
                        onCameraError = vm::showError,
                        onRetryUpload = vm::uploadPhoto,
                        onHome = vm::backToHome,
                        onPrint = vm::printPhoto,
                        onTestPrinter = vm::testPrinter,
                        onUnlockAdmin = vm::unlockAdmin,
                        onLockAdmin = vm::lockAdmin,
                        onExitKiosk = ::toggleKiosk,
                        onSetPin = vm::setPin,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Kiosk.apply(this)
    }

    /** Im Kiosk-Modus ist die Zurueck-Taste gesperrt. */
    @Deprecated("Zurueck-Taste im Kiosk sperren")
    override fun onBackPressed() {
        if (!Kiosk.isEnabled(this)) super.onBackPressed()
    }

    private fun toggleKiosk() {
        if (Kiosk.isEnabled(this)) {
            Kiosk.exit(this)
        } else {
            Kiosk.setEnabled(this, true)
            Kiosk.apply(this)
        }
        vm.kioskChanged(Kiosk.isEnabled(this))
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
