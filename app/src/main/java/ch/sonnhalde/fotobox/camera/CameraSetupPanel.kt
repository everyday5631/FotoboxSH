package ch.sonnhalde.fotobox.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ch.sonnhalde.fotobox.ui.Sonn
import ch.sonnhalde.fotobox.ui.SonnButton
import kotlin.math.roundToInt

/** Setup, Reiter «Kamera»: Live-Bild mit Kameraauswahl, Belichtungskorrektur und Zoom (wie UpReach «Camera settings»). */
@Composable
fun CameraSetupPanel(
    useFront: Boolean,
    exposureIndex: Int,
    zoomRatio: Float,
    onFront: (Boolean) -> Unit,
    onExposure: (Int) -> Unit,
    onZoom: (Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exposure by remember(exposureIndex) { mutableFloatStateOf(exposureIndex.toFloat()) }
    var zoom by remember(zoomRatio) { mutableFloatStateOf(zoomRatio) }

    LaunchedEffect(hasPermission, useFront) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val p = cameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val bound = try {
                p.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (e: IllegalArgumentException) {
                p.bindToLifecycle(lifecycleOwner, if (useFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            }
            camera = bound
            error = null
            runCatching {
                if (bound.cameraInfo.exposureState.isExposureCompensationSupported) bound.cameraControl.setExposureCompensationIndex(exposureIndex)
                bound.cameraControl.setZoomRatio(zoomRatio)
            }
        } catch (e: Exception) {
            error = "Kamera konnte nicht gestartet werden: ${e.message}"
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Frontkamera", primary = useFront, onClick = { onFront(true) })
            SonnButton("Rückkamera", primary = !useFront, onClick = { onFront(false) })
        }
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Sonn.NavyDeep),
            )
        } else {
            Text("Bitte Kamera-Zugriff erlauben.", color = Sonn.Stone)
        }
        error?.let { Text(it, color = Sonn.Error) }

        val cam = camera
        val expState = cam?.cameraInfo?.exposureState
        if (cam != null && expState != null && expState.isExposureCompensationSupported) {
            val range = expState.exposureCompensationRange
            Text("Belichtungskorrektur: ${exposure.roundToInt()}")
            Slider(
                value = exposure.coerceIn(range.lower.toFloat(), range.upper.toFloat()),
                onValueChange = { exposure = it; cam.cameraControl.setExposureCompensationIndex(it.roundToInt()) },
                onValueChangeFinished = { onExposure(exposure.roundToInt()) },
                valueRange = range.lower.toFloat()..range.upper.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Sonn.Navy, activeTrackColor = Sonn.HeadingGold),
            )
        }
        val zs = cam?.cameraInfo?.zoomState?.value
        if (cam != null && zs != null && zs.maxZoomRatio > zs.minZoomRatio) {
            Text("Zoom: ${"%.1f".format(zoom)}x")
            Slider(
                value = zoom.coerceIn(zs.minZoomRatio, zs.maxZoomRatio),
                onValueChange = { zoom = it; cam.cameraControl.setZoomRatio(it) },
                onValueChangeFinished = { onZoom(zoom) },
                valueRange = zs.minZoomRatio..zs.maxZoomRatio,
                colors = SliderDefaults.colors(thumbColor = Sonn.Navy, activeTrackColor = Sonn.HeadingGold),
            )
        }
    }
}
