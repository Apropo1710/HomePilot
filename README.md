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
- Räume, Gerätenamen, Raumzuordnungen, ausgeblendete Geräte und übernommene Hue-Lampen werden lokal gespeichert und mit ioBroker sowie gekoppelten Smartphones abgeglichen.
- HomePilot-Benutzerkonten (Administrator/Benutzer) mit lokaler Anmeldung; Passwörter nur als PBKDF2-Hash. Konten werden automatisch aus ioBroker übernommen oder per QR-Code von einem eingerichteten Smartphone.
- Persönliche Benutzerprofile (Favoriten, Kachel-Reihenfolgen, Hintergrund-Timeout) pro Konto, ebenfalls abgeglichen.
- Ausgeblendete Geräte können später wieder eingeblendet werden.
- Die Waschmaschine kann als Gerät verwaltet werden, wird aber nicht als Testgerät verwendet.
- BambuLab-3D-Drucker (Adapter `bambulab-connect.0`) als reine Statusanzeige ohne Steuerung: Startseiten-Kachel und Detailseite mit Status, Fortschritt, Restzeit, Schicht, Temperaturen, HMS-Fehlern und Druckverlauf (`<Drucker>.history.jobs`, benötigt BambuLab Connect mit Druckverlauf).

## Smartphone-Kopplung und Abgleich

- Einstellungen → „Smartphones koppeln“: ein Smartphone zeigt einen QR-Code, das andere scannt ihn einmal. Die Geräte authentifizieren sich gegenseitig (Android-Keystore, ECDSA) und speichern einander als vertrauenswürdig.
- Gekoppelte Smartphones gleichen sich im selben WLAN automatisch ab, solange HomePilot geöffnet ist (DNS-SD `_homepilot-sync._tcp.`, alle 30 s). Die Übertragung ist authentifiziert, aber nicht verschlüsselt.
- Für alle Daten gilt dieselbe Regel wie bei ioBroker: höhere Revision gewinnt, bei Gleichstand die neuere Änderung.

## In-App-Updater

- Prüft das GitHub-Release `latest`, lädt `HomePilot.apk` herunter und öffnet den Android-Installationsdialog (`ACTION_VIEW`, FileProvider, ClipData, `REQUEST_INSTALL_PACKAGES`) – identisch mit KlimaPilot, auf dem Gerät getestet mit 1.30.2.
- Der Updater ist gesperrt und wird nur auf ausdrückliche Anweisung verändert (siehe `PROJECT_STATUS.md`).

## Bauen und Veröffentlichen

GitHub Actions erstellt nach Änderungen auf dem Branch `main` eine installierbare Release-APK und stellt sie im GitHub-Release `latest` bereit. Andere Branches werden nur geprüft (Unit-Tests und Kompilierung, `.github/workflows/branch-validation.yml`), ohne APK oder Veröffentlichung.

