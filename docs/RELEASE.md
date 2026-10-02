# Release veröffentlichen

Ein Release mit installierbarer APK entsteht per Knopfdruck in GitHub (Workflow `.github/workflows/release.yml`).
Die APK muss nicht mehr von Hand hochgeladen werden.

## Einmalige Einstellung in GitHub

*Settings → Actions → General → Workflow permissions* → **Read and write permissions** wählen und speichern.
Ohne das kann der Workflow kein Release anlegen. (Der Workflow verlangt `contents: write` selbst; die Organisation/das Repo darf es aber nicht blockieren.)

## Release starten

1. Auf GitHub: **Actions → Release → Run workflow**.
2. Eingaben:
   - **tag**: Versions-Tag, Format `vX.Y.Z`, z. B. `v0.2.0` (auch `v0.2.0-rc1` ist erlaubt).
   - **ref**: Branch oder Commit, der gebaut wird. Leer lassen = `main`.
3. «Run workflow» drücken und warten (ca. 3–5 Minuten).

## Was danach passiert

- Der Workflow bricht ab, wenn der Tag (oder ein Release mit diesem Namen) **schon existiert**.
- Er baut die APK aus dem angegebenen Stand (Java 17, `./gradlew assembleDebug`, festes Zertifikat `app/debug.keystore`).
- Versionsanzeige: Tag `v0.2.0` → `versionName` «0.2.0», in der App «Version 0.2.0 (Commit)». Der Commit ist der gebaute Stand.
- `versionCode` = letzte Lauf-Nummer von `build.yml` + Lauf-Nummer des Release-Workflows. So ist sie höher als bei allen bisherigen Builds
  und Android erlaubt das Update über eine installierte Dev-/Vorversion. Spätere Dev-Builds können niedriger sein als ein Release:
  dann lässt sich der Dev-Build nicht über das Release installieren (vorher deinstallieren).
- Geprüft werden Signatur (`apksigner verify`), Paketname `ch.sonnhalde.fotobox`, `versionName` = Tag und Mindest-Android (API 29).
- Veröffentlicht wird ein normales Release (kein Pre-release, als «latest» markiert) mit `Sonnhalde-Fotobox-<tag>.apk`,
  der SHA-256-Prüfsumme im Text und automatisch erzeugten Release-Notes. Der Tag zeigt auf den gebauten Commit.
- `build.yml` und das automatische Vorab-Release `dev-build` laufen unverändert weiter.

## Versionsschema

Vorschlag: `0.x.y` bis zur Freigabe (neue Funktionen → `x` erhöhen, Fehlerbehebungen → `y`), danach `1.0.0`.
Das erste echte Release war `v0.1.36`; das nächste also z. B. `v0.2.0`.

## Release zurückziehen

1. *Releases* → das Release öffnen → **Delete** (oder «Edit» → «Set as a pre-release», wenn es nur nicht mehr «latest» sein soll).
2. Der Tag bleibt nach dem Löschen des Releases bestehen und blockiert den Namen. Unter *Tags* ebenfalls löschen,
   sonst muss ein neuer Tag (z. B. `v0.2.1`) verwendet werden. Ein bereits verwendeter Tag sollte nicht wiederverwendet werden.
3. Bereits auf dem Tablet installierte Version bleibt unverändert; ältere APK lässt sich nur nach Deinstallation einspielen
   (Android verhindert Downgrades).

## Offene Frage

Die APK ist mit dem festen **Debug-Zertifikat** signiert (Updates über die installierte App möglich, aber nicht geheim).
Ob später ein eigenes Release-Zertifikat verwendet wird, entscheidet die Nutzerperson. Ein Wechsel erfordert Deinstallation auf allen Tablets.

## Teststand

Der Workflow wurde lokal nur auf gültiges YAML geprüft (kein `actionlint` verfügbar) und **noch nie ausgeführt**.
Der erste echte Lauf (durch die Nutzerperson) ist der Test.
