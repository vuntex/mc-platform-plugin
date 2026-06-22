# Quickstart: Reports Plugin-Slice

## Voraussetzungen

- Java 21, Gradle Wrapper im Repo.
- Protocol-Artefakt in Maven Local: `com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT` (enthält `com.mcplatform.protocol.report`, verifiziert).
- Laufendes Backend (REST + Redis) für End-to-End.

```bash
# Abhängigkeiten inkl. Report-Protocol frisch ziehen
./gradlew build --refresh-dependencies
```

## Bauen & Tests

```bash
./gradlew build            # kompiliert + Unit-Tests + Shadow-JAR
./gradlew test --tests "com.mcplatform.plugin.feature.report.*"
```

Artefakt: `build/libs/mc-platform-plugin-0.1.0-SNAPSHOT.jar` → in `plugins/` des Paper-1.21-Servers.

## Konfiguration

`config.yml` (bestehend) liefert Backend-URL + Redis. Keine neuen Schlüssel nötig.

## Manuelle Verifikation (gegen laufendes Backend)

**P1 — Erstellen**
1. Als Spieler A: `/report B` → Kategorie-Menü erscheint (5 DE-Labels).
2. Kategorie klicken → Menü schließt, Chat-Prompt erscheint.
3. Grund tippen → Bestätigung; im Backend liegt ein offener Report mit korrekter Kategorie/Reporter/Ziel/Grund.
4. Vorher etwas öffentlich chatten → der Report enthält `chatContext` (Snapshot des globalen Fensters).
5. Self-Report (`/report A`) → 422-Meldung; sofort erneut melden → 429-Wartehinweis.

**P2 — Inbox** (Account mit `mcplatform.report.view`/`.handle`)
1. `/reports` → paginierte Liste aller offenen Reports (neueste zuerst).
2. Eintrag öffnen → Detail inkl. Chat-Kontext.
3. „In Bearbeitung“ → Confirm → Status gesetzt; „Erledigt“/„Ablehnen“ analog.
4. Ungültiger Übergang / paralleler Wechsel → 409-Meldung, Ansicht konsistent.
5. Inbox schließen → keine Beobachter-Leaks (siehe `ReportInboxMenuTest`).
6. Nicht-Team-Account → `/reports` ggf. ausgeblendet (UI-Gate); erzwungener LIST_OPEN → 403-Meldung.

**P3 — Live**
1. Team-Account online lassen; mit anderem Spieler einen Report erstellen → Chat-Zeile + Ton beim Team.
2. Offene Inbox aktualisiert sich live (neuer Report erscheint; nicht-offener verschwindet) ohne Neuöffnen.

## Definition of Done (aus Brief + Spec)

- `/report` + Inbox laufen gegen das Backend; Live-Ping bei neuem Report.
- Chat-Schnappschuss wird gesendet und im Detail angezeigt.
- `./gradlew build` grün; Tests für Transport-Anbindung, Ring-Buffer, Reason-Prompt, Menü-Verhalten/Pagination, Live-Updater.
- **Keine generische Klasse geändert** (Diff-Check: nur `feature/report/*` neu + 1 Zeile `McPlatformPlugin` + `plugin.yml`); LIVE-Inbox meldet sich beim Close ab.
