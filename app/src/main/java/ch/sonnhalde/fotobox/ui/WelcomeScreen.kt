package ch.sonnhalde.fotobox.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.AppVersion
import ch.sonnhalde.fotobox.R
import ch.sonnhalde.fotobox.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Startbildschirm im Retail-Modus (nach dem Vorbild von UpReach): Hintergrundbild, Logo oben links,
 * grosse Ueberschrift und weisser Knopf «jetzt starten». Langer Druck auf das Logo oeffnet die PIN-Abfrage.
 */
@Composable
fun WelcomeScreen(state: UiState, bgFile: File, onStart: () -> Unit, onAdmin: () -> Unit) {
    val background: ImageBitmap? by produceState<ImageBitmap?>(null, state.bgVersion) {
        value = withContext(Dispatchers.IO) {
            if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.path)?.asImageBitmap() else null
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Sonn.Navy, Sonn.NavyDeep)))) {
        background?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Verlauf nach unten, damit die weisse Schrift lesbar bleibt.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to Color(0xCC233446),
                ),
            ),
        )

        // Logo oben links auf weisser Flaeche
        Box(
            Modifier.statusBarsPadding().padding(start = 32.dp).width(170.dp).background(Color.White).padding(14.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onAdmin() }) },
        ) {
            Image(painterResource(R.drawable.sonnhalde_logo_vertical), contentDescription = "Sonnhalde", modifier = Modifier.fillMaxWidth())
        }

        Column(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 48.dp, end = 48.dp, bottom = 56.dp)
                .fillMaxWidth(0.72f),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                state.config.startTitle,
                style = TextStyle(
                    color = Color.White, fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Black,
                    shadow = Shadow(Color(0x66000000), Offset(0f, 4f), 12f),
                ),
            )
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable(onClick = onStart)
                    .padding(horizontal = 36.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(state.config.startButton, color = Sonn.Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("👆", fontSize = 26.sp)
            }
        }

        Text(
            AppVersion.label, color = Color(0x99FFFFFF), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        )

        state.message?.let { Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp).width(360.dp)) { Notice(it) } }
    }
}
