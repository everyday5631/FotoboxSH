# Einrichtung und Einsatz vor Ort

Anleitung für Mitarbeitende und IT. Überblick und Installation: [README.md](../README.md). Technik: [ENTWICKLUNG.md](ENTWICKLUNG.md).

> **Stand der Prüfung:** Getestet ist der Ablauf auf einem Xiaomi-Tablet mit DNP QW410 und WCMPlus (Release `v0.1.36`).
> Das Gerät ist im Einsatz bereits fertig eingerichtet; diese Anleitung dient zur Kontrolle und für den Neuaufbau.

## 1. Checkliste vor dem Anlass

- [ ] Tablet **geladen** (oder Netzteil dabei). Die App hält den Bildschirm an, das Tablet verbraucht also dauernd Strom.
- [ ] **App-Version** prüfen: *Setup* oben, «Aktive Konfiguration: SONNHALDE · Version 0.1.N (Commit)». Bei Bedarf aktualisieren (Abschnitt 8).
- [ ] **Drucker** an, **Papier- und Farbband** eingelegt (DNP QW410), WCMPlus-Modul eingeschaltet.
- [ ] **Netz** nach Abschnitt 2 aufgebaut; Tablet hat Internet.
- [ ] *Setup → Drucker*: Status «WCMPlus: verbunden» (bzw. **Erneut prüfen**), Druckformat **4x6** gewählt.
- [ ] **Testdruck** («Testdruck (Foto mit Banner)») ausgeführt, Bild kommt vollständig und richtig herum.
- [ ] *Setup → Konfiguration → Upload-Ziel*: **Upload testen**, QR-Code mit dem Handy scannen, Testfoto öffnet sich.
- [ ] *Setup → Warteschlange*: «Wartende Fotos: 0» (sonst **Jetzt senden**).
- [ ] **Banner-Text** für den Anlass gesetzt (max. 28 Zeichen), PIN bekannt.
- [ ] Ein vollständiger **Probedurchlauf** im Retail-Modus (Start → Foto → QR → Drucken).
- [ ] Kiosk-Status kontrolliert (Abschnitt 5).

## 2. Netz-Variante

**Warum so:** Das WCMPlus-Modul ist ein **Druckserver, kein Router**. Sein eigenes WLAN `WCMPLUS-xxx` hat **kein Internet**.
Die App braucht aber beides: Internet (Upload → QR-Code) und den Drucker. Ausserdem verstecken Gast-WLANs oft die Geräte
untereinander («Client-Isolation»), sodass das Tablet den Drucker im Gast-WLAN nicht sieht. Deshalb gilt:

| Variante | Wann | Funktionsweise |
|---|---|---|
| **A. Gast-WLAN mit automatischem WLAN-Wechsel** (z. B. «SH-GAST») – **Standard** | Normalfall: Gast-WLAN mit Internet, in dem der Drucker nicht erreichbar ist. | Tablet bleibt im Gast-WLAN (Upload, QR-Code). Nur zum Drucken wechselt die App kurz ins Drucker-WLAN `WCMPLUS-xxx` und danach zurück. |
| Nur WCMPlus-Hotspot (Notfall) | Kein Internet vorhanden. | Tablet direkt in `WCMPLUS-xxx`: Drucken funktioniert, der **Upload scheitert** ohne Internet. Fotos landen in der Warteschlange (*Setup → Warteschlange → Jetzt senden*, sobald wieder Internet da ist). |

### Variante A einrichten: Gast-WLAN + automatischer Wechsel

1. Tablet **einmal** im Gast-WLAN anmelden (Android-Einstellungen, z. B. über *Setup → WLAN → Android-WLAN-Einstellungen öffnen*).
2. WCMPlus-Hotspot einschalten lassen (Abschnitt 3). *Setup → Drucker → Drucker-WLAN automatisch*:
   - Schalter **«Zum Drucken ins Drucker-WLAN wechseln»** an
   - **Name des Drucker-WLANs** (z. B. `WCMPLUS-aed`), **Passwort des Drucker-WLANs** (das in Abschnitt 3 genannte; der Vorgabewert der App ist die Werkseinstellung `dnp12345`, er muss also angepasst sein), **Adresse des Druckers im Drucker-WLAN** (Standard `192.168.4.1`)
   - **Drucker-WLAN testen**
3. Ablauf pro Foto: Upload + QR-Code im Gast-WLAN → «Drucken» → Verbinden mit dem Drucker-WLAN → Druck → Android verbindet zurück.
   Der Wechsel dauert einige Sekunden und trennt das Internet kurz.
4. Technik: `WifiNetworkSpecifier` (ab Android 10). **Android fragt beim ersten Verbinden um Erlaubnis.**

Die Abfrage ist im Kiosk bedienbar, der Wechsel funktioniert (Rückmeldung aus dem Praxistest). Trotzdem im Probedurchlauf einmal prüfen.

## 3. WCMPlus einrichten

Quelle: Herstelleranleitung «UpReach Fotosystem | Drucker Android» (beschreibt das WCM Plus am DNP QW410; sie ist für UpReach geschrieben,
das Einrichten des Moduls ist aber für unsere App dasselbe). Ist das Modul bereits eingerichtet, **nichts verändern**; die folgenden
Schritte braucht es nur bei Neuaufbau oder wenn sich das WLAN ändert.

1. DNP QW410 an den Strom anschliessen und einschalten. Das WCMPlus-Modul (in der Regel am Drucker angebracht) startet und erstellt ein
   eigenes WLAN **`WCMPLUS-xxx`**.
2. **Nicht über die Fotobox-App**, sondern mit **Laptop oder Smartphone** (mit Browser) in dieses WLAN gehen. Passwort in der Sonnhalde: `$onnh4ld3-F0tob0x*` (von der Sonnhalde gesetzt;
   Werkseinstellung laut Hersteller `dnp12345`, andere Module wie AirCast haben ein anderes). Die Warnung «Kein Internetzugriff» ist normal, weil dieses
   WLAN kein Internet hat; bestätigen.
3. **Portal öffnen:** Der QW410 druckt in der Regel automatisch eine **Testseite mit dem Zugangs-Link** (z. B. `192.168.4.1`).
   Kommt kein Ausdruck, im Browser direkt `http://192.168.4.1` eingeben.
4. **Mit dem lokalen WLAN verbinden:** Menüsymbol (oft drei Balken) → **«Netzwerkeinstellungen»** (oder ähnlich) → **«WLAN-Anschluss»** →
   lokales WLAN wählen → **dessen** Passwort eingeben (nicht das Passwort des Modul-WLANs). Danach ist das Modul (und damit der Drucker) im lokalen WLAN.
5. Das Gerät, mit dem konfiguriert wurde, wieder vom Modul-WLAN trennen und ins normale WLAN zurückholen.
6. **Nur passwortgeschützte WLANs:** Das Modul unterstützt laut Hersteller nur die Verbindung zu einem **verschlüsselten** WLAN; bei einem
   offenen WLAN kann der Drucker nicht angezeigt werden. Der Hersteller empfiehlt ein verschlüsseltes WLAN auch aus Datenschutzgründen.
7. **WLAN-Passwort oder Netz geändert?** Schritte ab 2 mit dem neuen Netz wiederholen.
8. In diesem Projekt sind **WLAN-Anschluss** und **Antennenkonfiguration** (zwei Funkmodule: Hotspot und WLAN-Client gleichzeitig)
   bereits passend eingestellt; **nicht verändern**, solange alles funktioniert. Per DHCP kann sich die Adresse ändern (Beispiele aus der
   Praxis: `.211`, `.164`); dann die neue Adresse in der App unter *Setup → Drucker* eintragen.

> **Unterschied zur Herstelleranleitung:** Der Hersteller verlangt für UpReach, dass Drucker und Fotosystem **im selben WLAN** hängen und
> man den Drucker danach nicht mehr über das WCMPlus-WLAN verbindet. Unsere App löst den Fall «Gast-WLAN versteckt den Drucker» anders:
> Sie wechselt zum Drucken kurz ins Drucker-WLAN (Abschnitt 2).

### Druckwarteschlange und Testdruck

- **Druckwarteschlangen:** Der Druckserver meldet **pro Papierformat eine eigene Warteschlange**, z. B. `QW410-4x6` und `QW410-4x4`.
  Das Format steckt im Namen. In der App: *Setup → Drucker → Druckformat (Warteschlange des Druckers)* → **`QW410-4x6`** wählen
  (4x6 = 10 × 15 cm; «Automatisch (4x6)» wählt dasselbe). Werden keine Warteschlangen angezeigt: Drucker an und im selben WLAN? Dann **Erneut suchen**.
  Im Android-Druckdialog (Ausweg-Knopf) heisst der Drucker «QW410-4x6 @ dnpimage», Papierformat «4 x 6 in.» (= 10 × 15 cm).
- Die App sucht den Drucker per mDNS (`_ipp._tcp`, wie Mopria); im Hotspot gilt die feste Adresse `192.168.4.1`.
- **Testdruck** auslösen. Skalierung, Ausrichtung und «Bild drehen» nur ändern, wenn der Testdruck falsch aussieht.

## 4. Upload-Ziel

*Setup → Konfiguration → Upload-Ziel für QR-Code*, Ziel wählen. In beiden Fällen anschliessend **Upload testen** (Tablet braucht
Internet, der Drucker wird nicht gebraucht): Bei Erfolg erscheinen Link **und** QR-Code zum Testfoto.

- **Nextcloud:** Adresse, Benutzername (Konto-ID), App-Passwort, Ordner. Vorbereitung in Nextcloud, Fehlermeldungen des Tests (`1/4` … `4/4`):
  [nextcloud.md](nextcloud.md).
- **SharePoint (Power Automate):** Flow bauen und die HTTP-POST-URL als «Upload-Link» eintragen. Anleitung, Testskript
  (`tools/test-flow.sh`) und Lizenzhinweis (Premium-Connector): [power-automate-flow.md](power-automate-flow.md).

Die Zugangsdaten gehören **nur ins Tablet**, nicht ins Repository und nicht in Chats/Mails.

## 5. Kiosk / Retail-Modus

- **Start:** Unten im Setup **Start**. Ab dann Vollbild; nach einem Neustart der App geht es direkt im Retail-Modus weiter.
- **Beenden:** langer Druck auf das **Logo** (Startbildschirm) bzw. den **Banner**, dann **PIN** (Standard `1234`).
  Im Setup beendet ⏻ oben die App (ebenfalls nach PIN).
- **PIN ändern:** *Setup → Konfiguration → Sicherheit* («Neue PIN (mind. 4 Zeichen)» → **PIN speichern**). Standard-PIN vor dem Einsatz ändern.
- **Device Owner (volle Sperre):** einmalig per ADB, nur auf einem Gerät **ohne Konten** und **ohne anderen Device Owner** (z. B. UpReach):
  `adb shell dpm set-device-owner ch.sonnhalde.fotobox/.kiosk.AdminReceiver`. Danach startet der Kiosk ohne Rückfrage und ohne Ausstiegsgeste.
- **Ohne Device Owner:** Android «Bildschirm fixieren»: einmalige Rückfrage, Ausstieg per Systemgeste (gilt nicht als sichere Sperre
  gegen Gäste, die die Geste kennen).
- Welcher Fall aktiv ist, zeigt *Setup → Konfiguration → Sicherheit*: «Kiosk: Device Owner (volle Sperre)» oder «kein Device Owner – nur «Bildschirm fixieren»».

## 6. Fehlersuche

| Symptom | Wahrscheinliche Ursache | Abhilfe |
|---|---|---|
| Kein QR-Code, «Nochmals versuchen» | Tablet hat kein Internet (z. B. noch im Drucker-Hotspot), oder Upload-Ziel falsch eingerichtet | Internet-WLAN prüfen; *Setup → Konfiguration → Upload testen* (Meldung beachten); Foto wartet sonst in der **Warteschlange** → *Setup → Warteschlange → Jetzt senden* |
| Upload-Test: «1/4 Verbindung» | Kein Internet oder Nextcloud-Adresse falsch / keine Nextcloud | Netz prüfen; nur Hauptadresse ohne `/index.php` oder `/login` |
| Anmeldung abgelehnt (**HTTP 401**) | Benutzername (Konto-ID, nicht Anzeigename/E-Mail) oder Passwort falsch; bei Zwei-Faktor/SSO geht das normale Passwort nicht | **App-Passwort** neu erstellen und eintragen ([nextcloud.md](nextcloud.md)) |
| Upload «507» | Speicherplatz (Quota) des Nextcloud-Benutzers voll | Fotos löschen oder Quota erhöhen |
| «4/4 Freigabe-Link» schlägt fehl | «Teilen per Link erlauben» ist in Nextcloud aus | In der Nextcloud-Administration aktivieren |
| SharePoint: Flow antwortet nicht / kein Link | Flow-URL falsch oder Flow fehlerhaft; «Jeder»-Links nicht erlaubt | Ausführungsverlauf im Flow, `tools/test-flow.sh` ([power-automate-flow.md](power-automate-flow.md)) |
| Drucker nicht gefunden / «WCMPlus: nicht erreichbar» | Drucker/WCMPlus aus; Tablet nicht im selben Netz; Gast-WLAN versteckt den Drucker (Client-Isolation); Adresse nach DHCP-Wechsel geändert | Einschalten, Netz prüfen; Variante A (WLAN-Wechsel) nutzen; neue Adresse eintragen; **Erneut prüfen** / **Erneut suchen** |
| Keine Druckwarteschlangen in der Liste | Drucker nicht erreichbar | wie oben; Hotspot-Adresse `192.168.4.1` testen |
| Falsches Format / Bild abgeschnitten oder klein | Falsche Warteschlange (z. B. 4x4) oder Skalierung | *Setup → Drucker → Druckformat* auf **QW410-4x6**; ggf. Skalierung «Einpassen» / «Füllen (zuschneiden)», dann Testdruck |
| Bild gedreht gedruckt | Ausrichtung/Drehung | «Ausrichtung» bzw. «Bild drehen» anpassen, Testdruck |
| Direktdruck schlägt fehl | Drucker im Moment nicht erreichbar | Knopf «Über Android-Druckdialog drucken» (4 x 6 in.) als Ausweg |
| Kamera schwarz | **Berechtigung der App fehlt** (so aufgetreten) | In den Android-Einstellungen die Kamera-Berechtigung der App erteilen; App neu starten |
| PIN vergessen | – | Ein PIN-Reset ist in der App **nicht vorgesehen**. Als Ausweg bleibt die Neuinstallation der App (löscht die Einstellungen) |
| Update lässt sich nicht installieren | Alte Version war anders signiert | Alte App einmalig deinstallieren |

## 7. Datenschutz

- **Fotos zeigen Personen.** Gäste vor dem Foto informieren (Hinweis am Gerät). Bei Bewohnenden und Mitarbeitenden die internen Vorgaben
  der Sonnhalde (Einwilligung/Persönlichkeitsschutz) beachten.
- **Freigabe-Links** sind öffentlich: Wer den Link/QR-Code hat, sieht das Foto. Nextcloud: Ablaufdatum für Freigaben
  erzwingen; SharePoint: «Jeder»-Links nur wenn gewollt.
- **Wohin gehen die Fotos:** Im regulären Betrieb über Power Automate in SharePoint (Microsoft 365, siehe [Microsoft: Sicherheit, Datenschutz und Compliance](https://learn.microsoft.com/de-de/microsoft-365/business-premium/m365bp-security-privacy-compliance?view=o365-worldwide)); in der Test-/Beta-Version in eine private Nextcloud.
- **Aufbewahrung:** Fotos werden in der Regel **nach einem Monat gelöscht**, ausser jemand fragt nach. Die Löschung ist eine Vorgabe des Betriebs; die App löscht selbst nichts im Upload-Ordner, das muss im Ordner bzw. per Aufbewahrungsregel geschehen.
- **Auf dem Tablet:** Die App sendet die Fotos hoch; nicht hochgeladene Fotos liegen in der Warteschlange und können mit
  *Warteschlange leeren* entfernt werden.
- **Zugangsdaten** (App-Passwort, Flow-URL, WLAN-Passwort, PIN) liegen **unverschlüsselt** im App-Speicher. Eigenen Nextcloud-Benutzer
  mit minimalen Rechten verwenden; bei Verlust des Tablets das App-Passwort widerrufen bzw. die Flow-URL erneuern.

## 8. Wartung

- **App aktualisieren:** Neue APK von der Release-Seite laden (Releases entstehen per Knopfdruck, siehe [RELEASE.md](RELEASE.md)) und über die installierte App installieren (Einstellungen bleiben).
  Ältere, anders signierte Builds vorher deinstallieren. Danach Version im Setup prüfen und die Checkliste (Abschnitt 1) durchgehen.
- **Banner-Text:** *Setup → Konfiguration → Foto → Banner unten* (max. 28 Zeichen, Vorschau darunter).
- **Startbildschirm:** *Setup → Konfiguration → Retail-Modus → Startbildschirm*: Überschrift, Text des Start-Knopfs, **Hintergrundbild wählen** / **Entfernen**.
- **Logo und Standard-Hintergrund:** Dateien `sonnhalde_logo.png` (Banner) und `start_background.jpg` in `app/src/main/res/drawable-nodpi/`
  ersetzen und neu bauen (Entwickelnde); Austausch der Logo-Datei genügt.
- **PIN ändern:** *Setup → Konfiguration → Sicherheit* (Abschnitt 5).
- **Timer / Auto-Druck:** *Setup → Konfiguration → Ablauf*: Timer 3/5/10 s; «Foto automatisch drucken (1 Abzug)».
