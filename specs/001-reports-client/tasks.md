---
description: "Task list for Reports Plugin-Slice (Paper-1.21-Client)"
---

# Tasks: Reports — Plugin-Slice (Paper-1.21-Client)

**Input**: Design documents from `specs/001-reports-client/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: INCLUDED — the spec's Definition of Done requires tests for transport wiring, ring buffer, and menu behavior.

**Organization**: Tasks grouped by user story (P1 → P2 → P3). Each story is an independently testable increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 (Setup/Foundational/Polish carry no story label)

## Path Conventions

Single Gradle module. Feature code lives **only** under `src/main/java/com/mcplatform/plugin/feature/report/`; tests under `src/test/java/com/mcplatform/plugin/feature/report/`. The only edits to existing files are additive (`McPlatformPlugin`, `plugin.yml`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm dependencies and create the feature package.

- [x] T001 Run `./gradlew build --refresh-dependencies` and confirm `com.mcplatform.protocol.report.*` resolves (ReportEndpoints, ReportResponse, ReportChangedEventCodec); build is green before changes.
- [x] T002 Create the feature package directory `src/main/java/com/mcplatform/plugin/feature/report/` and the test package `src/test/java/com/mcplatform/plugin/feature/report/`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Feature must load and shared presentation must exist before any story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T003 Create `ReportFeature` skeleton in `src/main/java/com/mcplatform/plugin/feature/report/ReportFeature.java` — `implements PluginFeature`, `id()` returns `"report"`, constructor takes `MenuManager menus`, empty `onEnable(FeatureContext)` / `onDisable()` (story wiring added later).
- [x] T004 Register the feature in `src/main/java/com/mcplatform/plugin/platform/McPlatformPlugin.java` — add exactly one `.register(new ReportFeature(menus))` line to the existing `FeatureRegistry` chain (additive; no generic-logic change).
- [x] T005 [P] Implement `ReportFormat` in `src/main/java/com/mcplatform/plugin/feature/report/ReportFormat.java` — DE category labels (CHEATING/BELEIDIGUNG/SPAM_WERBUNG/TEAMING_BUG_ABUSE/SONSTIGES), DE status labels (OPEN/IN_PROGRESS/RESOLVED/REJECTED), `time(long)`, `chatLine(ChatMessage)`, `playerName(UUID)` (online → `OfflinePlayer.getName()` → short-UUID fallback), and `errorMessage(Throwable)` mapping per `contracts/report-endpoints.md` (422/429/403/404/409/5xx via `BackendException.statusCode()`). Adventure/`MenuText` only — no §-codes.
- [x] T006 [P] Write `ReportFormatTest` in `src/test/java/com/mcplatform/plugin/feature/report/ReportFormatTest.java` — assert label mappings and that each of 422/429/403/404/409/transport(0) produces the correct distinct message (construct `BackendException` instances / status codes).

**Checkpoint**: Plugin loads with an empty Report feature; shared formatting is tested.

---

## Phase 3: User Story 1 - Spieler meldet einen Mitspieler (Priority: P1) 🎯 MVP

**Goal**: `/report <spieler>` → category menu → chat-input reason → CREATE with `chatContext` snapshot → confirmation.

**Independent Test**: As one player, `/report <other>`, pick a category, type a reason → backend has a new OPEN report with correct fields and (if public chat occurred) a non-empty `chatContext`; reporter sees confirmation. Self-report → 422; rapid re-report → 429.

### Tests for User Story 1 ⚠️ (write first, ensure they FAIL)

- [x] T007 [P] [US1] `ChatRingBufferTest` in `src/test/java/com/mcplatform/plugin/feature/report/ChatRingBufferTest.java` — bounded at 20 (oldest evicted), `snapshot()` returns immutable oldest→newest copy, basic concurrent-append safety.
- [x] T008 [P] [US1] `ReportReasonPromptTest` in `src/test/java/com/mcplatform/plugin/feature/report/ReportReasonPromptTest.java` — `begin`/`take`/`cancel` semantics; building a `CreateReportRequest` from a pending entry + ring snapshot yields correct reporter/target/category/detail/chatContext (extract the request-build into a testable helper).

### Implementation for User Story 1

- [x] T009 [P] [US1] Implement `ChatRingBuffer` in `src/main/java/com/mcplatform/plugin/feature/report/ChatRingBuffer.java` — thread-safe bounded (20) FIFO of `ChatMessage`; `add(ChatMessage)`, `snapshot(): List<ChatMessage>` (immutable).
- [x] T010 [P] [US1] Implement `ReportReasonPrompt` in `src/main/java/com/mcplatform/plugin/feature/report/ReportReasonPrompt.java` — `Map<UUID, Pending(target, category, createdAtMillis)>`; `begin/take/cancel`; helper to build `CreateReportRequest` (reporter, pending, ring.snapshot()).
- [x] T011 [US1] Implement `PublicChatListener` in `src/main/java/com/mcplatform/plugin/feature/report/PublicChatListener.java` — `@EventHandler(priority = MONITOR, ignoreCancelled = true)` on `AsyncPlayerChatEvent`; append `new ChatMessage(sender, getMessage(), System.currentTimeMillis())` to the `ChatRingBuffer` (no Bukkit API beyond read; excludes cancelled/muted + consumed reason inputs).
- [x] T012 [P] [US1] Implement `ReportCategoryMenu` in `src/main/java/com/mcplatform/plugin/feature/report/ReportCategoryMenu.java` — STATIC `MenuBuilder.panel(...)` with 5 category buttons (`MenuItem.button`, `IconSpec`, DE labels via `ReportFormat`); on click → `ReportReasonPrompt.begin`, `view.close()`, send chat prompt (cancel word `abbrechen`). Per MENU_DESIGN.
- [x] T013 [US1] Implement `ReportChatInputListener` in `src/main/java/com/mcplatform/plugin/feature/report/ReportChatInputListener.java` — `@EventHandler(priority = LOWEST)` on `AsyncPlayerChatEvent`; if sender has a pending prompt → `event.setCancelled(true)`; `abbrechen` → cancel+feedback; else build request → `scheduler.runAsync` `backend.call(ReportEndpoints.CREATE, …)` → `scheduler.runSync` confirmation / `ReportFormat.errorMessage` (422/429).
- [x] T014 [P] [US1] Implement `ReportSession` (PlayerQuitListener) in `src/main/java/com/mcplatform/plugin/feature/report/ReportSession.java` — drop the player's pending prompt on `PlayerQuitEvent`.
- [x] T015 [US1] Implement `ReportCommand` in `src/main/java/com/mcplatform/plugin/feature/report/ReportCommand.java` — `/report <spieler>`: validate player sender + one arg, resolve online target (else `ReportFormat` hint), open `ReportCategoryMenu` via `menus.open(...)`.
- [x] T016 [US1] Wire US1 in `ReportFeature.onEnable` — instantiate `ChatRingBuffer` + `ReportReasonPrompt`; `registerListener` `PublicChatListener`, `ReportChatInputListener`, `ReportSession`; `registerCommand("report", new ReportCommand(...))`. Pass `backend`/`scheduler` from context.
- [x] T017 [US1] Add the `report` command to `src/main/resources/plugin.yml` (usage `/report <spieler>`, **no** permission gate — open to all). Per `contracts/commands-permissions.md`.

**Checkpoint**: `/report` works end-to-end against the backend (MVP). Demo-able.

---

## Phase 4: User Story 2 - Team bearbeitet Meldungen im Inbox-Menü (Priority: P2)

**Goal**: `/reports` → paginated LIVE list of all open reports → detail (incl. chat context) → set status via allowed transitions; clean unsubscribe on close.

**Independent Test**: With a team account, `/reports` lists all open reports newest-first; open one → detail with chat context; set OPEN→IN_PROGRESS → status changes; invalid/concurrent transition → 409 handled; non-team forced LIST_OPEN → 403; closing leaves no observers.

### Tests for User Story 2 ⚠️

- [x] T018 [P] [US2] `ReportInboxMenuTest` in `src/test/java/com/mcplatform/plugin/feature/report/ReportInboxMenuTest.java` — assert pagination (28-grid via `MenuBuilder.renderPage`, arrows only when pages exist), correct status-transition buttons per current status (OPEN→{IN_PROGRESS,REJECTED}, IN_PROGRESS→{RESOLVED,REJECTED}, terminal→none), and that the inbox menu carries a `LiveBinding` (LIVE) so `MenuManager` can unsubscribe on close. Model on `TransactionHistoryMenuTest`.

### Implementation for User Story 2

- [x] T019 [P] [US2] Implement `OpenReportCache` in `src/main/java/com/mcplatform/plugin/feature/report/OpenReportCache.java` — version-aware `reportId → ReportResponse` (reuse generic `FeatureCache<UUID, ReportResponse>`); `replaceAll(ReportResponse[])`, `openSortedNewestFirst()`, `get(id)`.
- [x] T020 [US2] Implement `ReportInboxMenu` in `src/main/java/com/mcplatform/plugin/feature/report/ReportInboxMenu.java` — LIVE `MenuBuilder.list(...)` + `.live(new LiveBinding(INBOX_TOPIC, view -> layout()))`; async `backend.callIdempotent(ReportEndpoints.LIST_OPEN, null, {staff:viewer})` → `OpenReportCache` → `view.refresh()`; paginate via `MenuBuilder.renderPage`; entry click → open `ReportDetailMenu`. "latest request wins" guard.
- [x] T021 [US2] Implement `ReportDetailMenu` in `src/main/java/com/mcplatform/plugin/feature/report/ReportDetailMenu.java` — render one cached `ReportResponse` (reporter/target/category/grund/status/timestamps + `chatContext` lines via `ReportFormat`); resolve reporter/target UUIDs to names via `ReportFormat.playerName(UUID)` (online → `OfflinePlayer.getName()` → short-UUID fallback), `IconSpec.head(...)` for the player head (see data-model "Display-Namen"); show only allowed status buttons (gated for display by `mcplatform.report.handle`); each → `ConfirmDialog` → `scheduler.runAsync` `backend.call(ReportEndpoints.CHANGE_STATUS, new ChangeStatusRequest(newStatus, viewer), reportId)` → sync success(back to inbox)/`ReportFormat.errorMessage` (403/404/409).
- [x] T022 [US2] Implement `ReportInboxCommand` in `src/main/java/com/mcplatform/plugin/feature/report/ReportInboxCommand.java` — `/reports`: open `ReportInboxMenu` for a player sender via `menus.open(...)`.
- [x] T023 [US2] Wire US2 in `ReportFeature.onEnable` — instantiate `OpenReportCache`; `registerCommand("reports", new ReportInboxCommand(...))` (pass `menus`, `backend`, `scheduler`, cache).
- [x] T024 [US2] Add to `src/main/resources/plugin.yml`: `reports` command (`permission: mcplatform.report.view` UI-gate) and permissions `mcplatform.report.view` + `mcplatform.report.handle` (`default: op`). Per `contracts/commands-permissions.md`.

**Checkpoint**: Inbox lists/handles reports against the backend; US1 still works.

---

## Phase 5: User Story 3 - Team wird live benachrichtigt (Priority: P3)

**Goal**: On a new report (`mc:report:changed`, CREATED), ping all online holders of `mcplatform.report.view` (chat line + sound); an open inbox live-refreshes.

**Independent Test**: With a team member online, create a report (or emit a CREATED event) → they get a chat line + sound; an open inbox shows the new report without reopening; STATUS_CHANGED removes a no-longer-open report live.

### Tests for User Story 3 ⚠️

- [x] T025 [P] [US3] `ReportLiveUpdaterTest` in `src/test/java/com/mcplatform/plugin/feature/report/ReportLiveUpdaterTest.java` — CREATED routes to the notifier path + triggers a refresh; STATUS_CHANGED triggers a refresh. Note `ReportChangedEvent` has **no `version`** (only `timestampEpochMilli`), so staleness is tested at the cache/refresh layer, not on the event: (a) `OpenReportCache` keeps a higher-`version` `ReportResponse` and rejects a lower-`version` one; (b) an out-of-order `LIST_OPEN` response is dropped by the "latest-request-wins" sequence guard. Use fakes for notifier/refresh callbacks.

### Implementation for User Story 3

- [x] T026 [P] [US3] Implement `ReportNotifier` in `src/main/java/com/mcplatform/plugin/feature/report/ReportNotifier.java` — on `scheduler.runSync`, iterate `Bukkit.getOnlinePlayers()`, filter `hasPermission("mcplatform.report.view")`, send Adventure chat component (category + target name via `ReportFormat.playerName(target)`) + `playSound`. No §-codes.
- [x] T027 [US3] Implement `ReportLiveUpdater` in `src/main/java/com/mcplatform/plugin/feature/report/ReportLiveUpdater.java` — `Consumer<ReportChangedEvent>`: CREATED → `ReportNotifier.ping(event)` + inbox refresh; STATUS_CHANGED → inbox refresh; refresh = async `LIST_OPEN` → `OpenReportCache` → `menus.liveBus().notifyChange(INBOX_TOPIC)`.
- [x] T028 [US3] Wire US3 in `ReportFeature.onEnable` — `context.eventBus().subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, reportLiveUpdater)` (instantiate notifier + updater with `menus`, `backend`, `scheduler`, cache).

**Checkpoint**: All three stories independently functional; live ping + inbox auto-refresh work.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T029 [P] Run full test suite `./gradlew test --tests "com.mcplatform.plugin.feature.report.*"` and the whole `./gradlew build` — all green, Shadow-JAR produced.
- [x] T030 Pattern-leak diff-check: `git diff --stat` shows only new `feature/report/*` files, exactly one changed line in `McPlatformPlugin.java`, and additive `plugin.yml` edits — **no** generic class (BackendClient, EventBus, FeatureRegistry, MenuBuilder, Scheduler, MessageEnvelope) modified. If any generic change appears → STOP and justify.
- [x] T031 Run `quickstart.md` manual verification (P1/P2/P3 flows) against a running backend; confirm LIVE inbox unsubscribes on close (no observer leak).
- [x] T032 [P] Update `PROGRESS.md` (and any feature index) with the Reports slice; no other docs touched.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; **blocks all stories**. (T003→T004 sequential; T005/T006 [P].)
- **US1 (Phase 3)**: depends on Foundational. MVP.
- **US2 (Phase 4)**: depends on Foundational; independent of US1 at runtime (shares only `ReportFeature.onEnable` + `plugin.yml`).
- **US3 (Phase 5)**: depends on Foundational; the **ping** is standalone, the **inbox live-refresh** integrates with US2's `OpenReportCache`/`ReportInboxMenu` (`INBOX_TOPIC`). Build US3 after US2 for the refresh path.
- **Polish (Phase 6)**: after the desired stories.

### Within Each User Story

- Tests written first and failing → then implementation.
- Data structures ([P]) → listeners/menus → command → `ReportFeature.onEnable` wiring → `plugin.yml`.
- `ReportFeature.onEnable` and `plugin.yml` are shared files → their per-story edits are sequential (not [P] across stories).

### Parallel Opportunities

- T005 + T006 (Foundational) in parallel.
- US1: T007/T008 (tests) parallel; T009/T010/T012/T014 parallel (distinct files); T011/T013/T015/T016/T017 sequential (depend on the above / shared files).
- US2: T018 parallel with US1 polish; T019 [P]; T020/T021 depend on T019; T022→T023→T024 sequential.
- US3: T025 + T026 parallel; T027 depends on T026 + US2 cache; T028 sequential.

---

## Parallel Example: User Story 1

```bash
# Tests first (parallel):
Task: "ChatRingBufferTest in src/test/java/com/mcplatform/plugin/feature/report/ChatRingBufferTest.java"
Task: "ReportReasonPromptTest in src/test/java/com/mcplatform/plugin/feature/report/ReportReasonPromptTest.java"

# Then independent implementation files (parallel):
Task: "ChatRingBuffer.java"
Task: "ReportReasonPrompt.java"
Task: "ReportCategoryMenu.java"
Task: "ReportSession.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → 4. **STOP & validate** `/report` end-to-end → 5. demo.

### Incremental Delivery

Setup + Foundational → US1 (MVP) → US2 (inbox) → US3 (live). Each story tested independently before the next; no story breaks a previous one.

---

## Implementation Notes (deviations from the original plan)

- **T019 — `OpenReportCache` folded into `ReportInboxMenu`.** A shared, version-keyed cache was dropped:
  the background live updater has no staff UUID to drive `LIST_OPEN` with, so each open inbox owns its
  read-model (`entries`) and re-fetches `LIST_OPEN(staff=viewer)` on open and on every live nudge. FR-021
  is met by the full-snapshot refresh + a `requestSeq`/`lastApplied` **latest-request-wins** guard (in
  `ReportInboxMenu`), not per-entry versions — the event carries no version anyway.
- **Name resolution injected as `Function<UUID,String>`.** `ReportInboxMenu`/`ReportDetailMenu` take a
  resolver (`ReportNames::of` in production, a fake in tests) instead of calling Bukkit directly — keeps
  the menus Bukkit-free and unit-testable, matching how `TransactionHistoryMenu` takes names.
- **Detail chat-context cap.** The detail menu renders the first 21 chat-context messages (3 interior
  rows); the 4th interior row holds the status-transition buttons. ~20 messages fit.
- **Verified:** `./gradlew build` green, JAR at `build/libs/mc-platform-plugin-0.1.0-SNAPSHOT.jar`; 17
  report tests pass; diff-check confirms no generic class changed (only +1 line in `McPlatformPlugin`,
  additive `plugin.yml`, new `feature/report/*`).

## Notes

- [P] = different files, no incomplete-task dependency. `ReportFeature.onEnable` + `plugin.yml` are shared → sequential.
- Threading: REST/Redis via `scheduler.runAsync`; Bukkit/menu/broadcast via `scheduler.runSync`. `AsyncPlayerChatEvent` handlers touch only `setCancelled`/`getMessage` + the thread-safe ring.
- Guardrail: **no generic class is modified** (verified in T030). Only additive Bestand touches: 1 line in `McPlatformPlugin`, declarations in `plugin.yml`. `protocol` untouched.
- Commit after each task or logical group.
