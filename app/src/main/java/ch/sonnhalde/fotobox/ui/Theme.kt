package ch.sonnhalde.fotobox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Farben aus dem Sonnhalde-Design-System (tokens.css). */
object Sonn {
    val Navy = Color(0xFF233446)
    val NavyDeep = Color(0xFF1A2836)
    val LogoGold = Color(0xFFEFBB50)
    val HeadingGold = Color(0xFFC8962E)
    val GoldDeep = Color(0xFFA97C22)
    val Cream = Color(0xFFFDF9F6)
    val CreamSink = Color(0xFFF6EFE8)
    val Stone = Color(0xFF6B6459)
    val Line = Color(0xFFDCD4C9)
    val Surface = Color(0xFFFFFFFF)
    val Ok = Color(0xFF3F6B4C)
    val OkBg = Color(0xFFE7EFE8)
    val Warn = Color(0xFF8A5A1E)
    val WarnBg = Color(0xFFF6E9D6)
    val Error = Color(0xFFB23B3B)
    val BannerMuted = Color(0xFF9FB0BF)

    // Android bringt kein Arial mit; SansSerif (Roboto) ist der naechste Ersatz.
    val Font = FontFamily.SansSerif
}

private val colors = lightColorScheme(
    primary = Sonn.HeadingGold,
    onPrimary = Sonn.Navy,
    secondary = Sonn.Navy,
    onSecondary = Sonn.Cream,
    background = Sonn.Cream,
    onBackground = Sonn.Navy,
    surface = Sonn.Surface,
    onSurface = Sonn.Navy,
    outline = Sonn.Line,
    error = Sonn.Error,
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Sonn.Font, fontSize = size.sp, fontWeight = weight, color = Sonn.Navy, lineHeight = (size * 1.4).sp)

private val typography = Typography(
    headlineSmall = style(24, FontWeight.Bold),
    titleMedium = style(20, FontWeight.Bold),
    bodyLarge = style(18),
    bodyMedium = style(16),
    labelLarge = style(15, FontWeight.Bold),
    labelSmall = style(12),
)

@Composable
fun SonnhaldeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(3.dp)),
        content = content,
    )
}
