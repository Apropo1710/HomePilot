<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: a3bde16eece8eb1be6d7994facfa2208354bde06
- App-Version / versionCode: **1.29.59 / 117**
- Build-Zeit: 2026-11-03 04:58:08 UTC
- APK-Dateiname: **HomePilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: **HomePilot-release-apk**, ID 13205751947, 11918848 Bytes
- APK-SHA-256: 67cc85a6d53d67ac0d9fe8d80b21b28679cf24984ac03fa3fb38cf8dfa93cc44
- GitHub-Release: **erfolgreich veröffentlicht**; Asset HomePilot.apk wurde aktualisiert.
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

- 02.11.2026: Profilvalidierung korrigiert: Der Disconnect-Timer akzeptiert jetzt auch 15 Minuten (neben 30/60 Minuten und Nie). GitHub Actions Run #231 erfolgreich; Commit `05520b0`. APK erfolgreich signiert und als `latest` veröffentlicht. Ein Smartphone-Test ist noch nicht bestätigt.

- 02.11.2026: Akku-Hinweis nur bei frischer Installation, nicht bei App-Updates; Einstellungen für Hintergrundverbindung kompakter; Akku-Kachel umbenannt und Text gekürzt. Version 1.29.43 (Code 101).


- 02.11.2026: Einstellung für automatische ioBroker-Trennung nach 15/30/60 Minuten oder nie (Standard 30 Minuten); Verbindung beim Resume automatisch wiederherstellen. Einmaliger Akkuoptimierungs-Hinweis mit Link zu Android-App-Einstellungen. Version 1.29.42 (Code 100).


- 02.11.2026: Lifecycle-Reconnect gibt einen klaren Status zurück, wenn der Socket bereits automatisch reconnectet; verhindert festhängenden „connecting“-Zustand und unterdrückt Dialog während laufendem Reconnect. Version 1.29.41 (Code 99).


- 02.11.2026: Stabilisierung der ioBroker-WebSocket-Wiederverbindung: HomePilot ersetzt eine bereits laufende Verbindung/Reconnect-Schleife beim App-Resume nicht mehr durch einen neuen Socket. Socket-Reconnect ignoriert verspätete Fehler nach explizitem Disconnect. Version 1.29.40 (Code 98).


- 02.11.2026: IoBroker-Verbindung wird beim Android-Lifecycle-Event ON_RESUME automatisch neu aufgebaut, falls sie getrennt ist. Reconnect-Statusdialog bei kurzem Verbindungsabbruch unterdrückt. Startverbindung wird gegen doppelte Connect-Aufrufe abgesichert. Version 1.29.39 (Code 97).


- 02.11.2026: Einheitliche Beschriftung aller Roboter-Kacheln: „Zur Robotersteuerung“ (MOVA statt „Zur Mähersteuerung“). Für zukünftige Roboter dieselbe Bezeichnung und Gestaltung verwenden. Produktionsversion 1.29.38 (Code 96).


- 01.11.2026: Roidmi-Status „Bereit“ auf der Zuhause-Kachel und der Detailseite erhält dieselbe dunkle Textfarbe wie beim MOVA-Mäher. Produktionsversion 1.29.37 (Code 95).


- 01.11.2026: Roidmi-Kacheln optisch an MOVA angeglichen: bei fehlerfreiem Adapterstatus wird „Bereit“ angezeigt, redundante Statuszeile auf der Zuhause-Kachel entfällt. Roidmi-Detailkarte zeigt ebenfalls „Bereit“ statt „Adapter verbunden“ und eine kompakte Statusdarstellung; Reinigungszonen werden in drei Spalten angeordnet. Produktionsversion 1.29.36 (Code 94).


- 01.11.2026: Roidmi-Raumliste kompakter gestaltet; Betriebsstatus, Fehler, Reinigungsart und Leistungsstufen anhand des V60-MIOT-Profils in Klartext übersetzt. Ladezustands- und einzelne Fehlercodes bleiben bei widersprüchlichen Referenzquellen ausdrücklich gekennzeichnet. WebSocket-Start wartet nicht mehr auf einen separaten Shelly-Testdatenpunkt; Wiederverbindungen melden den Status erneut und zuletzt bekannte Roidmi-Werte bleiben während kurzer Unterbrechungen sichtbar. Steuerbefehle sind bei unterbrochener ioBroker-Verbindung gesperrt.


- 31.10.2026: Erste zentrale Projektdokumentation auf Basis des geprüften Stands von `main` und der bisher festgehaltenen Projektentscheidungen angelegt.
- 31.10.2026: Verbindlich festgelegt, dass die weitere Entwicklung direkt auf `main` für die Hauptversion erfolgt; keine neuen Test-Branches oder separaten Testversionen.\n

## Neue geplante Geräteintegration: Roidmi EVE Plus

Beschluss vom 01.11.2026: Der Roidmi EVE Plus wird Bestandteil der nächsten größeren HomePilot-Version. Die Integration erfolgt über das eigenständige Projekt **Roidmi Iobroker Adapter** und nicht über eine direkte Xiaomi-Cloud- oder miIO-Implementierung in HomePilot.

- Projektname: `Roidmi Iobroker Adapter`
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
- Roidmi Iobroker Adapter und HomePilot bleiben getrennte Projekte mit getrennten Repositories und Builds.
- HomePilot wird zunächst auf eine klar abgegrenzte Geräteklasse und eine robuste, datenpunktbasierte Integration vorbereitet.
- Erste HomePilot-Integration auf `main`: eigene Roidmi-Kachel auf der Startseite und Detailansicht mit Akku, Ladezustand, Betriebsstatus, Fehlercode und Adapter-Verbindungsstatus. Die numerischen Statuswerte werden bewusst zunächst als Rohwerte angezeigt, bis ihre Bedeutung am realen Gerät bestätigt ist.
- Roidmi-Oberfläche an das MOVA-Layout angeglichen: Produktfoto, Status-/Akku-Karte, Steuerungsbereich und Einstellungsbereich. Die Raumliste wird dynamisch aus `roidmi.0.map.roomsJson` geladen; Raumreinigung nutzt `roidmi.0.control.cleanRooms` und wird nur bei gültiger `mapId` angeboten. Die reale Ausführung bleibt bis zum Gerätetest unbestätigt.
- In der Detailansicht sind Start, Pause, Fortsetzen und Rückkehr zur Ladestation an die Adapter-Steuerdatenpunkte angebunden. Die Oberfläche bestätigt nur die Übergabe an den Adapter, nicht die erfolgreiche Ausführung am Roboter.
- Nicht vorhandene, unfertige oder nicht praktisch getestete Adapterfunktionen dürfen in HomePilot weder als verfügbar angezeigt noch als funktionierend ausgegeben werden.
- Die Integration wird mit den tatsächlich vom Adapter bereitgestellten Datenpunkten umgesetzt; keine vorzeitige Festlegung auf unbestätigte IDs oder Befehlsnamen.
- Die Roidmi-Integration wird mit der geplanten Raumverwaltung, Geräteklassifizierung und Dashboard-Überarbeitung in einer gemeinsamen größeren HomePilot-Version gebündelt. Keine separate Zwischenveröffentlichung nur für den Roboter.
- Vor jeder Codeänderung den aktuellen Stand von `main` lesen; die normale HomePilot-Weiterentwicklung erfolgt gemäß Projektentscheidung direkt auf `main`.

### Vorgehensreihenfolge

1. Vorhandene HomePilot-Geräte- und Raumarchitektur prüfen und eine möglichst kleine, erweiterbare Einbindung für `robot_vacuum` entwerfen.
2. Parallel den tatsächlichen Datenpunkt- und Befehlsumfang von Roidmi Iobroker Adapter anhand des Adaptercodes und später anhand realer ioBroker-Objekte verifizieren.
3. Roboter-Dashboard und Statusanzeige mit tatsächlich verfügbaren Datenpunkten integrieren.
4. Steuerbefehle einzeln anbinden und am Roidmi EVE Plus praktisch testen.
5. Erweiterte Funktionen wie Räume/Zonen, Karte, Verlauf und Wartung erst ergänzen, wenn Adapter und Gerät sie zuverlässig bereitstellen.\n\n\n\n\n\n\n\n\n\n\n\n\n

## Neue Funktion: Gemeinsame Konfiguration, Benutzerprofile und Sicherung

Beschluss vom 02.11.2026 (aktualisierte vereinfachte Zielarchitektur):

### ioBroker-Authentifizierung und Speicherrechte (Entscheidung)
- HomePilot nutzt die bestehende ioBroker-Benutzerverwaltung und WebSocket-Authentifizierung, keine selbst entwickelte App-Identität.
- Andreas und Jenny sollen jeweils mit eigenem ioBroker-Benutzer angemeldet sein.
- ioBroker-Login-Daten werden auf Android mit Android Keystore-gestützter Verschlüsselung geschützt gespeichert; keine Klartextpasswörter in SharedPreferences.
- Der Nutzer hat ausdrücklich entschieden, dass HomePilot im bestehenden vertrauenswürdigen Heimnetz weiterhin `ws://` ohne Zertifikat verwendet. WSS/TLS ist keine Voraussetzung und soll nicht erzwungen werden. Dabei ist bekannt und akzeptiert, dass Zugangsdaten über WS unverschlüsselt im Netzwerk übertragen werden; daher nur im vertrauenswürdigen LAN und nicht über öffentliche/gast WLANs einsetzen. Keine Zertifikatseinrichtung oder WSS-Migration als Voraussetzung einplanen.
- Geplante ACL-Trennung: globaler Konfigurationsbereich für Jenny lesbar, aber nicht beschreibbar; eigenes Profil von Jenny les-/schreibbar; Andreas kann gemeinsame Konfiguration und beide Profile administrieren.
- UI-Rollenanzeige ergänzt ioBroker-ACLs und ersetzt sie nicht.
- Vor Implementation muss geprüft werden, welche Socket.IO-Methoden und Objekt-/State-ACLs im bestehenden ioBroker-Setup verfügbar sind. Keine pauschale Annahme, dass 0_userdata.0-Standard-ACLs automatisch diese Trennung erfüllen.

### Speicherentwurf (noch nicht implementiert)
- Namespace: 0_userdata.0.HomePilot
- config: gemeinsame Räume, Gerätezuordnungen, Gerätenamen, Klassifizierung, globale ausgeblendete Geräte und Revision/Änderungsmetadaten.
- profiles.<ioBrokerUser>: benutzerspezifische Favoriten, Reihenfolge, Disconnect-Timer und Anzeigepräferenzen.
- backup.config und backup.profiles.<user>: getrennte Sicherungsstände bzw. Snapshots; Wiederherstellung eines Profils darf globale Konfiguration oder andere Profile nicht ersetzen.
- Entwurf: eine versionierte JSON-Konfiguration je Bereich statt zahlreicher einzeln verstreuter State-IDs; ACLs müssen auf Objekt/State-Ebene passend geprüft und eingerichtet werden.
- Vor Migration zuerst zentral vorhandene Konfiguration prüfen; lokale Bestandsdaten nur initial importieren, wenn noch keine globale Konfiguration existiert. Bei mehreren Smartphones mit unterschiedlichem lokalen Bestand keine automatische Überschreibung, sondern kontrollierter Import/Abgleich.

### Gemeinsame Smart-Home-Konfiguration
- Räume, Raum-/Gerätezuordnungen, Gerätenamen, Geräteklassifizierung und globale Einstellungen sind zentral in ioBroker gespeichert.
- Andreas verwaltet die globale Konfiguration; beide Smartphones erhalten Änderungen automatisch.
- Jenny kann Geräte bedienen, aber die administrativen Konfigurationsbereiche (z. B. Geräteeinstellungen und ausgeblendete Geräte verwalten) sind in ihrer App ausgeblendet.
- Es wird kein zusätzlicher ioBroker-Adapter und kein weiterer dauerhaft laufender Serverdienst installiert.
- Die vereinfachte UI-Rollenlösung ist für den normalen Haushalt gedacht, nicht als sicherheitskritische Zugriffskontrolle. UI-Ausblendung allein ist keine serverseitige Autorisierung.

### Persönliche Einstellungen und Profile
- Persönliche Einstellungen bleiben pro Benutzer getrennt, z. B. Favoriten, Anzeigeoptionen und persönliche Zeitwerte (15/30/60 Minuten bzw. Nie).
- Jenny darf ihre persönlichen Einstellungen selbst ändern.
- Andreas’ persönliche Einstellungen dürfen durch Jenny weder überschrieben noch gelöscht werden.
- Persönliche Einstellungen und globale Konfiguration werden getrennt gespeichert und gesichert.

### Synchronisation, Sicherung und Wiederherstellung
- Gemeinsame Konfiguration wird über ioBroker synchronisiert und bei Änderungen auf beiden Smartphones aktualisiert.
- Persönliche Profile (Andreas/Jenny) werden separat gespeichert.
- Änderungen werden nach erfolgreicher Speicherung bestätigt; fehlgeschlagene/offline Änderungen dürfen nicht stillschweigend als synchronisiert gelten.
- Sicherung erfolgt automatisiert nach relevanten Änderungen mit kurzer Verzögerung sowie zusätzlich über „Jetzt sichern“.
- Wiederherstellung eines persönlichen Profils darf nicht die globale Raum-/Gerätekonfiguration oder das andere persönliche Profil überschreiben.
- Konflikte bei gleichzeitig geänderten Daten müssen erkannt und nachvollziehbar behandelt werden.
- Vorhandene lokale Einstellungen müssen beim Upgrade migriert werden, ohne sie zu verlieren. Bestehende lokale Räume, Gerätenamen, Zuordnungen, Klassifizierungen und ausgeblendete Geräte sind als Ausgangspunkt für die globale Konfiguration zu behandeln; die Migration darf keine vorhandene zentrale Konfiguration ungefragt überschreiben.

### Umsetzungsreihenfolge
1. Vorhandene WebSocket-/ioBroker-API und aktuelle Konfigurationspersistenz vollständig prüfen.
2. Schema und Speicherorte für gemeinsame Konfiguration sowie getrennte Benutzerprofile definieren.
3. Synchronisations- und Sicherungsmechanismus mit Fehler-/Offline-Verhalten implementieren.
4. Migration vorhandener Einstellungen und Konfliktverhalten implementieren.
5. Admin-/Benutzeransicht und persönliche Einstellungen sauber trennen.
6. Code-Diff, Android-Build, GitHub Actions und Release-Artefakt prüfen.

**Status:** Zielarchitektur aktualisiert. Feature-Code noch nicht implementiert; noch kein Feature-Build oder Smartphone-Test.\n\n\n

## Build-Status Authentifizierungs-Grundlage (02.11.2026)
- Commit `d16a2116e153a072eae9f9a85d93c26d3827c250` korrigiert die fehlenden ioBroker-Login-Parameter in der zweiten Einstellungsansicht.
- GitHub Actions Run `41481137422` für diesen Commit ist erfolgreich abgeschlossen.
- Erfolgreicher CI-Build bestätigt die Kompilierbarkeit, noch nicht die praktische ioBroker-Anmeldung oder Synchronisation auf beiden Smartphones.
- Nächster Entwicklungsschritt: zentrale JSON-State-API und sichere Initialisierung/Existenzprüfung des globalen Konfigurationsdatensatzes sowie getrennte Profil-Datenpunkte. Vor Umsetzung ACL-Verhalten und Rechte bei `0_userdata.0` prüfen.\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n

## Raumauswahl-Kachel (03.11.2026)
- Gemeinsame Raumauswahl wird in einer eigenen Kachel mit der Überschrift **Raumauswahl** dargestellt.
- Die Kachel erscheint auf der Startseite sowie in den Licht-, Geräte- und Raumansichten.
- In den Einstellungen kann ein Raum direkt gewählt werden; HomePilot öffnet anschließend die Raumansicht.
- Version für diese Änderung: **1.29.56 (versionCode 114)**. Smartphone-Test steht nach dem CI-Build noch aus.\n

## Einstellungsseite – aufklappbare Menüpunkte (03.11.2026)
- Alle bisherigen Einstellungen werden als kompakte, aufklappbare Menüpunkte angezeigt; beim Antippen öffnet sich jeweils der Inhalt, beim erneuten Antippen wird er geschlossen.
- Bereiche: Darstellung, Geräte-Einstellungen, Raumauswahl, ausgeblendete Geräte, Geräteprobleme, HomePilot-Konto, HomePilot-Benutzerverwaltung (nur Admin), ioBroker-Verbindung, Verbindung im Hintergrund, Energiesparen, Wetter und App-Version.
- Es bleibt jeweils höchstens ein Einstellungsbereich gleichzeitig geöffnet.
- Bestehende Funktionen, Dialoge, Eingabefelder und Updateprüfung bleiben erhalten.
- Zielversion: 1.29.57 (versionCode 115); Smartphone-Test nach Release erforderlich.\n\n\n