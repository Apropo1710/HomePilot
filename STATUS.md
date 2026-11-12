# Projektstatus – HomePilot

Zuletzt aktualisiert: 11.11.2026  
Repository: https://github.com/Apropo1710/HomePilot  
Produktionsbranch: `main`  
Aktuelle Arbeitsbranches: `feat/roidmi-eve-plus-options`, `fix/update-installer-result`

> Diese Datei ist die zentrale Übergabe für neue Chats. Vor jeder Arbeit zuerst lesen und nach jeder zusammenhängenden Änderung aktualisieren.

## Projektziel
Android-App zur Steuerung und Verwaltung von ioBroker-Geräten mit Kotlin und Jetpack Compose.

## Verbindliche technische Eckpunkte
- Kommunikation ausschließlich über `ws.0` via WebSocket, Standardport 8084; auch WSS wird unterstützt.
- Simple API / HTTP auf Port 8087 ist nicht der App-Kommunikationsweg.
- GitHub-Repository ist die technische Quelle der Wahrheit.
- Produktive Releases ausschließlich aus `main`; Testbranches dürfen produktive Artefakte/`latest` nicht überschreiben.
- Letzter bekannter Produktionsstand aus dem Projektverlauf: Version 1.29.15, versionCode 72 (Stand 05.11.2026; vor weiterer Versionsarbeit im Repo prüfen).

## Bereits vorhandene Funktionen laut README
- Verbindungstest und ioBroker-WebSocket-Status
- automatische Erkennung unterstützter Shelly- und Hue-Geräte
- manuelle Suche weiterer unterstützter Geräte, u. a. Tuya
- lesbare Gerätenamen aus ioBroker-Objekten und übergeordneten Kanälen
- Räume anlegen/umbenennen/löschen
- Geräte umbenennen, Räumen zuordnen/verschieben und in HomePilot ausblenden
- lokale Speicherung der Raum-/Gerätezuordnung
- Waschmaschine verwaltbar, aber ausdrücklich nicht als Testgerät verwenden

## Laufende bzw. vorgemerkte Arbeiten
- Roidmi Eve Plus in HomePilot integrieren und die vorhandene Roidmi-Raumauswahl-Kachel erweitern, nicht duplizieren.
- Xiaomi X20+ später analog zum Roidmi Eve Plus integrieren; korrekte Trennung der Geräte-Unterbäume beachten.
- Vor der Xiaomi/HomePilot-Anpassung auf eine stabile/finale Adapter-Objektstruktur warten.
- Raumorientierte Navigation: Raum antippen und Geräte/Sensoren des Raums anzeigen.
- App-Konfiguration: Räume erstellen/umbenennen/löschen; Geräte umbenennen/löschen und Räumen zuordnen/verschieben.
- Neue ioBroker-Geräte automatisch oder per manuellem Scan erkennen und zur Klassifizierung/Raumzuweisung anbieten.
- Geräteklassifizierung und übersichtlicheres Dashboard gemeinsam bündeln; keine separate Veröffentlichung nur dafür.
- Waschmaschine eventuell später schaltbar machen, aber nicht als Testgerät und nicht als eigener Release-Anlass.
- Plugin-System als langfristige Architekturidee vormerken, nicht kurzfristig umsetzen.

## KlimaPilot-Abgrenzung
KlimaPilot ist ein eigenes Repository/Projekt. Klimaanlagenfunktionen nicht ungeprüft in HomePilot als bereits fertig voraussetzen.

## Arbeits- und Release-Regeln
1. Vor Änderungen STATUS.md und relevante Quelldateien lesen.
2. Nach jeder zusammenhängenden Änderung STATUS.md im selben Commit aktualisieren.
3. Builds/Tests für den exakten Commit prüfen und Ergebnis samt Run/Artefakt festhalten.
4. Produktionsbranch und Testbranches strikt trennen.
5. Bei Chatwechsel hier beginnen; keine bereits erledigten Nutzertests erneut verlangen.


## Xiaomi X20+ – Dashboard und vollständige Steuerungsseite (12.11.2026)
- Die X20+-Startkachel ist optisch an die MOVA-/Roidmi-Roboterkacheln angeglichen: Produktbild, Status, Akku und direkter Einstieg in die Robotersteuerung.
- Die bisherige reine Zeitplanansicht wurde zur vollständigen X20+-Detailseite erweitert. Sie verwendet ausschließlich den eigenen Objektunterbaum `roidmi.0.x20Plus.*`.
- Enthalten sind die hardwarebestätigten Funktionen: Start/Fortsetzen, Pause, Rückkehr zur Station, Reinigungsart, Saugleistung, Wasserstufe, Teppich-Boost, gezielte Raumreinigung und Aktivieren/Deaktivieren gespeicherter Xiaomi-Home-Zeitpläne.
- Raumkarte und Zeitpläne bleiben getrennte Bereiche; Zeitplanänderungen behalten die Adapter-Readback-Bestätigung bei.
- Die Roboterkachel-Reihenfolge wird beim Verschieben auch bei einem abgebrochenen Drag-Gesture gespeichert. Der wechselnde Listeninhalt wird nicht mehr als Schlüssel für den laufenden Pointer-Handler verwendet; dadurch wird der Drag-Vorgang nicht bei jedem Positionswechsel neu gestartet.
- Keine Änderungen am Roidmi-Steuerablauf oder am APK-Installer.
- Installationsversion: **1.29.106 / versionCode 164**. GitHub-Actions-Build und Smartphone-Test stehen noch aus.


## Xiaomi X20+ – Bildquelle und Reinigungsmodus-Beschriftung (12.11.2026)
- Das in der Startkachel bisher verwendete Xiaomi-CDN-JPG wurde durch das offizielle Xiaomi-Produktbild im PNG-Format ersetzt. Das Motiv zeigt den X20+ mit All-in-One-Station und wird auf der Xiaomi-Produktseite verwendet.
- Die Reinigungsart mit Adapterwert 3 heißt in der Auswahl jetzt vollständig **„Saugen vor Wischen“** statt „Vorher“. Die Schaltfläche darf dafür zweizeilig umbrechen; die anderen Auswahlknöpfe behalten ihre einzeilige Darstellung.
- Die übrige X20+-Steuerung, Datenpunktzuordnung, Roidmi-Steuerung und Updatefunktion wurden nicht verändert.
- Installationsversion: **1.29.107 / versionCode 165**. GitHub-Actions-Build und Smartphone-Prüfung stehen noch aus.


## Xiaomi X20+ – Beschriftungen der Reinigungsart und Starttaste (12.11.2026)
- Die erste Reinigungsart-Schaltfläche zeigt jetzt **„Saugen und Wischen“** statt der Abkürzung „S+W“. Erste und vierte Schaltfläche dürfen zweizeilig umbrechen, damit beide Modi eindeutig lesbar sind.
- Die Starttaste zeigt **„Start / Fortsetzen“** vollständig in zwei Zeilen. Der vorhandene Befehl bleibt unverändert `control.command = start`; laut bisheriger Adapterintegration ist dies der gemeinsame Start-/Fortsetzen-Befehl, es wird kein neuer Steuerbefehl eingeführt.
- Die Steuerungsbeschreibung wurde auf „Reinigung starten, fortsetzen oder pausieren“ korrigiert.
- Installationsversion: **1.29.108 / versionCode 166**. GitHub-Actions-Build und Smartphone-Prüfung stehen noch aus.


## Xiaomi X20+ – Reihenfolge der Reinigungsarten an Xiaomi Home angeglichen (12.11.2026)
- Die vier sichtbaren Modi werden jetzt in derselben Reihenfolge wie in Xiaomi Home dargestellt: **Saugen → Saugen und Wischen → Wischen → Saugen vor Wischen**.
- Wichtig: Die UI-Reihenfolge ist von den Adapterwerten entkoppelt. Die Zuordnung bleibt unverändert: Saugen = 2, Saugen und Wischen = 0, Wischen = 1, Saugen vor Wischen = 3. Auswahl, Readback und angezeigter aktiver Modus bleiben dadurch korrekt.
- Saugleistungs- und Wasserstufen bleiben unverändert.
- Installationsversion: **1.29.109 / versionCode 167**. GitHub-Actions-Build und Smartphone-Prüfung stehen noch aus.


## Xiaomi X20+ – Wartungsanzeige (12.11.2026)
- In der X20+-Detailseite wurde eine Wartungskarte ergänzt: Hauptbürste, Seitenbürste, Filter und Wischmopps mit verbleibender Lebensdauer in Prozent und Reststunden.
- Die Werte stammen aus den vorhandenen Adapterdatenpunkten unter roidmi.0.x20Plus.status.*; der Adapter fragt diese Verbrauchswerte im 5-Minuten-Zyklus ab. Die UI fragt die Werte ebenfalls nur alle fünf Minuten erneut ab.
- Prozentwerte werden mit Fortschrittsbalken und dezenter Warnfarblogik angezeigt. Fehlende Daten bleiben als „—“ sichtbar; es werden keine Werte geschätzt.
- Keine Reset-Schaltflächen, da im aktuellen Adapter keine bestätigten Reset-Aktionen für diese Verbrauchsteile bereitgestellt werden.
- Installationsversion: **1.29.110 / versionCode 168**. GitHub-Actions-Build und Smartphone-Prüfung stehen noch aus.


## Wartung und Verschleißteil-Reset für beide Saugroboter (12.11.2026)
- Xiaomi X20+: Reset-Aktionen gemäß C102GL-MIoT-Profil: Hauptbürste 9/1, Seitenbürste 10/1, Filter 11/1, Wischmopps 18/1. Bestätigungsdialog warnt vor Reset ohne tatsächliche Reinigung/einen Austausch.
- Roidmi EVE Plus: Der Adapter-Feature-Branch stellt Wartungswerte für Filter (10/1-2), Hauptbürste (11/1-2), Seitenbürste (12/1-2) und Sensoren (15/1-2) bereit. Reset-Aktionen: Filter 10/1, Hauptbürste 11/1, Seitenbürste 12/1, Sensoren 15/1. Restzeit kommt in Minuten und wird in HomePilot in Stunden angezeigt.
- Beide Detailseiten erhalten Wartungskarten im einheitlichen HomePilot-Stil. Roidmi hat im verwendeten MIoT-Profil keinen eigenen Wischmopp-Verbrauchszähler; es wird kein Wert erfunden.
- Reset-Zuordnungen beruhen auf dokumentierten MIoT-Action-IDs und müssen am realen Gerät noch praktisch verifiziert werden. Keine automatischen Resets; jeder Reset erfordert Bestätigung.
- Build 168 (Version 1.29.110) scheiterte an einer doppelten @Composable-Annotation vor der Wartungszeilen-Composable. Korrektur in dieser Version.
- Installationsversion: **1.29.111 / versionCode 169**. Build und Tests stehen aus.


## Roboter-Detailseiten – Reihenfolge der Kacheln vereinheitlicht (12.11.2026)
- Xiaomi X20+: Die Karte **Reinigungsbereiche** steht jetzt ganz unten hinter Wartung und Zeitplänen.
- Roidmi EVE Plus: Die Reihenfolge entspricht dem X20+: **Gerätestatus → Steuerung → Saug/Wisch-Einstellungen → Wartung → Reinigungszonen**. Die Reinigungszonen stehen damit ebenfalls ganz unten.
- Es wurden ausschließlich die Positionen der vorhandenen Karten geändert; Inhalte, Datenpunkte, Steuerbefehle und Bestätigungsdialoge bleiben unverändert.
- Installationsversion: **1.29.112 / versionCode 170**.
- Build für diesen Stand läuft nach dem Commit; APK und Signatur müssen vor Freigabe geprüft werden.
