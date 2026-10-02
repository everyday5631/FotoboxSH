package ch.sonnhalde.fotobox.print

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Verpackt ein JPEG in ein einseitiges PDF im Format 4 x 6 Zoll (Hoch- oder Querformat je nach Bild).
 * Noetig, weil der WCMPlus-Druckserver laut «document-format-supported» PDF/PostScript/Raster annimmt, aber kein JPEG.
 */
object PdfWrap {
    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    fun jpegToPdf(jpeg: ByteArray, imgW: Int, imgH: Int): ByteArray {
        val portrait = imgH > imgW
        val pw = if (portrait) 288f else 432f // 4 x 6 Zoll in Punkt (1/72 Zoll)
        val ph = if (portrait) 432f else 288f
        val scale = minOf(pw / imgW, ph / imgH)
        val dw = imgW * scale
        val dh = imgH * scale
        val content = "q ${f(dw)} 0 0 ${f(dh)} ${f((pw - dw) / 2)} ${f((ph - dh) / 2)} cm /Im0 Do Q"

        val out = ByteArrayOutputStream()
        val offsets = IntArray(6)
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.4\n")
        offsets[1] = out.size(); w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets[2] = out.size(); w("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        offsets[3] = out.size()
        w("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")
        offsets[4] = out.size()
        w("4 0 obj\n<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
        out.write(jpeg)
        w("\nendstream\nendobj\n")
        offsets[5] = out.size(); w("5 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
        val xref = out.size()
        w("xref\n0 6\n0000000000 65535 f \n")
        for (i in 1..5) w(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        w("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
