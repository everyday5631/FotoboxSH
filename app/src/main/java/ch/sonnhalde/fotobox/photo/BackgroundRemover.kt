package ch.sonnhalde.fotobox.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ersetzt den Hintergrund hinter den Personen (wie bei Teams): ML-Kit-Selfie-Segmentierung direkt auf dem Tablet,
 * das Modell steckt in der App, es wird nichts hochgeladen und es braucht kein Internet.
 */
object BackgroundRemover {
    /** Laengste Seite des Bildes, auf dem die Personen erkannt werden (schneller, die Maske wird danach hochskaliert). */
    private const val SEGMENT_SIDE = 1024
    /** Laengste Seite des Ergebnisses; das Foto ist danach ohnehin auf 1800 px Breite beschraenkt. */
    private const val MAX_SIDE = 2000

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build(),
        )
    }

    /** Foto mit Personen vor [background]. Wirft eine Ausnahme, wenn die Erkennung fehlschlaegt (Aufrufer nimmt dann das Originalfoto). */
    class Replacement(val image: Bitmap, /** Anteil des Bildes, der als Person erkannt wurde (0..1). */ val personShare: Float)

    suspend fun replace(photo: Bitmap, background: Bitmap): Replacement {
        val person = downscale(photo, MAX_SIDE)
        val (mask, share) = segmentMask(person)
        val w = person.width
        val h = person.height

        // Hintergrund formatfuellend zuschneiden (Cover) auf die Groesse des Fotos.
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = maxOf(w / background.width.toFloat(), h / background.height.toFloat())
        val srcW = (w / scale).toInt().coerceIn(1, background.width)
        val srcH = (h / scale).toInt().coerceIn(1, background.height)
        val src = Rect((background.width - srcW) / 2, (background.height - srcH) / 2, (background.width + srcW) / 2, (background.height + srcH) / 2)
        Canvas(bg).drawBitmap(background, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        // Maske weich auf die Groesse des Fotos hochskalieren.
        val maskFull = Bitmap.createScaledBitmap(mask, w, h, true)

        // Pixelweise mischen, ohne Porter-Duff-Modi von Android: Ergebnis = Person * Maske + Hintergrund * (1 - Maske).
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val personRow = IntArray(w)
        val bgRow = IntArray(w)
        val maskRow = IntArray(w)
        for (y in 0 until h) {
            person.getPixels(personRow, 0, w, 0, y, w, 1)
            bg.getPixels(bgRow, 0, w, 0, y, w, 1)
            maskFull.getPixels(maskRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                personRow[x] = blend(personRow[x], bgRow[x], maskRow[x] ushr 24)
            }
            out.setPixels(personRow, 0, w, 0, y, w, 1)
        }
        return Replacement(out, share)
    }

    /** Mischt zwei Farben (ARGB, deckend): [alpha] 255 = nur [front], 0 = nur [back]. */
    internal fun blend(front: Int, back: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        val inv = 255 - a
        val r = (((front shr 16) and 0xFF) * a + ((back shr 16) and 0xFF) * inv + 127) / 255
        val g = (((front shr 8) and 0xFF) * a + ((back shr 8) and 0xFF) * inv + 127) / 255
        val bl = ((front and 0xFF) * a + (back and 0xFF) * inv + 127) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * Maske als ARGB-Bild (schwarz, nur der Alpha-Kanal traegt die Maske): 255 = Person, 0 = Hintergrund, weicher Rand.
     * Bewusst kein ALPHA_8: Android behandelt ein ALPHA_8-Bild beim Zeichnen als Abdeckung einer (deckenden) Paint-Farbe,
     * mit DST_IN bliebe das Foto dann unveraendert (so war es auf dem Tablet zu sehen).
     */
    private suspend fun segmentMask(photo: Bitmap): Pair<Bitmap, Float> {
        val small = downscale(photo, SEGMENT_SIDE)
        val result = suspendCancellableCoroutine { cont ->
            segmenter.process(InputImage.fromBitmap(small, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val mw = result.width
        val mh = result.height
        val buffer = result.buffer
        buffer.rewind()
        val floats = buffer.asFloatBuffer()
        val pixels = IntArray(mw * mh)
        var sum = 0.0
        for (i in pixels.indices) {
            // Konfidenz «Person» 0..1; unter 0.35 ganz Hintergrund, ueber 0.75 ganz Person, dazwischen weich.
            val a = ((floats.get(i) - LOW) / (HIGH - LOW)).coerceIn(0f, 1f)
            val soft = a * a * (3 - 2 * a)
            sum += soft
            pixels[i] = (soft * 255f).toInt().coerceIn(0, 255) shl 24
        }
        val mask = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
        return mask to (sum / pixels.size).toFloat()
    }

    private fun downscale(bmp: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide <= maxSide) return bmp
        val f = maxSide / longSide.toFloat()
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
    }

    private const val LOW = 0.35f
    private const val HIGH = 0.75f
}
