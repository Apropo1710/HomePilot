<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: e8c18709a67b2d83f4d4c671bae4ca649bc277df
- App-Version / versionCode: **1.31.8 / 202**
- Build-Zeit: 2026-11-28 15:50:12 UTC
- APK-Dateiname: **HomePilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: kein Actions-Artefakt; APK wird im GitHub-Release latest veröffentlicht
- APK-SHA-256: 7642b8d3ba04a6febc8f6409e1ab86a9dbca2e55dde148d2ed540ded9dedd387
- GitHub-Release: **erfolgreich veröffentlicht**; Asset HomePilot.apk wurde aktualisiert.
- Smartphone-Test: **nicht durch CI geprüft**; nur separat bestätigte Nutzerberichte gelten als Gerätetest.
- ChatGPT-Bibliotheksakte: separate Langzeit-/Historienakte; wird durch diesen Workflow nicht verändert.

<!-- AUTO-PRODUCTION-STATUS:END -->


# HomePilot – verbindlicher Projektstatus

**Letzter Abgleich:** 19.11.2026, 06:55 Uhr MEZ (Dokumentation bereinigt)
**Repository:** https://github.com/Apropo1710/HomePilot

Diese Datei ist die **einzige maßgebliche Statusquelle** (Produktion, Branches, Builds, Tests, Regeln, offene Punkte); es gibt keine weitere Statusdatei. Die README dient als Einstieg. Der aktuelle Code auf GitHub bleibt maßgeblich; diese Datei ersetzt nicht das Lesen des Codes.

## 1. Produktivstand – `main`

- Version, Quellcommit, APK-Hash und Release-Stand stehen ausschließlich im automatisch gepflegten Block am Dateianfang (wird vom Produktionsbuild geschrieben) und werden hier nicht wiederholt.
- Gerätetest 18.11.2026: 1.30.1 einmalig manuell installiert, danach Update 1.30.1 → 1.30.2 über den In-App-Updater. Installationsdialog öffnete sich, Play Protect meldete „Diese App ist anscheinend sicher“. Anmeldung und Daten blieben erhalten.
- [Direkter APK-Download](https://github.com/Apropo1710/HomePilot/releases/download/latest/HomePilot.apk)

## 2. Branches und Pull Requests

- Offene PRs: **keine**. PR #1 (`feat/roidmi-eve-plus-options`) am 05.11.2026 gemergt; PR #2 (`docs/status-refresh-2026-11-16`) ohne Merge geschlossen.
- Remote-Branches: nur `main` (`feature/bambu-status` am 28.11.2026 gelöscht).
- Releases: nur `latest` (Produktion, `HomePilot.apk`).
- Workflows: `.github/workflows/android.yml` baut bei Push auf `main` (außer `[skip ci]`) die Release-APK, veröffentlicht sie im Release `latest` und pflegt den automatischen Block oben. `.github/workflows/branch-validation.yml` prüft andere Branches (Unit-Tests, Release-Kotlin-Kompilierung) ohne APK oder Veröffentlichung. `.github/workflows/docs-guard.yml` prüft bei jedem Push und täglich, dass `PROJECT_STATUS.md` die einzige Statusdatei bleibt.

## 3. Offene Punkte

1. Kopplung zweier realer Smartphones und Abgleich von Raum, Gerät und Favorit zwischen ihnen; Einrichtung per QR-Code.
2. Roidmi EVE Plus: gezielter Hardwaretest einer Zeitplan-Schreibänderung (Synchronisation in beide Richtungen ist bestätigt).
3. Wartungs-Resets (X20+ und EVE Plus) am realen Gerät verifizieren.
4. MOVA: Anfahren des Wartungspunkts praktisch bestätigen.
5. Hue-Lampen (1.31.3/1.31.4): Schalten, Helligkeit, Farbtemperatur, Farbe und Farbauswahl am Gerät prüfen (Fall „Schrank“/`hue.0.Anrichte`).
6. BambuLab-Drucker: Statusseite und Druckverlauf am Gerät prüfen; der Druckverlauf im Adapter BambuLab Connect (auf `main`, PR #23) ist noch nicht am Drucker getestet.

## 4. Funktionsumfang

### Grundfunktionen
- Verbindung ausschließlich über ioBroker-WebSocket `ws.0` (Standard `ws://<IP>:8084`, `wss://` möglich). Simple API/HTTP (Port 8087) wird nicht verwendet.
- Geräteerkennung (Shelly, Hue automatisch; Tuya u. a. per manueller Suche), Räume anlegen/umbenennen/löschen, Geräte umbenennen, zuordnen, verschieben und ausblenden, Raumauswahl-Kachel, aufklappbare Einstellungsbereiche.
- Automatische Trennung im Hintergrund nach 15/30/60 Minuten oder nie; Wiederverbindung beim Zurückkehren in die App.
- Kopfbereich (HomePilot-Titel, Verbindungsstatus, Zurück) bleibt auf allen Seiten beim Scrollen oben stehen (seit 1.31.1).
- Appweiter Schalter „Diagnosetexte anzeigen“ (Standard aus) für technische Adapter-Rückmeldungen.
- Startup-Updatehinweis mit „Zu Einstellungen“/„Später“; Download und Installation nur in den Einstellungen.

### Konten, Konfiguration und Smartphone-Abgleich (seit 1.30.0)
- HomePilot-Konten (Administrator/Benutzer) mit lokaler Anmeldung, Passwörter nur als PBKDF2-Hash. Ohne lokale Konten automatische Übernahme aus ioBroker; sonst Adresseingabe oder QR-Einrichtung (Admin: Einstellungen → Benutzereinstellungen → „Anderes Smartphone einrichten“, 10 Minuten gültig).
- Speicherung in ioBroker: `0_userdata.0.HomePilot.accounts`, `…config` (Räume, Gerätenamen, Zuordnungen, Gerätetypen, ausgeblendete Geräte, übernommene Hue-Lampen) und `…profiles.<user>` (Favoriten, Reihenfolgen, Hintergrund-Timeout). Abgleich beim Verbinden und alle 30 s; höhere Revision gewinnt, dann neuere Änderungszeit, dann Inhalts-Digest.
- Smartphone-Kopplung per Ein-Scan-QR (Android-Keystore-ECDSA, signierte Speicherbestätigung), Paket `de.homepilot.app.sync` als Portierung aus KlimaPilot (`_homepilot-sync._tcp.`, Protokoll-Magic `HPT1`). Abgleich gekoppelter Geräte im Vordergrund alle 30 s; TCP authentifiziert, aber unverschlüsselt (Nutzerentscheidung, nur Heimnetz).
- Nicht umgesetzt aus dem früheren Entwurf: getrennte Sicherungsstände `backup.*` und ioBroker-ACL-Trennung pro Benutzer. Rollen werden in der App über die HomePilot-Konten abgebildet.

### Saugroboter und Mähroboter
- **Xiaomi X20+** (`roidmi.0.x20Plus.*`): Detailseite mit Start/Fortsetzen, Pause, Station, Reinigungsart (Reihenfolge wie Xiaomi Home: Saugen, Saugen und Wischen, Wischen, Saugen vor Wischen), Saugleistung, Wasserstufe, Teppich-Boost, Raumreinigung, Wartungskarte mit Reset (mit Bestätigung), Zeitpläne anzeigen, aktivieren und bearbeiten (Synchronisation in beide Richtungen bestätigt mit 1.29.120 und Adapter 0.1.63). Status kommt aus `status.statusText`; Faultcodes nur bei gemeldetem Fehler.
- **Roidmi EVE Plus** (`roidmi.0.*`): Status, Start/Pause/Stop/Station, Saug- und Wasserstufe, Reinigungsoptionen (`individual`, `doubleClean`, `pathType`), Raumreinigung über `control.cleanRooms`, Wartungskarte mit Reset, Zeitplaneditor mit Konfliktprüfung und Readback (Hardwaretest der Schreibänderung offen).
- Rückmeldung „Reinigung gestartet“ bei beiden Robotern direkt an den Reinigungsbereichen; bestätigte Meldung verschwindet nach 5 s, Fehler bleiben sichtbar. Kartenreihenfolge: Status → Steuerung → Einstellungen → Reinigungsbereiche → (X20+: Zeitpläne) → Wartung.
- **MOVA**: Mäher-Kachel und Wartungsbereich (Messer- und Gerätewartung).

### 3D-Drucker (BambuLab, seit 1.31.0)
- Nur Anzeige, keine Steuerung (Nutzerentscheidung). Eigene Seite „3D-Drucker“ über einen eigenen Menüpunkt in der unteren Leiste (seit 1.31.1; keine Startseiten-Kachel mehr). Drucker werden aus den Geräteobjekten von `bambulab-connect.0` gelesen; Aktualisierung alle 10 s, solange die Seite offen ist, Verlauf beim Öffnen und jede Minute. Es werden nur ioBroker-Zustände gelesen, keine Druckerabfragen ausgelöst.
- Angezeigt: `status.gcode_state`, `info.connection`, `print.mc_percent`, `print.mc_remaining_time`, `print.finishTime`, `print.subtask_name`, `print.layer_num`/`total_layer_num`, `temperature.nozzle_temper`/`bed_temper`/`chamber_temper`, `hms.hmsErrorCode`.
- Druckverlauf aller Drucker aus `<Drucker>.history.jobs` (vom Adapter geschrieben, letzte 50 pro Drucker). Fehlt der Datenpunkt, zeigt die App einen Hinweis statt eines leeren Verlaufs. Drucke, die enden, während der Adapter nicht läuft, fehlen im Verlauf.

## 5. Updater – absolute Sperre

**Der HomePilot-Updater darf niemals verändert werden**, auch nicht nebenbei bei Feature-Arbeiten, Fehlerkorrekturen oder Aufräumarbeiten. Nur eine ausdrückliche, konkrete Nutzeranweisung, die genau die gewünschte Updater-Änderung benennt, hebt die Sperre auf.

Geschützt sind: Updateprüfung und Versionsvergleich, GitHub-Abruf, APK-Download und Paketprüfung, Installer-Übergabe (Intent, MIME-Type, FileProvider, URI, Flags, ClipData), Manifest-Berechtigungen, Signierung/Keystore, Update-Oberfläche und Startup-Hinweis sowie die Release-Logik in `android.yml`.

**Geräte-getesteter Referenzstand: 1.30.2 / 193.** `ACTION_VIEW` + `application/vnd.android.package-archive` + FileProvider + `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK` + `ClipData` und **`REQUEST_INSTALL_PACKAGES` im Manifest** (identisch mit KlimaPilot). Ohne die Berechtigung öffnet „Update laden“ keinen Installationsdialog – sie darf auch bei einer Play-Protect-Warnung nicht entfernt werden.

Arbeitsweise:
- Jeden Diff auf direkte und indirekte Updater-Änderungen prüfen; bei möglichem Konflikt anhalten und den Nutzer informieren.
- Für installierbare Versionen `versionName` und `versionCode` erhöhen; Signatur muss zur installierten App passen.
- Download-Erfolg ist kein Nachweis für den Installationsdialog. Updater, Play Protect und Installation gelten nur durch Gerätetest des Nutzers als bestätigt, nicht durch CI.

## 6. Verbindliche Regeln

- Alles direkt auf `main`, keine Feature-Branches; Updates über den In-App-Updater (Nutzerentscheidung 28.11.2026). Keine separaten Testversionen.
- Vor Änderungen aktuellen Stand von `main` und betroffenen Code lesen; Änderungen klein und gezielt; Diff vor dem Push prüfen; danach Produktionsbuild und Release kontrollieren.
- Reine Dokumentationscommits auf `main` mit `[skip ci]` kennzeichnen.
- Nichts als gebaut, getestet oder funktionierend darstellen, was nicht tatsächlich verifiziert ist.
- HomePilot und KlimaPilot strikt getrennt behandeln; Klimaanlagenfunktionen nicht in HomePilot voraussetzen.
- Neue Stände in die passenden Abschnitte dieser Datei einarbeiten und überholte Aussagen ersetzen, keine zusätzlichen Abgleich-Abschnitte anhängen.
- Arbeitsregeln für Menschen und KI-Werkzeuge stehen zusätzlich in `AGENTS.md` (`CLAUDE.md` verweist darauf).
- Version, Build und Release-Stand nicht von Hand pflegen – sie stehen nur im automatischen Block.

## 7. Technische Entscheidungen und Grenzen

- Hue (`hue.0`, seit 1.31.3): Als Lampe gilt nur der `on`-Datenpunkt einer Lampe (`switch.light`) oder Gruppe (mit `anyOn`/`bri`); `anyOn`, `allOn`, Szenen und Sensor-Schalter werden nicht mehr als Lampen angeboten. Früher übernommene Nebendatenpunkte werden beim Verbinden auf `on` daneben umgestellt (Name, Raum, Art, Favorit wandern mit). Der Adapter führt `ct` in Kelvin, `hue` in 0–360°, `sat` und `bri` in 0–254; die App rechnet entsprechend um. Im Dialog „Gerät bearbeiten“ steht der verwendete Datenpunkt. Seit 1.31.4 gibt es bei Hue-Lampen auf Startseite, Raum- und Lichtseite ein Auswahlmenü „Lichtfarbe auswählen“ (Warmweiß 2700 K, Weiß 4000 K, Kaltweiß 6500 K, Rot, Orange, Gelb, Grün, Türkis, Blau, Violett, Pink); eine ausgeschaltete Lampe wird dabei eingeschaltet. Seit 1.31.7 gibt es keine Regler mehr für Farbtemperatur, Farbton und Sättigung; Farben nur über das Menü, Helligkeit weiter per Regler (Nutzerwunsch). Seit 1.31.8 zeigt der Knopf die aktuelle Lichtfarbe („Lichtfarbe: Warmweiß“): im Weißton-Modus (`colormode` = ct) die nächstliegende Weißstufe nach Kelvin, sonst die nächstliegende Farbe nach Farbton, kaum gesättigt gilt als Weiß; eine gerade gewählte Farbe steht sofort da. Seit 1.31.5 heißen neu gefundene Hue-Lampen ohne „.on“, Licht- und Geräteseite zeigen Geräte ohne Raum (Raummenü „Ohne Raum“, Geräteseite seit 1.31.6), und jede Kachel der Raumansicht auf der Startseite hat einen Bearbeiten-Stift.

- `ws://` ohne Zertifikat im vertrauenswürdigen Heimnetz ist ausdrücklich gewollt; WSS/TLS wird nicht erzwungen.
- SENEC/Energiefluss-Adapter nicht als Datenquelle einplanen (Last).
- `mhi-wfrac` erst nach Installation der Klimaanlage berücksichtigen.
- Hue-Bewegungsmelder im Flur vorerst nicht einplanen.
- Waschmaschine verwaltbar, aber kein Testgerät; schaltbar ggf. später, ohne eigenen Release-Anlass.
- Roboter: keine zusätzlichen zyklischen Abfragen; Schreib- und Prüfoperationen nur bei Nutzeraktion. Keine Werte erfinden, die der Adapter nicht liefert (z. B. m² beim EVE Plus, Wischmopp-Zähler beim EVE Plus).
- Plugin-System nur als langfristige Idee.

## 8. Verlauf (kompakt)

- **27.11.2026 – 1.31.2:** Druckerkarten kompakter (Status oben rechts, online grün), Fortschrittsbalken mit fließendem Farbverlauf Rot → Grün; offline kein veralteter Auftrag. Online-Status braucht BambuLab Connect mit Fix für `info.connection` (PR #24).
- **27.11.2026 – 1.31.1:** 3D-Drucker als eigener Menüpunkt unten statt Startseiten-Kachel; feststehender Kopfbereich beim Scrollen.
- **27.11.2026 – 1.31.0:** BambuLab-Drucker als reine Statusanzeige mit Druckverlauf (Kachel und Detailseite).
- **18.11.2026 – 1.30.2:** Lila/Rosa durch HomePilot-Blau ersetzt, Kopplungshinweis entfernt.
- **18.11.2026 – 1.30.1:** Updater an KlimaPilot angeglichen, `REQUEST_INSTALL_PACKAGES` wieder eingetragen (ausdrückliche Nutzeranweisung). Versionen ohne Berechtigung (1.29.132, 1.30.0) mussten einmalig manuell aktualisiert werden.
- **18.11.2026 – 1.30.0:** Smartphone-Kopplung, Smartphone-Sync und neue Anmeldung aus KlimaPilot übernommen.
- **17.11.2026 – 1.29.132:** Berechtigung wegen Play Protect entfernt – Annahme war falsch, durch 1.30.1 überholt. Rückmeldung „Reinigung gestartet“ (X20+), Roidmi-Beschriftung bereinigt.
- **16.11.2026 – 1.29.126–1.29.130:** „m² bearbeitet“ (X20+), Startrückmeldung an den Reinigungszonen, X20+-Status aus `statusText`.
- **12.11.2026 – 1.29.105–1.29.121:** X20+-Detailseite, Zeitpläne und Wartung, Diagnoseschalter, Wartungs-Resets, EVE-Plus-Zeitplaneditor.
- **05.11.2026 – 1.29.83–1.29.103:** Roidmi-Steuerung und -Optionen, mehrere Updater-Versuche (`ACTION_INSTALL_PACKAGE`, MediaStore, `ACTION_VIEW`) – alle überholt durch den Referenzstand 1.30.2.
- **01.–03.11.2026 – bis 1.29.57:** Roidmi-Integration über den eigenen Adapter, ioBroker-Wiederverbindung, Hintergrundtrennung, Raumauswahl, Einstellungsseite.
- **31.10.2026:** Erste zentrale Projektdokumentation.
