# Upload nach Nextcloud

Alternative zu Power Automate: Die App lädt das Foto direkt in eine Nextcloud (WebDAV) und erstellt dort einen
öffentlichen Freigabe-Link, aus dem der QR-Code entsteht. Es ist kein Flow dazwischen nötig.

## Einrichten (Nextcloud)

1. **Eigenen Benutzer** für die Fotobox anlegen (z. B. `fotobox`), damit das Passwort auf dem Tablet nicht auf das
   persönliche Konto zugreift. Optional einen Speicherplatz-Quota setzen.
2. Mit diesem Benutzer anmelden → *Einstellungen → Sicherheit → Geräte & Sitzungen* → **Neues App-Passwort**
   erstellen (Name z. B. «Fotobox Tablet») und kopieren.
3. In der Nextcloud-Administration unter *Einstellungen → Freigabe* muss **«Teilen per Link erlauben»**
   aktiv sein.

## Einrichten (App)

*Setup → Konfiguration → Upload-Ziel → Nextcloud*:

| Feld | Beispiel |
|---|---|
| Nextcloud-Adresse | `https://cloud.beispiel.ch` (bei Unterordner: `https://beispiel.ch/nextcloud`) |
| Benutzername | `fotobox` |
| App-Passwort | das kopierte App-Passwort |
| Ordner | `Fotobox` (wird angelegt, auch verschachtelt wie `Events/Personalfest`) |

Danach **«Upload testen»**: Die App lädt ein Testfoto hoch und zeigt den erzeugten Link oder den Fehler.

## Sicherheit

- Das App-Passwort liegt im App-Speicher des Tablets. Verwende deshalb den eigenen Benutzer und widerrufe das
  App-Passwort in Nextcloud, falls das Tablet verloren geht.
- Die Freigabe-Links sind öffentlich (jeder mit Link kann das Foto sehen). Ordner regelmässig aufräumen; in Nextcloud
  lässt sich ein Ablaufdatum für Freigaben erzwingen (*Freigabe → Standardablauf*).
- Fotos enthalten Personen: Aufbewahrungsdauer festlegen und am Gerät auf den Datenschutz hinweisen.
