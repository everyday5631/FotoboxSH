package ch.sonnhalde.fotobox.photo

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Haar-Erkennung (Google MediaPipe «hair_segmenter», 512 x 512, ca. 0,8 MB, Datei in `assets/models/`), direkt mit TensorFlow Lite.
 *
 * Das Modell hat dieselbe Form wie im Python-Test geprueft: Eingang [1, 512, 512, 4] (RGB 0..1 und ein vierter Kanal fuer die
 * Maske des Vorbildes bei Videos, hier immer 0), Ausgang [1, 512, 512, 2] mit Logits (0 = Hintergrund, 1 = Haar).
 * Es ergaenzt die Personen-Erkennung dort, wo diese Haare abschneidet.
 */
internal class HairModel private constructor(private val interpreter: Interpreter) {

    /** Wahrscheinlichkeit «Haar» 0..1 je Pixel (SIZE x SIZE, Zeile fuer Zeile); das Bild wird auf SIZE x SIZE gestaucht. */
    fun predict(bmp: Bitmap): FloatArray {
        val scaled = if (bmp.width == SIZE && bmp.height == SIZE) bmp else Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)

        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 4 * 4).order(ByteOrder.nativeOrder())
        val fin = input.asFloatBuffer()
        for (c in px) {
            fin.put(((c shr 16) and 0xFF) / 255f)
            fin.put(((c shr 8) and 0xFF) / 255f)
            fin.put((c and 0xFF) / 255f)
            fin.put(0f)
        }
        input.rewind()

        val output = ByteBuffer.allocateDirect(SIZE * SIZE * 2 * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val fout = output.asFloatBuffer()
        return FloatArray(SIZE * SIZE) {
            // Softmax ueber zwei Klassen = Sigmoid der Differenz.
            1f / (1f + exp(fout.get(2 * it) - fout.get(2 * it + 1)))
        }
    }

    companion object {
        const val SIZE = 512
        private const val ASSET = "models/hair_segmenter.tflite"

        fun load(context: Context): HairModel {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes)
            model.rewind()
            val options = Interpreter.Options().apply { setNumThreads(4) }
            return HairModel(Interpreter(model, options))
        }
    }
}
