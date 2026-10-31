# HomePilot – Projektstatus

Stand: 31.10.2026  
Repository: [Apropo1710/HomePilot](https://github.com/Apropo1710/HomePilot)  
Produktionsbranch: `main`

## Quelle der Wahrheit

Der jeweils aktuelle Code auf GitHub ist maßgeblich. Vor jeder Änderung muss der aktuelle Stand des vorgesehenen Branches gelesen werden. Diese Datei dokumentiert Entscheidungen und Arbeitsstand, ersetzt aber niemals die Prüfung des aktuellen Quellcodes.

## Aktueller Produktionsstand

- Branch: `main`
- Letzter bekannter Commit: `c392f470c671c40bf4ef882e94e3f903ed79ea7a`
- Commit-Nachricht: `Fix MOVA maintenance status labels`
- App-Version: `1.29.31`
- versionCode: `89`
- Android: compileSdk 35, targetSdk 35, minSdk 26
- Java/Kotlin-Ziel: Java 17
- GitHub Actions Workflow: `.github/workflows/android.yml`

Der letzte bekannte Commit korrigiert die Anzeige der MOVA-Wartungswerte (Messerzustand und Gerätewartung), sodass Kotlin-String-Interpolation korrekt ausgewertet wird.

## Build und Veröffentlichung

Der Produktionsworkflow wird bei Pushes auf `main` beziehungsweise `master` und manuell gestartet.

Der Workflow:
1. richtet Java 17, Gradle 8.9 und Android SDK 35 ein,
2. stellt den stabilen Signaturschlüssel aus dem GitHub Secret `DEBUG_KEYSTORE_BASE64` bereit,
3. baut die Release-APK,
4. prüft Paket-ID, Versionscode, Versionsname und Signatur,
5. lädt das APK-Artefakt hoch,
6. veröffentlicht beziehungsweise aktualisiert den GitHub-Release-Tag `latest` mit `HomePilot.apk`.

Downloadlinks erst nennen, wenn der konkrete Build beziehungsweise Release erfolgreich geprüft wurde. Test-Workflow-Ergebnisse sind kein Nachweis für einen erfolgreichen Produktionsbuild.

## Branch-Sicherheit

Bekannte Branches aus den GitHub-Zugriffstests:
- `test-project-github-check`
- `test-project-github-write-2026-10-31`

Diese Branches enthalten Teständerungen und dürfen nicht ungeprüft in `main` übernommen werden. `main` bleibt der Produktionsbranch. Vor jedem Merge ist der vollständige Unterschied zu `main` zu prüfen.

## Umgesetzte beziehungsweise zuletzt bearbeitete Funktionen

- MOVA-Wartungsbereich mit Anzeige des Messerzustands und des Gerätewartungszustands.
- Im MOVA-Wartungsbereich ist eine Funktion vorgesehen, die den in der originalen MOVA-App gespeicherten Wartungspunkt anfährt.
- Die Beschriftungen der MOVA-Wartungswerte wurden im letzten bekannten Commit korrigiert.

Hinweis: Die praktische Funktion des Anfahrens des Wartungspunkts war zum Stand dieser Dokumentation noch nicht als erfolgreich getestet bestätigt.

- Der untere Navigationspunkt „Zuhause“ soll unabhängig vom aktuellen Bereich immer zur Startseite führen. Den tatsächlichen Implementierungsstand bei der nächsten entsprechenden Arbeit im Code prüfen.

## Geplante Weiterentwicklung

Die folgenden Funktionen sollen sinnvoll gemeinsam in einer passenden Version umgesetzt werden, nicht als einzelne Zwischenveröffentlichungen:

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

- Vor Änderungen aktuellen Code und Branch-Stand lesen.
- Änderungen klein, gezielt und nachvollziehbar halten.
- Vor dem Commit Diff prüfen.
- Testbranches und Testdateien nicht ungeprüft in Produktion übernehmen.
- Nach einer Produktionsänderung genau den vorgesehenen GitHub-Actions-Build kontrollieren.
- Keine Änderungen, Builds oder Tests als erledigt darstellen, solange sie nicht tatsächlich durchgeführt und verifiziert wurden.
- Wichtige neue Entscheidungen, bestätigte Tests, Versionen und bekannte Probleme zeitnah in dieser Datei aktualisieren.

## Änderungsverlauf dieser Dokumentation

- 31.10.2026: Erste zentrale Projektdokumentation auf Basis des geprüften Stands von `main` und der bisher festgehaltenen Projektentscheidungen angelegt.
