# Sonnhalde Fotobox (Android)

Android-App (Kotlin, Jetpack Compose) im [Sonnhalde-Design-System](https://github.com/everyday5631/Se-Design):
Navy-Banner mit «SONNHALDE»-Wortmarke, Gold-Linien, Cream-Hintergrund.

## Modi (wie UpReach)

- **Setup-Modus** (wie die UpReach-Oberfläche): Reiter **Konfiguration** (Banner-Text, Startbildschirm, Timer, Auto-Druck, PIN),
  **Kamera** (Live-Bild, Front-/Rückkamera, Belichtung, Zoom), **Drucker** (WCMPlus-/IPP-Adresse, Testdruck),
  **Warteschlange** (nicht hochgeladene Fotos erneut senden), **WLAN**; unten grosser **Start**-Knopf, oben ⏻ = App beenden (PIN).
- **Retail-Modus** (nach START, Kiosk/Vollbild): **Startbildschirm** (Hintergrundbild, Logo, Überschrift, «jetzt starten»;
  Bild/Texte im Setup einstellbar) → Live-Kamera → «Foto aufnehmen» → Timer → Foto → Ergebnis mit
  **ZUM HERUNTERLADEN SCANNEN** (QR-Code), Anzahl Abzüge (1–3), **DRUCKEN**, **FERTIG**. Nach 90 s ohne Eingabe
  zurück zum Start. Beenden: langer Druck auf das Logo (Startbildschirm) bzw. den Banner, dann PIN. Nach einem Neustart der App geht es direkt im
  Retail-Modus weiter.

## SharePoint-Flow

Anleitung und Testskript: [docs/power-automate-flow.md](docs/power-automate-flow.md), `tools/test-flow.sh`.

## Standard-Hintergrund Startbildschirm

Datei `app/src/main/res/drawable-nodpi/start_background.jpg` ablegen (wird automatisch genutzt); im Setup gewählte Bilder haben Vorrang.

## Foto-Banner

Das Druckbild ist 3:2 (10 x 15 cm) mit hellem Banner unten: Sonnhalde-Logo mittig, frei wählbarer Text rechts
(max. 28 Zeichen, z. B. «Personalfest 2027»; Schrift passt sich an). Das Original-Logo liegt unter
`app/src/main/res/drawable-nodpi/sonnhalde_logo.png` (Austausch der Datei genügt).

## Ablauf (technisch)

1. **Foto aufnehmen** – 3-2-1-Countdown, Front- oder Rückkamera.
2. **Upload nach SharePoint** – automatisch über einen Power-Automate-Flow (siehe unten).
3. **QR-Code** zum SharePoint-Link wird erzeugt und kann als PNG nach `Downloads` geladen/geteilt werden.
4. **Drucken** über den Android-Druckdialog auf den **DNP QW410 mit WCMPlus-Modul**
   (Drucker «QW410-4x6 @ dnpimage», Papierformat «4 x 6 in.»). Tablet und WCMPlus müssen im selben WLAN sein
   (Einrichtung siehe «UpReach Fotosystem | Drucker Android»).

Es wird keine Cloud eines Herstellers benötigt.

## Kiosk-Modus (wie UpReach)

Die App startet im Vollbild, sperrt die Zurück-Taste und hält den Bildschirm an. Lock-Task-Modus:

- **Mit Device Owner (empfohlen):** einmalig per ADB, das Gerät darf dafür keine Konten haben und keinen anderen
  Device Owner (z. B. UpReach) besitzen:
  `adb shell dpm set-device-owner ch.sonnhalde.fotobox/.kiosk.AdminReceiver`
  Danach startet der Kiosk ohne Rückfrage und ohne Ausstiegsgeste.
- **Ohne Device Owner:** Android «Bildschirm anheften» (einmalige Rückfrage, Ausstieg per Systemgeste).

**Verwaltung:** langer Druck auf den Banner → PIN (Standard `1234`, bitte ändern). Dort: Einstellungen, QR aus Link,
Drucker testen, Kiosk beenden/starten. Gäste sehen nur «Foto aufnehmen».

## Drucken

«Drucken» sendet das Foto per **IPP direkt** an den QW410 (kein Android-Dialog). Die genaue IPP-Adresse ist nicht
dokumentiert; ohne Eintrag probiert die App übliche Pfade auf `192.168.4.1:631`. Unter *Verwaltung → Drucker testen*
sieht man das Ergebnis; eine gefundene Adresse kann bei Bedarf unter *Drucker-Adresse IPP* fest eingetragen werden.
Schlägt der Direktdruck fehl, erscheint ein Knopf für den normalen Android-Druckdialog (4 x 6 in.).

## WCMPlus

WCMPlus ist ein WLAN-Druckmodul (Portal standardmässig `http://192.168.4.1`). Gedruckt wird über den normalen
Android-Druckdienst, nicht über eine eigene API. Die App prüft nur, ob das Portal erreichbar ist
(Adresse in den Einstellungen änderbar; nach dem Einbinden ins lokale WLAN die neue IP eintragen).

## SharePoint-Upload (Power Automate)

In Power Automate einen Flow **«Beim Empfang einer HTTP-Anfrage»** anlegen:

- Anfrage-Schema: `{"fileName": "string", "contentBase64": "string"}`
- Aktion **SharePoint → Datei erstellen**: Dateiname `fileName`, Inhalt `base64ToBinary(triggerBody()?['contentBase64'])`
- Aktion **SharePoint → Freigabelink erstellen** (oder Link der Datei verwenden)
- Aktion **Antwort**: Status 200, Text `{"url": "<Link>"}`

Den HTTP-POST-URL des Triggers in der App unter *Einstellungen → Upload-Link* eintragen. Der Link enthält einen
geheimen Schlüssel – nicht ins Repository einchecken.

## Build

`./gradlew assembleDebug` (Android SDK 34, JDK 17). GitHub Actions baut bei jedem Push die APK und stellt sie als
Vorab-Release **dev-build** direkt (ohne ZIP) bereit.

Arial ist unter Android nicht vorhanden; es wird die System-Sans-Serif genutzt.

## Installation / Updates

Alle Builds sind mit demselben (nicht geheimen) Debug-Schlüssel signiert (`app/debug.keystore`), daher lässt sich ein
neuer Build direkt über die installierte App installieren. Ältere Builds davor waren anders signiert: einmalig die
alte App deinstallieren.
