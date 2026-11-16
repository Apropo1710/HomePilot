# Projektstatus – HomePilot

## Verifizierter Abgleich – 16.11.2026, 03:02 MEZ

- `main` Head: `e52440110ca9bdb11654aedb6c6f42dc0eaddf3c` (Dokumentationscommit). App-Code-Commit: `a3c5afed22d11ef3c707fc32236d891933a25bba`; Paketversion **1.29.125 / versionCode 183**.
- APK-Build-Run `43666036392`: Job `build` **completed / success**. `PROJECT_STATUS.md` dokumentiert das Release-Asset `HomePilot.apk`, SHA-256 `485899947c41f4f05fdf904fec92704f6c1d7789c4a3cbfbe0cd8fa0c819fd77`, und erfolgreiche Signaturprüfung.
- X20+: bidirektionale Zeitplansynchronisation und relevante Steuerfunktionen sind vom Nutzer praktisch bestätigt. Eve Plus: Zeitplaneditor und Synchronisierung in beide Richtungen bestätigt; der gezielte Hardwaretest einer Zeitplan-Schreibänderung bleibt offen.
- Remote-Branch-Abgleich: `feat/roidmi-eve-plus-options` (Head `18b47b5d944e7cb2737faa8b5b71e830180bcace`, Version 1.29.91 / 149) liegt **3 Commits voraus und 196 zurück**. PR #1 ist gemergt, aber der verbliebene Branch-Diff ist nicht automatisch als übernommen zu betrachten; kein offener PR. Nicht löschen oder ungeprüft übernehmen.
- `fix/update-installer-result` (Head `fd8cd1a08b1acf97a9e976fdb7bc6ee5a92bf779`, Version 1.29.91 / 149) liegt **0 Commits voraus und 193 zurück**; kein offener PR, historischer Vorfahrstand.
- Aktuell sind keine offenen HomePilot-Pull-Requests vorhanden.


Zuletzt aktualisiert: 15.11.2026, 03:10 MEZ
Repository: https://github.com/Apropo1710/HomePilot
Produktionsbranch: `main`
Aktuelle produktive Quelle: `main`

> Diese Datei ist die zentrale Übergabe für neue Chats. Vor jeder Arbeit zuerst lesen und nach jeder zusammenhängenden Änderung aktualisieren.

## Geprüfter Gesamtstand – 15.11.2026, 03:10 MEZ
- Maßgeblicher Produktionsbranch: `main`; vor dieser Statusaktualisierung geprüfter Head `32e81218b47a44988ecbdd85df7ad8c9d6990e09` (Dokumentationscommit).
- Letzter App-Code-Commit: `a3c5afed22d11ef3c707fc32236d891933a25bba`; Produktionsversion **1.29.125 / versionCode 183**.
- APK-Build-Quellcommit: `32e81218b47a44988ecbdd85df7ad8c9d6990e09`; GitHub Actions Run `43666036392`: **completed / success**.
- Release `latest`: Asset `HomePilot.apk` vorhanden, SHA-256 `485899947c41f4f05fdf904fec92704f6c1d7789c4a3cbfbe0cd8fa0c819fd77`; Signaturprüfung laut automatisch gepflegtem Produktionsnachweis erfolgreich.
- Eve Plus: Zeitpläne werden geladen und bearbeitet; bidirektionale Synchronisation ist bestätigt. Eine echte Zeitplan-Schreibänderung am Gerät bleibt noch zu testen.
- X20+: Zeitplan-Synchronisation in beide Richtungen und relevante Steuerfunktionen sind praktisch bestätigt.
- Es sind keine offenen HomePilot-Pull-Requests vorhanden.


## Remote-Branch-Abgleich – 15.11.2026
- `feat/roidmi-eve-plus-options`: Head `18b47b5d944e7cb2737faa8b5b71e830180bcace`, Paketversion 1.29.91; gegenüber `main` drei Commits voraus und 191 zurück, kein offener PR. Der Branch enthält noch eigene Änderungen und wird deshalb nicht als vollständig übernommen oder gelöscht betrachtet.
- `fix/update-installer-result`: Head `fd8cd1a08b1acf97a9e976fdb7bc6ee5a92bf779`, Paketversion 1.29.91; gegenüber `main` 188 Commits zurück und ohne eigene Commits voraus, kein offener PR. Er ist ein älterer Vorfahrstand und nicht produktiv.
- Beide Branches bleiben unverändert. Keine automatische Zusammenführung oder Löschung.

Zuletzt aktualisiert: 15.11.2026, 03:01 MEZ

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


## Roboter-Detailseiten – m²-Anzeige und Rückmeldung bei Raumreinigung (16.11.2026)
- Xiaomi X20+: Die bisherige Bezeichnung „Flächenwert“ in der Statuskachel lautet jetzt „m² bearbeitet“.
- Xiaomi X20+ und Roidmi EVE Plus: Nach dem Start über die Raum-Auswahl erscheint die Rückmeldung direkt in der oberen Gerätestatus-Kachel. Erfolgreich bestätigter Start wird grün, Fehler rot und ein noch nicht bestätigter Start gelb angezeigt.
- Beim X20+ wird „Reinigung gestartet“ nur angezeigt, wenn der Adapter die Startbeobachtung im regulären Status-Polling bestätigt. Vorher erscheint „Reinigungsauftrag angenommen – Start wird geprüft.“; ein nicht beobachteter Start wird nicht fälschlich als Erfolg dargestellt.
- Der aktuelle Roidmi-EVE-Plus-Adapter stellt keinen Datenpunkt für die bearbeitete Reinigungsfläche bereit. Deshalb wird dort keine m²-Zahl erfunden; die Start-/Fehlerrückmeldung ist trotzdem enthalten.
- Keine Änderungen an Adapter, Roboterkommunikation oder Reinigungsbefehlen. App-Version **1.29.126 / versionCode 184**.
- Quellcommits: `5a5e00580722706d38e023a0b11bb4502d8fedcb`, `61c08e6217d887c7d447787e5d0dd0b651cfaa7e`, `3aa8da15306140c2094ecac3aed62a070acff106`. GitHub-Actions-Build ausstehend.


## Xiaomi X20+ – Routine-Faultcodes nicht mehr als Störung anzeigen (16.11.2026)
- Recherche zur MIoT-Eigenschaft `vacuum.fault` (SIID 2 / PIID 2): Code **68** ist beim C102GL/X20+ ein Stationshinweis während der Wischpad-Trocknung, kein Defekt. Die Original-App zeigte im vorliegenden Screenshot parallel Status „Wischer-Pads werden getrocknet…“.
- Code **114** ist als Wartungshinweis zum Ausspülen/Reinigen der Wischschale dokumentiert, nicht als Roboterdefekt.
- HomePilot behandelt 68 und 114 nun gesondert: Sie lösen weder „Störung · Fehlercode …“ noch die rote Fehleranzeige aus. Bei Code 68 und Status 8 erscheint „Wischer-Pads werden getrocknet“, sonst ein neutraler Stationshinweis. Code 114 erscheint als Wartungshinweis. Unbekannte Nicht-Null-Codes bleiben bewusst als Fehler sichtbar, bis sie verifiziert sind.
- Statuscodes und Faultcodes bleiben getrennte Werte. Status 8 = Wischpads trocknen; nicht mit Faultcode 68 verwechseln.
- App-Version **1.29.127 / versionCode 185**. Quellenbasis: MIoT-Spezifikation und unabhängige, am Livegerät geprüfte C102GL-Implementierung; offizielle Xiaomi-FAQ bestätigt die automatische Trocknung an der Station.
- Quellcommits: `b23f07290349af8e3ebd599e554d4a04b7a2e73b` (UI), Versionscommit folgt. Build ausstehend.


## Xiaomi X20+ – dynamischen Status aus statusText anzeigen (16.11.2026)
- Die Faultcode-Sonderbehandlung für 68 und 114 wurde aus HomePilot entfernt. Diese Codes bleiben ausschließlich Diagnosewerte und werden nicht mehr als dauerhafte Statushinweise missbraucht.
- HomePilot liest jetzt `x20Plus.status.statusText` zusätzlich zum normalen Status-Polling (30 Sekunden) und verwendet den vom Adapter gelieferten dynamischen Text in der Roboterkachel und Detailansicht.
- Nur wenn `statusText` ausdrücklich „Fehler“ meldet, wird ein vorhandener Faultcode ergänzend als Fehlercode angezeigt. Ein alter, stehenbleibender Faultcode kann einen normalen aktuellen Status nicht mehr überschreiben.
- Fallback auf die bisherige vacuumState-Übersetzung bleibt erhalten, falls statusText leer/nicht verfügbar ist. Keine Änderung am Adapter oder dessen Abfrageintervallen.
- App-Version **1.29.128 / versionCode 186**. Build ausstehend.
- UI-Commit: `bb857f5e3d5e4f036ec20476f6767a6bda5d110e`.


## X20+ statusText – Buildkorrektur (16.11.2026)
- Der erste Build von 1.29.128 scheiterte an zwei übrig gebliebenen Verweisen auf die entfernte Hilfsfunktion `isX20PlusNoticeFault` in den Textfarben der Start- und Detailansicht.
- Beide Verweise wurden durch dieselbe aktuelle `statusText`-basierte Fehlerfarbprüfung ersetzt. Keine Änderung an der Statuslogik selbst.
- Korrekturversion **1.29.129 / versionCode 187**.
- Korrekturcommit: `60fbd24319bf391fe551e54783f34a3bd6d81d48`. Build wird erneut ausgeführt.
