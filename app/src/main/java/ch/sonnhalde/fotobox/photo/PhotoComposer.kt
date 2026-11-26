package ch.sonnhalde.fotobox.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import ch.sonnhalde.fotobox.wcm.WcmConfig
import java.io.File

/** Aussehen des Banners (aus den Einstellungen). */
data class BannerStyle(
    val textLeft: String = "",
    val textRight: String = "",
    val color: String = "navy",
    /** Deckkraft des Hintergrunds 0..100. */
    val alphaPercent: Int = 100,
    val digital: Boolean = true,
) {
    companion object {
        fun from(config: WcmConfig) = BannerStyle(
            textLeft = config.bannerTextLeft,
            textRight = config.bannerText,
            color = config.bannerColor,
            alphaPercent = config.bannerAlpha,
            digital = config.bannerDigital,
        )
    }
}

/**
 * Setzt das Foto zum Druckbild zusammen: 3:2 (10 x 15 cm / 4 x 6 in.) mit Banner unten
 * (Logo mittig, frei waehlbarer Text links und rechts), wie auf dem gescannten Muster.
 * Ist der Banner deckend, steht er unter dem Foto; sonst liegt er durchscheinend auf dem Foto.
 */
object PhotoComposer {
    const val MAX_TEXT = 28

    private const val BANNER_RATIO = 0.15f
    private val NAVY = Color.parseColor("#233446")
    private val GOLD = Color.parseColor("#D9A235")
    private val TEXT_GOLD = Color.parseColor("#C8962E")
    private val TEXT_BLACK = Color.parseColor("#111111")
    private val BANNER_BG = Color.parseColor("#F4F6F6")

    private val RAINBOW = intArrayOf(
        Color.parseColor("#E5473C"), Color.parseColor("#F28C28"), Color.parseColor("#F2C230"),
        Color.parseColor("#5DB55E"), Color.parseColor("#3AA6D8"), Color.parseColor("#8E5AA8"), Color.parseColor("#E8579A"),
    )

    /** Eigenes Logo (PNG oder JPG, beim Auswaehlen als PNG gespeichert); fehlt die Datei, gilt das Sonnhalde-Logo. */
    fun logoFile(context: Context) = File(context.filesDir, "banner_logo.png")

    /** @param width Breite des Ergebnisses in Pixel (Hoehe = 2/3); 1800 = 300 dpi bei 6 Zoll. */
    fun compose(context: Context, photo: Bitmap, style: BannerStyle, width: Int = 1800): Bitmap {
        val height = width * 2 / 3
        val bannerH = (height * BANNER_RATIO).toInt()
        val opaque = style.alphaPercent >= 100
        val photoH = if (opaque) height - bannerH else height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Foto mittig zuschneiden (Cover).
        val scale = maxOf(width / photo.width.toFloat(), photoH / photo.height.toFloat())
        val srcW = (width / scale).toInt().coerceAtMost(photo.width)
        val srcH = (photoH / scale).toInt().coerceAtMost(photo.height)
        val src = Rect((photo.width - srcW) / 2, (photo.height - srcH) / 2, (photo.width + srcW) / 2, (photo.height + srcH) / 2)
        canvas.drawBitmap(photo, src, Rect(0, 0, width, photoH), Paint(Paint.FILTER_BITMAP_FLAG))

        drawBanner(context, canvas, style, width, (height - bannerH).toFloat(), bannerH.toFloat())
        return out
    }

    /**
     * Digitale Version (fuer QR/Download): Foto in Originalformat. Mit eingeschaltetem Banner
     * steht er unter dem Foto (deckend) oder durchscheinend auf dem unteren Rand.
     */
    fun composeDigital(context: Context, photo: Bitmap, style: BannerStyle, maxWidth: Int = 1800): Bitmap {
        val scale = minOf(1f, maxWidth / photo.width.toFloat())
        val w = (photo.width * scale).toInt()
        val h = (photo.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(photo, w, h, true)
        if (!style.digital) return scaled.copy(Bitmap.Config.ARGB_8888, true)

        val bannerH = (w * 2 / 3 * BANNER_RATIO).toInt()
        val opaque = style.alphaPercent >= 100
        val out = Bitmap.createBitmap(w, if (opaque) h + bannerH else h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        drawBanner(context, canvas, style, w, (out.height - bannerH).toFloat(), bannerH.toFloat())
        return out
    }

    /** Kleine Vorschau fuer die Einstellungen (Platzhalterfoto). */
    fun preview(context: Context, style: BannerStyle, width: Int = 900): Bitmap {
        val placeholder = Bitmap.createBitmap(width, width * 2 / 3, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.parseColor("#6E7F8F"))
        }
        return compose(context, placeholder, style, width)
    }

    fun previewDigital(context: Context, style: BannerStyle, width: Int = 900): Bitmap {
        val placeholder = Bitmap.createBitmap(width, width * 3 / 4, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.parseColor("#6E7F8F"))
        }
        return composeDigital(context, placeholder, style, width)
    }

    private fun drawBanner(context: Context, canvas: Canvas, style: BannerStyle, width: Int, top: Float, bannerH: Float) {
        val bgAlpha = style.alphaPercent.coerceIn(0, 100) * 255 / 100
        if (bgAlpha > 0) {
            canvas.drawRect(0f, top, width.toFloat(), top + bannerH, Paint().apply { color = BANNER_BG; alpha = bgAlpha })
        }
        drawLogo(context, canvas, centerX = width / 2f, top = top, bannerH = bannerH)
        drawTexts(canvas, style, width, top, bannerH)
    }

    private fun drawLogo(context: Context, canvas: Canvas, centerX: Float, top: Float, bannerH: Float) {
        val h = bannerH * 0.62f
        val maxW = bannerH * 3.6f
        fun draw(logo: Bitmap) {
            var lh = h
            var lw = lh * logo.width / logo.height
            if (lw > maxW) { lw = maxW; lh = lw * logo.height / logo.width }
            canvas.drawBitmap(logo, null, RectF(centerX - lw / 2, top + (bannerH - lh) / 2, centerX + lw / 2, top + (bannerH + lh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        // Eigenes Logo (Einstellungen) hat Vorrang.
        val custom = logoFile(context)
        if (custom.exists()) {
            BitmapFactory.decodeFile(custom.path)?.let { draw(it); return }
        }
        // Mitgeliefertes Logo: app/src/main/res/drawable-nodpi/sonnhalde_logo.png
        val id = context.resources.getIdentifier("sonnhalde_logo", "drawable", context.packageName)
        if (id != 0) {
            BitmapFactory.decodeResource(context.resources, id)?.let { draw(it); return }
        }
        // Naeherung des Logos: goldener Ring + Wortmarke.
        val unit = bannerH
        val ringR = unit * 0.20f
        val ringCx = centerX - unit * 1.05f
        val cy = top + bannerH / 2
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = GOLD; strokeWidth = unit * 0.075f }
        canvas.drawCircle(ringCx, cy, ringR, ring)
        canvas.drawOval(RectF(ringCx - ringR * 0.85f, cy - ringR * 1.02f, ringCx + ringR * 1.05f, cy + ringR * 0.9f), ring.apply { strokeWidth = unit * 0.03f })

        val textX = ringCx + ringR + unit * 0.18f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY; textSize = unit * 0.27f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); letterSpacing = 0.1f
        }
        canvas.drawText("SONNHALDE", textX, cy - unit * 0.02f, title)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = unit * 0.115f; typeface = Typeface.SANS_SERIF }
        canvas.drawText("Pflege- und Betreuungszentrum", textX + unit * 0.06f, cy + unit * 0.2f, sub)
    }

    private fun drawTexts(canvas: Canvas, style: BannerStyle, width: Int, top: Float, bannerH: Float) {
        val left = style.textLeft.trim().take(MAX_TEXT)
        val right = style.textRight.trim().take(MAX_TEXT)
        if (left.isEmpty() && right.isEmpty()) return
        val margin = width * 0.04f
        val areaW = width * 0.27f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        // Gleiche Schriftgroesse links und rechts: so gross wie moeglich, bis beide Texte in ihren Bereich passen.
        var size = bannerH * 0.36f
        paint.textSize = size
        while ((paint.measureText(left) > areaW || paint.measureText(right) > areaW) && size > bannerH * 0.14f) { size -= 1f; paint.textSize = size }
        val baseline = top + bannerH / 2 - (paint.ascent() + paint.descent()) / 2
        val line = Paint().apply { color = GOLD }
        val lineTop = top + bannerH * 0.25f
        val lineBottom = top + bannerH * 0.75f
        if (left.isNotEmpty()) {
            drawColored(canvas, left, margin, baseline, paint, style.color, Paint.Align.LEFT)
            val x = margin + areaW + width * 0.015f
            canvas.drawRect(x, lineTop, x + 4f, lineBottom, line)
        }
        if (right.isNotEmpty()) {
            drawColored(canvas, right, width - margin, baseline, paint, style.color, Paint.Align.RIGHT)
            val x = width - margin - areaW - width * 0.015f
            canvas.drawRect(x, lineTop, x + 4f, lineBottom, line)
        }
    }

    /** Text in der gewaehlten Farbe; «rainbow» faerbt jeden Buchstaben einzeln (mit leichtem Schatten). */
    private fun drawColored(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint, color: String, align: Paint.Align) {
        paint.textAlign = Paint.Align.LEFT
        val total = paint.measureText(text)
        var cx = if (align == Paint.Align.RIGHT) x - total else x
        if (color != "rainbow") {
            paint.color = when (color) { "black" -> TEXT_BLACK; "gold" -> TEXT_GOLD; else -> NAVY }
            canvas.drawText(text, cx, baseline, paint)
            return
        }
        var index = 0
        for (ch in text) {
            val w = paint.measureText(ch.toString())
            if (ch != ' ') {
                paint.color = Color.argb(90, 0, 0, 0)
                canvas.drawText(ch.toString(), cx + paint.textSize * 0.04f, baseline + paint.textSize * 0.04f, paint)
                paint.color = RAINBOW[index++ % RAINBOW.size]
                canvas.drawText(ch.toString(), cx, baseline, paint)
            }
            cx += w
        }
    }
}
