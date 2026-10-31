# HomePilot – Räume und Geräteverwaltung

Android-App auf Basis von Kotlin und Jetpack Compose zur Steuerung und Verwaltung von ioBroker-Geräten.

## Verbindung zu ioBroker

HomePilot kommuniziert mit ioBroker ausschließlich über den WebSocket-Adapter `ws.0`.

- Standardadresse: `ws://<ioBroker-IP>:8084`
- Verschlüsselte Verbindungen über `wss://` werden ebenfalls unterstützt.
- Die App verwendet weder die Simple API noch HTTP-Endpunkte des ioBroker-Systems.
- Für eine lokale Verbindung mit `ws://` ist Android-Cleartext-Netzwerkverkehr freigegeben. Die Verbindung sollte deshalb auf das vertrauenswürdige Heimnetz beschränkt bleiben und nicht ungeschützt ins Internet freigegeben werden.

## Funktionen

- Verbindungstest und Statusanzeige für ioBroker über WebSocket.
- Automatische Erkennung unterstützter Shelly- und Hue-Geräte.
- Manuelle Suche nach weiteren unterstützten Geräten, unter anderem Tuya.
- Lesbare Gerätenamen aus ioBroker-Objekten und übergeordneten Geräten/Kanälen.
- Räume in der App anlegen, umbenennen und löschen.
- Geräte umbenennen, Räumen zuordnen und zwischen Räumen verschieben.
- Geräte in HomePilot ausblenden, ohne sie in ioBroker zu löschen.
- Räume, Gerätenamen und Raumzuordnungen werden lokal auf dem Gerät gespeichert.
- Ausgeblendete Geräte können später wieder eingeblendet werden.
- Die Waschmaschine kann als Gerät verwaltet werden, wird aber nicht als Testgerät verwendet.

## Bauen und Veröffentlichen

GitHub Actions erstellt nach Änderungen auf dem Branch `main` eine installierbare Release-APK und stellt sie als Workflow-Artefakt sowie im GitHub-Release bereit.

