package ch.sonnhalde.fotobox.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ch.sonnhalde.fotobox.ui.Notice
import ch.sonnhalde.fotobox.ui.Sonn
import ch.sonnhalde.fotobox.ui.SonnhaldeBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun cameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { runCatching { future.get() }.fold(cont::resume, cont::resumeWithException) },
            ContextCompat.getMainExecutor(context),
        )
    }

/**
 * Startbildschirm der Fotobox: Live-Kamerabild im Hintergrund, ein Tipp irgendwo startet
 * Countdown und Foto. Langer Druck auf den Banner oeffnet die Verwaltung.
 */
@Composable
fun CameraScreen(
    useFront: Boolean,
    timerSeconds: Int,
    message: String?,
    onCaptured: (File) -> Unit,
    onFailure: (String) -> Unit,
    onAdmin: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    // Niemand tippt mehr: zurueck zum Startbildschirm.
    LaunchedEffect(Unit) { delay(60_000); if (countdown == null) onBack() }

    LaunchedEffect(hasPermission, useFront) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val p = cameraProvider(context)
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: IllegalArgumentException) {
                // Gewuenschte Kamera fehlt (z. B. Tablet ohne Frontkamera): andere Kamera versuchen.
                val other = if (useFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                p.bindToLifecycle(lifecycleOwner, other, preview, imageCapture)
            }
        } catch (e: Exception) {
            onFailure("Kamera konnte nicht gestartet werden: ${e.message}")
        }
    }
    // Kamera freigeben, sobald der Startbildschirm verlassen wird.
    DisposableEffect(provider) { onDispose { provider?.unbindAll() } }

    fun shoot() {
        if (countdown != null || !hasPermission) return
        scope.launch {
            for (i in timerSeconds downTo 1) { countdown = i; delay(1000) }
            countdown = null
            val file = File.createTempFile("foto", ".jpg", context.cacheDir)
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(file)
                    override fun onError(exc: ImageCaptureException) = onFailure("Foto fehlgeschlagen: ${exc.message}")
                },
            )
        }
    }

    Box(
        Modifier.fillMaxSize().background(Sonn.NavyDeep)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { shoot() },
    ) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                "Bitte Kamera-Zugriff erlauben.",
                color = Sonn.Cream, fontSize = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        Column(Modifier.align(Alignment.TopCenter)) {
            SonnhaldeBanner(title = "Fotobox", onLongPress = onAdmin)
            message?.let { Box(Modifier.padding(16.dp)) { Notice(it) } }
        }

        Text(
            "‹ Zurück", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color(0x66000000)).clickable(onClick = onBack).padding(horizontal = 18.dp, vertical = 12.dp),
        )

        val c = countdown
        if (c != null) {
            Text(
                text = c.toString(), color = Sonn.LogoGold, fontSize = 160.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Foto aufnehmen",
                    color = Sonn.Navy, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Sonn.LogoGold)
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                )
            }
        }
    }
}
