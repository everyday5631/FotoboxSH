package ch.sonnhalde.fotobox.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/** Ein waehlbarer Foto-Hintergrund. [id] steht in den Einstellungen: «asset:name.jpg» (mitgeliefert) oder «file:name.jpg» (eigenes Bild). */
data class BackgroundItem(val id: String, val label: String, val builtIn: Boolean)

/**
 * Sammlung der Foto-Hintergruende (Ersatz fuer den Hintergrund hinter den Personen, wie bei Teams).
 * Mitgelieferte Platzhalter liegen unter `app/src/main/assets/backgrounds/`; eigene Bilder (aus der Galerie)
 * werden verkleinert in `files/backgrounds/` abgelegt.
 */
class BackgroundLibrary(private val context: Context) {
    private val dir = File(context.filesDir, "backgrounds")

    fun builtIn(): List<BackgroundItem> =
        (context.assets.list(ASSET_DIR) ?: emptyArray()).filter { it.isImage() }.sorted()
            .map { BackgroundItem("asset:$it", it.substringBeforeLast('.').replace('_', ' ').replaceFirstChar(Char::uppercase), builtIn = true) }

    fun custom(): List<BackgroundItem> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.isImage() }.sortedBy { it.lastModified() }
            .mapIndexed { i, f -> BackgroundItem("file:${f.name}", "Eigenes Bild ${i + 1}", builtIn = false) }

    fun all(): List<BackgroundItem> = builtIn() + custom()

    /** Laedt den Hintergrund; die lange Seite ist hoechstens [maxSide] Pixel gross. Null, wenn er fehlt oder nicht lesbar ist. */
    fun load(id: String, maxSide: Int = 2400): Bitmap? = runCatching {
        when {
            id.startsWith("asset:") -> decode(maxSide) { context.assets.open("$ASSET_DIR/${id.removePrefix("asset:")}") }
            id.startsWith("file:") -> File(dir, id.removePrefix("file:")).takeIf { it.exists() }
                ?.let { f -> decode(maxSide) { f.inputStream() } }
            else -> null
        }
    }.getOrNull()

    /** Kleine Vorschau fuer die Auswahl im Setup. */
    fun thumbnail(id: String): Bitmap? = load(id, 360)

    /** Gibt es das Bild noch? (Vor allem fuer eigene Bilder, die geloescht sein koennen.) */
    fun exists(id: String): Boolean = when {
        id.startsWith("asset:") -> builtIn().any { it.id == id }
        id.startsWith("file:") -> File(dir, id.removePrefix("file:")).exists()
        else -> false
    }

    /** Fuegt ein Bild aus der Galerie hinzu (als JPEG, lange Seite hoechstens 2400 px). Liefert die neue ID oder null. */
    fun add(uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        var bmp = decode(MAX_STORED_SIDE) { resolver.openInputStream(uri) ?: error("Bild nicht lesbar") } ?: return null
        // Handyfotos aus der Galerie tragen die Drehung oft nur als EXIF-Angabe.
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        dir.mkdirs()
        val name = "bg_${System.currentTimeMillis()}.jpg"
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        "file:$name"
    }.getOrNull()

    /** Loescht ein eigenes Bild; mitgelieferte lassen sich nicht loeschen. */
    fun delete(id: String): Boolean =
        id.startsWith("file:") && File(dir, id.removePrefix("file:")).delete()

    private fun decode(maxSide: Int, open: () -> java.io.InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val bmp = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide <= maxSide) return bmp
        val f = maxSide / longSide.toFloat()
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
    }

    private fun String.isImage() = lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }

    companion object {
        private const val ASSET_DIR = "backgrounds"
        private const val MAX_STORED_SIDE = 2400
    }
}
