# Regeln für die Arbeit an diesem Repository

Diese Datei gilt für jeden, der hier Änderungen macht – Menschen und KI-Werkzeuge (Claude, ChatGPT/Codex, Copilot …). Sie wird bei jeder Sitzung zuerst gelesen und hat Vorrang vor eigenen Gewohnheiten.

## Projekt in einem Satz

HomePilot ist eine Android-App (Kotlin, Jetpack Compose) zur Steuerung und Verwaltung von ioBroker-Geräten über den WebSocket-Adapter `ws.0`.

## Vor jeder Änderung lesen

1. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) – einzige Statusquelle, insbesondere **„Updater – absolute Sperre“**, **„Verbindliche Regeln“** und **„Technische Entscheidungen und Grenzen“**. Diese Regeln gelten vollständig; diese Datei wiederholt sie nicht.
2. Den aktuellen Stand von `main` und die betroffenen Quelldateien.

## Unverhandelbar

- **Der Updater darf niemals verändert werden** – auch nicht nebenbei bei Features, Fehlerkorrekturen oder Aufräumarbeiten. Nur eine ausdrückliche, konkrete Nutzeranweisung, die genau diese Updater-Änderung benennt, hebt die Sperre auf.
- **`PROJECT_STATUS.md` ist die einzige Statusdatei.** Keine weiteren Status-, Plan-, Übergabe-, TODO- oder Notizdateien anlegen, auch nicht „vorübergehend“. Neue Stände in die passenden Abschnitte einarbeiten und überholte Aussagen ersetzen, keine zusätzlichen Abgleich-Abschnitte anhängen. Der Workflow „Docs guard“ schlägt fehl, wenn eine weitere solche Datei auftaucht.
- **Den automatisch gepflegten Block** am Anfang von `PROJECT_STATUS.md` (Version, Build, Release) nie von Hand ändern.
- **Ablauf:** Entwicklung direkt auf `main`; größere Funktionen vorher auf einem Feature-Branch mit der Branch-Prüfung. Reine Dokumentationscommits auf `main` mit `[skip ci]` kennzeichnen.
- **Nichts als gebaut, getestet oder funktionierend darstellen, was nicht verifiziert ist.** CI-Ergebnis und Gerätetest klar trennen.
- **HomePilot und KlimaPilot strikt getrennt halten.**
- **Roboter und andere Geräte:** keine zusätzlichen zyklischen Abfragen; keine Werte erfinden, die ein Adapter nicht liefert.

## Dokumentation – feste Plätze

- `README.md` – Einstieg und Funktionen, keine zweite Statusübersicht
- `PROJECT_STATUS.md` – Stand, Branches, offene Punkte, Regeln, Entscheidungen

## Keine Reste

Veraltete Branches, Releases, Workflows, Dateien und Code werden entfernt, nicht liegen gelassen oder „als veraltet markiert“.

## Sicherheit

Keystore, Signaturschlüssel, Passwörter, Tokens und persönliche Daten gehören nie in Git, Logs, Issues oder Screenshots.
