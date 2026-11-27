package ch.sonnhalde.fotobox.photo

/**
 * Reine Rechenhilfen fuer die Personenmaske (ohne Android-Klassen). Masken sind Float-Felder 0..1, Zeile fuer Zeile.
 */
internal object MaskMath {

    /** Bilineares Skalieren eines Float-Feldes (Pixelmitten ausgerichtet, wie bei Bitmaps). */
    fun resizeBilinear(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = FloatArray(dw * dh)
        val xScale = sw.toFloat() / dw
        val yScale = sh.toFloat() / dh
        // x-Positionen einmal vorberechnen.
        val x0 = IntArray(dw)
        val x1 = IntArray(dw)
        val fx = FloatArray(dw)
        for (x in 0 until dw) {
            val sx = ((x + 0.5f) * xScale - 0.5f).coerceIn(0f, (sw - 1).toFloat())
            val i = sx.toInt()
            x0[x] = i
            x1[x] = minOf(i + 1, sw - 1)
            fx[x] = sx - i
        }
        for (y in 0 until dh) {
            val sy = ((y + 0.5f) * yScale - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val j0 = sy.toInt()
            val j1 = minOf(j0 + 1, sh - 1)
            val fy = sy - j0
            val r0 = j0 * sw
            val r1 = j1 * sw
            val o = y * dw
            for (x in 0 until dw) {
                val top = src[r0 + x0[x]] * (1 - fx[x]) + src[r0 + x1[x]] * fx[x]
                val bot = src[r1 + x0[x]] * (1 - fx[x]) + src[r1 + x1[x]] * fx[x]
                out[o + x] = top * (1 - fy) + bot * fy
            }
        }
        return out
    }

    /** Mittelwert im Fenster (2r+1)x(2r+1), am Rand nur ueber die vorhandenen Pixel. */
    fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h)
        // Waagrecht: gleitende Summe je Zeile.
        for (y in 0 until h) {
            val row = y * w
            var sum = 0.0
            for (x in 0..minOf(r, w - 1)) sum += src[row + x]
            for (x in 0 until w) {
                val lo = maxOf(x - r, 0)
                val hi = minOf(x + r, w - 1)
                tmp[row + x] = (sum / (hi - lo + 1)).toFloat()
                if (x + r + 1 < w) sum += src[row + x + r + 1]
                if (x - r >= 0) sum -= src[row + x - r]
            }
        }
        // Senkrecht: gleitende Summe je Spalte, zeilenweise (cache-freundlich).
        val out = FloatArray(w * h)
        val col = DoubleArray(w)
        for (y in 0..minOf(r, h - 1)) {
            val row = y * w
            for (x in 0 until w) col[x] = col[x] + tmp[row + x].toDouble()
        }
        for (y in 0 until h) {
            val lo = maxOf(y - r, 0)
            val hi = minOf(y + r, h - 1)
            val n = (hi - lo + 1).toDouble()
            val row = y * w
            for (x in 0 until w) out[row + x] = (col[x] / n).toFloat()
            if (y + r + 1 < h) {
                val add = (y + r + 1) * w
                for (x in 0 until w) col[x] = col[x] + tmp[add + x].toDouble()
            }
            if (y - r >= 0) {
                val sub = (y - r) * w
                for (x in 0 until w) col[x] = col[x] - tmp[sub + x].toDouble()
            }
        }
        return out
    }

    /**
     * Guided Filter (He et al.): glaettet die grobe Maske [p] so, dass ihr Rand den Kanten des Fuehrungsbildes [guide]
     * (Helligkeit 0..1) folgt. [r] Fensterradius in Pixeln, [eps] Regularisierung (kleiner = naeher an den Bildkanten).
     */
    fun guidedFilter(guide: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = w * h
        val meanI = boxMean(guide, w, h, r)
        val meanP = boxMean(p, w, h, r)
        val ip = FloatArray(n) { guide[it] * p[it] }
        val corrIp = boxMean(ip, w, h, r)
        val ii = FloatArray(n) { guide[it] * guide[it] }
        val corrII = boxMean(ii, w, h, r)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val varI = corrII[i] - meanI[i] * meanI[i]
            val covIp = corrIp[i] - meanI[i] * meanP[i]
            val ai = covIp / (varI + eps)
            a[i] = ai
            b[i] = meanP[i] - ai * meanI[i]
        }
        val meanA = boxMean(a, w, h, r)
        val meanB = boxMean(b, w, h, r)
        return FloatArray(n) { (meanA[it] * guide[it] + meanB[it]).coerceIn(0f, 1f) }
    }

    /** Weiche Stufe: unter [low] 0, ueber [high] 1, dazwischen S-Kurve. */
    fun smoothstep(x: Float, low: Float, high: Float): Float {
        val t = ((x - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
