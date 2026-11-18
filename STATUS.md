## Gerätetest Updater und Play Protect – 18.11.2026, 22:29–22:44 Uhr (bestätigt)

- **22:29 Uhr – 1.30.1 einmalig manuell installiert** (APK aus Release `latest`, per Messenger aufs Smartphone): Google Play Protect meldet **„Diese App ist anscheinend sicher – Du kannst mit der Installation fortfahren“**, obwohl `REQUEST_INSTALL_PACKAGES` im Manifest steht. Installation erfolgreich, Anmeldung und Daten blieben erhalten.
- **22:44 Uhr – Update 1.30.1 → 1.30.2 über den In-App-Updater** („Update laden“ in den Einstellungen): Der Android-Installationsdialog öffnet sich, Play Protect meldet erneut **„Diese App ist anscheinend sicher“**. Damit ist der KlimaPilot-gleiche Updater (`ACTION_VIEW` + `application/vnd.android.package-archive` + FileProvider + URI-Leseberechtigung + ClipData + `REQUEST_INSTALL_PACKAGES`) auf dem Gerät bestätigt.
- **Schlussfolgerung:** `REQUEST_INSTALL_PACKAGES` ist für den Updater nötig und nach diesen beiden Scans nicht der Auslöser der früheren Play-Protect-Blockaden. Die Begründung der Rücknahme vom 17.11.2026 (1.29.132) war falsch; ohne die Berechtigung öffnete „Update laden“ keinen Installationsdialog. Play Protect kann seine Einstufung später ändern – bei einer neuen Warnung zuerst diesen Stand vergleichen, nicht die Berechtigung entfernen.
- **Verbindlicher, geräte-getesteter Updater-Referenzstand: HomePilot 1.30.2 / versionCode 193** (Build #442, Commit `5abe795`). Dieser Stand ist bei allen künftigen Arbeiten zu bewahren; die Updater-Sperre gilt unverändert.
- Ebenfalls bestätigt: Nach dem Update bleibt die Anmeldung erhalten (neue lokale Kontenliste, Übernahme aus ioBroker). Die Oberfläche zeigt den Abschnitt „Smartphones koppeln“.
- **Noch offen:** Kopplung zweier Smartphones und Abgleich von Raum, Gerät und Favorit zwischen ihnen; Einrichtung per QR-Code.

## Farben und Kopplungstext – 1.30.2 / 193 (18.11.2026)

- Lila/Rosa entfernt: Raumauswahl-Kachel nutzt jetzt das HomePilot-Blau (wie die MOVA-Kachel); alle bisher nicht gesetzten Material-Standardfarben (Container, Navigationsmarkierung, Schalter, Dialog- und Menüflächen) im hellen und dunklen Schema auf Blau/Blaugrau abgestimmt. Farbvorgaben für Hue-Lampen („Violett“, „Pink“) bleiben, da sie echte Lampenfarben darstellen.
- „Smartphones koppeln“: erklärender Hinweistext entfernt.
- Updater unverändert.

## Updater wie KlimaPilot – 1.30.1 / 192 (18.11.2026, ausdrückliche Nutzeranweisung)

- Befund: Seit 1.29.132 fehlte `REQUEST_INSTALL_PACKAGES`; „Update laden“ öffnete deshalb keinen Installationsdialog (gleiches Verhalten wie im KlimaPilot-Gerätetest vom 18.11.2026, Abschnitt 2h dort).
- Auf ausdrückliche Anweisung des Nutzers („Mach es wie bei KlimaPilot“) an den geräte-getesteten KlimaPilot-Updater angeglichen: `REQUEST_INSTALL_PACKAGES` wieder im Manifest, Übergabe direkt per `ACTION_VIEW` + `application/vnd.android.package-archive` + FileProvider + URI-Leseberechtigung + ClipData (ohne Systemauswahl/Chooser). Download, Paketprüfung, Updateprüfung und Release-Workflow unverändert.
- **Verbindlicher Updater-Stand ab jetzt (wie KlimaPilot):** `ACTION_VIEW` + FileProvider + ClipData + `REQUEST_INSTALL_PACKAGES`. Die frühere Annahme, die Berechtigung sei überflüssig, ist durch den KlimaPilot-Gerätetest widerlegt. Play Protect kann beim Update „Trotzdem installieren“ verlangen.
- **Einmaliger Übergang:** Installierte Versionen ohne die Berechtigung (1.29.132, 1.30.0) konnten 1.30.1 nicht selbst installieren; 1.30.1 wurde am 18.11.2026 einmalig manuell installiert. Danach funktioniert der In-App-Updater wieder (Gerätetest siehe oben).
- Die Updater-Sperre gilt weiter: keine weiteren Updater-Änderungen ohne ausdrückliche Nutzeranweisung.

## Smartphone-Kopplung, Smartphone-Sync und neue Anmeldung – 1.30.0 / 191 (18.11.2026)

- Übernahme der Lösung aus KlimaPilot (Stand 0.4.78), auf ausdrücklichen Wunsch des Nutzers direkt nach `main`.
- **Kopplung:** Einstellungen → „Smartphones koppeln“: ein Gerät zeigt einen QR-Code, das andere scannt ihn einmal (signierte Einladung/Antwort/Speicherbestätigung, Android-Keystore-ECDSA, lokale Liste vertrauenswürdiger Geräte). Paket `de.homepilot.app.sync` ist eine 1:1-Portierung des KlimaPilot-Pakets mit eigenen Kennungen (`_homepilot-sync._tcp.`, Protokoll-Magic `HPT1`, Keystore-Alias `de.homepilot.peer.identity.v1`).
- **Abgleich zwischen Smartphones:** automatisch im Vordergrund, sobald mindestens ein Gerät gekoppelt ist (DNS-SD-Suche, Abgleich beim Finden und alle 30 s). Inhalt: Benutzerkonten, gemeinsame Konfiguration (Räume, Gerätenamen, Raumzuordnung, Gerätetypen, ausgeblendete Geräte, übernommene Hue-Lampen) und Benutzerprofile (Favoriten, Reihenfolgen, Hintergrund-Timeout) sowie die ioBroker-Adresse, falls keine gespeichert ist. TCP im WLAN ist wie bei KlimaPilot unverschlüsselt, aber gegenseitig authentifiziert.
- **ioBroker bleibt parallel:** Konten (`0_userdata.0.HomePilot.accounts`), Konfiguration (`…config`) und Profile (`…profiles.<user>`) werden jetzt lokal gespeichert und beidseitig mit ioBroker abgeglichen (beim Verbinden und alle 30 s). Regel überall gleich: höhere Revision gewinnt, bei gleicher Revision neuere Änderungszeit, danach Inhalts-Digest. Neues optionales Feld `importedLights` in der Konfiguration (ältere Dokumente bleiben lesbar).
- **Anmeldung:** lokale Kontenliste, damit Anmeldung auch ohne ioBroker funktioniert. Ohne lokale Konten werden sie automatisch aus ioBroker übernommen (gemerkter Benutzer bleibt angemeldet). Falls ioBroker nicht erreichbar ist: Adresseingabe oder Einrichtung per QR-Code. Admins zeigen den QR unter Einstellungen → Benutzereinstellungen → „Anderes Smartphone einrichten“ (10 Minuten gültig, nur Passwort-Hashes). Der alte Bildschirm „Mit ioBroker verbinden“ entfällt.
- **Manifest:** nur `ACCESS_NETWORK_STATE` und `CHANGE_WIFI_MULTICAST_STATE` sowie ML-Kit-Metadaten für den QR-Scanner ergänzt. Keine Installationsberechtigung.
- **Updater unverändert:** `GithubUpdateCard.kt`, `GithubPrivateUpdates.kt`, `StartupUpdateCheck`, FileProvider, Signierung und `android.yml` sind nicht verändert.
- **Neuer Prüf-Workflow** `.github/workflows/branch-validation.yml`: nur für Branches außer `main`, führt Unit-Tests und Release-Kotlin-Kompilierung aus, baut/signiert/veröffentlicht nichts. Lauf auf Branch `feature/smartphone-sync` (Commit `7cf4216`) erfolgreich, 65 Unit-Tests bestanden.
- **Hinweis Übergang:** Smartphones mit älterer HomePilot-Version melden beim Speichern von Räumen/Profilen ggf. „Konflikt erkannt“, bis sie ebenfalls aktualisiert sind.
- Anmeldung nach Update vom Nutzer bestätigt (18.11.2026). **Noch offen:** Kopplung, Abgleich Raum/Gerät/Favorit zwischen zwei Smartphones, QR-Einrichtung.

## Verbindliche Projektsperre: Updater bleibt unangetastet (17.11.2026)
- Dauerhafte Nutzerregel: Der HomePilot-Updater darf bei keiner normalen Entwicklungsarbeit verändert werden – weder direkt noch indirekt. Das umfasst Updateprüfung, Download, Installer, Manifest-Berechtigungen, FileProvider/URI, Signierung/Keystore, Update-UI und Release-/Workflow-Logik.
- Vor jedem Feature-Diff ist zu prüfen, dass der Updater unberührt bleibt. Bei möglichem Konflikt Arbeit stoppen und Nutzer informieren. Nur eine ausdrückliche, konkrete spätere Nutzeranweisung kann eine gezielte Updater-Änderung erlauben.
- Vollständige verbindliche Regel: `PROJECT_STATUS.md`, Abschnitt **„Absolute Sperre: Updater darf nicht verändert werden“**.
- Geräte-getesteter Referenzstand seit 18.11.2026: **1.30.2** mit `REQUEST_INSTALL_PACKAGES` (siehe „Gerätetest Updater und Play Protect“).

## Play-Protect-Korrektur – Rücknahme der Installer-Berechtigung (17.11.2026) – ÜBERHOLT

> **Überholt seit 18.11.2026:** Die hier angenommene Ursache war falsch. Ohne `REQUEST_INSTALL_PACKAGES` öffnete der Updater keinen Installationsdialog; mit der Berechtigung stuft Play Protect HomePilot als sicher ein (Gerätetest 1.30.1/1.30.2). Maßgeblich ist der Abschnitt „Gerätetest Updater und Play Protect“.

- Ursache des erneuten Play-Protect-Blocks: `android.permission.REQUEST_INSTALL_PACKAGES` war versehentlich erneut in `app/src/main/AndroidManifest.xml` eingetragen.
- Die Berechtigung wurde im Code-Commit `2060a03e20ea` wieder entfernt. Der bewährte `ACTION_VIEW`-Übergabepfad in `GithubUpdateCard.kt` wurde ausdrücklich nicht verändert.
- Versionscommit: `5dddd23680ef`; Version **1.29.132 / versionCode 190**.
- GitHub Actions [Build #439](https://github.com/Apropo1710/HomePilot/actions/runs/44147449227) für APK-Build-Quellcommit `73b8601c473b`: **completed / success**. Signaturprüfung erfolgreich.
- Release `latest`: Asset `HomePilot.apk`, Asset-ID `760164758`, 12.552.576 Bytes; aktualisiert 17.11.2026, 18:30:42 UTC.
- APK-SHA-256 laut automatisch gepflegtem Produktionsnachweis: `eb28ebfa36b0f697d7ec92452f3035ba2a47616f496acae15e4265b62d9140fe`.
- **Noch offen:** erneuter Play-Protect-Test und Nutzer-Endtest der installierten 1.29.132.
- Die dauerhafte Updater-Sperre bleibt bestehen: keine weiteren Änderungen an Updateprüfung, Download, Installer, Manifest-Berechtigungen, FileProvider/URI, Signierung, Update-UI oder Release-/Workflowlogik ohne ausdrückliche konkrete Nutzeranweisung.


# Projektstatus – HomePilot

## Roboter-Rückmeldungen und Roidmi-Beschriftung (17.11.2026)

- Xiaomi X20+: Nach erfolgreicher Annahme des Raumreinigungsbefehls erscheint direkt bei den Reinigungsbereichen die grüne Rückmeldung **„Reinigung gestartet“**. Eine nachgelagerte Statusprüfung kann sie weiterhin durch einen tatsächlichen Fehler ersetzen.
- Roidmi EVE Plus: Der erklärende Satz zum global einstellbaren Saug-/Wasserlevel wurde entfernt; Modus-, Saugstufen- und Wasserstufenanzeigen bleiben erhalten.
- Diese Änderungen sind in der aktuellen Version **1.29.132 / versionCode 190** enthalten.

## Aktueller verifizierter Produktionsstand – 18.11.2026, 22:45 MEZ

- Version aus `app/build.gradle.kts`: **1.30.2 / versionCode 193**.
- APK-Build-Quellcommit: `5abe795971b996ec7160676202b19ebaf96af7b2`; GitHub Actions Build **#442** (Run `44403463104`): **completed / success**, Signaturprüfung erfolgreich.
- Release `latest`: Asset `HomePilot.apk`, Asset-ID `765111340`, 13.713.155 Bytes; aktualisiert 18.11.2026, 21:41:22 UTC.
- APK-SHA-256: `17362222023b7aa9c9b5a8b3f46c3fdf7f3bb5cae943c2aa9f27ee808044d306`.
- In-App-Update auf diese Version vom Nutzer bestätigt (siehe „Gerätetest Updater und Play Protect“).
- APK: https://github.com/Apropo1710/HomePilot/releases/download/latest/HomePilot.apk

> Vorheriger Stand (03:09 MEZ, 1.29.132 ohne `REQUEST_INSTALL_PACKAGES`) ist überholt.

## Branchstatus außerhalb von `main` – 18.11.2026

- `feat/roidmi-eve-plus-options`: PR #1 ist gemergt; der Branch enthält weiterhin **3 Commits voraus / 252 zurück** gegenüber `main`. Zusätzliche Commits sind nicht automatisch als übernommen zu betrachten.
- `fix/update-installer-result`: **0 Commits voraus / 249 zurück**, kein offener PR; historischer Nebenbranch.
- `docs/status-refresh-2026-11-16`: PR #2 wurde geschlossen, nicht gemergt; abweichende Statusdatei, nicht ungeprüft übernehmen.
- Keine Nebenbranch wurde in dieser Statuspflege gemergt oder gelöscht. `main` bleibt maßgeblich.

## Roboter-Detailseiten – Rückmeldung bei Raumreinigung direkt an den Reinigungszonen (16.11.2026)
- Die dauerhafte Rückmeldung „Reinigung gestartet“ wurde aus der oberen Gerätestatus-Kachel entfernt.
- Xiaomi X20+ und Roidmi EVE Plus zeigen die Rückmeldung jetzt direkt im jeweiligen Bereich „Reinigungsbereiche“/„Reinigungszonen“, unmittelbar bei der Schaltfläche zum Starten.
- Eine erfolgreich bestätigte Meldung „Reinigung gestartet“ blendet sich nach fünf Sekunden automatisch aus. Noch ausstehende Prüfungen und Fehlermeldungen bleiben sichtbar, damit ein nicht bestätigter Start nicht als Erfolg erscheint und Fehler gelesen werden können.
- Reinigungsbefehle, Statusabfragen und Adapterkommunikation bleiben unverändert.
- Installationsversion **1.29.130 / versionCode 188**. Build #431 / Run `43924784448` ist **completed / success**; das Release-Asset `HomePilot.apk` wurde aktualisiert und die Signaturprüfung war erfolgreich. Details und SHA-256 stehen im aktuellen Produktionsblock oben.


## Historischer Abgleich – 16.11.2026, 03:02 MEZ (vor Version 1.29.130)

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
