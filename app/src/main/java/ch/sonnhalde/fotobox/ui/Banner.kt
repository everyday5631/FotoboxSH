package ch.sonnhalde.fotobox.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.R

/**
 * Sonnhalde-Banner nach dem DocumentHeader/AppBar-Muster des Design-Systems:
 * Navy-Flaeche, weisses Logo mit Zusatz «Pflege- und Betreuungszentrum» (Sonnhalde-Logo-Zusatz-hor-neg,
 * Design-System Se-Design), Titel, Logo-Gold-Kante unten.
 */
@Composable
fun SonnhaldeBanner(title: String, onLongPress: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(Sonn.Navy).pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp)) {
            Image(
                painter = painterResource(R.drawable.sonnhalde_logo_zusatz_neg),
                contentDescription = "Sonnhalde, Pflege- und Betreuungszentrum",
                modifier = Modifier.height(56.dp),
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
