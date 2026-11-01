<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Zuletzt erfolgreicher Workflow: [Run #183](https://github.com/Apropo1710/HomePilot/actions/runs/41278509623) – success
- Gebauter Quellcommit: 
- App-Version / versionCode: **1.29.32 / 90**
- Build-Zeit: 2026-11-01 05:57:16 UTC
- APK-Signaturprüfung: erfolgreich (CI-Schritt apksigner verify --print-certs)
- Build-Artefakt: , ID 13072686907, 11077802 Bytes
- APK-SHA-256: fff53735dc7edf30ed1d6deb4965dfca95bf9081a91ec2c8cfdbe86276aa6651
- Release: wird nach diesem Dokumentationscommit mit Git-Tag latest synchronisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- ChatGPT-Bibliotheksakte: wird durch diesen Workflow nicht verändert; sie bleibt eine separate Langzeit-/Historienakte.

<!-- AUTO-PRODUCTION-STATUS:END -->

# HomePilot – Projektstatus

Stand: 31.10.2026  
Repository: [Apropo1710/HomePilot](https://github.com/Apropo1710/HomePilot)  
Produktions- und Entwicklungsbranch: `main`

## Quelle der Wahrheit

Der jeweils aktuelle Code auf GitHub ist maßgeblich. Vor jeder Änderung muss der aktuelle Stand des vorgesehenen Branches gelesen werden. Diese Datei dokumentiert Entscheidungen und Arbeitsstand, ersetzt aber niemals die Prüfung des aktuellen Quellcodes.

## Verbindliche Entwicklungsstrategie

**Die weitere HomePilot-Entwicklung erfolgt direkt auf `main` und ist immer für die Hauptversion bestimmt.**

- Keine separaten Testversionen und keine neuen Test-Branches für die normale Weiterentwicklung.
- Änderungen werden nach Prüfung direkt auf `main` umgesetzt.
- Die bisherigen Test-Branches stammen ausschließlich aus den abgeschlossenen GitHub-Zugriffstests und sind historische Teststände. Sie sind keine Grundlage für die weitere Entwicklung und werden nicht in die Hauptversion übernommen.
- Vor jeder Änderung den aktuellen Stand von `main` lesen.
- Änderungen gezielt umsetzen, den Diff prüfen und anschließend den durch den Push ausgelösten Produktionsbuild kontrollieren.
- Eine neue APK wird erst nach erfolgreichem Build und verifizierter Verfügbarkeit als Download angeboten.

## Aktueller Produktionsstand

- Branch: `main`
- Zuletzt vor dieser Dokumentationspflege bekannter App-Code-Commit: `c392f470c671c40bf4ef882e94e3f903ed79ea7a`
- Commit-Nachricht: `Fix MOVA maintenance status labels`
- App-Version: `1.29.31`
- versionCode: `89`
- Android: compileSdk 35, targetSdk 35, minSdk 26
- Java/Kotlin-Ziel: Java 17
- GitHub Actions Workflow: `.github/workflows/android.yml`

Der genannte App-Code-Commit korrigiert die Anzeige der MOVA-Wartungswerte (Messerzustand und Gerätewartung), sodass Kotlin-String-Interpolation korrekt ausgewertet wird. Spätere Commits können ausschließlich die Projektdokumentation betreffen; vor jeder Entwicklungsarbeit ist der tatsächliche aktuelle Stand von `main` erneut zu lesen.

## Build und Veröffentlichung

Der Produktionsworkflow wird bei Pushes auf `main` beziehungsweise `master` und manuell gestartet.

Der Workflow:
1. richtet Java 17, Gradle 8.9 und Android SDK 35 ein,
2. stellt den stabilen Signaturschlüssel aus dem GitHub Secret `DEBUG_KEYSTORE_BASE64` bereit,
3. baut die Release-APK,
4. prüft Paket-ID, Versionscode, Versionsname und Signatur,
5. lädt das APK-Artefakt hoch,
6. veröffentlicht beziehungsweise aktualisiert den GitHub-Release-Tag `latest` mit `HomePilot.apk`.

Downloadlinks erst nennen, wenn der konkrete Build beziehungsweise Release erfolgreich geprüft wurde. Ein erfolgreicher Build ohne App-Code-Änderung bedeutet nicht automatisch eine neue App-Version.

## Historische Branches aus den Zugriffstests

- `test-project-github-check`
- `test-project-github-write-2026-10-31`

Diese Branches dienten ausschließlich den abgeschlossenen GitHub-Zugriffstests. Sie enthalten Teststände und sind nicht Teil der zukünftigen HomePilot-Entwicklung. Keine Übernahme in `main`.

## Umgesetzte beziehungsweise zuletzt bearbeitete Funktionen

- MOVA-Wartungsbereich mit Anzeige des Messerzustands und des Gerätewartungszustands.
- Im MOVA-Wartungsbereich ist eine Funktion vorgesehen, die den in der originalen MOVA-App gespeicherten Wartungspunkt anfährt.
- Die Beschriftungen der MOVA-Wartungswerte wurden im letzten bekannten App-Code-Commit korrigiert.

Hinweis: Die praktische Funktion des Anfahrens des Wartungspunkts war zum Stand dieser Dokumentation noch nicht als erfolgreich getestet bestätigt.

- Der untere Navigationspunkt „Zuhause“ soll unabhängig vom aktuellen Bereich immer zur Startseite führen. Den tatsächlichen Implementierungsstand bei der nächsten entsprechenden Arbeit im Code prüfen.

## Geplante Weiterentwicklung

Die folgenden Funktionen sollen sinnvoll gemeinsam in einer passenden Version umgesetzt werden:

- Raumbasierte Navigation: Räume antippen und die zugehörigen Geräte und Sensoren anzeigen.
- Räume in der App erstellen, umbenennen und löschen.
- Geräte umbenennen und löschen.
- Geräte Räumen zuweisen und zwischen Räumen verschieben.
- Neue ioBroker-Geräte automatisch erkennen oder über einen manuellen Scan suchen.
- Neu erkannte Geräte zur Klassifizierung und Raumzuweisung anzeigen.
- Geräteklassifizierung und ein übersichtlicheres Dashboard integrieren.

Weitere Entscheidung:
- Die Waschmaschine kann in einer späteren Version schaltbar werden, soll aber kein Testgerät sein und keine separate Veröffentlichung auslösen.

## Technische Entscheidungen und Einschränkungen

- SENEC beziehungsweise der zugehörige Energiefluss-Adapter wird nicht als Datenquelle eingeplant, da häufige Abfragen den PC überlasten können.
- Der ioBroker-Adapter `mhi-wfrac` ist noch nicht eingerichtet und soll erst nach Installation der Klimaanlage berücksichtigt werden.
- Hue-Bewegungsmelder im Flur werden zunächst nicht für die neue Smart-Home-App eingeplant.

## Arbeitsregeln

- Vor Änderungen aktuellen Code und Branch-Stand auf `main` lesen.
- Änderungen klein, gezielt und nachvollziehbar halten.
- Vor dem Commit beziehungsweise Push den Diff prüfen.
- Keine Test-Branches oder Testveröffentlichungen für die normale Weiterentwicklung anlegen.
- Nach einer Änderung den zugehörigen GitHub-Actions-Produktionsbuild kontrollieren.
- Keine Änderungen, Builds oder Tests als erledigt darstellen, solange sie nicht tatsächlich durchgeführt und verifiziert wurden.
- Wichtige neue Entscheidungen, bestätigte Tests, Versionen und bekannte Probleme zeitnah in dieser Datei aktualisieren.
- HomePilot und KlimaPilot strikt getrennt behandeln.

## Änderungsverlauf dieser Dokumentation

- 31.10.2026: Erste zentrale Projektdokumentation auf Basis des geprüften Stands von `main` und der bisher festgehaltenen Projektentscheidungen angelegt.
- 31.10.2026: Verbindlich festgelegt, dass die weitere Entwicklung direkt auf `main` für die Hauptversion erfolgt; keine neuen Test-Branches oder separaten Testversionen.
