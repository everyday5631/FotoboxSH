# Power-Automate-Flow für den SharePoint-Upload

Die App schickt jedes Foto (digitale Version) an einen Flow. Der legt es in SharePoint ab und gibt einen
Freigabe-Link zurück. Aus dem Link macht die App den QR-Code.

> **Lizenz:** Der Trigger «Beim Empfang einer HTTP-Anfrage» ist ein **Premium-Connector** (Power Automate
> Premium bzw. passende Lizenz für den Flow-Besitzer). Ohne diese Lizenz gibt es den Trigger nicht.

## Vertrag zwischen App und Flow

- **Anfrage (App → Flow):** `POST`, `Content-Type: application/json`

  ```json
  { "fileName": "foto-1727780000000.jpg", "contentBase64": "<JPEG als Base64>" }
  ```
- **Antwort (Flow → App):** Status `200`, `Content-Type: application/json`

  ```json
  { "url": "https://<tenant>.sharepoint.com/:i:/s/<site>/..." }
  ```

## Flow bauen (ca. 10 Minuten)

1. <https://make.powerautomate.com> → **Erstellen → Sofortiger Cloud-Flow** → Name «Fotobox Upload» →
   Trigger-Auswahl überspringen (**Überspringen**).
2. **Trigger hinzufügen:** «Beim Empfang einer HTTP-Anfrage» (*When an HTTP request is received*).
   - **Wer kann den Flow auslösen:** *Jeder* (die App meldet sich nicht an; die URL enthält einen geheimen Schlüssel).
   - **JSON-Schema des Anforderungstexts:**

     ```json
     {
       "type": "object",
       "properties": {
         "fileName": { "type": "string" },
         "contentBase64": { "type": "string" }
       },
       "required": ["fileName", "contentBase64"]
     }
     ```
3. **Aktion «Datei erstellen»** (SharePoint, *Create file*):
   - Websiteadresse: eure SharePoint-Site
   - Ordnerpfad: z. B. `/Freigegebene Dokumente/Fotobox`
   - Dateiname (Ausdruck, ignoriert den Namen der App und vermeidet Kollisionen):
     `concat('foto-', formatDateTime(utcNow(), 'yyyyMMdd-HHmmss'), '-', substring(guid(), 0, 8), '.jpg')`
   - Dateiinhalt (Ausdruck): `base64ToBinary(triggerBody()?['contentBase64'])`
4. **Aktion «Freigabelink für Datei oder Ordner erstellen»** (SharePoint, *Create sharing link for a file or folder*):
   - Websiteadresse / Bibliothek: wie oben
   - Datei-ID: dynamischer Inhalt **Bezeichner** aus «Datei erstellen»
   - Linktyp: **Anzeigen**; Linkbereich: **Jeder** (anonym), damit Gäste den QR-Code ohne Anmeldung öffnen können.
   - Hinweis: «Jeder»-Links müssen im SharePoint-Admin-Center für die Site/Organisation erlaubt sein.
     Sonst ist der QR-Link nur für Mitarbeitende mit Konto nutzbar.
5. **Aktion «Antwort»** (*Response*):
   - Statuscode: `200`
   - Header: `Content-Type` = `application/json`
   - Text (den Link per dynamischem Inhalt «Link – Web-URL» der vorigen Aktion einsetzen):

     ```json
     { "url": "@{body('Freigabelink_für_Datei_oder_Ordner_erstellen')?['link']?['webUrl']}" }
     ```

     Der Aktionsname im Ausdruck muss genau dem Namen eurer Aktion entsprechen (Leerzeichen → `_`).
     Nach einem Testlauf im Ausführungsverlauf prüfen, wie die Ausgabe heißt.
6. **Speichern.** Im Trigger erscheint die **HTTP-POST-URL**. Diese URL in der App eintragen:
   *Setup → Konfiguration/Drucker → Upload-Link*. Sie enthält einen geheimen Schlüssel: nicht weitergeben und
   nicht ins Repository einchecken.

## Testen ohne App

```bash
tools/test-flow.sh "<HTTP-POST-URL>" test.jpg
```

Erwartet: eine Zeile mit `{"url":"https://..."}`. Schlägt es fehl, im Flow den **Ausführungsverlauf** öffnen.

## Betrieb und Sicherheit

- Die SharePoint-Verbindung läuft unter dem Konto, mit dem der Flow erstellt wurde. Besser ein **Dienstkonto**
  verwenden, damit der Flow nicht ausfällt, wenn eine Person die Firma verlässt.
- Wer die URL kennt, kann Dateien in den Ordner hochladen. Ordner mit Quote/Aufbewahrungsregel versehen; bei
  Missbrauch beim Trigger den Schlüssel erneuern («URL neu generieren») und in der App ersetzen.
- Fotos enthalten Personen: Aufbewahrungsdauer festlegen (z. B. Aufbewahrungsrichtlinie oder ein zweiter Flow,
  der Dateien nach X Tagen löscht) und ggf. einen Hinweis zum Datenschutz am Gerät anbringen.

## Mögliche Erweiterung: E-Mail

Wie bei UpReach lässt sich ein E-Mail-Versand mit demselben Flow lösen (zusätzliche Aktion «E-Mail senden (V2)»
mit dem Foto als Anhang, Adresse aus einem weiteren Feld der Anfrage). In der App ist dafür noch kein Knopf eingebaut.
