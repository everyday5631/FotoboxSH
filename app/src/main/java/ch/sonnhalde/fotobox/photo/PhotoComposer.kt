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

/**
 * Setzt das Foto zum Druckbild zusammen: 3:2 (10 x 15 cm / 4 x 6 in.) mit hellem Banner unten
 * (Sonnhalde-Logo mittig, frei waehlbarer Text rechts), wie auf dem gescannten Muster.
 */
object PhotoComposer {
    const val MAX_TEXT = 28

    private const val BANNER_RATIO = 0.15f
    private val NAVY = Color.parseColor("#233446")
    private val GOLD = Color.parseColor("#D9A235")
    private val BANNER_BG = Color.parseColor("#F4F6F6")

    /** @param width Breite des Ergebnisses in Pixel (Hoehe = 2/3); 1800 = 300 dpi bei 6 Zoll. */
    fun compose(context: Context, photo: Bitmap, bannerText: String, width: Int = 1800): Bitmap {
        val height = width * 2 / 3
        val bannerH = (height * BANNER_RATIO).toInt()
        val photoH = height - bannerH
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Foto mittig zuschneiden (Cover) in den oberen Bereich.
        val scale = maxOf(width / photo.width.toFloat(), photoH / photo.height.toFloat())
        val srcW = (width / scale).toInt().coerceAtMost(photo.width)
        val srcH = (photoH / scale).toInt().coerceAtMost(photo.height)
        val src = Rect((photo.width - srcW) / 2, (photo.height - srcH) / 2, (photo.width + srcW) / 2, (photo.height + srcH) / 2)
        canvas.drawBitmap(photo, src, Rect(0, 0, width, photoH), Paint(Paint.FILTER_BITMAP_FLAG))

        // Banner
        canvas.drawRect(0f, photoH.toFloat(), width.toFloat(), height.toFloat(), Paint().apply { color = BANNER_BG })
        drawLogo(context, canvas, centerX = width / 2f, top = photoH.toFloat(), bannerH = bannerH.toFloat())
        drawText(canvas, bannerText.trim().take(MAX_TEXT), width, photoH.toFloat(), bannerH.toFloat())
        return out
    }

    /** Kleine Vorschau fuer die Einstellungen (Platzhalterfoto). */
    fun preview(context: Context, bannerText: String): Bitmap {
        val w = 900
        val placeholder = Bitmap.createBitmap(w, w * 2 / 3, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.parseColor("#6E7F8F"))
        }
        return compose(context, placeholder, bannerText, w)
    }

    private fun drawLogo(context: Context, canvas: Canvas, centerX: Float, top: Float, bannerH: Float) {
        // Eigenes Logo hat Vorrang: Datei app/src/main/res/drawable-nodpi/sonnhalde_logo.png ablegen.
        val id = context.resources.getIdentifier("sonnhalde_logo", "drawable", context.packageName)
        if (id != 0) {
            BitmapFactory.decodeResource(context.resources, id)?.let { logo ->
                val h = bannerH * 0.62f
                val w = h * logo.width / logo.height
                canvas.drawBitmap(logo, null, RectF(centerX - w / 2, top + (bannerH - h) / 2, centerX + w / 2, top + (bannerH + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                return
            }
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

    private fun drawText(canvas: Canvas, text: String, width: Int, top: Float, bannerH: Float) {
        if (text.isEmpty()) return
        val areaLeft = width * 0.69f
        val areaRight = width - width * 0.04f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT
        }
        // Schrift so gross wie moeglich, bis der Text in den Bereich rechts passt.
        var size = bannerH * 0.36f
        paint.textSize = size
        while (paint.measureText(text) > areaRight - areaLeft && size > bannerH * 0.14f) { size -= 1f; paint.textSize = size }
        val cy = top + bannerH / 2 - (paint.ascent() + paint.descent()) / 2
        // dezente Goldlinie links vom Text
        canvas.drawRect(areaLeft - width * 0.015f, top + bannerH * 0.25f, areaLeft - width * 0.015f + 4f, top + bannerH * 0.75f, Paint().apply { color = GOLD })
        canvas.drawText(text, areaRight, cy, paint)
    }
}
