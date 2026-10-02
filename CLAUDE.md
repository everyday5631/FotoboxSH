# Sonnhalde Fotobox – Hinweise für Claude

Android-App (Kotlin, Jetpack Compose) als Ersatz für die UpReach-Fotobox: Foto mit Timer, Banner, QR-Code zum Download,
Druck auf DNP QW410 über WCMPlus, Kiosk-Modus. Auftraggeber: Pflege- und Betreuungszentrum Sonnhalde AG.
Details, Gerätefakten und Entscheidungen: **`docs/ENTWICKLUNG.md`** (zuerst lesen). Bedienung: `README.md`.

## Arbeitsweise (wichtig)

- **Sprache:** Deutsch mit der Nutzerperson (Schweizer Kontext, «ss» statt «ß»); Code und Bezeichner Englisch/Deutsch gemischt wie im Bestand.
- **Bauen:** In der Cloud-Umgebung ist kein Android-SDK erreichbar. Kompilieren und APK-Prüfung laufen in **GitHub Actions**
  (`.github/workflows/build.yml`). Nach jedem Push den Lauf prüfen; ohne grünen Build nichts als fertig melden.
- **Tests macht die Nutzerperson auf dem Tablet.** Nie behaupten, etwas sei auf dem Gerät getestet, wenn es nicht so war;
  ungetestete Teile ausdrücklich als ungetestet benennen.
- **Git:** Entwickeln auf dem vorgegebenen Zweig, nach Merge den Zweig mit `git merge --ff-only origin/main` auf `main` vorspulen
  (kein `reset --hard`), neuer PR als Entwurf. Merge macht die Nutzerperson (normaler Merge-Commit, nicht Squash).
- **Releases:** Mit den GitHub-Werkzeugen lassen sich keine Releases anlegen; die Nutzerperson veröffentlicht sie im Browser.
  `dev-build` ist ein automatisches Vorab-Release (wird bei jedem Push überschrieben). `v0.1.36` ist das erste echte Release.
- **Sicherheit:** Keine Zugangsdaten (App-Passwörter, Flow-URL, WLAN-Passwörter) ins Repository. `app/debug.keystore` ist bewusst
  eingecheckt (nicht geheim, sorgt für gleiche Signatur bei jedem Build).
- **UpReach** ist kommerzielle, lizenzierte Software: nur als Vorbild für Aussehen und Ablauf verwenden, nicht zerlegen oder umbauen.

## Struktur

Eine Activity (`MainActivity`), ein `MainViewModel` mit `UiState`, Bildschirme in `ui/` (`Screen`: Splash, Overview/Setup, Welcome,
Start/Kamera, Result). Pakete: `kiosk`, `camera`, `photo`, `print`, `upload`, `qr`, `wcm`. Einstellungen in `WcmConfig` (SharedPreferences).
