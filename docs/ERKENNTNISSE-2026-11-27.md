# Arbeitsstand und Erkenntnisse vom 27.11.2026

Protokoll einer langen Sitzung zwischen Nutzerperson und Claude (Sitzung `session_014cQ27QzRmik6yGdLfkuZQt`, Vorgängersitzung
«Offene PR Review» `session_014cQ27QHKzLi7NCXwNSnLjc`). Zweck: Nichts geht verloren, wenn der Chat wechselt. Die Gerätefakten,
Entscheidungen und die offene Liste stehen weiterhin in `docs/ENTWICKLUNG.md`; hier steht der Weg dorthin, mit Zahlen und Begründungen.

**Kennzeichnung der Aussagen**

- **gemessen**: von der Nutzerperson auf dem Tablet getestet (Bildschirm, Video, Logcat).
- **nachgerechnet**: von Claude in der Cloud-Umgebung mit Python oder aus dem Code nachgeprüft, nicht auf dem Tablet.
- **Annahme**: nicht belegt, bitte nicht als Tatsache behandeln.

Grundsatz aus `CLAUDE.md` gilt weiter: Ein grüner Build heisst nur, dass der Code kompiliert. Was nicht auf dem Tablet getestet wurde, ist ungetestet.

## 1. Kurzfassung

- Der **Foto-Hintergrund** (Person freistellen, vor ein Bild setzen) funktioniert auf dem Tablet und ist laut Nutzerperson mit echten Personen
  «voll okay» (gemessen, Build 0.1.113). Fehler vom Mittwoch (Hintergrund wurde nie eingesetzt) ist behoben.
- Ränder wurden deutlich besser durch: zweiter Erkennungsdurchgang auf dem Personenausschnitt, Guided Filter, **enge** Schwellen 0.40/0.60.
- Das **Haarmodell** läuft (gemessen), ergänzt aber fast nichts (0,03 bis 0,06 % des Bildes). Es bleibt auf Wunsch der Nutzerperson drin, mit
  Schalter zum Ausschalten.
- **Emoji im Banner** gehen jetzt. Ursache war ein Fehler im Regenbogen-Code (Text wurde in einzelne UTF-16-Zeichen zerlegt).
- **Ergebnis-Bildschirm** scrollt im Querformat nicht mehr (gemessen, gut sichtbar). **Setup-Kopfleiste** hat Logo, Kennzeichnung «ENTWICKLERMODUS»
  und ein echtes Ausschalten-Symbol (gemessen).
- **Rückmeldung für Gäste** nach dem Auslösen ist gebaut (Build grün), auf dem Tablet **noch nicht getestet**.
- Neue Traktanden: Töne, Modell-Neustart-Knopf, Speicher und Rechenzeit genauer prüfen, Druck als Pflicht mit Warteschlange, Benutzerdefinitionen. Siehe Abschnitt 9.
- Druck und Kiosk-Beenden werden **zuletzt** getestet (Fotopapier ist knapp).

## 2. Stand von Code, Pull Requests und Builds

| Was | Stand |
|---|---|
| PR [#12](https://github.com/everyday5631/FotoboxSH/pull/12) | Entwurf, offen. Hintergrund-Auswahl beim Fotografieren, halbtransparente Vorschau, Diagnose-Zeile, weisses Logo im Ergebnis-Banner, Fix «Maske ARGB» und «pixelweise mischen». |
| PR [#13](https://github.com/everyday5631/FotoboxSH/pull/13) | **Geschlossen, nicht gemergt** (Dokumentation als GitHub-Pages-Seite, Kategorie-Pflicht pro Seite). Ob die Seite doch gewollt ist, ist offen. Die Kategorie-Regel steht deshalb **nicht** in `CLAUDE.md` auf diesem Zweig. |
| PR [#14](https://github.com/everyday5631/FotoboxSH/pull/14) | Entwurf, offen, Zweig `claude/dazzling-thompson-fvlhl1`. Enthält die Commits von #12 (damit es keine Konflikte gibt) plus alles Neue unten. Wird #12 zuerst gemergt, schrumpft #14 auf die eigenen Commits. |
| Gemergt | #8 Banner anpassbar, #11 Foto-Hintergrund und App-Icon (beide am 26.11. gemergt). |

**Versionsnummern auf dem Tablet** (Anzeige «Version 0.1.N (Commit)», N = Lauf-Nummer von GitHub Actions; Push- und PR-Lauf eines Commits haben je eine Nummer):

| Version | Commit | Inhalt |
|---|---|---|
| 0.1.95 | `a64f3cb` | weisses Logo im Banner, Hintergrund wurde noch nicht eingesetzt |
| 0.1.96 / 0.1.97 | `a22caf0` | Maske als ARGB statt ALPHA_8 |
| 0.1.98 / 0.1.99 | `8dae367` | Person und Hintergrund pixelweise mischen: **Hintergrund funktioniert** |
| 0.1.107 | `8eae85f` | Querformat ohne Scrollen, zweiter Durchgang, Guided Filter (Schwellen 0.30/0.80, wirkten verwaschen) |
| 0.1.113 | `2ba8e94` | Haarmodell, enge Schwellen 0.40/0.60, Emoji, Kopfleiste |
| höher als 0.1.113 | `529f7cf` | Rückmeldung nach dem Auslösen, Schalter Haarmodell, Dauer in der Diagnose-Zeile |

Commit-Reihenfolge auf PR #14 (neueste oben): `529f7cf` Rückmeldung/Schalter, `2ba8e94` Emoji/Kopfleiste, `53900e7` Doku, `35029fc` Haarmodell,
`8eae85f` Kompilierfehler `MaskMath`, `e790e15` zweiter Durchgang + Guided Filter, `399142e` Doku Punkt 9, `f780afd` Layout ohne Scrollen.

Dateien dieser Sitzung: `photo/BackgroundRemover.kt` (Ablauf), `photo/MaskMath.kt` (Rechenteile, ohne Android-Klassen), `photo/HairModel.kt`
(TensorFlow Lite), `photo/Graphemes.kt` (Emoji-sicheres Zerlegen), `photo/PhotoComposer.kt` (Banner-Text), `ui/CustomerScreens.kt` (Ergebnis),
`camera/CameraScreen.kt` (Countdown, Rückmeldung), `ui/SetupScreen.kt` (Kopfleiste, Schalter), `wcm/WcmSettings.kt` (Einstellungen),
`MainViewModel.kt` (Ablauf, `processing`), `assets/models/hair_segmenter.tflite`, `res/drawable/ic_power.xml`, `tools/maskcheck/` (Prüfskripte).

## 3. Umgebung und Arbeitsweise (was wir gelernt haben)

- **ADB läuft auf dem Linux-Rechner der Nutzerperson** (Konsole, `neo@bazzite`, Ordner `~/Downloads/SOHA/platform-tools`). Befehle daher `./adb …`.
  Anfangs hatte Claude Windows-Befehle (`.\adb`, `cmd /c`) genannt, das war falsch. Die Cloud-Umgebung erreicht das Tablet nicht und es ist kein
  Rechner der Nutzerperson angehängt; Claude wertet nur aus, was die Nutzerperson schickt.
- **Log holen:** `./adb logcat -c`, dann Foto machen, dann `./adb logcat -d -s Fotobox:V AndroidRuntime:E`. Tag `Fotobox`. Zeilen:
  `Freistellung N ms (Foto WxH, Maske WxH, Haarmodell an|aus)`, `Hintergrund ersetzt, Personenanteil X`, `Zweiter Durchgang auf Ausschnitt …`,
  `Haarmodell N ms, Haar im Bild X, zur Maske ergaenzt Y`, bei Problemen `Haarmodell nicht verfügbar`, `… übersprungen`.
- **Bauen** nur in GitHub Actions (`.github/workflows/build.yml`), ca. 1,5 Minuten. APK als Artefakt des Laufs; direkt über die installierte App
  installierbar (festes Debug-Zertifikat). Zwei Läufe je Push (Push und Pull Request).
- **Maven Central kann mit 429 «Too Many Requests» antworten.** Das war ein Fehlschlag ohne Codefehler; `rerun_failed_jobs` löste ihn
  (Werkzeug `mcp__github__actions_run_trigger`). Immer erst das Log lesen, bevor man etwas am Code ändert.
- **Google Maven (`dl.google.com`) ist aus der Cloud-Umgebung gesperrt**; `storage.googleapis.com` (Modelle) und Maven Central sind erreichbar, `pip install`
  geht (`ai-edge-litert` zum Ausführen der Modelle in Python). Deshalb konnte Claude Modelle hier prüfen, aber keine Android-Bibliothek.
- **Videos auswerten:** `ffmpeg` ist vorhanden (Bildschirmaufnahmen der Nutzerperson sind 3048 x 2032, MP4). Einzelbilder per `ffmpeg -vf fps=…`.
- **Testbilder:** Die Nutzerperson schwärzt oder verpixelt Gesichter in Screenshots. Das verfälscht die Erkennung (ein schwarzer Fleck wird als Haar
  erkannt). Für Modell-Tests braucht Claude **unveränderte Originale** (Wahl «Original», ohne Hintergrund-Ersatz). Nutzerfotos nicht ins Repository legen.
- **Eine Nachricht statt zwei:** Die Nutzerperson antwortet pro Nachricht; wenn sie mitten im Schreiben ist, bitte Meldungen zusammenlegen.
- **Sprache:** Deutsch, Schweizer Schreibung («ss»). Neuer PR immer als Entwurf, Merge macht die Nutzerperson.

## 4. Gerät und Testaufbau

- **Tablet:** Xiaomi Pad 6S Pro 12.4, 8 GB RAM, 256 GB (Angabe der Nutzerperson; Typbezeichnung «X0028SZ1K9»). Prozessor laut Claudes Kenntnis Snapdragon 8 Gen 2 (**Annahme**, nicht geprüft).
  Läuft voraussichtlich am Ladegerät, nicht im Akkubetrieb. Ausser der Fotobox läuft nichts von Hand; im Hintergrund nur Xiaomi-Systemdienste (und der UpReach-Ordner, nicht gestartet).
- **Kamera:** Es wird **ausschliesslich die Frontkamera** benutzt (Nutzerperson). Der Schalter «Rückkamera» im Setup **bleibt** als Notlösung, falls Android die
  Kameras einmal verwechselt (Entscheidung der Nutzerperson).
- **Foto von der Frontkamera:** 1632 x 1224 Pixel (**gemessen**, Logcat). Erkennungsbild 1024 x 768.
- **Timer:** 3 Sekunden. **90 Sekunden** ohne Eingabe auf dem Ergebnis zurück zum Start (**gemessen**, mehrfach live erlebt). 60 Sekunden ohne Tippen auf dem Kamera-Bildschirm zurück zum Start.
- **Drucker:** DNP QW410 mit WCMPlus. **Bei jedem Start des DNP-Systems druckt der Drucker eine Status-Seite.** Ein Drucktest kostet also zwei Abzüge, das Fotopapier ist knapp.
  Darum werden Druck, WLAN-Wechsel zum Drucker und Kiosk-Beenden **zuletzt** getestet.
- **Kiosk:** «App anheften» funktioniert (Meldung «Diese App ist angeheftet» im Video).
- **Testmotive:** «Severin» ist ein KI-erzeugtes Porträt (Stockbild `portrait-handsome-white-guy-image-generated-by-ai_803126-1310`: heller Grund, weisses T-Shirt, brauner Haarschopf).
  Es wurde meist vom Bildschirm eines zweiten Tablets abfotografiert. **Ein Foto vom Foto ist ein harter, unrealistischer Test:** Tablet-Rahmen und Hände landen in der Maske,
  das helle Shirt verschwimmt mit dem hellen Bildschirm. Aussagekräftig ist nur der Test mit einer echten Person.

## 5. Foto-Hintergrund (Freistellung)

### 5.1 Verlauf der Fehler

1. Mittwoch: Hintergrund wurde nie eingesetzt, obwohl 21–22 % als Person erkannt wurden. Ursache: Maske als `ALPHA_8`-Bild; Android wertet das beim Zeichnen mit `DST_IN`
   als Abdeckung einer deckenden Farbe. Fix: Maske als ARGB (`a22caf0`), dann **pixelweise mischen** statt Porter-Duff (`8dae367`): Ergebnis = Person · Maske + Hintergrund · (1 − Maske).
   Auf dem Tablet bestätigt (0.1.99).
2. Qualität: Ränder stufig und unscharf, Gesichtsumriss schlecht. Ursache: Das Selfie-Modell liefert die Maske nur mit **ca. 256 Pixeln**, wir skalierten sie hoch.
3. Flach abgeschnittene Haarspitze bei Severin (Foto vom Foto). Nach den Änderungen unten sind die Haare in den Tests vollständig; wieviel davon am Haarmodell liegt: fast nichts (siehe 5.4).

### 5.2 Ablauf heute (`BackgroundRemover.replace`)

1. Foto auf höchstens 2000 px Seite begrenzen (hier unverändert 1632 x 1224).
2. Erkennungsbild: höchstens 1024 px Seite. **Durchgang 1** auf dem ganzen Bild (ML Kit Selfie-Segmentierung, `SINGLE_IMAGE_MODE`, `enableRawSizeMask`, Maske ~256 px) → auf Erkennungsgrösse hochskaliert.
3. **Durchgang 2** auf dem Ausschnitt um alle Pixel mit Konfidenz > 0.5 (Rand 10 % + 2 % der Boxgrösse), nur wenn der Ausschnitt ≥ 64 px ist, mehr als 0,2 % der Maske erkannt wurde und
   der Ausschnitt **kleiner als 70 %** der Bildfläche ist. Übergang am Ausschnittsrand weich (8 % der kleineren Seite). Fällt er aus, gilt Durchgang 1.
   Bei grossen Personenflächen (Arm, Gruppe) wird er also übersprungen (im Log fehlt dann «Zweiter Durchgang»).
4. **Haarmodell** (`HairModel`, optional, siehe 5.4): Haar-Wahrscheinlichkeit auf dem Erkennungsbild und, falls vorhanden, auf dem Ausschnitt; zählt nur nahe an der erkannten Person
   (Fensterradius Maskenbreite/16, Verstärkung 6), Stufe 0.5 bis 0.9.
5. **Guided Filter** (`MaskMath.guidedFilter`): Radius 12, eps 1e-3, Führungsbild = Helligkeit des Erkennungsbildes. Legt den Rand an die Fotokanten.
6. **Schwellen** 0.40/0.60 (smoothstep). Bewusst **eng**: Mit 0.30/0.80 sah ein weisser Hemdrand vor weisser Wand verwaschen aus (nachgerechnet am sauberen Severin-Bild).
7. Maske auf Fotogrösse hochskalieren (ARGB, bilinear), pixelweise mischen.

Mehrere Personen: Die ML-Kit-Dokumentation sagt «single or multiple users»; das Modell liefert **eine gemeinsame Maske** aller Personen, nicht pro Person.
Schwächen (**Annahme** aus der Dokumentation, nicht getestet): kleine/entfernte Personen werden eher übersehen, gehaltene Gegenstände können fehlen oder dazukommen.
Der Ausschnitt umschliesst alle Personen. **Gruppenfotos sind noch nicht getestet.**

### 5.3 Messwerte (gemessen, Logcat)

| Build | Foto | Freistellung | Personenanteil | Bemerkung |
|---|---|---|---|---|
| 0.1.107 | Severin | 485 ms | 5,2 % | zweiter Durchgang auf 700 x 519 bei (360, 424) |
| 0.1.113 | Severin 1 | 635 ms | 27,1 % | erstes Foto, Haarmodell 317 ms (Modell wird geladen) |
| 0.1.113 | Severin 2 | 250 ms | 27,4 % | Haarmodell 107 ms |
| 0.1.113 | echte Person (blond) | 264 ms | 27,1 % | Haarmodell 117 ms, Haar im Bild 2,3 %, ergänzt 0,06 % |

Haar im Bild 3,3 % / 1,9 % / 2,3 %, zur Maske ergänzt 0,03 % / 0,03 % / 0,06 %.

### 5.4 Haarmodell

- **Modell:** Googles MediaPipe `hair_segmenter.tflite` (Float32, 781 618 Byte, SHA-256 `2628cf3ce5f695f604cbea2841e00befcaa3624bf80caf3664bef2656d59bf84`),
  Quelle `https://storage.googleapis.com/mediapipe-models/image_segmenter/hair_segmenter/float32/latest/hair_segmenter.tflite`, in der App unter `assets/models/`.
  **Lizenz laut Modellkarte noch nicht geprüft** (vor einer Weitergabe der App an Dritte klären).
- **Form (nachgerechnet, in Python am Modell geprüft):** Eingang `[1, 512, 512, 4]` Float32 = RGB 0..1 **plus ein vierter Kanal** (Maske des Vorbildes bei Videos, wir geben 0); Ausgang
  `[1, 512, 512, 2]` **Logits** (0 Hintergrund, 1 Haar); Wahrscheinlichkeit Haar = Sigmoid(l1 − l0). Normierung 0..1 (−1..1 war schlechter). Laufzeit: TensorFlow Lite 2.17.0 direkt, **nicht** MediaPipe Tasks
  (Version nicht von hier prüfbar, weniger Abhängigkeit, Vor- und Nachbearbeitung unter eigener Kontrolle).
- **Ergebnis:** Auf dem sauberen Porträt zeichnet es den Haarumriss sehr genau nach, ergänzt aber fast nichts, weil die Personen-Erkennung die Haare schon findet. Auf dem Tablet dasselbe (0,03–0,06 %).
  Bei unveränderten blonden Haaren in einem echten Foto gab es keine Probleme (Personen-Erkennung allein genügte). In den **geschwärzten** Screenshots erkennt es nur den schwarzen Fleck als Haar (Testartefakt).
- **Entscheidung der Nutzerperson:** Modell **bleibt**, dazu ein **Schalter** «Haarmodell verwenden» (Konfiguration, Foto-Hintergrund; Standard an), falls es zu lange dauert oder Probleme macht.
  Fällt das Modell beim Laden aus, gilt automatisch nur die Personen-Erkennung. Claudes Empfehlung war das Entfernen (kein messbarer Nutzen, kostet App-Grösse); die Nutzerperson entschied anders.
- **App-Grösse:** durch TensorFlow Lite und die Modelldatei grösser; **Zahl nicht gemessen** (am APK im Actions-Artefakt ablesbar).

### 5.5 Weitere geprüfte Modelle (nachgerechnet, nicht eingebaut)

- `selfie_multiclass_256x256` (16 371 837 Byte): Eingang `[1,256,256,3]`, Ausgang `[1,256,256,6]` Logits (Hintergrund, Haar, Körper-Haut, Gesichts-Haut, Kleider, Sonstiges). Personenmaske = 1 − P(Hintergrund) ist weich
  (Hintergrund nicht sauber 0). Noch kleine Auflösung 256 px.
- `selfie_segmenter` float16 (249 537 Byte): `[1,256,256,3]` → `[1,256,256,1]` Wahrscheinlichkeit Person; gleiche Familie wie ML Kit.
- Wenn die Qualität künftig nicht reicht: Matting-Modell (z. B. MODNet), ML Kit «Subject Segmentation» (pro Person getrennte Masken; berührende Personen gelten als eine) oder MediaPipe mit Haar-Klasse.
  Jeweils grösserer Umbau; vorher mit Originalfotos (siehe Abschnitt 3) vergleichen.
- Prüfskripte: `tools/maskcheck/` (siehe Abschnitt 11).

### 5.6 Nachgerechnete Parameter (Python, synthetisches Bild, grobe Maske aus 64 x 64 hochskaliert)

Rand-Fehler im Randband: grobe Maske 0.0769; Guided Filter r=8 0.0226, r=12 0.0143, r=16 0.0094 (eps 1e-3); eps 1e-2 deutlich schlechter (0.0377); eps 1e-4 0.0119.
Am sauberen Severin-Bild (Anteil Randband an der Schulter): Raster alt 0.026, Filter mit Schwellen 0.30/0.80 **0.037 (verwaschen)**, mit 0.42/0.58 **0.011**, r=6 mit 0.35/0.65 0.017.
**Grenze:** Der Guided Filter nutzt die Helligkeit. Bei Haaren oder Kleidern mit ähnlicher Helligkeit wie der Hintergrund hilft er wenig (weisses Shirt vor weisser Wand).
Die Nachbildung ist nur ein Test; echte Fotos können schlechter ausfallen.

## 6. Oberfläche

### 6.1 Ergebnis-Bildschirm
Im Querformat füllen beide Blöcke die Höhe, das Bild bekommt nur den Platz zwischen Überschrift und QR-Code bzw. Druckknopf und wird verkleinert (`weight(1f, fill = false)`); kein Scrollen.
Hochformat unverändert (scrollt). **Gemessen:** QR-Code und «ZUM HERUNTERLADEN SCANNEN» ohne Scrollen sichtbar. Die Diagnose-Zeile zeigt Hintergrund, Personenanteil und (neu) die Dauer.
Der Haken unten rechts überdeckt nur den freien Rand.

### 6.2 Emoji im Banner (gemessen: «2027 🇨🇭» im Regenbogen funktioniert)
Ursache: «Regenbogen» zeichnete den Text Zeichen für Zeichen (`for (ch in text)` über UTF-16-Einheiten). Ein Emoji, besonders eine Flagge, besteht aus mehreren Einheiten; die Hälften wurden zu Kästchen.
Jetzt: `photo/Graphemes.kt` zerlegt nach sichtbaren Zeichen (ICU `BreakIterator`), Emoji werden als Ganzes und ohne Schatten gezeichnet und zählen im Farbverlauf nicht mit; die 28-Zeichen-Grenze und der Zähler zählen
sichtbare Zeichen; Kürzen schneidet kein Emoji mehr durch (auch Startbildschirm-Texte 60/24 Zeichen). **Grenze (Annahme):** Sehr neue Emoji können als Kästchen erscheinen, wenn der Emoji-Zeichensatz des Tablets sie nicht kennt;
die Vorschau im Setup zeigt es vor dem Druck. Verboten wurde nichts.

### 6.3 Setup-Kopfleiste
Weisses Sonnhalde-Logo mit Zusatz statt Text «SONNHALDE», goldener Rahmen «ENTWICKLERMODUS» (das Wort stammt aus der Markierung der Nutzerperson im Screenshot; die Nutzerperson nennt das Setup «Entwicklermodus»),
Ausschalten-Symbol als Vektor `ic_power.xml` statt des Unicode-Zeichens `⏻` (fehlte in der Schrift). **Gemessen:** sieht gut aus. Das Logo ist weiterhin fest eingebaut (Traktandum «Benutzerdefinitionen»).

### 6.4 Rückmeldung für Gäste (gebaut, **noch nicht auf dem Tablet getestet**)
Problem laut Nutzerperson: Nach den drei Sekunden und dem Auslösen passiert für Gäste nichts sichtbar, während im Hintergrund freigestellt wird. Gebaut:
- im Countdown unter der Zahl «Bitte lächeln und kurz stillhalten»;
- nach dem Auslösen ein Bildschirm «Foto aufgenommen!» mit Fortschrittskreis, Schritt-Text (`UiState.processing`: «Ihr Foto wird vorbereitet …», «Sie werden freigestellt und vor Ihren Hintergrund gesetzt …», «Ihr Foto wird fertiggestellt …»)
  und «Sie können sich jetzt wieder bewegen.»;
- Taps während der Verarbeitung werden ignoriert (kein doppeltes Auslösen).
Die Nutzerperson will zusätzlich **Töne** (siehe Abschnitt 9, Punkt 10).

## 7. Rechenzeit und Speicher

### 7.1 Faktoren, die unter Last langsamer machen können (Einordnung von Claude, Wirkung auf diesem Tablet **nicht gemessen**)
- **Hitze (Thermal Throttling):** Dauerbetrieb, Laden, Sonne, Gehäuse. Bei einem Snapdragon 8 Gen 2 (Annahme) in Kurzbursts eher unkritisch, im Dauerbetrieb zu beobachten. Prüfen: `./adb shell dumpsys thermalservice`.
- **Erstes Foto:** Modell und Klassen werden geladen (635 ms gegen ~250 ms danach, **gemessen**).
- **Wenig freier Arbeitsspeicher / Speicherbereinigung** (Abschnitt 7.2).
- **Hintergrund-Apps:** laut Nutzerperson nur Xiaomi-Systemdienste.
- **Akku-Sparmodus:** laut Nutzerperson kein Thema (Ladegerät).
- **Personenanteil:** laut Nutzerperson nichts zu ändern. (Technisch: grosser Anteil überspringt den zweiten Durchgang und spart Zeit; der Filter braucht wegen fester Bildgrösse immer ähnlich lang.)
- **Netz:** Upload und QR-Code hängen vom WLAN und laufen nach dem Foto; getrennt von der Freistellung.
Die Nutzerperson will **alle Punkte unter 2 später noch genauer prüfen** (Abschnitt 9, Punkt 3).

### 7.2 Was die App im Arbeitsspeicher hält (aus dem Code, nachgerechnet; nicht per `dumpsys meminfo` gemessen)
- **Immer nur ein Foto.** `UiState` hält `photo` (Druck-Version) und `digital` (digitale Version), das `MainViewModel` die zwei JPEG-Byte-Felder (`photoJpeg`, `digitalJpeg`); `backToHome()` setzt alles auf `null`, das Ergebnis geht nach
  **90 s Inaktivität** (`RESULT_TIMEOUT_MS`) oder per Haken zurück zum Start. Das Foto wird danach von der Speicherbereinigung eingesammelt. Eine «Entfernen nach 2–3 Minuten»-Funktion gibt es faktisch schon (90 s).
- **Gehalten, solange das Ergebnis sichtbar ist:** etwa 16 MB Bitmaps (Druck ~1800 x 1200 + digital 1632 x 1224, ARGB) plus ~2 MB JPEG plus QR.
- **Spitze während der Verarbeitung (Schätzung):** Bitmaps Foto, Hintergrund, Maske klein/gross, Ergebnis, Banner-Versionen zusammen ~56 MB; Guided Filter bis ~14 Float-Felder à 3 MB ≈ 42 MB;
  Haarmodell Eingang 4 MB + Ausgang 2 MB + Float-Felder. Insgesamt **grob 100–150 MB kurzzeitig** (**Schätzung**, nicht gemessen). Die App setzt `largeHeap` **nicht**; die Heap-Grenze des Geräts ist unbekannt.
- **Warteschlange liegt auf dem Speicher, nicht im Arbeitsspeicher:** `UploadQueue` schreibt fehlgeschlagene digitale Fotos nach `files/upload-queue/`. Mehr Fotos dort belasten den RAM nicht.
  Erneut senden heute **von Hand** im Reiter «Warteschlange»; automatisches Senden bei Internet ist offen (Punkt 6 der alten Liste).
- **Fotos in Dateien statt im Speicher halten?** Das würde den RAM-Bedarf *während das Ergebnis sichtbar ist* von ~18 MB auf wenige MB senken und kostet je Foto einige zehn Millisekunden
  Lesen/Schreiben von ein bis zwei MB. Die **Spitze während der Verarbeitung** ändert das nicht. Daher: erst messen (`./adb shell dumpsys meminfo ch.sonnhalde.fotobox`, `./adb shell getprop dalvik.vm.heapgrowthlimit`), dann entscheiden.

### 7.3 Messanleitung für den Stresstest
20–30 Fotos hintereinander (auch mit Gästen, Gruppen), Tablet warm; danach `Freistellung … ms` im Log (oder die Diagnose-Zeile) vergleichen, `dumpsys meminfo` vor und nach den Fotos, `dumpsys thermalservice`.
Wenn es zu lange dauert: «Haarmodell verwenden» ausschalten und erneut messen. Ein automatisches Umschalten nach Zeit gibt es **nicht**.

### 7.4 Lücke beim Drucken nach dem Zeitlimit (aus dem Code, nachgerechnet; auf dem Tablet **noch nicht** nachgestellt)
Frage der Nutzerperson: Was, wenn der Weg zum Drucker und zurück ins WLAN länger dauert als die 90 Sekunden und das Foto dann weg ist? **Antwort: Das ist eine echte Lücke.**
- Das Ergebnis geht nach 90 s zurück zum Start. Der Zeitgeber startet bei jeder Zustandsänderung neu (`LaunchedEffect(photo, print, upload)`); die Druck-Schritte («Verbinde mit Drucker-WLAN …», «Foto wird gedruckt …», «Zurück ins Internet-WLAN …»)
  setzen ihn zurück. **Eine Phase ohne Zustandswechsel länger als 90 s** (Android-Abfrage «Verbinden» bleibt unbestätigt, Drucker antwortet bis zum Lese-Timeout von 90 s nicht) lässt den Gast trotzdem zum Start springen.
- Der Druckauftrag läuft im Hintergrund weiter, `photo` und `photoJpeg` sind danach aber weg. **Schlägt der Druck nach dem Verlassen fehl, sieht ihn niemand**, und es gibt **keinen erneuten Druck**.
- Es gibt **keine Liste der letzten Fotos** und keinen Entwickler-Bereich zum erneuten Drucken. Die **Druck-Version wird nirgends gespeichert**. Die digitale Version liegt nach erfolgreichem Upload in Nextcloud (bzw. in der Warteschlange).
- Folge für die Planung: Traktanden 6, 16 und 17 in Abschnitt 9 (Druck als Pflicht, Foto-Verlauf mit erneutem Drucken, Zeitlimit während des Druckens pausieren).

### 7.5 Upload-Warteschlange: Speicherort und Löschen (aus dem Code)
- Ort: `files/upload-queue/` im **App-internen Speicher** (`/data/data/ch.sonnhalde.fotobox/files/…`), nicht im geteilten Bereich `/storage/emulated/0/`. Beides ist derselbe Flash-Speicher; «intern» heisst nur: privat für die App.
- Es kommen nur **digitale** Fotos hinein, **wenn der Upload fehlschlägt**: beim Verlassen des Ergebnisses mit Status «fehlgeschlagen» oder wenn der Upload erst nach dem Verlassen scheitert.
- **Löschen:** `retryQueue()` löscht eine Datei **nach erfolgreichem Senden**, `clearQueue()` («Warteschlange leeren» im Reiter) löscht alle. **Von allein verschwindet nichts**, es gibt keine Aufbewahrungsdauer.
  Bei dauerhaft fehlendem Internet wächst die Warteschlange (je ~1 MB). Speicherplatz ist bei 256 GB kein Problem, aber Aufbewahrung von Gästefotos über Tage ist eine Datenschutzfrage der Sonnhalde.
- Abholen der Dateien von Hand (Debug-Build): `./adb shell run-as ch.sonnhalde.fotobox ls files/upload-queue`, einzeln `./adb exec-out run-as ch.sonnhalde.fotobox cat files/upload-queue/NAME.jpg > NAME.jpg`. Geht nur bei Debug-Builds.

**Intern oder geteilter Speicher (`/storage/emulated/0/…`)?** Einschätzung von Claude, nicht getestet:

| | Intern (heute) | Geteilter Speicher (z. B. `Pictures/` oder `Download/`) | App-eigener externer Ordner (`/Android/data/…/files`) |
|---|---|---|---|
| Berechtigung | keine | je nach Android-Version Medien-/Dateizugriff, `MediaStore` für Bilder | keine |
| Sichtbar im Dateimanager und am PC | nein (nur ADB, Debug) | ja, für alle Apps und Nutzer des Tablets | am PC per USB meist ja, Android 11+ sperrt andere Apps |
| Galerie und andere Apps | sehen es nicht | **Galerie zeigt die Fotos**, andere Apps mit Zugriff können sie lesen | Galerie kann es erfassen, mit `.nomedia`-Datei verhindern |
| Datenschutz (Gästefotos im Pflegezentrum) | am besten | **am schwächsten** | mittel |
| Bleibt nach Deinstallation | nein | ja (MediaStore) | nein |
| Aufwand | keiner | mittel (Berechtigungen, Aufräumen) | gering |

Empfehlung: **intern lassen** und im Entwicklermodus eine **Ansicht mit Export** bauen (Foto antippen, «Teilen/Speichern»), statt die Fotos dauerhaft für alle Apps sichtbar abzulegen.
Dazu eine **Löschregel** (z. B. nach erfolgreichem Senden und höchstens 24 h bzw. nur die letzten N Fotos). Die Entscheidung liegt bei der Nutzerperson; Datenschutzfragen klärt die Sonnhalde.

### 7.6 Messbarkeit
Ja, auf zwei Wegen (Traktandum 3 und 18):
- **Von Hand mit ADB** (sofort möglich, nicht an einem Gerät erprobt, Ausgabeformat der `dumpsys`-Befehle kann je nach Android-Version abweichen):
  ```bash
  while true; do date +%T; ./adb shell dumpsys meminfo ch.sonnhalde.fotobox | grep -E "TOTAL PSS|Java Heap|Native Heap"; ./adb shell dumpsys battery | grep -i temperature; ./adb shell dumpsys thermalservice | grep -i "thermal status"; sleep 5; done | tee messung.txt
  ```
  Dazu im Log die Zeilen `Freistellung … ms`.
- **In der App (Entwicklermodus, noch nicht gebaut):** «Dauertest»: ein gespeichertes Testfoto N-mal durch die Freistellung schicken und je Lauf Dauer, Java-/Native-Heap
  (`Runtime`, `Debug.getNativeHeapAllocatedSize`), Thermal-Status (`PowerManager.getCurrentThermalStatus`, Stufen 0–6, ab Android 10) und Akkutemperatur (`BatteryManager`) aufzeichnen und als Zusammenfassung anzeigen.
  Grenze: Die Temperatur des Prozessors selbst ist für Apps nicht lesbar; der Thermal-Status ist grob.

### 7.7 App-Grösse und APK
Das Debug-APK aus dem Build `29e316a` (Code gleich wie `529f7cf`) ist **124 707 957 Byte (rund 119 MB)** gross, mit allen Prozessor-Architekturen, nicht verkleinert. Wie viel davon TensorFlow Lite und das Haarmodell ausmachen,
ist **nicht gemessen** (frühere APKs wurden nicht aufgehoben). Das Release **`dev-build`** (Pre-Release) enthält immer das APK des **letzten Pushs auf irgendeinem Zweig**, direkt installierbar:
`https://github.com/everyday5631/FotoboxSH/releases/download/dev-build/Sonnhalde-Fotobox-debug.apk`. Es wird bei jedem Push überschrieben (auch von Doku-Commits); die Versionsanzeige im Setup nennt den Commit.

## 8. Entscheidungen der Nutzerperson (heute)
1. Rückkamera-Schalter im Setup **bleibt** (Notlösung bei Kamera-Verwechslung).
2. **Haarmodell bleibt**, mit Schalter zum Ausschalten.
3. **Druck und Kiosk-Beenden zuletzt** testen (Fotopapier; jeder Systemstart des DNP druckt eine Status-Seite).
4. Gäste-Rückmeldung nach dem Auslösen: Vorschlag angenommen; **Töne** gewünscht (Countdown und einer, der wartet, bis es wirklich geklappt hat). Taps während der Verarbeitung ignorieren: gut.
5. **Drucker ist Pflicht:** Der Druck muss immer erfolgen, weil es sonst vor Ort Probleme gibt. Der Upload (Nextcloud) darf ausfallen und in eine Warteschlange.
6. Emoji im Banner: unterstützen (nicht verbieten). Kopfleiste: Logo, Kennzeichnung, richtiges Symbol.
7. Eigenes Erscheinungsbild für die **Vermietung an Dritte** gewünscht (Logos je Verwendung, Reiter «Benutzerdefinitionen»), nicht dringend.
8. Qualität der Freistellung mit echter Person: «voll okay».

## 9. Offene Traktanden (vollständig, mit Status)

| Nr. | Traktandum | Stand |
|---|---|---|
| 1 | **PR #12 und #14 mergen** (zuerst #12, als normaler Merge-Commit, nicht Squash; #12 ist `clean` mergbar, Basis `main` unverändert, aber noch Entwurf). PR #13 (Pages) klären, ob gewollt. | offen, Nutzerperson |
| 2 | **Rückmeldung für Gäste auf dem Tablet testen** (Countdown-Hinweis, Bildschirm nach dem Auslösen, Taps ignoriert). | gebaut, ungetestet |
| 3 | **Rechenzeit und Speicher genauer prüfen** (alle Punkte aus Abschnitt 7): Stresstest, `dumpsys meminfo`, Thermal, evtl. automatisches Umschalten des Haarmodells nach Zeit. | offen |
| 4 | **Knopf «Modell neu laden» im Entwicklermodus** (Haarmodell und ML-Kit-Erkennung freigeben und neu laden; evtl. zusätzlich beim App-Start vorwärmen, damit das erste Foto nicht langsamer ist). | offen |
| 5 | **Speicher:** Foto nach 2–3 Minuten freigeben (faktisch heute 90 s) und Fotos als Datei statt im Arbeitsspeicher halten: erst messen, dann entscheiden (Abschnitt 7.2). | offen |
| 6 | **Druck als Pflicht (Failsafe):** Druckaufträge dürfen nicht verloren gehen: Aufträge auf dem Speicher ablegen, bei Fehler erneut versuchen, klare Meldung für das Personal. Heute: bei Fehler Meldung und Knopf «Über Android-Druckdialog drucken», keine Warteschlange für Druckaufträge. Upload bleibt davon getrennt. | offen |
| 7 | **Upload-Warteschlange automatisch senden**, sobald Internet da ist (heute von Hand im Reiter «Warteschlange»). Hängt mit Punkt 6 zusammen. | offen (alte Liste Punkt 6) |
| 8 | **Töne:** Tick im Countdown, Auslöse-Ton, Ton wenn das Foto fertig ist (Erfolg), evtl. Fehlerton; Schalter im Setup. Möglich ohne Dateien über `ToneGenerator`, sonst mitgelieferte Klänge. | offen |
| 9 | **Benutzerdefinitionen / Vermietung:** eigenes Logo je Verwendung (hell: Start und Foto-Banner; dunkelblau: Kopfbalken), Texte, ggf. Farben, eigener Reiter im Setup. Das Icon ist zur Build-Zeit fest (Sonderfall). | offen, nicht dringend |
| 10 | **Banner:** Transparenz und Druck auf Papier prüfen. | offen |
| 11 | **Freistellung weiter beobachten:** Gruppen, blonde/helle Haare mit unveränderten Originalfotos, grosse Personenfläche; ggf. anderes Modell (Abschnitt 5.5). | offen |
| 12 | **Haarmodell:** Lizenz der Modelldatei prüfen, App-Grösse am APK ablesen. | offen |
| 13 | **Druck und Kiosk-Beenden testen** (WLAN-Wechsel zum Drucker, PIN, Anheften). **Zuletzt.** | offen |
| 14 | **Zugangsdaten sicher ablegen** (Nextcloud-App-Passwort, später Power-Automate-Flow-URL): vor dem Einsatz bei der Sonnhalde zu klären. | offen (alte Liste Punkt 4) |
| 15 | Netz-Diagnose im Setup, Konfiguration exportieren/importieren, Fische-Bild als Standard-Hintergrund, Version 1.0 und README für Dritte. | zurückgestellt (alte Liste 2, 3, 5, 7) |

| 16 | **Foto-Verlauf im Entwicklermodus:** die letzten ~6 Fotos (Druck- und digitale Version) auf dem internen Speicher, mit Vorschau, «Erneut drucken», «Erneut hochladen», «Löschen/Exportieren»; Löschregel (älteste verdrängen, nach Erfolg und spätestens nach ~24 h). Anlass: Foto ist nach dem Zeitlimit weg (Abschnitt 7.4). | offen |
| 17 | **Zeitlimit des Ergebnisses pausieren, solange gedruckt wird**; **Druckfehler nach dem Verlassen sichtbar machen** (heute wird er still verworfen), z. B. Hinweis für das Personal im Entwicklermodus. | offen |
| 18 | **Dauertest/Messung im Entwicklermodus** (Abschnitt 7.6). | offen |
| 19 | **Löschregel und Speicherort der Upload-Warteschlange** entscheiden (Abschnitt 7.5); Empfehlung: intern lassen, Ansicht mit Export im Entwicklermodus. | offen |

**Sicherheitshinweis:** Das Repository ist laut Beschreibung von PR #13 **öffentlich**. In der Dokumentation steht das **Werkspasswort des WCMPlus-Hotspots**; laut `CLAUDE.md` gehören keine WLAN-Passwörter ins Repository.
Ob es entfernt wird (und die Historie bereinigt), ist offen. Keine neuen Zugangsdaten eintragen.

## 10. Testprotokoll für den nächsten APK (Version höher als 0.1.113)
1. Countdown: steht unter der Zahl «Bitte lächeln und kurz stillhalten», lesbar?
2. Nach dem Auslösen: erscheint sofort «Foto aufgenommen!» mit Kreis und Schritt-Text, wechselt der Text, verschwindet der Bildschirm beim Ergebnis? Nochmals tippen: passiert nichts?
3. Setup → Konfiguration → Foto – Hintergrund: «Haarmodell verwenden» aus und an; die Diagnose-Zeile nennt die Dauer; Log zeigt «Haarmodell an/aus».
4. Stresstest wie 7.3, Log mit `./adb logcat -d -s Fotobox:V AndroidRuntime:E`.
5. Blondes Originalfoto (Wahl «Original», ungeschwärzt) für Modellvergleiche mitschicken, wenn möglich.

## 11. Anhang

**Prüfskripte** (`tools/maskcheck/`, laufen ohne Android; `pip install numpy pillow ai-edge-litert`):
- `check_filters.py`: boxMean gegen Brute-Force, Guided-Filter-Simulation (Zahlen aus 5.6).
- `inspect_models.py BILD.jpg [AUSGABE.jpg]`: lädt die drei Modelle, zeigt Ein-/Ausgänge, vergleicht sie an einem Bild. Modelle landen in `tools/maskcheck/models/` (nicht eingecheckt).

**Befehle** (Linux, im Ordner `platform-tools`):
```bash
./adb devices
./adb logcat -c
./adb logcat -d -s Fotobox:V AndroidRuntime:E
./adb shell dumpsys meminfo ch.sonnhalde.fotobox
./adb shell dumpsys thermalservice
./adb shell getprop dalvik.vm.heapgrowthlimit
```

**Weiterführend:** `docs/ENTWICKLUNG.md` (Gerätefakten, Pakete, Entscheidungen), `docs/BEDIENUNG.md`, `docs/RELEASE.md`, `CLAUDE.md`.
Quellen zu den Modellen: ML Kit [Selfie segmentation](https://developers.google.com/ml-kit/vision/selfie-segmentation), MediaPipe [Image segmentation guide](https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter).
