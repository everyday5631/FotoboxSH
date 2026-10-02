package ch.sonnhalde.fotobox.upload

import android.content.Context
import java.io.File

/** Warteschlange fuer Fotos, deren Upload fehlgeschlagen ist (z. B. kein Internet); wird im Setup erneut gesendet. */
class UploadQueue(context: Context) {
    private val dir = File(context.filesDir, "upload-queue").apply { mkdirs() }

    fun add(jpeg: ByteArray) {
        File(dir, "foto-${System.currentTimeMillis()}.jpg").writeBytes(jpeg)
    }

    fun files(): List<File> = dir.listFiles()?.sortedBy { it.name }.orEmpty()
    fun count(): Int = files().size
    fun clear() = files().forEach { it.delete() }
}
