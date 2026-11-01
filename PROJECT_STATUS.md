<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: a92a3be47a6e1eb0f8b91e243e204517753a519b
- App-Version / versionCode: **1.29.32 / 90**
- Build-Zeit: 2026-11-01 20:01:50 UTC
- APK-Dateiname: **HomePilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: **HomePilot-release-apk**, ID 13098866275, 11077807 Bytes
- APK-SHA-256: 38fd7e5f18dcc7c738df3095467c09d15aed0b98cf5ad69cd774ffc3e6b50b9c
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

## Neue geplante Geräteintegration: Roidmi EVE Plus

Beschluss vom 01.11.2026: Der Roidmi EVE Plus wird Bestandteil der nächsten größeren HomePilot-Version. Die Integration erfolgt über das eigenständige ioBroker-Adapterprojekt **RoidmiPilot** und nicht über eine direkte Xiaomi-Cloud- oder miIO-Implementierung in HomePilot.

- Projektname: `RoidmiPilot`
- GitHub-Projekt: [Apropo1710/RoidmiPilot](https://github.com/Apropo1710/RoidmiPilot)
- ioBroker-Adapter-Repository: [Apropo1710/ioBroker.roidmi](https://github.com/Apropo1710/ioBroker.roidmi)
- Zielgerät: Roidmi EVE Plus
- HomePilot-Geräteklasse: `robot_vacuum`

### Geplanter Funktionsumfang

1. Eigene Roboter-Ansicht mit Status, Akkustand, Ladezustand und Reinigungsfortschritt, soweit die Adapter-Datenpunkte diese Werte zuverlässig liefern.
2. Steuerung für Start, Pause/Fortsetzen und Rückkehr zur Ladestation.
3. Saug- und Wischfunktionen sowie Absaugstation, sofern die entsprechenden Adapterbefehle implementiert und getestet sind.
4. Später Raum- und Zonenreinigung, Kartenansicht und Reinigungsverlauf, soweit Modell und Adapter dies unterstützen.
5. Wartungsinformationen wie Filter- und Bürstenzustand, sofern verfügbar.

### Umsetzungsregeln

- HomePilot bleibt ausschließlich Client des ioBroker-WebSocket-Adapters `ws.0`; keine direkte Xiaomi-Cloud-Anmeldung und keine direkte Roboterkommunikation in HomePilot.
- RoidmiPilot und HomePilot bleiben getrennte Projekte mit getrennten Repositories und Builds.
- HomePilot wird zunächst auf eine klar abgegrenzte Geräteklasse und eine robuste, datenpunktbasierte Integration vorbereitet.
- Nicht vorhandene, unfertige oder nicht praktisch getestete Adapterfunktionen dürfen in HomePilot weder als verfügbar angezeigt noch als funktionierend ausgegeben werden.
- Die Integration wird mit den tatsächlich vom Adapter bereitgestellten Datenpunkten umgesetzt; keine vorzeitige Festlegung auf unbestätigte IDs oder Befehlsnamen.
- Die Roidmi-Integration wird mit der geplanten Raumverwaltung, Geräteklassifizierung und Dashboard-Überarbeitung in einer gemeinsamen größeren HomePilot-Version gebündelt. Keine separate Zwischenveröffentlichung nur für den Roboter.
- Vor jeder Codeänderung den aktuellen Stand von `main` lesen; die normale HomePilot-Weiterentwicklung erfolgt gemäß Projektentscheidung direkt auf `main`.

### Vorgehensreihenfolge

1. Vorhandene HomePilot-Geräte- und Raumarchitektur prüfen und eine möglichst kleine, erweiterbare Einbindung für `robot_vacuum` entwerfen.
2. Parallel den tatsächlichen Datenpunkt- und Befehlsumfang von RoidmiPilot anhand des Adaptercodes und später anhand realer ioBroker-Objekte verifizieren.
3. Roboter-Dashboard und Statusanzeige mit tatsächlich verfügbaren Datenpunkten integrieren.
4. Steuerbefehle einzeln anbinden und am Roidmi EVE Plus praktisch testen.
5. Erweiterte Funktionen wie Räume/Zonen, Karte, Verlauf und Wartung erst ergänzen, wenn Adapter und Gerät sie zuverlässig bereitstellen.\n