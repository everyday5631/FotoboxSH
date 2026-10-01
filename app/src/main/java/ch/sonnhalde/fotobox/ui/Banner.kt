package ch.sonnhalde.fotobox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sonnhalde-Banner nach dem DocumentHeader/AppBar-Muster des Design-Systems:
 * Navy-Flaeche, Wortmarke "SONN" + "HALDE" in Logo-Gold, Logo-Gold-Kante unten.
 */
@Composable
fun SonnhaldeBanner(title: String, kicker: String = "Pflege- und Betreuungszentrum", onLongPress: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(Sonn.Navy).pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("SONN")
                    withStyle(SpanStyle(color = Sonn.LogoGold)) { append("HALDE") }
                },
                color = Sonn.Cream,
                fontFamily = Sonn.Font,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 2.4.sp,
            )
            Text(
                text = kicker.uppercase(),
                color = Sonn.BannerMuted,
                fontFamily = Sonn.Font,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = title,
                color = Sonn.Cream,
                fontFamily = Sonn.Font,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp).width(72.dp).height(3.dp).background(Sonn.LogoGold)
        )
    }
}
