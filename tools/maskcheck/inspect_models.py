#!/usr/bin/env python3
"""Laedt die drei Google-MediaPipe-Modelle, zeigt ihre Ein-/Ausgaenge und vergleicht sie an einem Bild.

Aufruf:  pip install numpy pillow ai-edge-litert
         python3 tools/maskcheck/inspect_models.py BILD.jpg [AUSGABE.jpg]

Ausgabe: eine Bilderreihe  Original | Selfie-Person | Multiclass-Person | Multiclass-Haar | Haarmodell
Die Bilder duerfen KEINE geschwaerzten oder verpixelten Gesichter enthalten (ein schwarzer Fleck wird als Haar erkannt).
Nutzerfotos nicht ins Repository legen.

Bekannte Formen (27.11.2026):
  hair_segmenter   781618 B  Eingang [1,512,512,4] float32 (RGB 0..1 + 4. Kanal fuer die Maske des Vorbildes, hier 0),
                             Ausgang [1,512,512,2] Logits (0 Hintergrund, 1 Haar)   -> Haar = sigmoid(l1 - l0)
  selfie_multiclass 16371837 B Eingang [1,256,256,3]  Ausgang [1,256,256,6] Logits (Hintergrund, Haar, Koerper-Haut,
                             Gesichts-Haut, Kleider, Sonstiges)   -> Softmax
  selfie_segmenter  249537 B  Eingang [1,256,256,3]  Ausgang [1,256,256,1] Wahrscheinlichkeit Person
"""
import os
import sys
import urllib.request

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

BASE = "https://storage.googleapis.com/mediapipe-models/image_segmenter/"
MODELS = {
    "hair": BASE + "hair_segmenter/float32/latest/hair_segmenter.tflite",
    "multiclass": BASE + "selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite",
    "selfie": BASE + "selfie_segmenter/float16/latest/selfie_segmenter.tflite",
}
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "models")  # in .gitignore


def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name + ".tflite")
    if not os.path.exists(path):
        print("lade", MODELS[name])
        urllib.request.urlretrieve(MODELS[name], path)
    it = Interpreter(model_path=path)
    it.allocate_tensors()
    ins = [(d["shape"].tolist(), d["dtype"].__name__) for d in it.get_input_details()]
    outs = [d["shape"].tolist() for d in it.get_output_details()]
    print("%-10s %9d B  Eingang %s  Ausgang %s" % (name, os.path.getsize(path), ins, outs))
    return it


def run(it, arr):
    it.set_tensor(it.get_input_details()[0]["index"], arr)
    it.invoke()
    return it.get_tensor(it.get_output_details()[0]["index"])[0]


def main():
    models = {n: fetch(n) for n in MODELS}
    if len(sys.argv) < 2:
        return
    img = Image.open(sys.argv[1]).convert("RGB")
    prep = lambda n: np.asarray(img.resize((n, n), Image.BILINEAR), np.float32) / 255.0
    sf = run(models["selfie"], prep(256)[None])[..., 0]
    mc = run(models["multiclass"], prep(256)[None])
    e = np.exp(mc - mc.max(axis=2, keepdims=True))
    sm = e / e.sum(axis=2, keepdims=True)
    h = run(models["hair"], np.concatenate([prep(512), np.zeros((512, 512, 1), np.float32)], axis=2)[None])
    hair = 1 / (1 + np.exp(h[..., 0] - h[..., 1]))
    tile = lambda m: Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8)).resize((384, 384)).convert("RGB")
    out = Image.new("RGB", (384 * 5, 384))
    for i, t in enumerate([img.resize((384, 384)), tile(sf), tile(1 - sm[..., 0]), tile(sm[..., 1]), tile(hair)]):
        out.paste(t, (384 * i, 0))
    dest = sys.argv[2] if len(sys.argv) > 2 else "modelle_vergleich.jpg"
    out.save(dest, quality=88)
    print("gespeichert:", dest)


if __name__ == "__main__":
    main()
