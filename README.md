# Sonnhalde Fotobox (Android)

Android-App (Kotlin, Jetpack Compose) im [Sonnhalde-Design-System](https://github.com/everyday5631/Se-Design):
Navy-Banner mit «SONNHALDE»-Wortmarke, Gold-Linien, Cream-Hintergrund.

## Funktionen

- **WCMPlus-Verbindung:** prüft per HTTP-GET, ob WCMPlus erreichbar ist
  (Standard `http://localhost:8080`, Adresse/Pfad/Token in den Einstellungen änderbar).
- **QR-Code aus SharePoint-Link:** Link einfügen oder in SharePoint/Teams/Browser «Teilen» → *Sonnhalde Fotobox*.
  Der QR-Code wird als PNG in `Downloads` gespeichert und kann direkt geteilt werden
  (z. B. zurück nach SharePoint/OneDrive oder an den Drucker-PC).

## Offen

Die WCMPlus-Schnittstelle (Endpunkte für Druckaufträge usw.) ist nicht dokumentiert im Repo;
`wcm/WcmClient.kt` enthält deshalb nur den Verbindungstest. Weitere Aufrufe dort ergänzen.
Die App erzeugt den QR-Code lokal, es ist kein SharePoint-Login nötig.
Arial ist unter Android nicht vorhanden; es wird die System-Sans-Serif genutzt.

## Build

`./gradlew assembleDebug` (Android SDK 34, JDK 17). Die APK baut auch GitHub Actions.
