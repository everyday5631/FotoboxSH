#!/usr/bin/env python3
"""Prueft die Rechenteile der Personenmaske (photo/MaskMath.kt) ohne Android: 1:1-Nachbildung in Python.

1. boxMean (gleitende Summen) gegen eine Brute-Force-Rechnung.
2. Guided Filter an einer synthetischen Szene (Person mit gezackter Kontur, grobe Maske aus 64x64 hochskaliert):
   Fehler im Randband fuer verschiedene Radien, eps und Schwellen.

Aufruf:  pip install numpy pillow   und dann   python3 tools/maskcheck/check_filters.py
Ergebnis vom 27.11.2026: Rand-Fehler der groben Maske 0.077, mit Guided Filter r=12 eps=1e-3 etwa 0.014 (5-8x kleiner);
eps=1e-2 ist deutlich schlechter (0.038).
"""
import numpy as np


def box_mean(src, w, h, r):
    """Nachbildung von MaskMath.boxMean: Mittelwert im Fenster (2r+1)^2, am Rand nur ueber vorhandene Pixel."""
    s = src.reshape(h, w).astype(np.float64)
    tmp = np.zeros((h, w))
    for y in range(h):
        sm = 0.0
        for x in range(0, min(r, w - 1) + 1):
            sm += s[y, x]
        for x in range(w):
            lo, hi = max(x - r, 0), min(x + r, w - 1)
            tmp[y, x] = sm / (hi - lo + 1)
            if x + r + 1 < w:
                sm += s[y, x + r + 1]
            if x - r >= 0:
                sm -= s[y, x - r]
    out = np.zeros((h, w))
    col = np.zeros(w)
    for y in range(0, min(r, h - 1) + 1):
        col += tmp[y]
    for y in range(h):
        lo, hi = max(y - r, 0), min(y + r, h - 1)
        out[y] = col / (hi - lo + 1)
        if y + r + 1 < h:
            col += tmp[y + r + 1]
        if y - r >= 0:
            col -= tmp[y - r]
    return out.ravel()


def brute(src, w, h, r):
    s = src.reshape(h, w)
    o = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            o[y, x] = s[max(y - r, 0):min(y + r, h - 1) + 1, max(x - r, 0):min(x + r, w - 1) + 1].mean()
    return o.ravel()


def guided(I, p, w, h, r, eps):
    """Nachbildung von MaskMath.guidedFilter (He et al.), Fuehrungsbild I = Helligkeit 0..1."""
    mI, mP = box_mean(I, w, h, r), box_mean(p, w, h, r)
    cIp, cII = box_mean(I * p, w, h, r), box_mean(I * I, w, h, r)
    a = (cIp - mI * mP) / (cII - mI * mI + eps)
    b = mP - a * mI
    return np.clip(box_mean(a, w, h, r) * I + box_mean(b, w, h, r), 0, 1)


def smoothstep(x, lo, hi):
    t = np.clip((x - lo) / (hi - lo), 0, 1)
    return t * t * (3 - 2 * t)


def bilinear_up(a, n):
    s = a.shape[0]
    out = np.zeros((n, n))
    for y in range(n):
        sy = np.clip((y + .5) * s / n - .5, 0, s - 1)
        j0 = int(sy); j1 = min(j0 + 1, s - 1); fy = sy - j0
        for x in range(n):
            sx = np.clip((x + .5) * s / n - .5, 0, s - 1)
            i0 = int(sx); i1 = min(i0 + 1, s - 1); fx = sx - i0
            out[y, x] = (a[j0, i0] * (1 - fx) + a[j0, i1] * fx) * (1 - fy) + (a[j1, i0] * (1 - fx) + a[j1, i1] * fx) * fy
    return out


def main():
    rng = np.random.default_rng(1)
    for (w, h, r) in [(17, 11, 3), (5, 5, 7), (1, 9, 2), (30, 4, 1), (12, 12, 0)]:
        a = rng.random(w * h)
        d = np.abs(box_mean(a, w, h, r) - brute(a, w, h, r)).max()
        print("boxMean", (w, h, r), "max. Abweichung", d)
        assert d < 1e-9
    W = H = 512
    yy, xx = np.mgrid[0:H, 0:W]
    ang = np.arctan2(yy - 250, xx - 256)
    rad = np.hypot(yy - 250, xx - 256)
    edge = 150 + 10 * np.sin(ang * 23) + 6 * np.sin(ang * 41)
    truth = (rad < edge).astype(float)
    bg = 0.30 + 0.15 * np.sin(xx / 37.0) * np.cos(yy / 29.0)
    img = np.where(truth > 0, 0.75 + 0.05 * np.sin(xx / 11.0), bg) + 0.01 * rng.standard_normal((H, W))
    coarse = bilinear_up(truth.reshape(64, 8, 64, 8).mean(axis=(1, 3)), W)  # Modell: grob, dann hochskaliert
    band = np.abs(rad - edge) < 14
    err = lambda m: np.abs(m - truth)[band].mean()
    print("Rand-Fehler alt (Raster 0.35-0.75): %.4f" % err(smoothstep(coarse, 0.35, 0.75)))
    I = img.ravel()
    for r, eps in [(8, 1e-3), (12, 1e-3), (12, 1e-2), (16, 1e-3), (12, 1e-4)]:
        g = guided(I, coarse.ravel(), W, H, r, eps).reshape(H, W)
        print("Guided r=%2d eps=%g: Rand-Fehler %.4f" % (r, eps, err(smoothstep(g, 0.3, 0.8))))


if __name__ == "__main__":
    main()
