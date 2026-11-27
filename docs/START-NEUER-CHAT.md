# Einstiegs-Prompt für einen neuen Chat

Zum Kopieren in einen neuen Chat (Stand 27.11.2026 abends: PR #12, #14 und #23 sind gemergt; PR [#24](https://github.com/everyday5631/FotoboxSH/pull/24) mit Block A, Splashscreen-Logo und neuer Reiter-Ordnung ist bereit zum Merge, siehe unten).
Dieselbe Datei steht im Repo, damit der Prompt nicht verloren geht. Hintergrund und Begründungen: `docs/ERKENNTNISSE-2026-11-27.md`.

---

```text
Wir arbeiten an der Sonnhalde Fotobox (Android-App, Kotlin/Compose, Repo everyday5631/FotoboxSH). Spreche Deutsch mit mir, Schweizer Schreibung (ss statt ß).

LIES ZUERST, in dieser Reihenfolge:
1. CLAUDE.md
2. docs/ENTWICKLUNG.md
3. docs/ERKENNTNISSE-2026-11-27.md (Abschnitte 0 bis 2, 8, 9 und 10a genügen für den Start; der Rest ist Nachschlagewerk)

AUSGANGSLAGE (bitte selbst prüfen, nicht blind übernehmen):
- PR #12, #14 und #23 sind gemergt. PR #24 (Zweig claude/festive-lamport-h0abxg: Foto-Verlauf, Zeitlimit, Druckwarteschlange mit Wiederholung, Abzug-Grenze, Splashscreen-Logo, neue Reiter-Ordnung; letzter Build 0.1.144, Commit 85f42fc, beide Läufe grün) wollte ich am 27.11. abends selbst mergen. Prüfe zuerst mit list_pull_requests, ob das geschehen ist. Ich merge selbst, als normaler Merge-Commit, nicht Squash. Neue Arbeit läuft als neuer Entwurf-PR auf dem Zweig, den dir der Chat vorgibt.
  Prüfe zuerst den echten Stand (list_pull_requests, git log origin/main), spule den Arbeitszweig mit git merge --ff-only origin/main auf main vor (kein reset --hard) und prüfe, dass die Builds in GitHub Actions grün sind (Push- UND Pull-Request-Lauf, Log lesen, nie nur raten).
- Das Release dev-build ist ein automatisches Pre-Release und wird bei jedem Push auf irgendeinem Zweig überschrieben; ein dauerhaftes, benanntes Pre-Release (z. B. v0.2.0-rc1) veröffentliche ich selbst im Browser.
- Nichts als fertig melden ohne grünen Build. Nichts als getestet bezeichnen, was ich nicht auf dem Tablet getestet habe. Ungetestetes ausdrücklich so nennen.
- Das Tablet testet nur ich (Xiaomi Pad 6S Pro). ADB brauche ich im Moment nicht mehr; falls doch, läuft es auf meinem Linux-Rechner: Befehle immer als ./adb ...; Log: ./adb logcat -c, Foto machen, ./adb logcat -d -s Fotobox:V AndroidRuntime:E
- Druck- und Kiosk-Tests ganz am Schluss (Fotopapier knapp, der DNP druckt bei jedem Start eine Status-Seite).
- Keine Zugangsdaten ins Repository. Kein Merge und kein Release durch dich. Neue PRs als Entwurf.
- Bündle Meldungen: ich antworte oft pro Nachricht und schreibe mitten im Satz weiter.

ARBEITSREIHENFOLGE (vereinbart):
A. ERLEDIGT (gebaut in PR #24, 27.11.): Foto-Verlauf (Reiter «Fotos»: 6 Fotos, 24 h, einstellbar), einstellbares Zeitlimit (Standard 90 s, pausiert beim Drucken), Druck vom Speicher mit 2 Wiederholungen je 1 Abzug, höchstens 3 Abzüge pro Foto, Druckknopf grau. Ich habe «ging alles super» gemeldet, aber die Einzelpunkte nicht aufgeschlüsselt; frag nach, was ich genau gesehen habe, und halte nur das als bestätigt fest. Nicht getestet: der Druck selbst, die Wiederholungen, Nachholen nach Neustart.
   Offen aus A: Soll das Personal Druckfehler auch auf dem Startbildschirm sehen (heute nur im Setup, «(!)» am Reiter «Fotos»)? War «zwei Versuche» als 2 Wiederholungen gemeint (so gebaut)?
B. Drucker pausieren (20) als nächster Block. Entscheid von mir: Ist der Drucker pausiert, wird der Druckknopf GRAU und darunter steht ein Hinweis, dass der Drucker pausiert ist und das Personal gerufen werden soll (es gibt dann keine neuen Aufträge). Vorschlag von dir, den ich noch bestätigen muss: Knöpfe «Drucker pausieren» / «Drucker freigeben» oben im Reiter «Drucker» mit Status, Pause übersteht einen Neustart, laufender Druck wird bis 2 Minuten abgewartet bevor «Sie können abkoppeln» erscheint, Auto-Druck wird während der Pause übersprungen (Foto bleibt im Verlauf als nicht gedruckt), Erneut-Drucken im Reiter «Fotos» ist während der Pause gesperrt, beim Freigeben werden bereits wartende Aufträge der Reihe nach gedruckt.
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
