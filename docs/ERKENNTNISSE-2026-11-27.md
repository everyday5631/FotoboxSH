# Arbeitsstand und Erkenntnisse vom 27.11.2026

Protokoll einer langen Sitzung zwischen Nutzerperson und Claude (Sitzung `session_014cQ27QzRmik6yGdLfkuZQt`, Vorgängersitzung
«Offene PR Review» `session_014cQ27QHKzLi7NCXwNSnLjc`). Zweck: Nichts geht verloren, wenn der Chat wechselt. Die Gerätefakten,
Entscheidungen und die offene Liste stehen weiterhin in `docs/ENTWICKLUNG.md`; hier steht der Weg dorthin, mit Zahlen und Begründungen.

## 0. Einstieg für einen neuen Chat (zuerst lesen)

1. `CLAUDE.md` und `docs/ENTWICKLUNG.md` lesen, dann dieses Dokument (Abschnitte 1, 2, 8, 9 genügen für den Start).
2. **PR #12 und PR #14 sind gemergt** (#12 am 27.11. um 11:14, #14 um 12:57; `main` = `c347c24`, Build-Lauf 127, in der App «Version 0.1.127 (c347c24)»). Der Zweig `claude/dazzling-thompson-fvlhl1`
   wurde danach mit `git merge --ff-only origin/main` auf `main` vorgespult; **für neue Arbeit gilt ein neuer Entwurf-PR** (Zweigname wird vom Chat vorgegeben). Immer zuerst den echten Stand prüfen
   (`list_pull_requests`, `git log origin/main`), nicht diesem Satz trauen.
3. **Vereinbarte Reihenfolge der nächsten Arbeit** (Nutzerperson: «besser für einen neuen Chat»): zuerst **Foto-Verlauf (16) mit Zeitlimit (17) und dauerhafter Druckwarteschlange (6)**, dann **Drucker-Wartungsmodus (20)**, dann **Töne (8)**.
   Messung/Dauertest (18) danach. **Druck und Kiosk-Beenden zuletzt testen** (Papier).
4. Jede Aussage hier ist als gemessen, nachgerechnet oder Annahme gekennzeichnet; bei Bauvorhaben neu prüfen, nicht blind übernehmen.
5. Offene Fragen an die Nutzerperson stehen in Abschnitt 9.2.
6. Fertiger Einstiegs-Prompt zum Einfügen: `docs/START-NEUER-CHAT.md`.

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
| PR [#12](https://github.com/everyday5631/FotoboxSH/pull/12) | **Gemergt am 27.11. 11:14** (Merge-Commit `3dba8e2`). Hintergrund-Auswahl beim Fotografieren, halbtransparente Vorschau, Diagnose-Zeile, weisses Logo im Ergebnis-Banner, Fix «Maske ARGB» und «pixelweise mischen». |
| PR [#13](https://github.com/everyday5631/FotoboxSH/pull/13) | **Erledigt:** war für das **falsche Repository** gedacht (Dokumentation als GitHub-Pages-Seite, Kategorie-Pflicht pro Seite) und ist von der Nutzerperson geschlossen. Eine Webseite aus der Dokumentation ist für dieses Repo **nicht gewollt**; die Kategorie-Regel steht deshalb nicht in `CLAUDE.md`. Der Zweig `claude/stoic-hopper-h7zvjy` existiert noch auf GitHub (aufräumen ist Sache der Nutzerperson). |
| PR [#14](https://github.com/everyday5631/FotoboxSH/pull/14) | **Gemergt am 27.11. 12:57** (Merge-Commit `c347c24`, Build-Lauf 127 grün). Enthielt die Commits von #12 plus alles Neue unten. Das Release `dev-build` (Pre-Release, von GitHub Actions) enthält diese APK, wird aber bei **jedem** Push auf irgendeinem Zweig überschrieben. |
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

Commit-Reihenfolge auf PR #14 (neueste oben; alles in `main` seit `c347c24`): `529f7cf` Rückmeldung/Schalter, `2ba8e94` Emoji/Kopfleiste, `53900e7` Doku, `35029fc` Haarmodell,
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
9. Töne: ja, mit echten Tondateien (freie Lizenz). Zeitlimit des Ergebnisses: einstellbar.
10. Die Webseite aus der Dokumentation (PR #13) war für ein anderes Repo und ist erledigt.
11. Das Werkspasswort des WCMPlus in der öffentlichen Doku: **noch nicht** entfernen (Entscheid vertagt).
12. Reihenfolge der nächsten Arbeit gilt, aber **in einem neuen Chat**.
13. Frühere Erfahrung mit der gemieteten Box: Fotos blieben meist etwa einen Monat, eher **40 Tage** (siehe 9.2, Aufbewahrung).
14. **Töne:** echte Tondateien aus einer freien Quelle; als **Rückfall** ein von Android erzeugter Ton (`ToneGenerator` bzw. Systemklang), falls Dateien fehlen oder nicht abspielbar sind.
15. **Pausierter Drucker (Traktandum 20): Gäste sehen keinen Hinweis** («der Gast braucht das nicht zu wissen»). Auslegung von Claude, in einem neuen Chat kurz bestätigen: Der Druckknopf bleibt wie gewohnt sichtbar,
    der Auftrag wird still gespeichert und später vom Personal über «Drucker freigeben» gedruckt. Alternative (Knopf ausblenden) wäre auch ohne Hinweis möglich.
16. **Traktandum 14 (Zugangsdaten sicher ablegen) schliesst die Nutzerperson selbst ab.** Ergebnis steht aus; im neuen Chat nachfragen, was entschieden oder umgesetzt wurde.

### 8a. Entscheidungen der Nutzerperson vom Folgechat (Block A)
17. **Foto-Verlauf:** 6 Fotos. Wird ein siebtes gemacht, fällt das älteste hinaus. Zusätzlich gerne automatisch nach **24 Stunden** löschen.
18. **Zeitlimit des Ergebnisses:** 90 Sekunden als Standard, im Entwicklermodus einstellbar.
19. **Druck-Wiederholung:** nur **zwei** Wiederholungen, und dabei **nur je 1 Abzug** (wählte der Gast 3 Abzüge und es klappt nicht, gibt es nicht 6, sondern höchstens 1 + 1 weitere). Papier ist knapp.
    Auslegung von Claude: erster Versuch mit der gewählten Menge, danach höchstens 2 Wiederholungen mit je 1 Abzug. Risiko (nicht geprüft): Hat der Drucker einen Auftrag angenommen und die App sah trotzdem einen Fehler (Zeitüberschreitung), kommt ein Abzug doppelt.
21. **Abzüge pro Foto begrenzt (Fund beim ersten Tablet-Test):** Zuerst konnte ein Gast «Drucken» beliebig oft tippen (3 Abzüge gewählt, dreimal getippt = 9 Abzüge). Jetzt sind pro Foto insgesamt höchstens 3 Abzüge möglich (Zähler `printedCopies`, nur erfolgreiche Aufträge zählen);
    der Knopf ist während des Druckens und danach grau, der Auftrag wird schon beim Tippen als «läuft» markiert. Das Personal kann über den Reiter «Fotos» weiter nachdrucken (dort gilt die Grenze nicht).
20. **Pausierter Drucker (Block B):** Der Druckknopf wird **grau** und es steht ein **Hinweis**, dass der Drucker pausiert ist und das Personal gerufen werden soll (das ersetzt Punkt 15; das Personal steht vor Ort daneben). Noch nicht gebaut.

## 9. Offene Traktanden (vollständig, mit Status)

| Nr. | Traktandum | Stand |
|---|---|---|
| 1 | ~~PR #12 und #14 mergen~~ **erledigt am 27.11.** (PR #13 war das falsche Repo, geschlossen). Offen bleibt nur, ob aus dem Stand `c347c24` ein **dauerhaftes Pre-Release** (benannter Tag, z. B. `v0.2.0-rc1`) im Browser veröffentlicht wird; `dev-build` wird laufend überschrieben. | erledigt / Nutzerperson |
| 2 | **Rückmeldung für Gäste auf dem Tablet testen** (Countdown-Hinweis, Bildschirm nach dem Auslösen, Taps ignoriert). | gebaut, ungetestet |
| 3 | **Rechenzeit und Speicher genauer prüfen** (alle Punkte aus Abschnitt 7): Stresstest, `dumpsys meminfo`, Thermal, evtl. automatisches Umschalten des Haarmodells nach Zeit. | offen |
| 4 | **Knopf «Modell neu laden» im Entwicklermodus** (Haarmodell und ML-Kit-Erkennung freigeben und neu laden; evtl. zusätzlich beim App-Start vorwärmen, damit das erste Foto nicht langsamer ist). | offen |
| 5 | **Speicher:** Foto nach 2–3 Minuten freigeben (faktisch heute 90 s) und Fotos als Datei statt im Arbeitsspeicher halten: erst messen, dann entscheiden (Abschnitt 7.2). | offen |
| 6 | **Druck als Pflicht (Failsafe):** Druckaufträge dürfen nicht verloren gehen: Aufträge auf dem Speicher ablegen, bei Fehler erneut versuchen, klare Meldung für das Personal. Heute: bei Fehler Meldung und Knopf «Über Android-Druckdialog drucken», keine Warteschlange für Druckaufträge. Upload bleibt davon getrennt. | **gebaut (PR #24); Nutzerperson meldet «ging alles super», Einzelpunkte nicht aufgeschlüsselt; Druck selbst und Wiederholungen ungetestet.** Druck liest das Foto vom Speicher, Aufträge laufen nacheinander, Status im Verlauf, wartende Aufträge werden nach einem Neustart nachgeholt, bei Fehler 2 Wiederholungen mit je 1 Abzug. Meldung ans Personal: Markierung «(!)» am Reiter «Fotos». |
| 7 | **Upload-Warteschlange automatisch senden**, sobald Internet da ist (heute von Hand im Reiter «Warteschlange»). Hängt mit Punkt 6 zusammen. | offen (alte Liste Punkt 6) |
| 8 | **Töne** (Nutzerperson: «Vorschlag super»): Tick im Countdown, Auslöse-Ton, Ton wenn das Foto fertig ist (Erfolg), evtl. Fehlerton; Schalter im Setup. Die Nutzerperson will **richtige Tondateien**, keine einfachen Pieptöne: aus einer Bibliothek mit **freier Lizenz** (z. B. CC0, wie Kenney oder Freesound mit CC0-Filter; Verfügbarkeit und Lizenz vor dem Einbau prüfen) in `res/raw/`, Quelle und Lizenz im Repo vermerken. **Rückfall:** von Android erzeugter Ton (`ToneGenerator`/Systemklang), falls Dateien fehlen oder nicht abspielbar sind. Entschieden. | offen, entschieden |
| 9 | **Benutzerdefinitionen / Vermietung:** eigenes Logo je Verwendung (hell: Start und Foto-Banner; dunkelblau: Kopfbalken), Texte, ggf. Farben, eigener Reiter im Setup. Das Icon ist zur Build-Zeit fest (Sonderfall). | offen, nicht dringend |
| 10 | **Banner:** Transparenz und Druck auf Papier prüfen. Stand 27.11.: **eigenes Logo im Banner getestet und in Ordnung**; **Transparenz noch nicht getestet**; Druck folgt zuletzt. | offen (Transparenz, Druck) |
| 11 | **Freistellung weiter beobachten:** Gruppen, blonde/helle Haare mit unveränderten Originalfotos, grosse Personenfläche; ggf. anderes Modell (Abschnitt 5.5). | offen |
| 12 | **Haarmodell:** Lizenz der Modelldatei prüfen, App-Grösse am APK ablesen. | offen |
| 13 | **Druck und Kiosk-Beenden testen** (WLAN-Wechsel zum Drucker, PIN, Anheften). **Zuletzt.** | offen |
| 14 | **Zugangsdaten sicher ablegen** (Nextcloud-App-Passwort, später Power-Automate-Flow-URL): vor dem Einsatz bei der Sonnhalde zu klären. | offen (alte Liste Punkt 4) |
| 15 | Netz-Diagnose im Setup, Konfiguration exportieren/importieren, Fische-Bild als Standard-Hintergrund, Version 1.0 und README für Dritte. | zurückgestellt (alte Liste 2, 3, 5, 7) |
| 16 | **Foto-Verlauf im Entwicklermodus** («letzte ~6 Fotos»): mit Vorschau, erneut drucken, erneut hochladen, **exportieren** und **löschen** (einzeln und alle, mit PIN); Ring-Puffer und Löschregel. Anlass: Foto ist nach dem Zeitlimit weg (7.4). Ausführung in 9.1. | **gebaut (PR #24); Nutzerperson meldet «ging alles super» (Einzelpunkte nicht aufgeschlüsselt).** Reiter «Fotos»: 6 Fotos, höchstens 24 h (beides einstellbar), Vorschau, erneut drucken/hochladen, exportieren (Downloads/Fotobox), löschen mit PIN. |
| 17 | **Zeitlimit des Ergebnisses einstellbar machen** (Nutzerperson: ja; heute fest 90 s), **pausieren, solange gedruckt wird**; **Druckfehler nach dem Verlassen sichtbar machen** (heute wird er still verworfen), z. B. Hinweis für das Personal im Entwicklermodus. | **gebaut (PR #24); Nutzerperson meldet «ging alles super» (Einzelpunkte nicht aufgeschlüsselt).** Zeitlimit einstellbar (30–300 s, Standard 90 s), pausiert beim Drucken (höchstens 5 Minuten pro Druckschritt); Druckfehler nach dem Verlassen stehen im Verlauf. |
| 18 | **Dauertest/Messung im Entwicklermodus** (Abschnitt 7.6). Die Nutzerperson will zuerst die übrigen Traktanden klären, danach die Messung per ADB und in der App. | offen, später |
| 19 | **Löschregel und Speicherort der Upload-Warteschlange** entscheiden (Abschnitt 7.5); Empfehlung: intern lassen, Ansicht mit Export **und Lösch-Knopf** im Entwicklermodus (Nutzerperson: Export allein genügt nicht, es braucht auch das Löschen). | offen |
| 20 | **Drucker-Wartungsmodus («Drucker pausieren / freigeben»):** Knopf im Entwicklermodus, damit der Drucker sicher abgekoppelt, ausgeschaltet oder gewartet werden kann (neue Papierrolle, hängende Aufträge); Aufträge bleiben gespeichert und werden nach dem Freigeben der Reihe nach gedruckt. Ausführung in 9.1. Baut auf Traktandum 6 auf. | offen |
| 21 | **Ablaufdatum der Nextcloud-Freigabe einstellbar** (z. B. 40 Tage) und Aufräumen alter Dateien (siehe 9.2); mit dem Nextcloud-Verantwortlichen klären. | offen, Klärung nötig |
| 22 | **Automatische Update-Prüfung:** Neue APK-Versionen soll das Gerät selbst erkennen, entweder über **Obtainium** (App-Store-Ersatz für GitHub-Releases) oder **eigenständig in der Fotobox-App**. Entscheid und Ausführung in 9.3. | offen, Entscheid nötig |
| 23 | **Kleinigkeiten vom 27.11. abends (in PR #24):** Splashscreen mit echtem Sonnhalde-Logo; Setup-Reiter neu geordnet (links technisch, rechts Alltag: System, Drucker, QR & Upload, Kamera, Ablauf, Aussehen, Fotos; Startreiter «Aussehen»). Die Screenshots in `docs/anleitung` zeigen noch die alte Aufteilung. | gebaut, Tablet-Test: «ging alles super» (nicht aufgeschlüsselt) |

### 9.1 Ausführung der neuen Traktanden 16, 17 und 20 (Entwurf, **nichts davon ist umgesetzt**)

**Traktandum 16, 17: Foto-Verlauf und Zeitlimit** (Wunsch der Nutzerperson, in Claudes Worten)
- *Warum:* Nach den 90 Sekunden springt die App zum Start, die Druck-Version ist dann weg. Das kann auch selten auftreten und ist trotzdem schlimm, zum Beispiel wenn dem Drucker die Papierrolle ausgeht
  oder der WLAN-Wechsel hängt. Das Personal muss ein Foto später noch drucken können.
- *Was:* Beim Fertigstellen jedes Fotos werden Druck- und digitale Version auf dem **internen Speicher** abgelegt (z. B. `files/history/<Zeitstempel>/` mit `print.jpg`, `digital.jpg`, `status.json` für Kopien, Druck- und Upload-Status,
  Uhrzeit). Ring-Puffer der letzten **N = 6** Fotos (Einstellung); ältere werden automatisch verdrängt.
- *Wo:* Reiter «Fotos» im Setup (Entwicklermodus, hinter der PIN). Liste mit Vorschaubild, Uhrzeit, Zeichen für «gedruckt» und «hochgeladen» (Erfolg/Fehler/offen).
- *Aktionen je Foto:* Vorschau gross, **Erneut drucken** (Anzahl Abzüge wählbar), **Erneut hochladen** (neuer QR-Code), **Exportieren** (Teilen/Speichern), **Löschen**. Oben **Alle löschen** (mit PIN-Abfrage und Bestätigung).
  Die Nutzerperson will das Löschen ausdrücklich zusätzlich zum Export.
- *Löschregel:* Standard: nur die letzten N, dazu automatisch nach **spätestens ~24 Stunden** (Wert einstellbar), weil es Gästefotos in einem Pflegezentrum sind; Entscheid über die Frist und ob Fotos nach erfolgreichem Druck und Upload früher
  gelöscht werden, trifft die Nutzerperson mit der Sonnhalde.
- *Zeitlimit (Traktandum 17):* Das 90-Sekunden-Limit des Ergebnisses pausiert, solange der Status «Drucken» läuft. Schlägt ein Druck fehl, nachdem der Gast gegangen ist, zeigt der Entwicklermodus (und optional ein kleiner Hinweis auf dem Startbildschirm für das Personal)
  «Druck fehlgeschlagen», und das Foto steht im Verlauf zum erneuten Drucken.
- *Speicherbedarf:* 6 Fotos mit je zwei JPEGs (~1–2 MB) = rund 10–25 MB auf dem Gerät, im Arbeitsspeicher nichts Zusätzliches (Vorschaubilder klein).
- *Abhängigkeit:* Der Verlauf liefert die Grundlage für Traktandum 6 (Druckaufträge dürfen nicht verloren gehen) und 20.

**Traktandum 20: Drucker-Wartungsmodus** (Vorschlag der Nutzerperson, in Claudes Worten)
- *Warum:* Wenn Aufträge hängen, der Drucker eine neue Papierrolle braucht oder neu gestartet werden muss, soll das Personal den Drucker **sicher abkoppeln** können, ohne dass Fotos verloren gehen, und danach alles in einem Zug nachdrucken.
- *Ablauf:*
  1. Knopf **«Drucker pausieren»**: Die App sendet nichts mehr an den Drucker. Ein laufender Auftrag wird abgewartet (mit Zeitgrenze) oder abgebrochen. Neue Druckwünsche von Gästen landen in einer **dauerhaften Druckwarteschlange**
     (Foto, Anzahl Abzüge, Zeit). Die App meldet «Sie können den Drucker jetzt ausschalten oder trennen» erst, wenn nichts mehr in Arbeit ist.
  2. **Gäste sehen keinen Hinweis** (Entscheid der Nutzerperson: «der Gast braucht das nicht zu wissen»). Auslegung: Der Druckknopf bleibt wie gewohnt, der Auftrag wird still gespeichert; die Nutzerperson soll im neuen Chat bestätigen, dass
     das gemeint ist (Alternative: Knopf ausblenden).
  3. Knopf **«Drucker freigeben»**: Die App verbindet neu (inklusive WLAN-Wechsel), liest den Druckerstatus (IPP `printer-state`, `printer-state-reasons`, z. B. Papier leer) und sendet die wartenden Aufträge **der Reihe nach**.
     Fortschritt «3 von 5», Fehlgeschlagenes bleibt in der Warteschlange.
- *Grenzen (ehrlich):* Die App kann den Drucker **nicht selbst ausschalten** und nicht neu starten. Aufträge, die der WCMPlus oder der DNP schon angenommen hat, kann die App nur abbrechen, wenn WCMPlus die IPP-Befehle für das Abbrechen von Aufträgen
  unterstützt (**nicht geprüft**). Gefahr von **Doppeldrucken**: Hat der Drucker einen Auftrag angenommen, die App aber einen Fehler gesehen, würde das Freigeben ihn nochmals senden; Aufträge brauchen eine eigene Kennung und die App sollte `Get-Jobs` prüfen
  (**nicht erprobt**). **Beim Start des DNP-Systems druckt der Drucker eine Status-Seite**, also kostet ein Neustart einen Abzug.
- *Voraussetzung:* Traktandum 6 (dauerhafte Druckaufträge mit Wiederholung) und Traktandum 16 (Fotos bleiben erhalten).
- *Tests:* Papierrolle bei den Tests knapp; Test nach den anderen Punkten und möglichst mit wenigen Abzügen.

### 9.2 Offene Fragen an die Nutzerperson und Befunde
- **Pausierter Drucker (Traktandum 20):** **Beantwortet:** kein Hinweis für Gäste. Offen nur die Feinheit, ob der Druckknopf dann sichtbar bleibt (Claudes Auslegung, still speichern) oder ausgeblendet wird; kurz bestätigen lassen.
- **Aufbewahrung, Klärung nötig:** Die Nutzerperson nannte als Erfahrung der gemieteten Box «meist um einen Monat, eher 40 Tage». Unklar ist, **was** damals so lange blieb: die Fotos auf dem Gerät, die Download-Links oder die Dateien in der Cloud?
  Für den **Foto-Verlauf auf dem Tablet** (Traktandum 16) sind 40 Tage mehr als nötig und aus Datenschutzgründen eher zu lang; für die **Download-Links** (QR-Code) wäre ein fester Ablauf sinnvoll.
- **Befund Nextcloud (aus dem Code, nachgerechnet):** `NextcloudUploader` legt den öffentlichen Link **ohne Ablaufdatum** an (nur `path`, `shareType=3`, `permissions=1`, kein `expireDate`, kein Passwort). Wie lange ein QR-Link gilt, bestimmt also allein
  die Einstellung des Nextcloud-Servers (Administrator kann ein Standard-Ablaufdatum vorgeben; welches gilt, ist **nicht geprüft**). Die App löscht hochgeladene Dateien nie. Mögliches Traktandum: Ablaufdatum der Freigabe (z. B. 40 Tage) einstellbar machen
  und Dateien nach der Frist serverseitig aufräumen lassen. Entscheid der Nutzerperson und Absprache mit dem Nextcloud-Verantwortlichen nötig.
- **Sicherheit:** Entscheid vertagt (Abschnitt 8, Punkt 11).

### 9.3 Traktandum 22: Automatische Update-Prüfung (Wunsch der Nutzerperson am Ende der Sitzung; **nichts davon ist umgesetzt oder geprüft**)

Ziel: Das Tablet merkt von selbst, dass es eine neuere Version gibt, damit nicht jedes Mal von Hand eine APK geladen werden muss. Zwei Wege, Einschätzung von Claude (Aussagen zu Obtainium und Android sind **Annahmen aus Claudes Kenntnis**, vor dem Bauen zu prüfen):

**Weg A: Obtainium** (separate Open-Source-App, holt Updates direkt aus GitHub-Releases)
- Vorteil: **kein Code in der Fotobox-App nötig**, Prüfintervall und Benachrichtigung stellt Obtainium selbst ein; die Fotobox bleibt schlank.
- Voraussetzung: **benannte Releases** mit APK. Das Release `dev-build` hat immer **denselben Tag**; eine Versionserkennung über den Tag kann dort keine neue Version sehen. Für Obtainium wären Releases mit steigendem Tag (`vX.Y.Z`, auch `-rc1`) sinnvoll,
  über den vorhandenen Workflow `release.yml` oder von Hand im Browser. Ob Obtainium Pre-Releases und die APK-Versionsnummer wie gewünscht auswertet, ist **nicht geprüft**.
- Nachteil: zweite App auf dem Kiosk-Tablet; Updates müssen vom Personal bestätigt werden (Android fragt beim Installieren), und der Installationsdialog kann im **angehefteten** Kiosk-Modus blockiert sein (erst Anheften lösen, PIN).

**Weg B: eigenständig in der Fotobox-App** («Nach Updates suchen» im Entwicklermodus, hinter der PIN)
- Ablauf: App fragt die GitHub-API des öffentlichen Repos nach dem neuesten Release (kein Zugangsdaten nötig; ohne Anmeldung gilt ein Anfrage-Limit von 60 pro Stunde), vergleicht mit der eigenen Version (`BuildConfig`), zeigt «Update verfügbar», lädt die APK
  und startet die Installation über den Android-Installer.
- Android verlangt dafür die Berechtigung «Unbekannte Apps installieren» und eine **Bestätigung durch die Person** am Gerät; eine stille Installation geht nur als Geräte-Eigentümer-App (Device Owner) oder mit besonderen Rechten (Annahme, nicht geprüft).
- **Sicherheitsrisiko:** Das Signaturzertifikat `app/debug.keystore` ist **öffentlich im Repository**. Android prüft bei einem Update nur, dass die Signatur zur installierten App passt, und diese Signatur kann jeder nachbauen. Eine Update-Funktion, die APKs aus dem Netz lädt,
  braucht deshalb eine **eigene Prüfung** (z. B. SHA-256 aus einer vertrauenswürdigen Quelle) oder ein **privates Release-Zertifikat** (Wechsel erfordert Deinstallation auf allen Tablets, siehe `docs/RELEASE.md`). Ohne das ist ein automatisches Update ein Einfallstor.
- Mehr Aufwand und Pflege als Weg A; dafür volle Kontrolle (Zeitfenster, Hinweis nur im Entwicklermodus, nie mitten im Anlass).

**Gemeinsames:**
- Android erlaubt **kein Downgrade**; jede neue APK braucht einen höheren `versionCode` (`build.yml` und `release.yml` sorgen dafür). Spätere Dev-Builds können niedriger als ein Release sein.
- Update nie während eines Anlasses; bei der Fotobox nur mit Aufsicht, Ergebnis danach testen.
- Fragen an die Nutzerperson: Reicht eine **manuelle Prüfung** («Nach Updates suchen») oder soll es **automatisch** laufen (z. B. nachts im Leerlauf)? Soll ein **Stable**- und ein **Pre-Release**-Kanal getrennt werden? Wer bestätigt am Tablet?
  Ist ein eigenes Release-Zertifikat gewünscht (hängt mit Traktandum 14 und der Sicherheit zusammen)?
- Empfehlung von Claude zum Start: zuerst **Weg A (Obtainium)** mit benannten Pre-Releases ausprobieren (kein Code, geringes Risiko); eine eigene Update-Funktion erst bauen, wenn ein privates Zertifikat und eine Prüfsumme feststehen.

**Sicherheitshinweis:** Das Repository ist laut Beschreibung von PR #13 **öffentlich**. In der Dokumentation steht das **Werkspasswort des WCMPlus-Hotspots**; laut `CLAUDE.md` gehören keine WLAN-Passwörter ins Repository.
Ob es entfernt wird (und die Historie bereinigt), ist offen. Keine neuen Zugangsdaten eintragen.

## 10. Testprotokoll für den nächsten APK (Version höher als 0.1.113)
1. Countdown: steht unter der Zahl «Bitte lächeln und kurz stillhalten», lesbar?
2. Nach dem Auslösen: erscheint sofort «Foto aufgenommen!» mit Kreis und Schritt-Text, wechselt der Text, verschwindet der Bildschirm beim Ergebnis? Nochmals tippen: passiert nichts?
3. Setup → Konfiguration → Foto – Hintergrund: «Haarmodell verwenden» aus und an; die Diagnose-Zeile nennt die Dauer; Log zeigt «Haarmodell an/aus».
4. Stresstest wie 7.3, Log mit `./adb logcat -d -s Fotobox:V AndroidRuntime:E`.
5. Blondes Originalfoto (Wahl «Original», ungeschwärzt) für Modellvergleiche mitschicken, wenn möglich.

## 10a. Lehren aus dieser Sitzung (Fehler von Claude und was daraus folgt)

- **Zu früh «alles grün» gemeldet:** Ein Kompilierfehler (`DoubleArray` mit `Float`) wurde erst nach der Meldung bemerkt; der Pull-Request-Lauf war grün, der Push-Lauf nicht. **Beide Läufe prüfen**, Log lesen, erst dann melden.
- **Windows-Befehle für ADB** statt Linux: Vorher fragen, welches System, oder aus Screenshots ablesen.
- **Falsche Vermutung zur Haar-Erkennung:** Das Haarmodell wurde auf Verdacht als «grösster Hebel» empfohlen; Messung auf dem Tablet zeigte fast keinen Beitrag. **Erst messen, dann bauen**; das Log-Schema (`Haarmodell … zur Maske ergaenzt`) war dafür der Gewinn.
- **Testbilder verfälschen:** Geschwärzte oder freigestellte Screenshots eignen sich nicht für Modell-Tests; Originalfotos verlangen.
- **Zahlen und Begriffe der Nutzerperson übernehmen** («Entwicklermodus» = Setup), Unklarheiten kurz zurückfragen statt raten.
- **Nachrichten bündeln:** Die Nutzerperson antwortet pro Nachricht und schreibt oft mitten im Satz weiter.
- **Kein Merge, kein Release durch Claude** (Regel in `CLAUDE.md`); Entwürfe für PRs.

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
