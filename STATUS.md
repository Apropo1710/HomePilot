# Projektstatus – HomePilot

Zuletzt aktualisiert: 14.11.2026, 02:57 MEZ
Repository: https://github.com/Apropo1710/HomePilot
Produktionsbranch: `main`
Aktuelle produktive Quelle: `main`

> Diese Datei ist die zentrale Übergabe für neue Chats. Vor jeder Arbeit zuerst lesen und nach jeder zusammenhängenden Änderung aktualisieren.

## Geprüfter Gesamtstand – 14.11.2026, 02:57 MEZ
- Zuletzt vor dieser Statuspflege geprüfter Branch-Head auf `main`: `f1700819958bf04f32d320c71aa54269e5a9054c` (reiner Dokumentationscommit); der App-Code blieb unverändert.
- Letzter App-Code-Commit: `a3c5afed22d11ef3c707fc32236d891933a25bba`; Produktionsversion **1.29.125 / versionCode 183**.
- APK-Build-Quellcommit: `1139405a8ff17514ba9c653e1314b9042381991b` (Dokumentationscommit auf demselben App-Code-Stand); GitHub Actions Run `43405642595`: **completed / success**.
- Release `latest`: Asset `HomePilot.apk` vorhanden, SHA-256 `fb49790c51b7655c257e9d55070934d37754c9964b0be33b1940406ce5414339`; Signaturprüfung laut Produktionsnachweis erfolgreich.
- Eve Plus: Zeitpläne werden geladen und bearbeitet; bidirektionale Synchronisation ist bestätigt. Die Hardwarebestätigung einer tatsächlichen Zeitplan-Schreibänderung am Eve Plus bleibt offen.
- X20+: Zeitplan-Synchronisation in beide Richtungen und relevante Steuerfunktionen sind praktisch bestätigt.
- Ältere Versions-/Build-Pending-Angaben im Änderungsverlauf sind historische Zwischenstände, keine aktuellen offenen Build-Aufgaben.

## Projektziel
Android-App zur Steuerung und Verwaltung von ioBroker-Geräten mit Kotlin und Jetpack Compose.

## Verbindliche technische Eckpunkte
- Kommunikation ausschließlich über `ws.0` via WebSocket, Standardport 8084; auch WSS wird unterstützt.
- Simple API / HTTP auf Port 8087 ist nicht der App-Kommunikationsweg.
- GitHub-Repository ist die technische Quelle der Wahrheit.
- Produktive Releases ausschließlich aus `main`; Testbranches dürfen produktive Artefakte/`latest` nicht überschreiben.
- Aktueller Produktionsstand: **1.29.125 / versionCode 183** (siehe `PROJECT_STATUS.md` und Release `latest`).

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
- Eve-Plus-Zeitplanbearbeitung: Implementierung vorhanden; bidirektionale Synchronisation mit der Original-App wurde bestätigt. Ein gezielter Hardwaretest der Zeitplan-Schreibfunktion bleibt separat offen.
- Die auf dem X20+ bestätigte Zeitplan-Synchronisation und Gerätebedienung bleiben erhalten; keine erneute Grundsatzintegration erforderlich.
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


## Roboter-Detailseiten – Wartungskarte ganz nach unten (12.11.2026)
- Xiaomi X20+: **Wartung** steht jetzt nach Zeitplänen und Reinigungsbereichen als letzte Kachel.
- Roidmi EVE Plus: **Wartung** steht jetzt nach den Reinigungszonen als letzte Kachel.
- Alle Kachelinhalte, Datenpunkte, Steuerbefehle und Dialoge bleiben unverändert.
- Installationsversion: **1.29.113 / versionCode 171**.


## Xiaomi X20+ – Reihenfolge von Zeitplänen und Reinigungsbereichen getauscht (12.11.2026)
- Die Kacheln stehen jetzt in der Reihenfolge **Reinigungsbereiche → Zeitpläne → Wartung**.
- Die Wartung bleibt die letzte Kachel. Die Roidmi-Reihenfolge bleibt unverändert.
- Nur die Position der beiden X20+-Karten wurde getauscht; Inhalte und Funktionen bleiben unverändert.
- Installationsversion: **1.29.114 / versionCode 172**.


## Appweite Diagnosetexte ein-/ausblenden (12.11.2026)
- Unter **Einstellungen → App-Einstellungen** gibt es den Schalter **Diagnosetexte anzeigen**.
- Standard ist **Aus**; die Auswahl wird lokal in den App-Einstellungen gespeichert und bleibt nach Neustart erhalten.
- Die Anzeige technischer Adapter-Rückmeldungen lässt sich zentral steuern. Diagnosewerte und Abfragen bleiben aktiv; nur die Textausgabe wird ausgeblendet.
- Erfasst sind unter anderem Xiaomi-X20+-Raumkarten-/Reinigungs-/Zeitplan-Rückmeldungen, Zeitplan-Parsefehler sowie technische Roidmi-Fehlercodes und Adapterdetails.
- Normale Bedienhinweise, Gerätestatus und die eigentliche Steuerung bleiben sichtbar und unverändert.
- Installationsversion: **1.29.115 / versionCode 173**.


## Diagnose-Schalter – zusätzliche Fehlercode-Ausgabe (12.11.2026)
- Auch der numerische Fehlercode des Xiaomi X20+ wird über **Diagnosetexte anzeigen** gesteuert. Der normale Fehler-/Betriebsstatus des Roboters bleibt sichtbar.
- Installationsversion: **1.29.116 / versionCode 174**.


## Roidmi EVE Plus – Zeitplanbearbeitung (12.11.2026)
- Die Roidmi-Detailseite erhält eine eigene Karte für gespeicherte Zeitpläne. Bestehende Einträge können hinsichtlich Uhrzeit, Aktivierung, Wochentagen, Saugstufe, Wasserstufe und – sofern die Raumkarte verfügbar ist – Raumzuordnung bearbeitet werden.
- Der Reinigungsmodus und unbekannte Felder bleiben unverändert, bis ihre numerischen Werte eindeutig verifiziert sind. Der Adapter prüft vor dem Schreiben, ob sich der zuvor angezeigte Zeitplan zwischenzeitlich geändert hat, und bestätigt Änderungen durch Readback.
- Die Aktualisierung und Bearbeitung erfolgen ausschließlich auf ausdrückliche Benutzeraktion; es kommen keine neuen zyklischen Abfragen hinzu.
- Für eine installierbare Aktualisierung wird die App-Version auf **1.29.121 / versionCode 179** angehoben.
- Android-Release-Build für **1.29.121 / versionCode 179** erfolgreich; APK-Paket und Signatur wurden von GitHub Actions geprüft und das GitHub-Release `latest` aktualisiert.
- Adapter **0.1.64**: beide CI-Prüfungen erfolgreich (Build/Test). Hardwaretest der Eve-Plus-Zeitplanbearbeitung steht noch aus; bis dahin keine Zeitplan-Schreiboperation am Roboter.
