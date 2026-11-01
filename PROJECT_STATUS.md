<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: b59a9e567e7b60625377bc7b7ef8835099aec374
- App-Version / versionCode: **1.29.32 / 90**
- Build-Zeit: 2026-11-01 06:13:19 UTC
- APK-Dateiname: **HomePilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: **HomePilot-release-apk**, ID 13073130961, 11077807 Bytes
- APK-SHA-256: 5ea26083141153931811caa982b0f72978db797fd75c484247735011eec3e740
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

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

Die verbindlichen aktuellen Build-, Signatur-, Artefakt- und Release-Daten stehen im automatisch gepflegten Produktionsstatus am Dokumentanfang. Ältere Einzelstände werden nicht als aktueller Produktionsstand fortgeführt.

## Build und Veröffentlichung

Die verbindlichen aktuellen Build-, Signatur-, Artefakt- und Release-Daten stehen im automatisch gepflegten Produktionsstatus am Dokumentanfang. Ältere Einzelstände werden nicht als aktueller Produktionsstand fortgeführt.

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
- 31.10.2026: Verbindlich festgelegt, dass die weitere Entwicklung direkt auf `main` für die Hauptversion erfolgt; keine neuen Test-Branches oder separaten Testversionen.\n