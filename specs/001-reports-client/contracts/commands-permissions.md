# Contract: Command-, Permission- & Chat-Input-Surface (Client)

Additive Deklarationen in `src/main/resources/plugin.yml` (kein generischer Code). UI-Gates sind reiner Komfort — die echte Prüfung erfolgt backend-autoritativ (403).

## Commands (plugin.yml)

```yaml
commands:
  report:
    description: Einen Mitspieler melden.
    usage: /report <spieler>
    # bewusst KEIN permission-Gate: Erstellen ist für alle offen
  reports:
    description: Team-Inbox offener Meldungen öffnen.
    usage: /reports
    permission: mcplatform.report.view   # UI-Gate (Komfort); Backend bleibt autoritativ
```

- `report` → `ReportCommand` (registriert via `context.registerCommand("report", …)`).
- `reports` → `ReportInboxCommand` (registriert via `context.registerCommand("reports", …)`).

## Permissions (plugin.yml)

```yaml
permissions:
  mcplatform.report.view:
    description: Report-Inbox sehen + Live-Benachrichtigung erhalten (UI-Gate).
    default: op
  mcplatform.report.handle:
    description: Report-Status setzen (UI-Gate).
    default: op
```

- `mcplatform.report.view`: blendet `/reports` ein und bestimmt die **Empfänger der Live-Benachrichtigung** (Ping an alle online-Träger). Backend prüft `report.view` real (403).
- `mcplatform.report.handle`: blendet im Detail-Menü die Status-Buttons ein. Backend prüft `report.handle` real (403).

## `/report <spieler>` — Ablauf-Contract

1. Sender muss Spieler sein; `args.length == 1`, sonst Usage-Hinweis.
2. Ziel auflösen (online per Name). Nicht auflösbar → `ReportFormat`-Hinweis, Abbruch.
3. `scheduler.runSync` (bereits Main): `menus.open(sender, new ReportCategoryMenu(...).menu())` — STATIC.
4. Kategorie-Klick → `ReportReasonPrompt.begin(reporter, target, categoryWire)`; `view.close()`; Chat-Prompt senden („Bitte gib jetzt den Grund im Chat ein. Zum Abbrechen: `abbrechen`.“).
5. Nächste öffentliche Chat-Nachricht des Reporters wird von `ReportChatInputListener` (LOWEST, cancelt) konsumiert:
   - `abbrechen` → `ReportReasonPrompt.cancel`, Abbruch-Feedback.
   - sonst → `CreateReportRequest` bauen (inkl. `ChatRingBuffer.snapshot()`) → CREATE (async) → Bestätigung (sync).

## `/reports` — Ablauf-Contract

1. UI-Gate via `permission` (Komfort). 2. `menus.open(viewer, new ReportInboxMenu(...).menu())` — LIVE (`LiveBinding(INBOX_TOPIC, onChange)`).
3. Async `LIST_OPEN(staff=viewer)` → `OpenReportCache` → `view.refresh()`.
4. Listeneintrag-Klick → `ReportDetailMenu` (aus Cache; chatContext bereits enthalten).
5. Menü-Close → `MenuManager` schließt das `LiveBinding`-`LiveHandle` automatisch (keine Beobachter-Leaks).

## Nicht-Ziele dieses Contracts

- Keine neuen Transport-/EventBus-/Menu-Framework-APIs. Alle obigen Punkte nutzen ausschließlich bestehende Methoden (`registerCommand`, `registerListener`, `menus.open`, `MenuBuilder.*`, `eventBus().subscribe`, `backend.call*`, `scheduler.run*`).
