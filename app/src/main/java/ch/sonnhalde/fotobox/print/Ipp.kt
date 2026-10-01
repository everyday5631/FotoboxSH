package ch.sonnhalde.fotobox.print

import java.io.ByteArrayOutputStream

/** Minimaler IPP-Codec (RFC 8010/8011) fuer Get-Printer-Attributes und Print-Job. */
object Ipp {
    const val OP_PRINT_JOB = 0x0002
    const val OP_GET_PRINTER_ATTRIBUTES = 0x000B

    private class Writer {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v)
        fun u16(v: Int) { u8(v shr 8 and 0xFF); u8(v and 0xFF) }
        fun u32(v: Int) { u16(v ushr 16 and 0xFFFF); u16(v and 0xFFFF) }
        fun attr(tag: Int, name: String, value: ByteArray) {
            u8(tag); u16(name.length); out.write(name.toByteArray()); u16(value.size); out.write(value)
        }
        fun str(tag: Int, name: String, value: String) = attr(tag, name, value.toByteArray())
        fun int(tag: Int, name: String, value: Int) =
            attr(tag, name, ByteArray(4) { (value ushr (24 - 8 * it) and 0xFF).toByte() })
    }

    /** Eine Variante der Job-Attribute; null-Felder werden weggelassen. */
    data class JobOptions(
        val media: String? = null,
        val scalingFill: Boolean = false,
        val landscape: Boolean = false,
        val copies: Int = 1,
    )

    fun getPrinterAttributes(ippUri: String): ByteArray {
        val w = Writer()
        header(w, OP_GET_PRINTER_ATTRIBUTES, ippUri)
        w.str(0x44, "requested-attributes", "printer-name")
        for (a in listOf("printer-state", "printer-state-reasons", "document-format-supported", "media-supported", "media-default")) {
            w.str(0x44, "", a)
        }
        w.u8(0x03)
        return w.out.toByteArray()
    }

    fun printJob(ippUri: String, jobName: String, mime: String, options: JobOptions, data: ByteArray): ByteArray {
        val w = Writer()
        header(w, OP_PRINT_JOB, ippUri)
        w.str(0x42, "job-name", jobName)
        w.str(0x49, "document-format", mime)
        w.u8(0x02)
        w.int(0x21, "copies", options.copies)
        options.media?.let { w.str(0x44, "media", it) }
        if (options.scalingFill) w.str(0x44, "print-scaling", "fill")
        if (options.landscape) w.int(0x23, "orientation-requested", 4)
        w.u8(0x03)
        w.out.write(data)
        return w.out.toByteArray()
    }

    private fun header(w: Writer, op: Int, ippUri: String) {
        w.u8(1); w.u8(1); w.u16(op); w.u32(1)
        w.u8(0x01)
        w.str(0x47, "attributes-charset", "utf-8")
        w.str(0x48, "attributes-natural-language", "en")
        w.str(0x45, "printer-uri", ippUri)
        w.str(0x42, "requesting-user-name", "fotobox")
    }

    class Response(val status: Int, val attrs: Map<String, List<String>>) {
        val ok get() = status < 0x0100
        fun statusText() = "IPP 0x" + status.toString(16).padStart(4, '0')
    }

    fun parse(b: ByteArray): Response {
        require(b.size >= 8) { "Antwort zu kurz" }
        fun u16(p: Int) = (b[p].toInt() and 0xFF shl 8) or (b[p + 1].toInt() and 0xFF)
        val status = u16(2)
        val attrs = linkedMapOf<String, MutableList<String>>()
        var p = 8
        var last = ""
        try {
            while (p < b.size) {
                val tag = b[p++].toInt() and 0xFF
                if (tag == 0x03) break
                if (tag < 0x10) continue
                val nl = u16(p); p += 2
                val name = String(b, p, nl); p += nl
                val vl = u16(p); p += 2
                val value = b.copyOfRange(p, p + vl); p += vl
                if (nl > 0) last = name
                val text = when (tag) {
                    0x21, 0x23 -> if (value.size == 4) ((value[0].toInt() shl 24) or (value[1].toInt() and 0xFF shl 16) or
                        (value[2].toInt() and 0xFF shl 8) or (value[3].toInt() and 0xFF)).toString() else ""
                    0x41, 0x42, 0x44, 0x45, 0x47, 0x48, 0x49 -> String(value)
                    else -> continue
                }
                attrs.getOrPut(last) { mutableListOf() }.add(text)
            }
        } catch (_: IndexOutOfBoundsException) {
            // Abgeschnittene Antwort: bisher gelesene Attribute verwenden.
        }
        return Response(status, attrs)
    }
}
