package ch.sonnhalde.fotobox.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ersetzt den Hintergrund hinter den Personen (wie bei Teams): ML-Kit-Selfie-Segmentierung direkt auf dem Tablet,
 * das Modell steckt in der App, es wird nichts hochgeladen und es braucht kein Internet.
 */
object BackgroundRemover {
    /** Laengste Seite des Bildes, auf dem die Personen erkannt werden (das Modell rechnet intern nur mit ca. 256 px). */
    private const val TAG = "Fotobox"
    private const val SEGMENT_SIDE = 1024
    /** Laengste Seite des Ergebnisses; das Foto ist danach ohnehin auf 1800 px Breite beschraenkt. */
    private const val MAX_SIDE = 2000
    /**
     * Konfidenz «Person» nach dem Glaetten: unter LOW ganz Hintergrund, ueber HIGH ganz Person. Bewusst eng um 0.5:
     * der Guided Filter macht den Rand schon weich; ein breites Band verwischt ihn (im Test sah eine weisse Schulter
     * vor weissem Grund mit 0.30/0.80 verwaschen aus, mit 0.40/0.60 glatt und scharf).
     */
    private const val LOW = 0.40f
    private const val HIGH = 0.60f
    /** Guided Filter: Fensterradius (Pixel auf dem Erkennungsbild) und Regularisierung; kleiner eps = naeher an den Bildkanten. */
    private const val FILTER_RADIUS = 12
    private const val FILTER_EPS = 1e-3f
    /** Haarmodell: Wahrscheinlichkeit unter HAIR_LOW zaehlt nicht, ueber HAIR_HIGH voll. */
    private const val HAIR_LOW = 0.5f
    private const val HAIR_HIGH = 0.9f
    /** Haare zaehlen nur nahe an der erkannten Person (Fensterradius = Maskenbreite / HAIR_NEAR_DIV, Verstaerkung HAIR_GATE_GAIN). */
    private const val HAIR_NEAR_DIV = 16
    private const val HAIR_GATE_GAIN = 6f

    private var hairModel: HairModel? = null
    private var hairFailed = false

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

    /** Rohe Modellausgabe: Konfidenz «Person» 0..1 je Pixel der Maske (w x h, meist 256 x 256). */
    private class Confidence(val w: Int, val h: Int, val v: FloatArray)

    suspend fun replace(context: Context, photo: Bitmap, background: Bitmap): Replacement {
        val t0 = System.currentTimeMillis()
        val person = downscale(photo, MAX_SIDE)
        val w = person.width
        val h = person.height

        val (soft, gw, gh) = buildMask(context, person)
        var sum = 0.0
        for (v in soft) sum += v
        val share = (sum / soft.size).toFloat()

        // Hintergrund formatfuellend zuschneiden (Cover) auf die Groesse des Fotos.
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = maxOf(w / background.width.toFloat(), h / background.height.toFloat())
        val srcW = (w / scale).toInt().coerceIn(1, background.width)
        val srcH = (h / scale).toInt().coerceIn(1, background.height)
        val src = Rect((background.width - srcW) / 2, (background.height - srcH) / 2, (background.width + srcW) / 2, (background.height + srcH) / 2)
        Canvas(bg).drawBitmap(background, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        // Maske (Erkennungsgroesse) weich auf die Groesse des Fotos hochskalieren. ARGB, nur der Alpha-Kanal traegt die Maske
        // (bewusst kein ALPHA_8: damit blieb das Foto auf dem Tablet unveraendert).
        val maskPixels = IntArray(gw * gh) { (soft[it] * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24 }
        val maskSmall = Bitmap.createBitmap(maskPixels, gw, gh, Bitmap.Config.ARGB_8888)
        val maskFull = if (gw == w && gh == h) maskSmall else Bitmap.createScaledBitmap(maskSmall, w, h, true)

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
        Log.i(TAG, "Freistellung %d ms (Foto %dx%d, Maske %dx%d)".format(System.currentTimeMillis() - t0, w, h, gw, gh))
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
     * Personenmaske auf Erkennungsgroesse (laengste Seite [SEGMENT_SIDE]): 0 = Hintergrund, 1 = Person, weicher Rand.
     *
     * 1. Erster Durchgang auf dem ganzen Bild (findet alle Personen, auch mehrere zusammen als eine Maske).
     * 2. Zweiter Durchgang nur auf dem Ausschnitt um die gefundenen Personen: das Modell sieht sie groesser und liefert
     *    einen feineren Rand. Schlaegt er fehl, gilt der erste.
     * 3. Haarmodell: ergaenzt Haare nahe an der Person, wo die Personen-Erkennung sie abschneidet.
     * 4. Guided Filter: der Maskenrand wird an die Kanten des Fotos angelegt (Haare, Schultern), statt am groben Raster zu haengen.
     */
    private suspend fun buildMask(context: Context, person: Bitmap): Triple<FloatArray, Int, Int> {
        val small = downscale(person, SEGMENT_SIDE)
        val gw = small.width
        val gh = small.height

        val first = segment(small)
        val mask = MaskMath.resizeBilinear(first.v, first.w, first.h, gw, gh)

        // Ausschnitt um alles, was beim ersten Durchgang als Person galt (mit Rand).
        var cropBitmap: Bitmap? = null
        var cropRegion: IntArray? = null
        val box = personBox(first)
        if (box != null) {
            val bw = box[2] - box[0]
            val bh = box[3] - box[1]
            val mx = bw * 0.10f + 0.02f
            val my = bh * 0.10f + 0.02f
            val fx0 = (box[0] - mx).coerceAtLeast(0f)
            val fy0 = (box[1] - my).coerceAtLeast(0f)
            val fx1 = (box[2] + mx).coerceAtMost(1f)
            val fy1 = (box[3] + my).coerceAtMost(1f)
            val rx = (fx0 * person.width).toInt()
            val ry = (fy0 * person.height).toInt()
            val rw = ((fx1 - fx0) * person.width).toInt().coerceAtMost(person.width - rx)
            val rh = ((fy1 - fy0) * person.height).toInt().coerceAtMost(person.height - ry)
            // Lohnt sich nur, wenn der Ausschnitt deutlich kleiner als das Bild ist.
            if (rw >= 64 && rh >= 64 && rw.toLong() * rh < 0.7 * person.width * person.height) {
                try {
                    Log.i(TAG, "Zweiter Durchgang auf Ausschnitt %dx%d bei (%d,%d)".format(rw, rh, rx, ry))
                    val crop = downscale(Bitmap.createBitmap(person, rx, ry, rw, rh), SEGMENT_SIDE)
                    cropRegion = mergeRegion(mask, gw, gh, segment(crop), rx, ry, rw, rh, person.width, person.height)
                    cropBitmap = crop
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Zweiter Durchgang uebersprungen, es gilt der erste", e)
                }
            }
        }

        addHair(context, small, cropBitmap, cropRegion, mask, gw, gh)

        // Fuehrungsbild: Helligkeit des Erkennungsbildes.
        val px = IntArray(gw * gh)
        small.getPixels(px, 0, gw, 0, 0, gw, gh)
        val guide = FloatArray(px.size) {
            val c = px[it]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        val refined = MaskMath.guidedFilter(guide, mask, gw, gh, FILTER_RADIUS, FILTER_EPS)
        val soft = FloatArray(refined.size) { MaskMath.smoothstep(refined[it], LOW, HIGH) }
        return Triple(soft, gw, gh)
    }

    /** Legt die Maske des Ausschnitts ([rx], [ry], [rw], [rh] in Fotopixeln) ueber [mask]; der Uebergang am Ausschnittsrand ist weich. */
    private fun mergeRegion(
        mask: FloatArray, gw: Int, gh: Int, crop: Confidence,
        rx: Int, ry: Int, rw: Int, rh: Int, photoW: Int, photoH: Int,
    ): IntArray {
        val sx = gw.toFloat() / photoW
        val sy = gh.toFloat() / photoH
        val gx0 = (rx * sx).toInt().coerceIn(0, gw - 1)
        val gy0 = (ry * sy).toInt().coerceIn(0, gh - 1)
        val gx1 = ((rx + rw) * sx).toInt().coerceIn(gx0 + 1, gw)
        val gy1 = ((ry + rh) * sy).toInt().coerceIn(gy0 + 1, gh)
        val cw = gx1 - gx0
        val ch = gy1 - gy0
        val region = MaskMath.resizeBilinear(crop.v, crop.w, crop.h, cw, ch)
        val feather = maxOf(2f, minOf(cw, ch) * 0.08f)
        val big = 1e6f
        for (y in 0 until ch) {
            val dTop = if (gy0 == 0) big else y.toFloat()
            val dBottom = if (gy1 == gh) big else (ch - 1 - y).toFloat()
            for (x in 0 until cw) {
                val dLeft = if (gx0 == 0) big else x.toFloat()
                val dRight = if (gx1 == gw) big else (cw - 1 - x).toFloat()
                val wgt = ((minOf(minOf(dLeft, dRight), minOf(dTop, dBottom)) + 1f) / feather).coerceIn(0f, 1f)
                val i = (gy0 + y) * gw + gx0 + x
                mask[i] = region[y * cw + x] * wgt + mask[i] * (1f - wgt)
            }
        }
        return intArrayOf(gx0, gy0, cw, ch)
    }

    private fun hairModel(context: Context): HairModel? {
        hairModel?.let { return it }
        if (hairFailed) return null
        return try {
            HairModel.load(context.applicationContext).also { hairModel = it }
        } catch (e: Throwable) {
            hairFailed = true
            Log.w(TAG, "Haarmodell nicht verfuegbar, es gilt nur die Personen-Erkennung", e)
            null
        }
    }

    /**
     * Ergaenzt [mask] um Haare: das Haarmodell laeuft auf dem Erkennungsbild und, falls vorhanden, auf dem Ausschnitt um die Personen
     * (feiner). Haare zaehlen nur nahe an der erkannten Person, damit dunkle Dinge im Hintergrund nicht als Haar durchgehen.
     * Faellt das Haarmodell aus, bleibt die Maske unveraendert.
     */
    private fun addHair(context: Context, small: Bitmap, crop: Bitmap?, region: IntArray?, mask: FloatArray, gw: Int, gh: Int) {
        val model = hairModel(context) ?: return
        try {
            val t0 = System.currentTimeMillis()
            val hair = MaskMath.resizeBilinear(model.predict(small), HairModel.SIZE, HairModel.SIZE, gw, gh)
            if (crop != null && region != null) {
                val gx0 = region[0]
                val gy0 = region[1]
                val cw = region[2]
                val ch = region[3]
                val fine = MaskMath.resizeBilinear(model.predict(crop), HairModel.SIZE, HairModel.SIZE, cw, ch)
                for (y in 0 until ch) for (x in 0 until cw) {
                    val i = (gy0 + y) * gw + gx0 + x
                    hair[i] = maxOf(hair[i], fine[y * cw + x])
                }
            }
            // Nahbereich der Person (Anteil Personenpixel im Fenster), daraus ein Tor 0..1.
            val personBinary = FloatArray(mask.size) { if (mask[it] > 0.5f) 1f else 0f }
            val near = MaskMath.boxMean(personBinary, gw, gh, maxOf(8, gw / HAIR_NEAR_DIV))
            var added = 0.0
            var hairShare = 0.0
            for (i in mask.indices) {
                if (hair[i] > 0.5f) hairShare += 1.0
                val gate = minOf(1f, near[i] * HAIR_GATE_GAIN)
                val h = MaskMath.smoothstep(hair[i], HAIR_LOW, HAIR_HIGH) * gate
                if (h > mask[i]) {
                    added += h - mask[i]
                    mask[i] = h
                }
            }
            Log.i(TAG, "Haarmodell %d ms, Haar im Bild %.3f, zur Maske ergaenzt %.4f".format(
                System.currentTimeMillis() - t0, hairShare / mask.size, added / mask.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Haarmodell uebersprungen, es gilt nur die Personen-Erkennung", e)
        }
    }

    /** Umschliessendes Rechteck aller Pixel mit Konfidenz > 0.5 als Anteile [x0, y0, x1, y1] (0..1); null, wenn fast nichts erkannt wurde. */
    private fun personBox(c: Confidence): FloatArray? {
        var minX = c.w; var minY = c.h; var maxX = -1; var maxY = -1; var count = 0
        for (y in 0 until c.h) for (x in 0 until c.w) {
            if (c.v[y * c.w + x] > 0.5f) {
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (count < c.w * c.h * 0.002f) return null
        return floatArrayOf(minX / c.w.toFloat(), minY / c.h.toFloat(), (maxX + 1) / c.w.toFloat(), (maxY + 1) / c.h.toFloat())
    }

    private suspend fun segment(bmp: Bitmap): Confidence {
        val result = suspendCancellableCoroutine { cont ->
            segmenter.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val mw = result.width
        val mh = result.height
        val buffer = result.buffer
        buffer.rewind()
        val floats = buffer.asFloatBuffer()
        return Confidence(mw, mh, FloatArray(mw * mh) { floats.get(it) })
    }

    private fun downscale(bmp: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide <= maxSide) return bmp
        val f = maxSide / longSide.toFloat()
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
    }
}
