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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ch.sonnhalde.fotobox.ui.SonnButton
import ch.sonnhalde.fotobox.ui.Sonn
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

/** Kamera-Vorschau mit 3-2-1-Countdown und Ausloeser. */
@Composable
fun CameraScreen(onCaptured: (File) -> Unit, onCancel: () -> Unit, onFailure: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var useFront by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(hasPermission, useFront) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val provider = cameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: IllegalArgumentException) {
                // Gewuenschte Kamera fehlt (z. B. Tablet ohne Frontkamera): andere Kamera versuchen.
                val other = if (useFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                provider.bindToLifecycle(lifecycleOwner, other, preview, imageCapture)
            }
        } catch (e: Exception) {
            onFailure("Kamera konnte nicht gestartet werden: ${e.message}")
        }
    }

    fun shoot() {
        if (countdown != null) return
        scope.launch {
            for (i in 3 downTo 1) { countdown = i; delay(1000) }
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

    Column(Modifier.fillMaxSize().background(Sonn.NavyDeep).navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (hasPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            } else {
                Text("Kamera-Berechtigung wird benötigt.", color = Sonn.Cream, modifier = Modifier.padding(24.dp))
            }
            countdown?.let { Text(it.toString(), color = Sonn.LogoGold, fontSize = 120.sp) }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            SonnButton("Abbrechen", primary = false, onDark = true, onClick = onCancel)
            SonnButton("Foto aufnehmen", primary = true, onClick = ::shoot)
            SonnButton("Kamera wechseln", primary = false, onDark = true, onClick = { useFront = !useFront })
        }
    }
}
