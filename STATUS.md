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
