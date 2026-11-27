package ch.sonnhalde.fotobox.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File

/**
 * Ein Foto im Verlauf. Statuswerte (Text, damit die Datei lesbar bleibt):
 * - [printStatus]: none (noch nicht gedruckt) | pending (Auftrag wartet) | printing | done | failed
 * - [uploadStatus]: pending (offen oder laeuft) | done | failed
 */
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    /** Abzuege des letzten Druckauftrags (so viele wollte der Gast beim ersten Versuch). */
    val copies: Int = 1,
    val printStatus: String = "none",
    val printDetail: String = "",
    val printedAt: Long = 0L,
    val uploadStatus: String = "pending",
    val uploadUrl: String = "",
)

/**
 * Die letzten Fotos (Druck- und digitale Version) auf dem internen Speicher der App: `files/history/<Zeitstempel>/`
 * mit `print.jpg`, `digital.jpg` und `status.json`. Ring-Puffer nach Anzahl und Alter; Fotos mit wartendem oder
 * laufendem Druck werden nie entfernt (der Druck ist Pflicht).
 */
class PhotoHistory(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "history").apply { mkdirs() }
    private val lock = Any()

    fun printFile(id: String) = File(dir, "${safe(id)}/print.jpg")
    fun digitalFile(id: String) = File(dir, "${safe(id)}/digital.jpg")

    /** Nur Ziffern: die IDs sind Zeitstempel, so kann nie ein Pfad ausserhalb des Ordners entstehen. */
    private fun safe(id: String): String = if (id.isNotEmpty() && id.all { it.isDigit() }) id else error("ungueltige ID")

    fun add(printJpeg: ByteArray, digitalJpeg: ByteArray): HistoryEntry = synchronized(lock) {
        var stamp = System.currentTimeMillis()
        while (File(dir, stamp.toString()).exists()) stamp++
        val d = File(dir, stamp.toString()).apply { mkdirs() }
        try {
            File(d, "print.jpg").writeBytes(printJpeg)
            File(d, "digital.jpg").writeBytes(digitalJpeg)
            val entry = HistoryEntry(id = stamp.toString(), createdAt = stamp)
            write(d, entry)
            entry
        } catch (e: Exception) {
            d.deleteRecursively()
            throw e
        }
    }

    /** Neueste zuerst. */
    fun list(): List<HistoryEntry> = synchronized(lock) {
        dir.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { parse(it) }.sortedByDescending { it.createdAt }
    }

    fun get(id: String): HistoryEntry? = synchronized(lock) { parse(File(dir, safe(id))) }

    /** Aendert einen Eintrag; null, wenn es ihn nicht mehr gibt (z. B. inzwischen geloescht). */
    fun update(id: String, change: (HistoryEntry) -> HistoryEntry): HistoryEntry? = synchronized(lock) {
        val d = File(dir, safe(id))
        val old = parse(d) ?: return null
        change(old).copy(id = old.id, createdAt = old.createdAt).also { write(d, it) }
    }

    fun delete(id: String) = synchronized(lock) { File(dir, safe(id)).deleteRecursively(); Unit }

    fun clear() = synchronized(lock) { dir.listFiles().orEmpty().forEach { it.deleteRecursively() } }

    /**
     * Entfernt, was ueber die Anzahl hinaus ist oder aelter als [maxAgeHours] (0 = kein Alterslimit). Wartende und
     * laufende Druckauftraege bleiben. Verwaiste Ordner ohne Status (abgebrochenes Speichern) werden aufgeraeumt.
     */
    fun prune(maxCount: Int, maxAgeHours: Int) = synchronized(lock) {
        val now = System.currentTimeMillis()
        dir.listFiles().orEmpty().filter { it.isDirectory && !File(it, "status.json").exists() && now - it.lastModified() > 5 * 60_000L }
            .forEach { it.deleteRecursively() }
        list().forEachIndexed { index, e ->
            val busy = e.printStatus == "pending" || e.printStatus == "printing"
            val tooMany = maxCount > 0 && index >= maxCount
            val tooOld = maxAgeHours > 0 && now - e.createdAt > maxAgeHours * 3_600_000L
            if (!busy && (tooMany || tooOld)) File(dir, e.id).deleteRecursively()
        }
    }

    /** Nach einem Neustart: ein Druck, der gerade lief, ist ungewiss (Abzug evtl. schon gedruckt) und wird nicht automatisch wiederholt. */
    fun recoverInterrupted() = synchronized(lock) {
        list().filter { it.printStatus == "printing" }.forEach { e ->
            update(e.id) { it.copy(printStatus = "failed", printDetail = "Unterbrochen: die App wurde während des Drucks beendet. Bitte am Drucker prüfen, ob ein Abzug kam.") }
        }
    }

    /** Verkleinerte Ansicht (laengste Seite hoechstens [maxSide] Pixel). */
    fun image(id: String, maxSide: Int, digital: Boolean = false): Bitmap? = runCatching {
        val file = if (digital) digitalFile(id) else printFile(id)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun thumbnail(id: String): Bitmap? = image(id, 300)

    /** Beide Versionen als JPEG in «Downloads/Fotobox» (sichtbar im Dateimanager und per USB); gibt die Zahl der Dateien zurueck. */
    fun exportToDownloads(id: String): Int {
        val resolver = appContext.contentResolver
        var count = 0
        for ((suffix, file) in listOf("druck" to printFile(id), "digital" to digitalFile(id))) {
            if (!file.exists()) continue
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "fotobox-$id-$suffix.jpg")
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fotobox")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Datei konnte nicht angelegt werden")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Datei konnte nicht geschrieben werden")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                count++
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        return count
    }

    private fun parse(d: File): HistoryEntry? = runCatching {
        val j = JSONObject(File(d, "status.json").readText())
        HistoryEntry(
            id = d.name,
            createdAt = j.optLong("createdAt", d.name.toLongOrNull() ?: 0L),
            copies = j.optInt("copies", 1),
            printStatus = j.optString("printStatus", "none"),
            printDetail = j.optString("printDetail", ""),
            printedAt = j.optLong("printedAt", 0L),
            uploadStatus = j.optString("uploadStatus", "pending"),
            uploadUrl = j.optString("uploadUrl", ""),
        )
    }.getOrNull()

    /** Erst in eine Hilfsdatei, dann umbenennen: ein Absturz mitten im Schreiben laesst keine halbe Statusdatei zurueck. */
    private fun write(d: File, e: HistoryEntry) {
        val json = JSONObject()
            .put("createdAt", e.createdAt)
            .put("copies", e.copies)
            .put("printStatus", e.printStatus)
            .put("printDetail", e.printDetail)
            .put("printedAt", e.printedAt)
            .put("uploadStatus", e.uploadStatus)
            .put("uploadUrl", e.uploadUrl)
        val tmp = File(d, "status.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(File(d, "status.json"))) {
            File(d, "status.json").writeText(json.toString())
            tmp.delete()
        }
    }
}
