# Entwicklungsnotizen

Stand: erstes Release `v0.1.36` (Commit `16df1e6`), getestet auf Xiaomi-Tablet mit DNP QW410 und WCMPlus.

## Ablauf der App

1. **Splash** → **Setup-Modus** (Reiter Konfiguration, Kamera, Drucker, Warteschlange, WLAN; Start-Knopf unten; ⏻ beendet die App nach PIN).
2. **Retail-Modus** (Kiosk): **Welcome** (Hintergrund, Logo, Überschrift, «jetzt starten») → **Kamera** (Live-Bild, Timer) → **Result**
   (digitale Version mit QR-Code links, Druck-Version mit «DRUCKEN» rechts, Haken = fertig). Nach 90 s ohne Eingabe zurück zum Start.
   Beenden: langer Druck auf Logo/Banner, dann PIN (Standard `1234`).
3. Pro Foto entstehen zwei Bilder (`photo/PhotoComposer`): **Druck-Version** 3:2 mit hellem Banner (Logo mittig, Text rechts, max. 28 Zeichen)
   und **digitale Version** mit buntem Text-Sticker. Hochgeladen wird die digitale, gedruckt die Druck-Version.

## Pakete

| Paket | Inhalt |
|---|---|
| `kiosk` | Device-Admin-Receiver, Lock-Task/Vollbild, PIN. Voller Kiosk nur als Device Owner (`adb shell dpm set-device-owner ch.sonnhalde.fotobox/.kiosk.AdminReceiver`), sonst «Bildschirm fixieren». |
| `camera` | CameraX: Retail-Kamera, Setup-Panel (Front/Rück, Belichtung, Zoom). Kamera nur beim Verlassen des Bildschirms freigeben, **nicht** an State koppeln (sonst «Not bound to a valid Camera»). |
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

- Festes Debug-Zertifikat (`app/debug.keystore`): Updates über die installierte App möglich. Ältere Builds vorher deinstallieren.
- Versionsanzeige «Version 0.1.N (Commit)» aus CI (N = Lauf-Nummer, Commit = echter Head, bei PR-Builds nicht der Merge-Commit).
- Nextcloud-Upload mit **Konto-ID und App-Passwort**; Power Automate (HTTP-Trigger = Premium-Connector) bleibt Option für die Firma.
- Logo/Hintergrund per Dateien in `app/src/main/res/drawable-nodpi/` (`sonnhalde_logo.png`, `sonnhalde_logo_vertical.png`, optional `start_background.jpg`).

## Bekannte Grenzen / Risiken

- WLAN-Wechsel: Android fragt beim Verbinden um Erlaubnis; im gesperrten Kiosk kann diese Systemabfrage hakeln. Trennt das Internet kurz.
- Keine Berechtigung/Sperre gegen Schliessen der Hotspot-Anmeldung; Passwörter liegen im App-Speicher (SharedPreferences, unverschlüsselt).
- Kein Video/GIF-Hintergrund, kein E-Mail-Versand, keine Hintergrund-Freisteller (UpReach hat sie).

## Offene Punkte (Reihenfolge nach Wunsch der Nutzerperson)

1. Release-Workflow (Tag eingeben → Release mit APK).
2. Netz-Diagnose im Setup (eigene IP, Erreichbarkeit, Netz-Suche, Protokoll zum Teilen).
3. Konfiguration exportieren/importieren (zweites Tablet).
4. Fische-Bild als Standard-Hintergrund (`start_background.jpg`), Datei steht noch aus.
5. Warteschlange automatisch senden, sobald Internet da ist.
6. Version 1.0 ausweisen, README für Dritte.
