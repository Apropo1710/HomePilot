<!-- AUTO-PRODUCTION-STATUS:START -->
## Automatisch gepflegter Produktionsstatus

- Quellcommit des APK-Builds: 2a2b1e822568199bce1dc1596489bc3abde26f87
- App-Version / versionCode: **1.31.0 / 194**
- Build-Zeit: 2026-11-27 01:51:26 UTC
- APK-Dateiname: **HomePilot.apk**
- APK-Signaturprüfung: erfolgreich (apksigner verify --print-certs)
- Build-Artefakt: HomePilot-release-apk, ID 15166480319, 13127501 Bytes (manueller Build)
- APK-SHA-256: 4f338d812ce538f7b6fdba055b5119369b534a57429e38398702a8dbf115b21b
- GitHub-Release: Veröffentlichung wird in diesem Workflow nach dem Statuscommit abgeschlossen.
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
- Remote-Branches: `main`; `feature/bambu-status` nur bis zur Übernahme der 3D-Drucker-Anzeige. Alle alten Feature- und Dokumentationsbranches sowie `STATUS.md` wurden am 19.11.2026 gelöscht.
- Releases: nur `latest` (Produktion, `HomePilot.apk`).
- Workflows: `.github/workflows/android.yml` baut bei Push auf `main` (außer `[skip ci]`) die Release-APK, veröffentlicht sie im Release `latest` und pflegt den automatischen Block oben. `.github/workflows/branch-validation.yml` prüft andere Branches (Unit-Tests, Release-Kotlin-Kompilierung) ohne APK oder Veröffentlichung. `.github/workflows/docs-guard.yml` prüft bei jedem Push und täglich, dass `PROJECT_STATUS.md` die einzige Statusdatei bleibt.

## 3. Offene Punkte

1. Kopplung zweier realer Smartphones und Abgleich von Raum, Gerät und Favorit zwischen ihnen; Einrichtung per QR-Code.
2. Roidmi EVE Plus: gezielter Hardwaretest einer Zeitplan-Schreibänderung (Synchronisation in beide Richtungen ist bestätigt).
3. Wartungs-Resets (X20+ und EVE Plus) am realen Gerät verifizieren.
4. MOVA: Anfahren des Wartungspunkts praktisch bestätigen.
5. BambuLab-Drucker (1.31.0): Statusanzeige und Druckverlauf am Gerät prüfen. Der Verlauf braucht den Adapter-Branch `feature/print-history` (BambuLab Connect, PR #23), der noch nicht am Drucker getestet ist.

## 4. Funktionsumfang

### Grundfunktionen
- Verbindung ausschließlich über ioBroker-WebSocket `ws.0` (Standard `ws://<IP>:8084`, `wss://` möglich). Simple API/HTTP (Port 8087) wird nicht verwendet.
- Geräteerkennung (Shelly, Hue automatisch; Tuya u. a. per manueller Suche), Räume anlegen/umbenennen/löschen, Geräte umbenennen, zuordnen, verschieben und ausblenden, Raumauswahl-Kachel, aufklappbare Einstellungsbereiche.
- Automatische Trennung im Hintergrund nach 15/30/60 Minuten oder nie; Wiederverbindung beim Zurückkehren in die App.
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
- Nur Anzeige, keine Steuerung (Nutzerentscheidung). Drucker werden aus den Geräteobjekten von `bambulab-connect.0` gelesen; Kachel „3D-Drucker“ auf der Startseite (alle 30 s), Detailseite (alle 10 s, Verlauf beim Öffnen und jede Minute). Es werden nur ioBroker-Zustände gelesen, keine Druckerabfragen ausgelöst.
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

- Entwicklung direkt auf `main` für die Hauptversion; größere Funktionen können vorher auf einem Feature-Branch mit `branch-validation.yml` geprüft werden. Keine separaten Testversionen.
- Vor Änderungen aktuellen Stand von `main` und betroffenen Code lesen; Änderungen klein und gezielt; Diff vor dem Push prüfen; danach Produktionsbuild und Release kontrollieren.
- Reine Dokumentationscommits auf `main` mit `[skip ci]` kennzeichnen.
- Nichts als gebaut, getestet oder funktionierend darstellen, was nicht tatsächlich verifiziert ist.
- HomePilot und KlimaPilot strikt getrennt behandeln; Klimaanlagenfunktionen nicht in HomePilot voraussetzen.
- Neue Stände in die passenden Abschnitte dieser Datei einarbeiten und überholte Aussagen ersetzen, keine zusätzlichen Abgleich-Abschnitte anhängen.
- Arbeitsregeln für Menschen und KI-Werkzeuge stehen zusätzlich in `AGENTS.md` (`CLAUDE.md` verweist darauf).
- Version, Build und Release-Stand nicht von Hand pflegen – sie stehen nur im automatischen Block.

## 7. Technische Entscheidungen und Grenzen

- `ws://` ohne Zertifikat im vertrauenswürdigen Heimnetz ist ausdrücklich gewollt; WSS/TLS wird nicht erzwungen.
- SENEC/Energiefluss-Adapter nicht als Datenquelle einplanen (Last).
- `mhi-wfrac` erst nach Installation der Klimaanlage berücksichtigen.
- Hue-Bewegungsmelder im Flur vorerst nicht einplanen.
- Waschmaschine verwaltbar, aber kein Testgerät; schaltbar ggf. später, ohne eigenen Release-Anlass.
- Roboter: keine zusätzlichen zyklischen Abfragen; Schreib- und Prüfoperationen nur bei Nutzeraktion. Keine Werte erfinden, die der Adapter nicht liefert (z. B. m² beim EVE Plus, Wischmopp-Zähler beim EVE Plus).
- Plugin-System nur als langfristige Idee.

## 8. Verlauf (kompakt)

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
