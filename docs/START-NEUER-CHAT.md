# Einstiegs-Prompt für einen neuen Chat

Zum Kopieren in einen neuen Chat (Stand 27.11.2026 abends: PR #12 und PR [#14](https://github.com/everyday5631/FotoboxSH/pull/14) sind gemergt, `main` = `c347c24`).
Dieselbe Datei steht im Repo, damit der Prompt nicht verloren geht. Hintergrund und Begründungen: `docs/ERKENNTNISSE-2026-11-27.md`.

---

```text
Wir arbeiten an der Sonnhalde Fotobox (Android-App, Kotlin/Compose, Repo everyday5631/FotoboxSH). Spreche Deutsch mit mir, Schweizer Schreibung (ss statt ß).

LIES ZUERST, in dieser Reihenfolge:
1. CLAUDE.md
2. docs/ENTWICKLUNG.md
3. docs/ERKENNTNISSE-2026-11-27.md (Abschnitte 0 bis 2, 8, 9 und 10a genügen für den Start; der Rest ist Nachschlagewerk)

AUSGANGSLAGE (bitte selbst prüfen, nicht blind übernehmen):
- PR #12 und PR #14 sind gemergt (27.11.2026), main = c347c24, in der App «Version 0.1.127 (c347c24)». Ich merge selbst, als normaler Merge-Commit, nicht Squash. Neue Arbeit läuft als neuer Entwurf-PR auf dem Zweig, den dir der Chat vorgibt.
  Prüfe zuerst den echten Stand (list_pull_requests, git log origin/main), spule den Arbeitszweig mit git merge --ff-only origin/main auf main vor (kein reset --hard) und prüfe, dass die Builds in GitHub Actions grün sind (Push- UND Pull-Request-Lauf, Log lesen, nie nur raten).
- Das Release dev-build ist ein automatisches Pre-Release und wird bei jedem Push auf irgendeinem Zweig überschrieben; ein dauerhaftes, benanntes Pre-Release (z. B. v0.2.0-rc1) veröffentliche ich selbst im Browser.
- Nichts als fertig melden ohne grünen Build. Nichts als getestet bezeichnen, was ich nicht auf dem Tablet getestet habe. Ungetestetes ausdrücklich so nennen.
- Das Tablet testet nur ich (Xiaomi Pad 6S Pro). ADB läuft auf meinem Linux-Rechner: Befehle immer als ./adb ...; Log: ./adb logcat -c, Foto machen, ./adb logcat -d -s Fotobox:V AndroidRuntime:E
- Druck- und Kiosk-Tests ganz am Schluss (Fotopapier knapp, der DNP druckt bei jedem Start eine Status-Seite).
- Keine Zugangsdaten ins Repository. Kein Merge und kein Release durch dich. Neue PRs als Entwurf.
- Bündle Meldungen: ich antworte oft pro Nachricht und schreibe mitten im Satz weiter.

ARBEITSREIHENFOLGE (vereinbart):
A. Foto-Verlauf im Entwicklermodus (Traktandum 16) mit Zeitlimit (17) und dauerhafter Druckwarteschlange (6):
   - letzte ~6 Fotos (Druck- und digitale Version) auf dem internen Speicher; Vorschau, Erneut drucken, Erneut hochladen, Exportieren, Löschen (einzeln und alle, mit PIN); Ring-Puffer und Löschregel
   - Zeitlimit des Ergebnisses (heute fest 90 s) einstellbar und pausiert, solange gedruckt wird; Druckfehler nach dem Verlassen sichtbar machen
   - Der Druck ist Pflicht: Aufträge dürfen nicht verloren gehen (dauerhaft speichern, wiederholen); der Upload darf ausfallen (Warteschlange besteht schon auf dem Speicher)
B. Drucker-Wartungsmodus (20): «Drucker pausieren» / «Drucker freigeben», Aufträge werden gespeichert und danach der Reihe nach gedruckt. Gäste sehen dazu KEINEN Hinweis. Bestätige kurz mit mir, ob der Druckknopf dann sichtbar bleibt und der Auftrag still gespeichert wird (deine Auslegung) oder ausgeblendet wird.
C. Töne (8): echte Tondateien aus einer Quelle mit freier Lizenz (z. B. CC0), Quelle und Lizenz im Repo vermerken; Rückfall: von Android erzeugter Ton. Ticks im Countdown, Auslöse-Ton, Ton wenn das Foto fertig ist; Schalter im Setup.
D. Danach Messung und Dauertest (18, 3), Knopf «Modell neu laden» im Entwicklermodus (4), Nextcloud-Freigabe mit Ablaufdatum (21, erst mit mir klären).
E. Automatische Update-Prüfung (22): Obtainium oder eigenständig in der App. Lies zuerst Abschnitt 9.3 in docs/ERKENNTNISSE-2026-11-27.md (Sicherheitsrisiko: das Signaturzertifikat app/debug.keystore ist öffentlich). Klär mit mir, ob manuell oder automatisch, welche Releases (Stable/Pre-Release) und ob ein eigenes Release-Zertifikat gewünscht ist, bevor du etwas baust.

BITTE VOR DEM CODEN:
- Mach mir pro Block einen kurzen Plan und stelle gebündelt die Fragen, die du wirklich von mir brauchst (z. B. Anzahl Fotos im Verlauf, Aufbewahrungsfrist; die Sonnhalde soll mitreden: die frühere Mietbox hielt Fotos etwa 40 Tage, unklar wo).
- Prüfe jede Annahme im Code oder im Log. Kennzeichne in deinen Antworten, was gemessen, nachgerechnet oder nur eine Annahme ist.

OFFEN BEI MIR:
- Traktandum 14 (Zugangsdaten sicher ablegen) schliesse ich selbst ab; frag mich, was entschieden wurde.
- Das Werkspasswort des WCMPlus-Hotspots steht in der öffentlichen Doku; Entscheid vertagt.
- Banner-Transparenz und Druck auf Papier sind nicht getestet. Das Haarmodell habe ich noch nicht auf Lizenz geprüft.
- Wenn du Tests brauchst, sag mir genau, was ich tun und schicken soll (Screenshot, Video, Logzeilen).
```

---

## Hinweise zur Benutzung

- Der Prompt verweist auf die Dokumentation, statt sie zu wiederholen. Neue Erkenntnisse gehören am Ende eines Chats wieder in `docs/ERKENNTNISSE-…` (neues Datum) und in `docs/ENTWICKLUNG.md`.
- Wenn sich Zweig oder PR-Nummern ändern, den Abschnitt «Ausgangslage» im Prompt anpassen.
