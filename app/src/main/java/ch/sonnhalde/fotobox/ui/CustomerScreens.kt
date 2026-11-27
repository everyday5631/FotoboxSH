package ch.sonnhalde.fotobox.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.AppVersion
import ch.sonnhalde.fotobox.PrintState
import ch.sonnhalde.fotobox.UiState
import ch.sonnhalde.fotobox.UploadState
import ch.sonnhalde.fotobox.print.PhotoPrinter
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Das Zeitlimit des Ergebnis-Bildschirms ist einstellbar (Setup). Solange gedruckt wird, laeuft es nicht ab; haengt ein Druckschritt,
 * gilt spaetestens nach dieser Zeit trotzdem das Limit (jeder neue Schritt startet sie neu).
 */
private const val PRINT_PAUSE_CAP_MS = 300_000L

/** Startbild beim Oeffnen der App: Sonne + Wortmarke (wie der UpReach-Splashscreen). */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1800); onDone() }
    Box(Modifier.fillMaxSize().background(Sonn.Navy), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Canvas(Modifier.size(120.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Sonn.LogoGold, radius = size.minDimension * 0.22f, center = c)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(Sonn.LogoGold, c + dir * (size.minDimension * 0.34f), c + dir * (size.minDimension * 0.46f),
                        strokeWidth = 8f, cap = StrokeCap.Round)
                }
            }
            Text("SONNHALDE", color = Sonn.Cream, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text("FOTOBOX", color = Sonn.LogoGold, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Text(
            AppVersion.label, color = Sonn.BannerMuted, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        )
    }
}

/** Kundenansicht nach dem Foto (wie UpReach): digitale Version mit QR-Code und Druck-Version mit Drucken. */
@Composable
fun ResultScreen(state: UiState, actions: Actions, onAdmin: () -> Unit) {
    val context = LocalContext.current
    // Zurueck zum Start, wenn niemand mehr tippt; jede Zustandsaenderung startet die Frist neu, waehrend des Druckens ist sie pausiert.
    val timeoutMs = state.config.resultTimeoutSeconds * 1000L
    val printing = state.print is PrintState.Printing
    LaunchedEffect(state.photo, state.print, state.upload, timeoutMs) {
        delay(if (printing) maxOf(timeoutMs, PRINT_PAUSE_CAP_MS) else timeoutMs)
        actions.onHome()
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Bild eines Blocks: im Querformat (fit) bekommt es nur den Platz, der neben QR-Code bzw. Druckknopf bleibt,
    // und wird massstabsgetreu verkleinert, damit nichts gescrollt werden muss; sonst volle Breite.
    fun ColumnScope.imageModifier(fit: Boolean) =
        (if (fit) Modifier.weight(1f, fill = false) else Modifier.fillMaxWidth()).border(1.dp, Sonn.Line, MaterialShape)

    @Composable
    fun digitalBlock(modifier: Modifier, fit: Boolean = false) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DIGITALE VERSION", color = Sonn.Stone, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            state.digital?.let {
                Image(it.asImageBitmap(), contentDescription = "Digitale Version", modifier = imageModifier(fit))
            }
            when (val u = state.upload) {
                UploadState.Idle, UploadState.Uploading -> Text("QR-Code wird erstellt …", color = Sonn.Stone, fontSize = 16.sp)
                is UploadState.Failed -> {
                    Notice("Der Download-Link konnte nicht erstellt werden. ${u.reason}")
                    SonnButton("Nochmals versuchen", primary = false, onClick = actions.onRetryUpload)
                }
                is UploadState.Done -> state.qrBitmap?.let { bmp ->
                    Box(Modifier.border(1.dp, Sonn.Line, MaterialShape).background(Color.White).padding(10.dp)) {
                        Image(bmp.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(150.dp))
                    }
                    Text("ZUM HERUNTERLADEN SCANNEN", color = Sonn.Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }

    @Composable
    fun printBlock(modifier: Modifier, fit: Boolean = false) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DRUCK-VERSION", color = Sonn.Stone, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            state.photo?.let {
                Image(it.asImageBitmap(), contentDescription = "Druck-Version", modifier = imageModifier(fit))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SonnButton("−", primary = false, onClick = { actions.onCopies(state.copies - 1) })
                Text("${state.copies} ${if (state.copies == 1) "Abzug" else "Abzüge"}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SonnButton("+", primary = false, onClick = { actions.onCopies(state.copies + 1) })
            }
            SonnButton("DRUCKEN", primary = true, onClick = actions.onPrint)
            when (val pr = state.print) {
                PrintState.Idle -> Unit
                is PrintState.Printing -> Text(pr.stage, color = Sonn.Stone, fontSize = 16.sp)
                is PrintState.Done -> Text("Ihr Foto wird gedruckt.", color = Sonn.Ok, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                is PrintState.Failed -> {
                    Notice("Drucken nicht möglich. ${pr.reason}")
                    SonnButton("Über Android-Druckdialog drucken", primary = false,
                        onClick = { state.photo?.let { PhotoPrinter.print(context, it) } })
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Sonn.Cream)) {
        SonnhaldeBanner(title = "Ihr Foto", onLongPress = onAdmin)
        state.backgroundNote?.let { Notice(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (landscape) {
                // Querformat (Tablet): alles auf einem Bildschirm, ohne Scrollen. Der Haken unten rechts ueberdeckt nur den freien Rand.
                Row(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    digitalBlock(Modifier.weight(1f).fillMaxHeight(), fit = true)
                    printBlock(Modifier.weight(1f).fillMaxHeight(), fit = true)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    digitalBlock(Modifier.fillMaxWidth())
                    printBlock(Modifier.fillMaxWidth())
                }
            }
            // Grosser Haken unten rechts: fertig, zurueck zum Start.
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(28.dp).size(84.dp)
                    .clip(CircleShape).background(Sonn.Navy).clickable(onClick = actions.onHome),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = Sonn.LogoGold, fontSize = 44.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
