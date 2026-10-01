# Sonnhalde Fotobox (Android)

Android-App (Kotlin, Jetpack Compose) im [Sonnhalde-Design-System](https://github.com/everyday5631/Se-Design):
Navy-Banner mit «SONNHALDE»-Wortmarke, Gold-Linien, Cream-Hintergrund.

## Ablauf

1. **Foto aufnehmen** – 3-2-1-Countdown, Front- oder Rückkamera.
2. **Upload nach SharePoint** – automatisch über einen Power-Automate-Flow (siehe unten).
3. **QR-Code** zum SharePoint-Link wird erzeugt und kann als PNG nach `Downloads` geladen/geteilt werden.
4. **Drucken** über den Android-Druckdialog auf den **DNP QW410 mit WCMPlus-Modul**
   (Drucker «QW410-4x6 @ dnpimage», Papierformat «4 x 6 in.»). Tablet und WCMPlus müssen im selben WLAN sein
   (Einrichtung siehe «UpReach Fotosystem | Drucker Android»).

Es wird keine Cloud eines Herstellers benötigt.

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
