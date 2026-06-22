# Reports — Plugin-Slice Brief (mc-platform-plugin)

> **Zweck:** Handoff vom Backend-Feature „Reports (#47)" an das Plugin-Repo. Dient als Referenz UND als
> Seed für `/speckit.specify` im Plugin-Repo. Das Backend (Source of Truth) ist fertig & gebaut; hier
> entsteht nur der **Client**: `/report`-Command, Team-Inbox-Menü, Chat-Ringpuffer, Live-Benachrichtigung.
>
> **Zuerst lesen (Plugin-Repo):** `.specify/memory/constitution.md`, `PROGRESS.md`, `docs/MENU_DESIGN.md`.
> **Eiserne Regeln:** Plugin = reiner Client (keine DB, kein Spring, keine direkten Redis-HASH-Reads);
> Writes über REST, Live lesend über Pub/Sub; Main-Thread nie blockieren; „ein Feature = ein Anstecken"
> (neues `feature.report`-Package + FeatureRegistry-Eintrag — **keine generische Klasse ändern**).

## Was migriert wird (Verhalten 1:1)
Ein Spieler meldet einen Mitspieler mit Kategorie + Grund (`/report`). Das Online-Team wird **live**
benachrichtigt und arbeitet Meldungen über ein Inbox-Menü ab (offen → in Bearbeitung → erledigt/abgelehnt).
Optional hängt die Meldung einen **Schnappschuss der letzten öffentlichen Chat-Nachrichten** an.

## Was WEGFÄLLT (vom alten 1.8.9-Code)
- Eigene RAM-Haltung der Reports → **Backend ist Wahrheit** (Plugin hält nur einen lokalen Lese-Cache).
- §-Farbcodes → **Adventure-Components**. Manuelles Inventory-Click-Handling → **MenuBuilder/MenuManager**.
- Kein NMS/Reflection.

## Scope (dieser Slice)
**IM SCOPE**
- `/report <spieler>` → Kategorie-Auswahl-Menü + Grund-Eingabe (Anvil/Chat-Input nach MENU_DESIGN) →
  REST-Create. Eingangsbestätigung an den Reporter.
- **Chat-Ringpuffer** (plugin-seitig, RAM): pro Spieler die letzten ~20 **öffentlichen** Chat-Nachrichten
  (Absender-UUID, Text, Zeitstempel). Beim Erstellen wird der aktuelle Schnappschuss des **gemeldeten
  Spielers samt umgebendem öffentlichem Chat** als `chatContext` mitgeschickt.
- **Team-Inbox-Menü** (LIVE): offene Reports auflisten, Detail ansehen (inkl. Chat-Kontext), Status setzen
  (in Bearbeitung / erledigt / abgelehnt). Beim Close sauber abmelden.
- **Live-Benachrichtigung**: auf `mc:report:changed` subscriben → bei neuem Report Online-Team pingen
  (Chat-Hinweis / Inbox-Badge).

**NICHT in diesem Slice** (Backend hat sie auch ausgeklammert)
- PNs (`/msg`) im Chat-Kontext (Datenschutz-Policy offen), Support/Tickets (#48), Lese-Sicht auf
  abgeschlossene Reports, Retention, Referenz Report→Punishment.

## Backend-Contract (steht, über `plugin-protocol` Maven Local)
Artefakt `com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT` ist publiziert. Im Plugin-Repo:
`./gradlew build --refresh-dependencies`. Alles unten ist JDK-only, im Package `com.mcplatform.protocol.report`.

**Endpoints (`ReportEndpoints` — über den bestehenden `BackendClient` + `EndpointDescriptor`, KEINE Pfad-Strings):**
- `CREATE` = `POST /api/reports` — Body `CreateReportRequest` → `ReportResponse` (offen, kein Permission-Gate)
- `LIST_OPEN` = `GET /api/reports/open` — Query `?staff=<uuid>` → `ReportResponse[]` (braucht `report.view`)
- `CHANGE_STATUS` = `POST /api/reports/{id}/status` — Body `ChangeStatusRequest` → `ReportResponse` (braucht `report.handle`)

**DTOs:**
- `CreateReportRequest(UUID reporter, UUID target, String category, String detail, List<ChatMessage> chatContext)`
  — `chatContext` darf null/leer sein.
- `ChatMessage(UUID sender, String text, long timestampEpochMilli)`
- `ChangeStatusRequest(String newStatus, UUID handledBy)`
- `ReportResponse(UUID id, UUID reporter, UUID target, String category, String detail, String status,
  long createdAtEpochMilli, UUID lastHandledBy, long lastStatusChangeAtEpochMilli,
  List<ChatMessage> chatContext, long version)`

**Live-Event (`ReportChannels.CHANGED = mc:report:changed`):** dekodieren über **dieselbe**
`PlatformProtocol.create()` wie das Backend (Report-Codec ist dort bereits registriert).
- `ReportChangedEvent(UUID reportId, UUID reporter, UUID target, String category, String status,
  String changeType /* CREATED|STATUS_CHANGED */, long timestampEpochMilli)` — **enthält keinen Chat-Kontext**.

**Enums (kanonische Wire-Werte; Anzeige-Labels im Menü auf Deutsch mappen):**
- Kategorie: `CHEATING`, `BELEIDIGUNG`, `SPAM_WERBUNG`, `TEAMING_BUG_ABUSE`, `SONSTIGES`
- Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `REJECTED`
  (Übergänge: OPEN→IN_PROGRESS, OPEN→REJECTED, IN_PROGRESS→RESOLVED, IN_PROGRESS→REJECTED)

**Fehler-Codes (HTTP → sinnvolle Client-Reaktion):**
- `422 report_invalid` (Self-Report, leeres/zu langes Detail, unbekannte Kategorie/Status, Chat zu groß)
- `429 report_cooldown` (zu schnell erneut → Wartezeit-Hinweis)
- `403 permission_denied` (kein `report.view`/`report.handle`)
- `404 report_not_found` · `409 report_conflict` (ungültiger Statusübergang / konkurrierende Änderung)

## Berechtigungen
- **Backend-autoritativ.** Erstellen ist für alle offen. Inbox/Status nur Team (`report.view`/`report.handle`).
- UI-Gate (Inbox-Befehl/Item nur für Team einblenden) ist **nur Komfort** — die echte Prüfung macht das Backend (403).

## Wiederverwenden (nicht neu bauen)
- `BackendClient` (REST über `EndpointDescriptor`), `EventBus` (version-aware Redis-Sub),
  `FeatureRegistry` (`Feature`-Interface), `MenuBuilder`/`MenuManager` (LIVE/STATIC, MENU_DESIGN.md),
  Scheduler-Abstraktion (async REST, Welt-Mutation zurück auf Main-Thread).
- Der Chat-Ringpuffer ist **neue feature-lokale Mechanik** in `feature.report` (Listener auf
  `AsyncChatEvent` o.ä.) — kein Eingriff in `transport`/`platform`.

## „Ein Anstecken"
Neues Package `feature.report` (lokaler Cache offener Reports, `/report`-Command, Inbox-Menü,
Chat-Ring-Listener, Channel-Handler für `mc:report:changed`) + **ein** Eintrag in der `FeatureRegistry`.
Wenn etwas eine generische Klasse ändern müsste → STOPP, als Muster-Leck melden.

## Definition of Done
- `/report` + Inbox-Menü funktionieren gegen das laufende Backend; Live-Ping bei neuem Report.
- Chat-Schnappschuss wird mitgeschickt und im Inbox-Detail angezeigt.
- `./gradlew build` (Plugin) grün; Tests für Transport-Anbindung + Ring-Buffer + Menü-Verhalten.
- Keine generische Klasse geändert; LIVE-Menü meldet sich beim Close ab.

---
### Als Spec-Kit-Seed verwenden
Im Plugin-Repo: Inhalt dieses Briefs (oder „migriere Reports-Plugin-Slice gemäß docs/REPORTS_PLUGIN_BRIEF.md")
als Argument an `/speckit.specify` geben; danach `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`.
