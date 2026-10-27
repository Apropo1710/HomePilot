# HomePilot v0.6 – Räume und Geräteverwaltung

Android-Studio-Projekt (Kotlin / Jetpack Compose).

## Enthalten
- Simple-API-Anbindung an ioBroker über Port 8087.
- Verbindungsmeldung ohne Waschmaschinenstatus.
- Erneute Gerätesuche über die Schaltfläche in Einstellungen.
- Geräteerkennung für Shelly- und Hue-Schaltzustände mit engerer Filterung, damit allgemeine boolesche Datenpunkte nicht alle als Schalter erscheinen.
- Bekannter Wasser-Plug wird bei der Suche gezielt erhalten und standardmäßig dem Wohnzimmer zugeordnet; Leistungsaufnahme wird weiter abgefragt.
- Räume in der App anlegen, umbenennen und löschen.
- Geräte umbenennen und Räumen zuordnen bzw. verschieben.
- Geräte aus HomePilot ausblenden, ohne sie in ioBroker zu löschen.
- Räume, Namen und Zuordnungen werden lokal auf dem Gerät gespeichert.
- Waschmaschinen-Schalter ist verfügbar, wird aber nicht als Testgerät verwendet.

## Öffnen
Den Ordner `HomePilot` in Android Studio öffnen, Gradle synchronisieren und auf dem Gerät starten.

## Hinweis zum Teststatus
Die ZIP-Datei und Quelltextstruktur wurden geprüft. Ein vollständiger Android/Gradle-Build konnte in der verfügbaren Umgebung nicht ausgeführt werden; bitte vor der Installation in Android Studio synchronisieren und kompilieren. Die Simple API ist in der aktuell eingerichteten ioBroker-Instanz ungeschützt und verwendet Admin-Rechte; nicht ins Internet freigeben.


## Version 0.8 – ausgeblendete Geräte und Bestätigungen

- Aus HomePilot entfernte Geräte werden mit ihren wichtigsten Gerätedaten lokal gespeichert.
- In Räume & Geräte gibt es den Bereich „Ausgeblendete Geräte“ mit „Wieder hinzufügen“.
- Entfernen und Raum löschen benötigen eine Bestätigung.
- Entfernte Geräte bleiben in ioBroker unverändert.
- Bereits bekannte Geräte wie Wasser und Waschmaschine werden bei der Suche nicht gegen eine explizite Ausblendung wieder eingeblendet.

Hinweis: Ein vollständiger Android-Gradle-Build wurde in dieser Umgebung nicht ausgeführt.


## Automatischer APK-Build über GitHub Actions

Dieses Projekt enthält `.github/workflows/android.yml`. Nach dem einmaligen Hochladen des Projektordners `HomePilot` in ein GitHub-Repository baut GitHub bei Pushes auf `main`/`master` automatisch eine Debug-APK. Ein manueller Start ist ebenfalls möglich: GitHub → Actions → HomePilot Android APK → Run workflow.

Die APK findest du nach erfolgreichem Lauf im Abschnitt **Artifacts** des jeweiligen Workflow-Laufs (`HomePilot-debug-apk`). Das ist zunächst eine Debug-APK, keine Play-Store-signierte Release-APK. Der Workflow wurde strukturell geprüft, aber nicht tatsächlich in GitHub ausgeführt; der erste Lauf muss bestätigen, dass alle Abhängigkeiten erfolgreich gebaut werden.
