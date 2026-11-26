package ch.sonnhalde.fotobox.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
    private const val MAX_SIDE = 2400

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build(),
        )
    }

    /** Foto mit Personen vor [background]. Wirft eine Ausnahme, wenn die Erkennung fehlschlaegt (Aufrufer nimmt dann das Originalfoto). */
    suspend fun replace(photo: Bitmap, background: Bitmap): Bitmap {
        val person = downscale(photo, MAX_SIDE)
        val mask = segmentMask(person)
        val w = person.width
        val h = person.height

        // Person freistellen: Alpha des Fotos mit der (weich hochskalierten) Maske multiplizieren.
        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cutCanvas = Canvas(cutout)
        cutCanvas.drawBitmap(person, 0f, 0f, null)
        val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        cutCanvas.drawBitmap(mask, null, Rect(0, 0, w, h), maskPaint)

        // Hintergrund formatfuellend zuschneiden (Cover), dann die Person darueber.
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = maxOf(w / background.width.toFloat(), h / background.height.toFloat())
        val srcW = (w / scale).toInt().coerceIn(1, background.width)
        val srcH = (h / scale).toInt().coerceIn(1, background.height)
        val src = Rect((background.width - srcW) / 2, (background.height - srcH) / 2, (background.width + srcW) / 2, (background.height + srcH) / 2)
        canvas.drawBitmap(background, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawBitmap(cutout, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Maske als ALPHA_8-Bild: 255 = Person, 0 = Hintergrund, mit weichem Uebergang am Rand. */
    private suspend fun segmentMask(photo: Bitmap): Bitmap {
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
        val bytes = ByteArray(mw * mh)
        for (i in bytes.indices) {
            // Konfidenz «Person» 0..1; unter 0.35 ganz Hintergrund, ueber 0.75 ganz Person, dazwischen weich.
            val a = ((floats.get(i) - LOW) / (HIGH - LOW)).coerceIn(0f, 1f)
            bytes[i] = (a * a * (3 - 2 * a) * 255f).toInt().toByte()
        }
        val mask = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8)
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        return mask
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
