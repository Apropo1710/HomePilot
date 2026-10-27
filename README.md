# HomePilot – Räume und Geräteverwaltung

Android-App auf Basis von Kotlin und Jetpack Compose zur Steuerung und Verwaltung von ioBroker-Geräten.

## Funktionen

- Verbindung zu ioBroker über die Simple API auf Port 8087.
- Verbindungstest ohne Abfrage des Waschmaschinenstatus.
- Manuelle Gerätesuche über die Schaltfläche in den Einstellungen.
- Erkennung von Shelly- und Hue-Schaltzuständen mit Filtern, damit nicht jeder boolesche Datenpunkt als Schalter erscheint.
- Bei der Geräteerkennung werden lesbare Namen aus ioBroker-Objekten und übergeordneten Geräten/Kanälen bevorzugt.
- Der bekannte Wasser-Plug wird bei der Suche berücksichtigt und standardmäßig dem Wohnzimmer zugeordnet; die Leistungsaufnahme kann ebenfalls abgefragt werden.
- Räume in der App anlegen, umbenennen und löschen.
- Geräte umbenennen, Räumen zuordnen und zwischen Räumen verschieben.
- Geräte in HomePilot ausblenden, ohne sie in ioBroker zu löschen.
- Räume, Gerätenamen und Raumzuordnungen werden lokal auf dem Gerät gespeichert.
- Ausgeblendete Geräte können über den Bereich „Ausgeblendete Geräte“ wieder hinzugefügt werden.
- Entfernen von Geräten und Löschen von Räumen werden bestätigt.
- Die Waschmaschine kann als Gerät verwaltet werden, ist aber ausdrücklich nicht das Testgerät.

## Verbindung und Sicherheit

Die App verwendet die ioBroker Simple API. In der aktuellen Einrichtung läuft diese über Port 8087. Prüfe die Absicherung deiner ioBroker-Instanz und gib die Simple API nicht ungeschützt im Internet frei.

## APK bauen und herunterladen

GitHub Actions baut nach Änderungen auf dem Branch `main automatisch eine Debug-APK. Der Workflow kann auch manuell gestartet werden:

1. Im GitHub-Repository den Bereich **Actions** öffnen.
2. **HomePilot Android APK** auswählen.
3. **Run workflow** starten.
4. Nach erfolgreichem Lauf den Eintrag **HomePilot-debug-apk** unter **Artifacts** herunterladen.

Die Debug-APK ist für Tests vorgesehen und keine Play-Store-signierte Release-APK.

## Letzte Änderung

- **Gerätenamen:** Die automatische Erkennung fragt nun ioBroker-Objekte einschließlich übergeordneter Objekte ab und versucht, aussagekräftige Geräte-/Kanalnamen statt generischer Zustandsnamen wie „Switch“ zu verwenden.

