package ch.sonnhalde.fotobox.photo

import android.icu.text.BreakIterator

/**
 * Text in sichtbare Zeichen zerlegen («Grapheme»): ein Emoji oder eine Flagge besteht aus mehreren UTF-16-Zeichen und darf
 * beim Kuerzen, Zaehlen und Einfaerben nicht zerrissen werden (sonst erscheinen Kaestchen statt des Emoji).
 */
object Graphemes {
    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out.add(text.substring(start, end))
            start = end
            end = it.next()
        }
        return out
    }

    fun count(text: String): Int = split(text).size

    /** Die ersten [n] sichtbaren Zeichen. */
    fun take(text: String, n: Int): String = if (text.length <= n) text else split(text).take(n).joinToString("")

    /** Ob das sichtbare Zeichen ein Emoji ist (Farb-Glyphe: eine Textfarbe hat darauf keine Wirkung). */
    fun isEmoji(cluster: String): Boolean {
        if (cluster.isEmpty()) return false
        val cp = cluster.codePointAt(0)
        return cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF ||
            cluster.indexOf('️') >= 0 || cluster.indexOf('‍') >= 0
    }
}
