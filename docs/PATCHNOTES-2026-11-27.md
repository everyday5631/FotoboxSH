# Patchnotes vom 27.11.2026

Alles, was am 27.11.2026 in der Sonnhalde Fotobox geändert wurde, aus vier Chats zusammengestellt:

| Chat | Zeitraum (UTC, Schweiz +1 h) | Thema |
|---|---|---|
| «27.11.26 Hintergründe und Traktanden» | 07:28 – 13:16 | Freistellung, Oberfläche, Rückmeldung für Gäste, Doku, Traktanden |
| «27.11.2026 Weitere Bearbeitung und Entwicklung» | 13:18 – 14:20 | Foto-Verlauf, Zeitlimit, Druckwarteschlange, Splashscreen, Setup-Reiter |
| «2711.26 GitHub Issues Behebung» | 13:43 – 14:27 | Nextcloud-Ablaufdatum für Download-Links |
| «Patchnotes für heute» | ab 14:39 | diese Zusammenstellung |

Quellen: Git-Verlauf aller Zweige, Pull Requests und Build-Läufe auf GitHub, Doku im Repo, Nachrichten der Chats.

**Wie verlässlich ist was?** Die Abschnitte tragen eine Kennzeichnung nach der Regel aus `CLAUDE.md`:

- **gebaut:** Der Code kompiliert, der GitHub-Actions-Lauf ist grün. Mehr sagt das nicht.
- **auf dem Tablet bestätigt:** Die Nutzerperson hat es getestet und das ausdrücklich gemeldet.
- **ungetestet:** nicht auf dem Tablet geprüft.

Der Druck auf Papier, das Kiosk-Beenden und das Warten auf einen wirklichen Druckfehler wurden **nicht** getestet (Fotopapier ist knapp).

---

## Stand der Pull Requests

| PR | Inhalt | Stand |
|---|---|---|
| [#12](https://github.com/everyday5631/FotoboxSH/pull/12) | Foto-Hintergrund: Auswahl beim Fotografieren, halbtransparente Vorschau, Hinweis bei Fehler | gemergt 11:14 |
| [#13](https://github.com/everyday5631/FotoboxSH/pull/13) | Dokumentation als GitHub-Pages-Seite | geschlossen (war für ein anderes Repository gedacht) |
| [#14](https://github.com/everyday5631/FotoboxSH/pull/14) | Ergebnis ohne Scrollen, feinere Freistellung, Haarmodell, Emoji, Rückmeldung für Gäste | gemergt 12:57 |
| [#23](https://github.com/everyday5631/FotoboxSH/pull/23) | Doku: Traktandum 22 (Update-Prüfung), PR-Stand | gemergt 13:18 |
| [#24](https://github.com/everyday5631/FotoboxSH/pull/24) | Foto-Verlauf, Zeitlimit, Druckwarteschlange, Abzug-Grenze, Splashscreen, Setup-Reiter | gemergt 14:19 (Build 0.1.147/148 grün) |
| [#25](https://github.com/everyday5631/FotoboxSH/pull/25) | Nextcloud: Download-Link mit Ablaufdatum | **offen als Entwurf**, Build-Läufe 152/153 grün |

---

## 1. Foto-Hintergrund (Freistellung) – PR #14

**Ausgangslage:** Der Hintergrund funktionierte seit dem Vortag (Fix «Maske ARGB» und «pixelweise mischen»), die Ränder waren aber stufig und unscharf, weil das Modell die Maske nur mit etwa 256 Pixeln liefert und sie einfach hochskaliert wurde.

**Neu**

- **Zweiter Erkennungsdurchgang** auf dem Ausschnitt um die erkannten Personen, damit die Person gross im Bild steht. Er wird übersprungen, wenn der Ausschnitt kleiner als 64 px ist, weniger als 0,2 % der Maske erkannt wurde oder der Ausschnitt mehr als 70 % des Bildes ausmacht. Fällt er aus, gilt Durchgang 1.
- **Guided Filter** (Radius 12, eps 1e-3, neue Datei `photo/MaskMath.kt` ohne Android-Klassen): legt den Maskenrand an die Kanten des Fotos an.
- **Schwellen 0.40/0.60:** zuerst 0.30/0.80, das machte einen weissen Hemdrand vor weisser Wand verwaschen (am sauberen Testbild nachgerechnet); deshalb enger gesetzt.
- **Haarmodell** (`photo/HairModel.kt`, MediaPipe `hair_segmenter.tflite`, 0,8 MB, direkt mit TensorFlow Lite 2.17.0): ergänzt Haare, die nahe an der erkannten Person liegen. Fällt das Modell beim Laden aus, gilt automatisch nur die Personen-Erkennung.
- **Schalter «Haarmodell verwenden»** (Konfiguration, Foto-Hintergrund; Standard an). Entscheid der Nutzerperson: Das Modell bleibt, obwohl Claude das Entfernen empfohlen hatte.
- **Diagnose-Zeile und Logcat** zeigen die Dauer der Freistellung, ob das Haarmodell an war, ob der zweite Durchgang lief und wie viel das Haarmodell zur Maske ergänzt hat.

**Messwerte auf dem Tablet** (Logcat, Xiaomi Pad 6S Pro)

| Build | Foto | Freistellung | Personenanteil | Bemerkung |
|---|---|---|---|---|
| 0.1.107 | Testporträt | 485 ms | 5,2 % | zweiter Durchgang auf 700 × 519 |
| 0.1.113 | Testporträt, erstes Foto | 635 ms | 27,1 % | Haarmodell 317 ms (Modell wird geladen) |
| 0.1.113 | Testporträt, zweites Foto | 250 ms | 27,4 % | Haarmodell 107 ms |
| 0.1.113 | echte Person (blond) | 264 ms | 27,1 % | Haarmodell 117 ms, ergänzt 0,06 % |

- Das Haarmodell ergänzt nur 0,03 bis 0,06 % des Bildes. Der Nutzen ist also klein, es bleibt trotzdem drin.
- **Auf dem Tablet bestätigt:** Mit einer echten Person «voll okay».
- **Ungetestet:** Gruppenfotos, grosse Personenfläche (Arm), Dauerbetrieb, Speicherbedarf. Gemessen wurde nur die Zeit. Die Lizenz der Modelldatei ist nicht geprüft (vor einer Weitergabe der App klären).

## 2. Oberfläche – PR #14

- **Ergebnis-Bildschirm im Querformat ohne Scrollen** (08:20): Das Bild füllte die Breite und schob den QR-Code unter den Rand. Jetzt nimmt es nur den Platz zwischen Überschrift und QR-Code bzw. Druckknopf und wird massstabsgetreu verkleinert. Das Hochformat ist unverändert (scrollt weiter). **Auf dem Tablet bestätigt:** QR-Code und «ZUM HERUNTERLADEN SCANNEN» sind ohne Scrollen sichtbar.
- **Emoji im Banner:** Der Regenbogen-Text zerlegte Emoji in einzelne UTF-16-Zeichen, daraus wurden Kästchen. Neu zerlegt `photo/Graphemes.kt` nach sichtbaren Zeichen (ICU `BreakIterator`), Emoji werden als Ganzes ohne Schatten gezeichnet. Die Grenzen 28/60/24 Zeichen und der Zähler zählen sichtbare Zeichen und schneiden kein Emoji mehr durch. **Auf dem Tablet bestätigt:** «2027 🇨🇭» im Regenbogen funktioniert. Sehr neue Emoji können auf dem Tablet als Kästchen erscheinen, falls dessen Zeichensatz sie nicht kennt (Annahme).
- **Setup-Kopfleiste:** weisses Sonnhalde-Logo statt Text «SONNHALDE» und goldene Kennzeichnung «ENTWICKLERMODUS». Das Ausschalten-Symbol ist jetzt ein Vektor (`ic_power.xml`), weil die Schrift das Unicode-Zeichen nicht kannte. **Auf dem Tablet bestätigt.**
- **Rückmeldung für Gäste nach dem Auslösen:**
  - Im Countdown steht unter der Zahl «Bitte lächeln und kurz stillhalten».
  - Nach dem Auslösen erscheint «Foto aufgenommen!» mit Fortschrittskreis und dem aktuellen Schritt (vorbereiten, freistellen, fertigstellen) sowie «Sie können sich jetzt wieder bewegen».
  - Taps während der Verarbeitung werden ignoriert.
  - **Gebaut, in den Doku-Notizen noch nicht auf dem Tablet getestet.** Später meldete die Nutzerperson pauschal «ging alles super», ohne Einzelpunkte.

## 3. Foto-Verlauf, Zeitlimit, Druckwarteschlange – PR #24

Entstanden aus der Frage, was nach den 90 Sekunden mit dem Foto passiert: Das Ergebnis springt zum Start, die Druck-Version war danach weg und ein späterer Druckfehler blieb unbemerkt.

**Foto-Verlauf (neuer Reiter «Fotos», hinter der PIN)**

- Druck- und digitale Version der letzten Fotos liegen im internen Speicher (`files/history/`). Standard: **6 Fotos, höchstens 24 Stunden**, beides einstellbar. Das älteste Foto fliesst heraus; wartende oder laufende Druckaufträge werden nie gelöscht.
- Pro Foto: Vorschau, **erneut drucken** (Anzahl wählbar), **erneut hochladen** (neuer QR-Code), **exportieren** (nach `Downloads/Fotobox`), **löschen**. **Alle löschen** verlangt eine Bestätigung und die PIN.
- Ein Druckfehler nach dem Verlassen ist im Reiter sichtbar («(!)» am Reiter).

**Zeitlimit**

- Das Zeitlimit des Ergebnis-Bildschirms ist einstellbar (30 – 300 s, Standard 90 s).
- Es **pausiert, solange gedruckt wird** (höchstens 5 Minuten pro Druckschritt).

**Druckwarteschlange («Druck ist Pflicht»)**

- Der Druck liest das Foto vom Speicher. Aufträge laufen **nacheinander**. Der Status überlebt einen Neustart: wartende Aufträge werden nachgeholt, ein unterbrochener Druck wird als «Fehler» markiert und nicht automatisch wiederholt.
- Bei einem Fehler gibt es bis zu **2 Wiederholungen mit je 1 Abzug** (Entscheid der Nutzerperson, Papier ist knapp). Risiko (nicht geprüft): Hat der Drucker einen Auftrag angenommen und die App sah trotzdem einen Fehler, kommt ein Abzug doppelt.
- Ein spät fertiger Upload oder Druck eines früheren Fotos überschreibt den Zustand des nächsten Gasts nicht mehr. Zwei schnelle Taps auf «DRUCKEN» starten keinen zweiten Auftrag mehr.

**Abzüge pro Foto begrenzt (Fund beim Tablet-Test)**

- Wiederholtes Tippen auf DRUCKEN druckte beliebig viele Abzüge (3 gewählt, 3× getippt = 9).
- Jetzt sind pro Foto höchstens **3 Abzüge** möglich (`UiState.printedCopies`, nur erfolgreich gesendete zählen). Der Status «läuft» wird schon beim Tippen gesetzt.
- Der Druckknopf ist während des Druckens und danach **grau** (`SonnButton` hat dafür einen `enabled`-Parameter).
- Das Personal kann im Reiter «Fotos» weiter nachdrucken, dort gilt die Grenze nicht.

**Stand:** Gebaut, Tablet-Test mit «ging alles super» gemeldet, aber nicht in Einzelpunkten. **Ungetestet:** der Druck selbst, die Wiederholungen, das Nachholen nach einem Neustart.

## 4. Splashscreen und Setup-Reiter – PR #24

- **Splashscreen:** Das echte Sonnhalde-Logo ersetzt die gezeichnete Sonne mit Text.
- **Setup-Reiter neu geordnet** (Wunsch der Nutzerperson: «je weiter links, desto technischer»): **System, Drucker, QR & Upload, Kamera, Ablauf, Aussehen, Fotos**.
  - Der frühere Reiter «Konfiguration» ist in Aussehen, Ablauf, QR & Upload und System aufgeteilt. Der Inhalt der Abschnitte ist unverändert.
  - Haarmodell und Diagnose-Zeile liegen jetzt in «System» (Freistellung gilt als technisch).
  - Upload-Warteschlange und QR von Hand stehen im Reiter «QR & Upload», WLAN steht in «System».
  - Der Reiter beim Öffnen ist «Aussehen».
  - `README.md` und `docs/BEDIENUNG.md` wurden angepasst. **Die Screenshots in `docs/anleitung` zeigen noch die alte Aufteilung.**
- **Stand:** Tablet-Test «ging alles super» (nicht in Einzelpunkten).

## 5. Nextcloud: Download-Link mit Ablaufdatum – PR #25 (offen)

**Anlass:** Der Befund vom Vormittag lautete, dass `NextcloudUploader` den öffentlichen Link ohne Ablaufdatum anlegte. Die Nutzerperson wollte 12 Stunden Standard, einstellbar.

**Neu**

- Jeder Freigabe-Link bekommt ein **Ablaufdatum**. Standard **12 Stunden**, einstellbar von **1 Stunde bis 7 Tage oder ohne Ablauf** (Setup, QR & Upload, Nextcloud, «Download-Link gültig für»).
- **Grenze von Nextcloud (auf dem Tablet bestätigt):** Das Ablaufdatum gilt nur in ganzen Tagen bis 23:59 Uhr. Nextcloud zeigt bei der Freigabe «11:59 PM», egal ob 1 oder 12 Stunden eingestellt sind. Das tatsächliche Datum steht im Logcat («Nextcloud-Freigabe laeuft ab»).
- **Schalter «Link nach Ablauf sofort ungültig machen»** (Standard **an**, so entschieden von der Nutzerperson): Die App **widerruft den Link zur eingestellten Zeit** (Timer, solange die App läuft). Wurde die App vorher beendet, widerruft sie beim nächsten Upload alle verpassten Links ihrer eigenen Fotos (`foto-<ms>.jpg`, `test-<ms>.jpg`). Das gilt höchstens 50 pro Durchgang, nur direkt im Upload-Ordner und nie im Hauptverzeichnis.
- **Es wird nur der Link gelöscht, nicht das Foto.** Zuerst war das Löschen der Fotos gebaut, die Nutzerperson stellte klar: «Also das Löschen des Links, nicht des gesamten Fotos», darum wurde es umgebaut.
- Das Ablaufdatum wird ohne Tagesaufschlag berechnet. Lehnt Nextcloud ein Datum «heute» ab, fängt die App das ab.
- `docs/nextcloud.md` und `docs/EINRICHTUNG.md` beschreiben das neue Verhalten.
- **Konfliktprüfung:** Der Zweig wurde nach dem Merge von #24 mit `main` zusammengeführt (14:20), danach grün gebaut. Vor dem Merge von #25 nochmals prüfen, ob `main` sich bewegt hat.

**Ungetestet:** Der Timer und das Auflisten der Freigaben eines Ordners per OCS (`subfiles=true`) sind nicht gegen einen echten Server geprüft.

## 6. Dokumentation und Werkzeuge

- **`docs/ERKENNTNISSE-2026-11-27.md`** (neu, ca. 430 Zeilen): Stand, Umgebung, Gerätefakten, Freistellung mit Messwerten, Entscheidungen, vollständige Traktandenliste 1–23, Testprotokoll, Lehren der Sitzung. Aussagen sind als gemessen, nachgerechnet oder Annahme gekennzeichnet.
- **`docs/START-NEUER-CHAT.md`** (neu): Einstiegs-Prompt für neue Chats, mehrfach nachgeführt (Reihenfolge A–E, offene Punkte).
- **`tools/maskcheck/`** (neu): `check_filters.py` bildet boxMean und Guided Filter in Python nach, `inspect_models.py` vergleicht die drei geprüften MediaPipe-Modelle. Die Skripte laufen ohne Android.
- `docs/ENTWICKLUNG.md`: Freistellung, Stand des Tests, neue Punkte («Benutzerdefinitionen», Punkt 10), Verweis auf die neuen Dokumente. `CLAUDE.md` verweist auf Erkenntnisse und Start-Prompt.
- `docs/BEDIENUNG.md`: Reiter «Fotos», neue Reiter-Ordnung.

## 7. Entscheidungen der Nutzerperson heute

1. Der Rückkamera-Schalter im Setup **bleibt** (Notlösung bei Kamera-Verwechslung).
2. Das **Haarmodell bleibt**, mit Schalter.
3. **Druck und Kiosk-Beenden werden zuletzt getestet** (Papier, der DNP druckt bei jedem Start eine Status-Seite).
4. **Druck ist Pflicht**, der Upload darf ausfallen und in die Warteschlange.
5. Emoji im Banner werden **unterstützt**, nicht verboten.
6. **Töne:** echte Tondateien aus einer freien Quelle (z. B. CC0), Quelle und Lizenz im Repo vermerken. Rückfall: von Android erzeugter Ton.
7. **Eigene Logos** (Vermietung an Dritte): ja, aber nicht dringend. Transparenz im Banner: nein.
8. **Pausierter Drucker:** erst «kein Hinweis für Gäste», später geändert: Der **Druckknopf wird grau** und ein Hinweis sagt, dass der Drucker pausiert ist und das Personal gerufen werden soll. Noch nicht gebaut.
9. **Foto-Verlauf:** 6 Fotos, zusätzlich automatisch nach 24 Stunden löschen. **Zeitlimit:** 90 s Standard, einstellbar.
10. **Druckwiederholung:** nur zwei Wiederholungen mit je 1 Abzug.
11. **Nextcloud:** Link-Ablauf 12 Stunden Standard, einstellbar. Nur der Link wird widerrufen, nicht das Foto, und das ist standardmässig an.
12. PR #13 (Webseite aus der Doku) war für ein anderes Repository, die Webseite ist für dieses Repo nicht gewollt.
13. Das Werkspasswort des WCMPlus in der öffentlichen Doku wird **noch nicht** entfernt (Entscheid vertagt). Traktandum 14 (Zugangsdaten sicher ablegen) schliesst die Nutzerperson selbst ab.
14. Die Nutzerperson macht **nächste Woche** weiter.

## 8. Neue Traktanden heute

| Nr. | Thema | Stand |
|---|---|---|
| 16 | Foto-Verlauf mit Export und Löschen | gebaut (PR #24) |
| 17 | Zeitlimit einstellbar und beim Drucken pausiert, Druckfehler sichtbar | gebaut (PR #24) |
| 18 | Dauertest und Messung im Entwicklermodus | offen |
| 19 | Löschregel und Speicherort der Upload-Warteschlange | offen |
| 20 | Drucker pausieren / freigeben | offen, als nächster Block (B) geplant |
| 21 | Ablaufdatum der Nextcloud-Freigabe | gebaut (PR #25), Timer ungetestet |
| 22 | Automatische Update-Prüfung (Obtainium oder in der App) | offen, Entscheid nötig |
| 23 | Splashscreen-Logo und neue Setup-Reiter | gebaut (PR #24) |

**Zu Traktandum 22:** Die Empfehlung ist, zuerst Obtainium mit benannten Pre-Releases auszuprobieren. Bei einer eigenen Update-Funktion ist das Signaturzertifikat `app/debug.keystore` ein Sicherheitsrisiko, weil es öffentlich im Repository liegt.

## 9. Offen und ungetestet (Übersicht)

- Rückmeldung für Gäste im Detail (Countdown-Hinweis, Bildschirm nach dem Auslösen, Taps ignoriert)
- Druck selbst, Wiederholungen, Nachholen nach Neustart, Druck auf Papier, Banner-Transparenz, Kiosk-Beenden
- Foto-Verlauf im Einzelnen (Ring-Puffer, Export, Löschen mit PIN)
- Nextcloud-Timer zum Widerrufen der Links und das Aufräumen verpasster Links
- Dauerbetrieb: Speicher, Hitze, Stresstest mit 20–30 Fotos (Traktandum 3 und 18)
- Gruppenfotos und Lizenz des Haarmodells
- Release: `dev-build` wird bei jedem Push überschrieben. Ein dauerhaftes benanntes Pre-Release veröffentlicht die Nutzerperson im Browser.

## 10. Chronologie (Uhrzeit UTC)

| Zeit | Commit | Änderung |
|---|---|---|
| 08:20 | `f780afd` | Ergebnis-Bildschirm ohne Scrollen, QR-Code immer sichtbar |
| 09:20 | `399142e` | Doku: Stand Test, «Benutzerdefinitionen» |
| 09:41 | `e790e15` | Freistellung: zweiter Durchgang, Guided Filter |
| 09:43 | `8eae85f` | Kompilierfehler in `MaskMath` behoben (Double-Summen) |
| 10:09 | `35029fc` | Haarmodell, engere Schwellen |
| 10:11 | `53900e7` | Doku Freistellung |
| 10:14 | `2ba8e94` | Emoji im Banner, Kopfleiste, Ausschalten-Symbol |
| 10:42 | `529f7cf` | Rückmeldung nach dem Auslösen, Haarmodell-Schalter, Dauer in der Diagnose |
| 11:03 | `29e316a` | Doku: Erkenntnisse, Traktanden, Prüfskripte |
| 11:12 – 13:13 | `896509d`, `6bc721f`, `e5697c7`, `8a30ae2`, `d295642` | Doku: neue Traktanden 16–22, Entscheidungen, Start-Prompt |
| 13:44 | `b4e1c2b` | Foto-Verlauf, Zeitlimit, Druckwarteschlange |
| 13:45 | `67c32c2` | Doku Block A |
| 13:57 | `d232834` | Abzüge auf 3 begrenzt, Druckknopf grau |
| 14:02 | `9b80eb8` | Nextcloud: Ablaufdatum für Download-Links |
| 14:03 | `aca4928` | Splashscreen mit echtem Logo |
| 14:08 | `85f42fc` | Setup-Reiter neu geordnet |
| 14:18 | `fc37136` | Nextcloud: Link widerrufen statt Foto löschen |
| 14:19 | `ec65851` | Doku: Übergabe für neuen Chat |
| 14:27 | `680208d` | Nextcloud: Timer, Ablaufdatum ohne Tagesaufschlag |

Gemergt: #12 um 11:14, #14 um 12:57, #23 um 13:18, #24 um 14:19. Offen: #25.
