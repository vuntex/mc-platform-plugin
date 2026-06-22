# Implementation Plan: Reports — Plugin-Slice (Paper-1.21-Client)

**Branch**: `001-reports-client` | **Date**: 2026-06-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-reports-client/spec.md`

## Summary

Client-only Migration des Reports-Features (#47) als drittes `PluginFeature`. Ein Spieler meldet per `/report <spieler>` (STATIC-Kategorie-Menü → Chat-Input-Grund) gegen den fertigen Backend-Contract; das Online-Team (Träger von `mcplatform.report.view`) wird live über `mc:report:changed` benachrichtigt und arbeitet die geteilte Warteschlange über ein LIVE-Inbox-Menü ab. Ein feature-lokaler, globaler Chat-Ringpuffer (~20 öffentliche Nachrichten) liefert den `chatContext`-Schnappschuss.

**Technischer Ansatz**: Komplett additiv im neuen Package `feature.report` + **einem** `FeatureRegistry`-Eintrag. **Keine generische Klasse wird geändert** — verifiziert gegen alle sechs im Auftrag genannten Bausteine (siehe Constitution Check + research.md §Pattern-Leak-Audit). Beide in der Spec offenen Risiken sind aufgelöst: das Protocol-Paket `com.mcplatform.protocol.report` ist im Artefakt vorhanden, und 403/429 sind feature-lokal über `BackendException.statusCode()` unterscheidbar (kein generischer Eingriff).

## Technical Context

**Language/Version**: Java 21 (Paper API `1.21.10-R0.1-SNAPSHOT`, `compileOnly`)

**Primary Dependencies** (alle vorhanden, keine neuen): `com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT` (geteiltes Artefakt, **wird nicht verändert**), Adventure (in Paper gebündelt), Gson 2.11 (geshaded, hinter `JsonCodec`), Lettuce 6.5 (geshaded, hinter `EventBus`)

**Storage**: Keine. Backend ist Source of Truth. Einziger lokaler Zustand: RAM — globaler Chat-Ringpuffer + Lese-Cache offener Reports (beides feature-lokal, nicht persistent).

**Testing**: JUnit 5 (`junit-bom:5.11.3`, `useJUnitPlatform()`); Muster wie `EconomyBalancesTest`/`TransactionHistoryMenuTest`. Kein Testcontainers nötig (keine Transport-Änderung).

**Target Platform**: Paper-1.21-Server-JVM (Java 21)

**Project Type**: Single Gradle module (Bukkit/Paper-Plugin) — Shadow-JAR

**Performance Goals**: Main-Thread NIE blockieren (alle REST/Redis async); Live-Ping < 2 s (SC-004); Inbox-Live-Refresh < 2 s (SC-005); vollständiger Melde-Flow < 30 s (SC-001)

**Constraints**: Adventure-Components only (keine §-Codes); kein NMS/Reflection; kein manuelles Inventory-Handling (nur Menü-Framework); **keine generische Klasse ändern**; `protocol` unverändert

**Scale/Scope**: Kleines Team; typischerweise zehner-Bereich offener Reports; ~20er Chat-Ring server-weit

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` ist ein **unausgefülltes Template** (keine ratifizierten Prinzipien). Die bindenden Leitplanken sind daher die Architektur-Regeln aus `docs/REPORTS_PLUGIN_BRIEF.md` und `docs/MENU_DESIGN.md`. Diese werden als Gates geprüft:

| Gate (de-facto-Constitution) | Status | Begründung |
|---|---|---|
| **G1 — „Ein Anstecken": neues Package + EIN Registry-Eintrag, keine generische Klasse geändert** | ✅ PASS | Nur `feature.report/*` neu; einzige Edits an Bestand: 1 `.register(...)`-Zeile in `McPlatformPlugin` (sanktionierter Composition-Root-Erweiterungspunkt, keine generische Logik) + additive Deklarationen in `plugin.yml`. Pattern-Leak-Audit: research.md §6. |
| **G2 — Plugin = reiner Client (keine DB/Spring/direkte Redis-HASH-Reads)** | ✅ PASS | Schreiben via `BackendClient`/REST, Live-Lesen via `EventBus`/Pub-Sub. Lokal nur RAM-Cache. |
| **G3 — Writes über REST via `EndpointDescriptor`, KEINE Pfad-Strings im Feature-Code** | ✅ PASS | Nur `ReportEndpoints.CREATE/LIST_OPEN/CHANGE_STATUS` aus dem Artefakt. |
| **G4 — JSON-(De)Serialisierung an der bestehenden EINEN Stelle** | ✅ PASS | Erfolgt in `HttpBackendClient`/`JsonCodec` bzw. `MessageProtocol`; Feature sieht nur typisierte Records. |
| **G5 — Live decode über dieselbe `PlatformProtocol.create()` wie Backend** | ✅ PASS | `EventBus` ist bereits mit `PlatformProtocol.create()` verdrahtet (Report-Codec registriert); Feature ruft nur `subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, …)`. |
| **G6 — Main-Thread NIE blockieren (Scheduler-Abstraktion)** | ✅ PASS | REST/Redis via `scheduler.runAsync`; Bukkit-/Inventory-Mutation via `scheduler.runSync`. |
| **G7 — Adventure-Components, kein §-Code, kein NMS, MenuBuilder/MenuManager nach MENU_DESIGN** | ✅ PASS | Alle UI über `MenuBuilder`/`MenuText`/`IconSpec`/`Lore`; LIVE-Inbox via `.live(LiveBinding)`, Abmeldung durch `MenuManager` beim Close. |
| **G8 — `protocol` (Backend-Contract) unverändert** | ✅ PASS | Nur lesend konsumiert. |

**Ergebnis: Alle Gates PASS.** Kein Eintrag in Complexity Tracking nötig.

## Project Structure

### Documentation (this feature)

```text
specs/001-reports-client/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, alternatives, pattern-leak audit
├── data-model.md        # Phase 1: entities, state machine, cache model
├── contracts/           # Phase 1: consumed REST/event contract + command/permission surface
│   ├── report-endpoints.md
│   └── commands-permissions.md
├── quickstart.md        # Phase 1: build, run-against-backend, test
├── checklists/
│   └── requirements.md   # (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/mcplatform/plugin/
├── platform/
│   ├── McPlatformPlugin.java          # EDIT (additive): +1 .register(new ReportFeature(menus))
│   └── menu/                          # REUSE as-is (MenuBuilder, MenuManager, LiveBinding, …)
├── transport/                        # REUSE as-is (BackendClient, EventBus, BackendException)
└── feature/
    └── report/                        # NEW — entire feature lives here
        ├── ReportFeature.java          # PluginFeature: id "report", onEnable wires all of the below
        ├── ChatRingBuffer.java         # global bounded ring of ChatMessage (thread-safe), snapshot()
        ├── PublicChatListener.java     # AsyncPlayerChatEvent (MONITOR, ignoreCancelled) → ring append
        ├── ReportReasonPrompt.java     # pending-input state map (reporter → {target, category})
        ├── ReportChatInputListener.java# AsyncPlayerChatEvent (LOWEST) → consume reason / cancel-word
        ├── ReportCommand.java          # /report <spieler> → resolve target → open category menu
        ├── ReportCategoryMenu.java     # STATIC: 5 category buttons → set pending + chat prompt
        ├── ReportInboxCommand.java     # /reports → open LIVE inbox (UI-gated mcplatform.report.view)
        ├── ReportInboxMenu.java        # LIVE list: paginated open reports (renderPage), live refresh
        ├── ReportDetailMenu.java       # one report: fields + chatContext + status-transition buttons
        ├── OpenReportCache.java        # local read-cache (reportId → ReportResponse), version-aware
        ├── ReportLiveUpdater.java       # Consumer<ReportChangedEvent>: ping (CREATED) + inbox refresh
        ├── ReportNotifier.java          # ping online holders of mcplatform.report.view (chat + sound)
        ├── ReportFormat.java            # DE category/status labels, time, chat-line, error→message (403/422/429/404/409/5xx)
        └── ReportSession.java           # PlayerQuitListener: drop pending prompt on quit

src/main/resources/plugin.yml          # EDIT (additive): +commands report,reports; +permissions

src/test/java/com/mcplatform/plugin/feature/report/
├── ChatRingBufferTest.java
├── ReportReasonPromptTest.java
├── ReportFormatTest.java
├── ReportInboxMenuTest.java            # pagination + allowed status-transition buttons + LIVE binding/unsubscribe
└── ReportLiveUpdaterTest.java          # CREATED → ping path; STATUS_CHANGED → cache/refresh; version staleness
```

**Structure Decision**: Single Gradle module; das gesamte Feature ist auf `feature/report/` beschränkt. Die einzigen Berührungen von Bestandsdateien sind **additiv** (ein Registry-Eintrag, Command-/Permission-Deklarationen) — keine Änderung generischer Logik. Schichtung wie etabliert: `platform → transport → feature.report → protocol (shared)`.

## Phasenüberblick (Implementierung)

Reihenfolge entlang der Story-Prioritäten der Spec (jede Stufe eigenständig lauffähig/testbar):

1. **P1 — Erstellen**: `ChatRingBuffer` + `PublicChatListener` → `ReportFormat` → `ReportCategoryMenu` + `ReportReasonPrompt` + `ReportChatInputListener` → `ReportCommand` → `ReportFeature`(create-Teil) + `plugin.yml`(report) + Registry-Eintrag. Liefert MVP: melden + `chatContext` + Bestätigung.
2. **P2 — Inbox**: `OpenReportCache` → `ReportInboxMenu` (LIST_OPEN, Pagination) → `ReportDetailMenu` (CHANGE_STATUS, erlaubte Übergänge) → `ReportInboxCommand` + `plugin.yml`(reports, perms).
3. **P3 — Live**: `ReportNotifier` + `ReportLiveUpdater` → `eventBus().subscribe(ReportChannels.CHANGED, …)` in `ReportFeature.onEnable`; Inbox-`LiveBinding` an `OpenReportCache` koppeln.

Details zu DTO-Mapping, State-Machine und Reuse-Punkten: `data-model.md`, `contracts/`, `research.md`.

## Complexity Tracking

> Keine Constitution-Verletzungen — Abschnitt entfällt.
