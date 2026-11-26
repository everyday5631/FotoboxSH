# Entwicklungsnotizen

Stand: erstes Release `v0.1.36` (Commit `16df1e6`), getestet auf Xiaomi-Tablet mit DNP QW410 und WCMPlus.

> **Referenzstand:** `v0.1.36` ist die letzte Version, die vollständig und fehlerfrei funktioniert hat (Angabe der Auftraggeberseite).
> Alles danach (z. B. das anpassbare Banner mit Text links/rechts, Farben, eigenem Logo, Transparenz und Banner auf der digitalen
> Version, Foto-Hintergrund mit Personen-Freistellung, neues App-Icon) ist nur gebaut, aber noch **nicht auf dem Tablet getestet**. Bei Fehlern ist `v0.1.36` der Rückfallstand.

## Ablauf der App

1. **Splash** → **Setup-Modus** (Reiter Konfiguration, Kamera, Drucker, Warteschlange, WLAN; Start-Knopf unten; ⏻ beendet die App nach PIN).
2. **Retail-Modus** (Kiosk): **Welcome** (Hintergrund, Logo, Überschrift, «jetzt starten») → **Kamera** (Live-Bild, Timer) → **Result**
   (digitale Version mit QR-Code links, Druck-Version mit «DRUCKEN» rechts, Haken = fertig). Nach 90 s ohne Eingabe zurück zum Start.
   Beenden: langer Druck auf Logo/Banner, dann PIN (Standard `1234`).
3. Pro Foto entstehen zwei Bilder (`photo/PhotoComposer`): **Druck-Version** 3:2 mit Banner (Logo mittig, Text links/rechts je max. 28 Zeichen, Farbe, Transparenz, eigenes Logo; `BannerStyle`)
   und **digitale Version** (Originalformat, Banner optional ein/aus). Hochgeladen wird die digitale, gedruckt die Druck-Version.

## Pakete

| Paket | Inhalt |
|---|---|
| `kiosk` | Device-Admin-Receiver, Lock-Task/Vollbild, PIN. Voller Kiosk nur als Device Owner (`adb shell dpm set-device-owner ch.sonnhalde.fotobox/.kiosk.AdminReceiver`), sonst «Bildschirm fixieren». |
| `camera` | CameraX: Retail-Kamera, Setup-Panel (Front/Rück, Belichtung, Zoom). Kamera nur beim Verlassen des Bildschirms freigeben, **nicht** an State koppeln (sonst «Not bound to a valid Camera»). |
| `photo` | `PhotoComposer` (Druck- und digitale Version, Banner), `BackgroundLibrary` (mitgelieferte Platzhalter in `app/src/main/assets/backgrounds/`, eigene Bilder in `files/backgrounds/`), `BackgroundRemover` (ML-Kit-Selfie-Segmentierung, Person vor gewähltes Bild setzen). Einstellung: `WcmConfig.backgroundId` (`asset:…` / `file:…`, leer = aus) als Vorauswahl, `backgroundGuestChoice` = Gäste wählen auf dem Kamera-Bildschirm (`UiState.guestBackgroundId`, zurückgesetzt beim Zurück zum Startbildschirm). |
| `print` | `Ipp` (Codec), `IppPrinter` (Suche, Auswahl der Warteschlange, Druck), `PdfWrap` (JPEG → PDF 4x6), `PrinterNetwork` (WLAN-Wechsel), `PhotoPrinter` (Android-Druckdialog als Ausweg). |
| `upload` | `NextcloudUploader` (WebDAV + OCS-Freigabe, Schrittmeldungen 1/4–4/4), `SharePointUploader` (Power-Automate-Flow, Vertrag in `docs/power-automate-flow.md`), `UploadQueue` (fehlgeschlagene Fotos). |
| `wcm` | `WcmConfig`/`WcmSettings` (alle Einstellungen), `WcmClient` (Erreichbarkeit des Portals). |

## Gerätefakten (WCMPlus / DNP QW410)

- WCMPlus ist ein **Druckserver, kein Router**. Eigener Hotspot `WCMPLUS-xxx` (Passwort `dnp12345`, Portal `192.168.4.1`) hat **kein Internet**.
  Zwei Funkmodule: wahlweise Hotspot + WLAN-Client gleichzeitig («Antennenkonfiguration»).
- IPP-Server meldet **pro Papierformat eine Warteschlange**: `ipp://<ip>:631/printers/QW410-4x6`, `…/QW410-4x4`. Das Format steckt im Namen der
  Warteschlange. `media-supported` ist «unknown» → kein `media`-Attribut senden.
- `document-format-supported`: PDF, PostScript, Raster, octet-stream, **kein `image/jpeg`** → die App verpackt das Foto als PDF.
- Adressen ändern sich per DHCP (Beispiele `.211`, `.164`). Suche per mDNS (`_ipp._tcp`) wie Mopria; im Hotspot feste Adresse `192.168.4.1`.
- **SH-GAST** (Gast-WLAN): hat Internet, aber das Tablet erreicht darin den Drucker nicht. Lösung in der App: beim Drucken **vorübergehend ins
  Drucker-WLAN wechseln** (`WifiNetworkSpecifier`, `PrinterNetwork`) und danach zurück. Upload/QR passiert vorher im Internet-WLAN. Das funktioniert
  auf dem Xiaomi-Tablet. Alternative: eigener Reiserouter im Repeater-Modus.
- UpReach druckt über Mopria/Android-Druckdialog; ihre Oberfläche kommt aus der Cloud-Konfiguration (nicht aus der APK nachbaubar).

## Entscheidungen

- App-Icon: Sonnhalde-Bildmarke (gelb) auf Dunkelblau `#233446`, Pfad aus dem Design-System-Repo `everyday5631/Se-Design` (`brand/vektor/Sonnhalde-Bildmarke-gelb.svg`), mit Themed-Icon-Variante. Das Repo enthält alle Branding-Dateien der Sonnhalde (Logos, Farben, Vorlagen), aber keine Landschaftsfotos.
- Festes Debug-Zertifikat (`app/debug.keystore`): Updates über die installierte App möglich. Ältere Builds vorher deinstallieren.
- Versionsanzeige «Version 0.1.N (Commit)» aus CI (N = Lauf-Nummer, Commit = echter Head, bei PR-Builds nicht der Merge-Commit).
- Nextcloud-Upload mit **Konto-ID und App-Passwort**; Power Automate (HTTP-Trigger = Premium-Connector) bleibt Option für die Firma.
- Logo/Hintergrund per Dateien in `app/src/main/res/drawable-nodpi/` (`sonnhalde_logo.png`, `sonnhalde_logo_vertical.png`, optional `start_background.jpg`).

## Bekannte Grenzen / Risiken

- WLAN-Wechsel: Android fragt beim Verbinden um Erlaubnis; im gesperrten Kiosk kann diese Systemabfrage hakeln. Trennt das Internet kurz.
  Mit «App anheften» (Bildschirm fixieren) hat der WLAN-Wechsel auf dem Xiaomi-Tablet funktioniert (Rückmeldung der Nutzerperson). Ob der volle
  Kiosk (Device Owner) sich überhaupt noch lohnt, ist offen; Anheften reicht vermutlich und braucht keine Einrichtung per `adb`.
- Zugangsdaten (Nextcloud-App-Passwort, Flow-URL, WLAN-Passwort, PIN) liegen im App-Speicher (SharedPreferences, unverschlüsselt). Eigene Zugänge mit minimalen Rechten verwenden.
  **Für den Entwicklungsbetrieb tragbar, für den Einsatz bei der Sonnhalde nicht** (Entscheid der Nutzerperson): muss vor allem dann gelöst werden, wenn
  Power Automate dazukommt (die Flow-URL enthält die Signatur und ist selbst ein Geheimnis). Ansatz offen, z. B. Android-Keystore-Verschlüsselung;
  ein Konfigurations-Export darf die Geheimnisse nicht im Klartext enthalten.
- Schrift: **Arial ist die offizielle Schrift des Corporate Designs** (Nutzerperson). Der Vermerk im Design-Repo `Se-Design` (Definitionen-Blatt nennt Open Sans/Source Serif Pro) gilt damit als geklärt.
- Kein Video/GIF-Hintergrund, kein E-Mail-Versand (UpReach hat sie).
- Foto-Hintergrund: Die Freistellung läuft erst nach der Aufnahme (ML Kit `segmentation-selfie`, Modell in der App, einige hundert ms bis wenige Sekunden je nach Tablet).
  Im Live-Bild liegt der gewählte Hintergrund nur halbtransparent über der Kamera (Andeutung, keine Freistellung). Bei Fehlern der Erkennung oder fehlendem Bild bleibt das Originalfoto;
  der Grund steht als Hinweis auf dem Ergebnis-Bildschirm (`UiState.backgroundNote`) und im Logcat (Tag `Fotobox`, auch Personenanteil), ebenso «keine Person erkannt» (<1 %) und «ganzes Bild als Person» (>97 %). Haare, Hände und Gruppen
  sind die Schwachstellen des Modells; Randqualität nur auf dem Tablet beurteilbar.

## Offene Punkte (Reihenfolge nach Wunsch der Nutzerperson)

1. ~~Release-Workflow~~ erstellt (`docs/RELEASE.md`), noch nicht ausgeführt
2. Netz-Diagnose im Setup (eigene IP, Erreichbarkeit, Netz-Suche, Protokoll zum Teilen): später, am besten mit `adb logcat` zum Mitschneiden; **zurückgestellt**.
3. Konfiguration exportieren/importieren (zweites Tablet): später; **zurückgestellt** (Geheimnisse beachten, siehe oben).
4. Zugangsdaten sicher ablegen (Nextcloud, später Power Automate): **vor dem Einsatz zu klären**.
5. Fische-Bild als Standard-Hintergrund (`start_background.jpg`), Datei steht noch aus.
6. Warteschlange automatisch senden, sobald Internet da ist: **bewusst offen gelassen**.
7. Version 1.0 ausweisen, README für Dritte: **bewusst offen gelassen**.
8. Banner (PR #8) und Foto-Hintergrund/Icon (PR #11) auf dem Tablet testen.
