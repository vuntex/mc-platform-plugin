---
description: "Task list for Permission-/Rank-System Plugin-Slice (Paper-1.21-Client)"
---

# Tasks: Permission-/Rank-System — Plugin-Slice (Paper-1.21-Client)

**Input**: Design documents from `specs/002-permission-rank-client/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: INCLUDED — the spec/plan require unit tests for icon mapping, cache/version, gate, live-update, and menu behavior.

**Organization**: Ordered by **layer** per the request — framework-free core logic (fully unit-tested) → transport wiring → platform (listeners/commands/menus). Each phase ends with `./gradlew build` green before the next begins. `[US#]` labels map tasks to spec user stories for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 (role mgmt + grants) / US2 (cache+live) / US3 (gate) / US4 (icon render) / US5 (/rank tool); Setup/Polish carry no label
- **⚠️ STOPP/Muster-Leck**: tasks that touch a generic class — flagged; the icon ones are the approved Phase R0, the others are checkpoints to halt-and-report if a generic change becomes necessary.

## Path Conventions

Single Gradle module. Feature code lives **only** under `src/main/java/com/mcplatform/plugin/feature/permission/`; tests under `src/test/java/com/mcplatform/plugin/feature/permission/`. The only edits to existing files are **additive** (`McPlatformPlugin` one register line, `plugin.yml` commands) **plus** the approved Phase R0 generic escape hatch (`IconSpec`, `MenuRenderer`).

> **⚠️ Divergenz von der Test-Vorgabe:** Die Vorgabe nannte „Menü-LIVE meldet sich beim Close ab". Laut geklärter Entscheidung sind `/ranks` und `/cp` **STATIC** (kein `MenuLiveBus`). Der Leak-Schutz wird daher als „**kein Observer registriert** → kein Leak per Konstruktion" geprüft (T044), nicht als LIVE-Abmeldung. Wenn doch ein LIVE-Menü gewünscht ist, hier melden — das ändert Phase 6/7 + fügt einen MenuLiveBus-Abmelde-Test hinzu.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm contract availability and create the feature package.

- [x] T001 Run `./gradlew build --refresh-dependencies` and confirm `com.mcplatform.protocol.permission.*` resolves (`PermissionEndpoints`, `PlayerPermissionsResponse`, `RoleResponse`, `RoleDisplay`, `PermissionChannels`, `PermissionChangedEventCodec`); confirm `PermissionChangedEventCodec.INSTANCE` is registered in `PlatformProtocol.create()` (already verified). Build green before any change.
- [x] T002 Create feature package `src/main/java/com/mcplatform/plugin/feature/permission/` and test package `src/test/java/com/mcplatform/plugin/feature/permission/`.

---

## Phase 2: Core Logic — framework-free, fully unit-tested (NO Bukkit, NO transport)

**Purpose**: The pure decision logic the rest of the feature builds on. Everything here is unit-tested with no server.

**Independent Test**: `./gradlew test` runs all of the below green without a running server/backend.

- [x] T003 [P] [US4] Implement `DisplayIconFormat` in `feature/permission/DisplayIconFormat.java` — SHARED prefix constants (`material:`, `head-texture:`, `head-player:`), pure `parse(String)→Parsed` (sealed: `Material`/`HeadTexture`/`HeadPlayer`/`Invalid`; split on FIRST `:`), and `material(name)`/`headTexture(base64)` format helpers. Bukkit-free.
- [x] T004 [P] [US4] **(TEST FOCUS: Icon-Mapping je Präfix + Default/unbekannt)** `DisplayIconFormatTest` in test pkg — `material:DIAMOND_SWORD`→Material; `head-texture:eyJ…`→HeadTexture (multi-colon payload kept whole); `head-player:<uuid>`→HeadPlayer; `head-player:not-a-uuid`/`null`/`""`/`"abc"`(no colon)/`banner:red`(unknown)/`material:`(empty payload)→Invalid; format helpers round-trip the constants.
- [x] T005 [P] [US2] Implement `PlayerPermissionsView` in `feature/permission/PlayerPermissionsView.java` — immutable record `(Set<String> effective, RoleDisplay display)`; static `from(PlayerPermissionsResponse)` (`effective = Set.copyOf(effectivePermissions)`, never null). Bukkit-free.
- [x] T006 [US2] Implement `PermissionCache` in `feature/permission/PermissionCache.java` — thin wrapper over the **generic** `FeatureCache<UUID, PlayerPermissionsView>`: `apply(uuid, view, version)` → `put`; `get(uuid)`; `evict(uuid)` → `remove`. Reuses `FeatureCache` unchanged.
- [x] T007 [P] [US2] `PermissionCacheTest` — version-aware apply (older `version` loses, equal `version` idempotent/first-wins), `evict` removes, `get` empty when absent.
- [x] T008 [US3] Implement `PermissionGate` in `feature/permission/PermissionGate.java` — `has(uuid, node)` = `cache.get(uuid).map(v → v.effective().contains(node)).orElse(true)` (cold-cache → neutral `true`). Pure over `PermissionCache`.
- [x] T009 [P] [US3] **(TEST FOCUS: Gate liest Cache)** `PermissionGateTest` — entry has node → `true`; entry lacks node → `false`; no entry (cold-cache) → `true` (neutral).
- [x] T010 [P] [US1] Implement `PermissionFormat` in `feature/permission/PermissionFormat.java` — DE role label/weight/teamRank marker, grant expiry (`permanent` / `läuft ab …`), reason; `error(int status)→MenuMessage/Component` for 403/404/409/422/429/5xx. Adventure/`MenuText` only, no §-codes.
- [x] T011 [P] [US1] `PermissionFormatTest` — label rendering + each status → distinct message.

**Checkpoint**: `./gradlew build` green — pure core logic compiles and all its unit tests pass; no Bukkit, no transport yet.

---

## Phase 3: Transport Wiring — cache fed by REST, refreshed live, evicted on quit (NO menus)

**Purpose**: US2 end-to-end at the data layer using the generic transport unchanged.

**Independent Test**: build green; on a running server a join loads `/effective`, `mc:permission:changed` reloads only that UUID, quit evicts (manual per quickstart).

- [x] T012 [US2] Create `PermissionFeature` skeleton in `feature/permission/PermissionFeature.java` — `implements PluginFeature`, `id()`→`"permission"`, ctor takes `MenuManager menus`, empty `onEnable(FeatureContext)`/`onDisable()` (wired incrementally below).
- [x] T013 **⚠️ STOPP/Checkpoint (sanctioned composition-root edit — NOT generic logic):** add exactly one `.register(new PermissionFeature(menus))` to the `FeatureRegistry` chain in `platform/McPlatformPlugin.java` (after `ReportFeature`, before `HubFeature`). Additive; if anything beyond one register line is needed → halt & report.
- [x] T014 [US2] Implement `PermissionLoader` in `feature/permission/PermissionLoader.java` — `load(uuid, version)`/`reload(uuid, version)`: `backend.call(PermissionEndpoints.EFFECTIVE, null, uuid.toString())` async → `PlayerPermissionsView.from(resp)` → `scheduler.runSync(() → cache.apply(uuid, view, version))`. Uses `BackendClient` + `PlatformScheduler` unchanged; main-thread never blocked.
- [x] T015 [US2] Implement `PermissionLiveUpdater` in `feature/permission/PermissionLiveUpdater.java` — `Consumer<PermissionChangedEvent>`: if player online → `loader.reload(event.playerUuid(), event.timestampEpochMilli())`; else ignore. All `changeType` (incl. unknown) → reload. Online-check via injected predicate (testable).
- [x] T016 [P] [US2] **(TEST FOCUS: Cache-Refresh genau für die betroffene UUID bei mc:permission:changed)** `PermissionLiveUpdaterTest` — online UUID → exactly one reload for THAT UUID and no other; offline UUID → zero reloads; `GRANT_ADDED/REVOKED/EXPIRED/ROLE_CONFIG_CHANGED` + unknown type → reload; uses fake loader + online predicate.
- [x] T017 [US2] Implement `PermissionJoinListener` in `feature/permission/PermissionJoinListener.java` — `PlayerJoinEvent` → `loader.load(uuid, System.currentTimeMillis())`; `PlayerQuitEvent` → `cache.evict(uuid)`.
- [x] T018 [US2] Wire transport in `PermissionFeature.onEnable` — `context.eventBus().subscribe(PermissionChannels.CHANGED, PermissionChangedEventCodec.INSTANCE, liveUpdater)` + `context.registerListener(joinListener)`. Uses `EventBus` unchanged.

**Checkpoint**: `./gradlew build` green — feature loads, cache lives on the existing Pub/Sub path; verify relog-free reload + offline-ignore + quit-evict manually.

---

## Phase 4: Phase R0 — generic icon escape hatch (⚠️ approved Muster-Leck)

**Purpose**: Let the menu render a feature-built `ItemStack`. Additive + backward-compatible.

- [x] T019 **⚠️ STOPP/Muster-Leck (FREIGEGEBEN 2026-06-23):** `platform/menu/IconSpec.java` — add nullable `ItemStack baseItem` + factory `IconSpec.ofItem(ItemStack base, MenuText name, List<LoreLine> lore)`; existing fields/factories unchanged (field defaults `null`). **GENERIC CLASS** — additive only.
- [x] T020 **⚠️ STOPP/Muster-Leck (FREIGEGEBEN):** `platform/menu/MenuRenderer.java` — in `toStack()`, when `icon.baseItem() != null` use `baseItem.clone()` instead of `new ItemStack(MenuStyle.material(...))`, then apply name/lore/italic-off/flags/glow as today; else unchanged enum path. **GENERIC CLASS** — additive only.
- [x] T021 [P] [US4] `MenuRendererIconTest` in `src/test/java/com/mcplatform/plugin/platform/menu/MenuRendererIconTest.java` — `ofItem(base,…)` path clones base and applies name/lore; `baseItem == null` → enum path unchanged.

**Checkpoint**: `./gradlew build` green; **all existing menu tests still pass** (backward-compat proof).

---

## Phase 5: Icon Two-Direction (US4 render + US5 tool)

**Goal**: One shared format; resolve String→ItemStack and extract ItemStack→String.

**Independent Test**: `/rank toDisplayIcon` emits the correct string for vanilla/custom-head/textureless head; resolver renders every prefix + a visible fallback.

- [x] T022 [US4] Implement `IconResolver` in `feature/permission/IconResolver.java` — `resolve(String)→ItemStack` via `DisplayIconFormat.parse`: `Material`→`new ItemStack(Material.valueOf(name))` (invalid → fallback); `HeadTexture`→`PLAYER_HEAD` + `SkullMeta` `PlayerProfile` with `ProfileProperty("textures", base64)` (Paper API, **no NMS**); `HeadPlayer`→`PLAYER_HEAD` profile of the UUID; `Invalid`/any exception → visible fallback (`BARRIER`/`PAPER`), never crash/empty.
- [x] T023 [US5] Implement `IconExtractor` in `feature/permission/IconExtractor.java` — `toDisplayIcon(ItemStack)`: texture extractable from `PlayerProfile`/`ProfileProperty("textures")` → `DisplayIconFormat.headTexture(base64)`; else (vanilla **or** textureless `PLAYER_HEAD`) → `DisplayIconFormat.material(type.name())`. No `head-player:` output. Read-only.
- [x] T024 [US5] Implement `RankCommand` (`/rank toDisplayIcon`) in `feature/permission/RankCommand.java` — read main-hand item → `IconExtractor` → send as **click-to-copy** Adventure component (`ClickEvent.copyToClipboard`); empty hand → hint; no backend call. Register in `PermissionFeature.onEnable`; declare command `rank` in `plugin.yml` (no blocking permission).
- [x] T025 [P] [US5] `RankCommandTest` — vanilla item → `material:<NAME>`; textured head → `head-texture:<base64>`; textureless `PLAYER_HEAD` → `material:PLAYER_HEAD`; empty hand → hint (no NPE).
- [x] T026 [P] [US4] **(TEST FOCUS: Icon-Roundtrip + Fallback bei Müll)** `IconRoundTripTest` — `resolve(extract(item))` ≈ item per prefix (vanilla material; custom head keeps texture); junk string → fallback icon (Paper test harness).

**Checkpoint**: `./gradlew build` green; `/rank toDisplayIcon` works; resolver covers all prefixes + fallback.

---

## Phase 6: Platform — `/ranks` Role Management (US1, STATIC menus)

**Goal**: `/ranks` → role list (icons via resolver) → create/edit/delete + role-permissions.

**Independent Test**: `/ranks` → list with icons; create a role; add/remove a role permission; delete via double-click confirm → backend reflects each.

- [x] T027 [US1] Implement `PermissionInput` in `feature/permission/PermissionInput.java` — anvil/chat helpers (MENU_DESIGN §4.6) for role name, permission string, duration; cancel-escape; returns to caller menu.
- [x] T028 [US1] Implement `RoleListMenu` (STATIC, paginated 7×4) in `feature/permission/RoleListMenu.java` — `LIST_ROLES` → per-role `IconSpec.ofItem(iconResolver.resolve(role.displayIcon()), name, lore)`; empty-list marker; create button; click → `RoleDetailMenu`.
- [x] T029 [US1] Implement `RoleDetailMenu` (STATIC) in `feature/permission/RoleDetailMenu.java` — edit fields (`UPDATE_ROLE`) + role-permissions add/remove (`ADD/REMOVE_ROLE_PERMISSION`); delete via `ConfirmDialog.critical()` → `DELETE_ROLE` (actor as query); create via `CREATE_ROLE`; re-render affected slots after own action; errors via `PermissionFormat`.
- [x] T030 [US1][US3] Implement `RanksCommand` (`/ranks`) in `feature/permission/RanksCommand.java` — `PermissionGate.has(uuid, "mcplatform.permission.roles.manage")` (cold-cache neutral) → open `RoleListMenu`; register in `onEnable`; declare command `ranks` in `plugin.yml` (no blocking permission).
- [x] T031 [P] [US1] `RoleListMenuTest` — pagination, one icon per role via `ofItem`, empty-list marker (uses `RecordingMenuView`).
- [x] T032 [P] [US1] `RoleDetailMenuTest` — permission add/remove buttons, delete needs double-click confirm, STATIC re-render after own action, 403/409 → error message.

**Checkpoint**: `./gradlew build` green; full role CRUD + role permissions via `/ranks` against the backend.

---

## Phase 7: Platform — `/cp <Spieler>` Control Panel (US1, STATIC menu)

**Goal**: `/cp <Spieler>` (online **and** offline) → grant/revoke ranks + direct permissions.

**Independent Test**: `/cp <online>` and `/cp <offlineName>` → resolves UUID, shows grants; grant a rank (optional expiry/reason) → backend reflects; revoke; unresolvable name → message, no menu.

- [x] T033 [US1] Implement `PlayerGrantsMenu` (STATIC) in `feature/permission/PlayerGrantsMenu.java` — `EFFECTIVE` → active roles + direct perms; `GRANT_ROLE`/`REVOKE_ROLE`, `GRANT_PERMISSION`/`REVOKE_PERMISSION` (optional expiry/reason via `PermissionInput`); re-render from the returned `PlayerPermissionsResponse`; role icons via `IconResolver`.
- [x] T034 [US1][US3] Implement `ControlPanelCommand` (`/cp <Spieler>`) in `feature/permission/ControlPanelCommand.java` — resolve name→UUID: online via `Bukkit.getPlayerExact` (main); else **off-main** (`scheduler.runAsync`) `Bukkit.getOfflinePlayer(name)` → UUID → `runSync` open; unresolvable → message, no menu; `PermissionGate.has(uuid, "mcplatform.permission.grants.manage")`. Register in `onEnable`; declare command `cp` in `plugin.yml` (no blocking permission).
- [x] T035 **⚠️ STOPP/Muster-Leck-Checkpoint (transport):** verify `HttpBackendClient` sends a request **body** on `DELETE` for `REVOKE_PERMISSION` (`RevokePermissionRequest`). If it does NOT → halt & report as a **separate** transport pattern-leak (do not silently change the generic client); see research §DELETE-mit-Body.
- [x] T036 [P] [US1] `PlayerGrantsMenuTest` — grant/revoke buttons, revoke confirm, STATIC re-render from `PlayerPermissionsResponse`, error paths (403/404/409/422/429).

**Checkpoint**: `./gradlew build` green; `/cp` grants/revokes for online + offline players.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T037 [P] **(TEST FOCUS reframed: kein Beobachter-Leak)** Verify menus are STATIC and register **no** `MenuLiveBus` observer (no leak by construction) — assert no `menus.liveBus().…` subscription in the permission package; `MenuManager` close path needs no special handling here. (If LIVE is later wanted, replace with a MenuLiveBus subscribe + close-unsubscribe test.)
- [x] T038 [P] [US3] Authority verification: each write button surfaces `PermissionFormat.error(403)` when the backend rejects, regardless of the optimistic gate (Backend is the truth).
- [x] T039 [P] `plugin.yml` final review — commands `rank`, `ranks`, `cp` declared with usage; **no** blocking `permission:` on them (gating is cache-based).
- [x] T040 Run `quickstart.md` end-to-end manual verification (cache relog-free, offline-ignore, `/ranks`, `/cp` online+offline, `/rank` click-to-copy, icon prefixes + fallback, authority 403).
- [x] T041 Final `./gradlew build` green; confirm `FeatureRegistry.enabledIds()` includes `"permission"`; confirm **no** generic class changed except the approved R0 (`IconSpec`, `MenuRenderer`).

---

## Dependencies & Execution Order

### Phase Dependencies (layered — the requested order)

- **Phase 1 Setup** → no deps.
- **Phase 2 Core Logic** → after Setup. Pure; fully unit-tested. BLOCKS later phases.
- **Phase 3 Transport** → after Core (uses `PermissionCache`, `PlayerPermissionsView`, `PermissionLoader`).
- **Phase 4 R0 escape hatch** → independent of Core/Transport; required before any icon **rendering** (Phase 5/6). Can be done any time after Setup, but placed here so Core+Transport ship without touching generics.
- **Phase 5 Icon** → after R0 (rendering) + Core (`DisplayIconFormat`).
- **Phase 6 `/ranks`** → after Icon (role icons) + Core (`PermissionFormat`, `PermissionGate`) + Transport (feature wiring).
- **Phase 7 `/cp`** → after Core + Transport + Icon; independent of Phase 6 (separate command/menu).
- **Phase 8 Polish** → after all desired phases.

### Story → phase map (traceability)

- **US2 (cache+live, P1)**: Phases 2–3.
- **US4 (icon render, P2)**: `DisplayIconFormat` (P2), R0 (P4), `IconResolver` (P5), used in P6/P7.
- **US5 (/rank tool, P2)**: Phase 5.
- **US1 (role mgmt + grants, P1)**: Phases 6–7 (+ `PermissionFormat` in P2).
- **US3 (gate, P2)**: `PermissionGate` (P2), wired in P6/P7, verified in P8.

### Parallel Opportunities

- Phase 2: T003/T004, T005, T007, T009, T010/T011 are largely independent ([P]).
- Phase 5: T025, T026 [P] after their subjects exist.
- Phase 6: T031, T032 [P]; Phase 7: T036 [P]; Phase 8: T037–T039 [P].

---

## Parallel Example: Phase 2 (Core Logic)

```bash
# Pure, independent files — implement/test in parallel:
Task: "DisplayIconFormat + DisplayIconFormatTest"
Task: "PlayerPermissionsView + PermissionCache + PermissionCacheTest"
Task: "PermissionGate + PermissionGateTest"
Task: "PermissionFormat + PermissionFormatTest"
```

---

## Implementation Strategy

### Layered, each phase green (the requested approach)

1. **Phase 1–2**: Setup + pure core logic, fully unit-tested → `./gradlew build` green.
2. **Phase 3**: Transport wiring → cache lives relog-free → green (US2 delivered).
3. **Phase 4–5**: Approved R0 escape hatch + icon two-direction → green (US4/US5 delivered).
4. **Phase 6**: `/ranks` role management → green (US1 part 1).
5. **Phase 7**: `/cp` control panel → green (US1 part 2).
6. **Phase 8**: polish + verification.

### MVP

The smallest shippable slice with user-visible value is **Phases 1–6** (relog-free cache + role management with icons). `/cp` (Phase 7) and polish follow incrementally.

### STOPP/Muster-Leck checkpoints (halt & report if hit)

- **T019/T020** — approved generic icon escape hatch (`IconSpec`/`MenuRenderer`), additive only.
- **T013** — composition-root register line (sanctioned, not generic logic).
- **T035** — DELETE-with-body for `REVOKE_PERMISSION`: if the generic client can't do it, report separately — do **not** silently change `HttpBackendClient`.

---

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Feature code stays under `feature/permission/`; existing-file edits are additive (`McPlatformPlugin` 1 line, `plugin.yml` commands) + approved R0 (`IconSpec`/`MenuRenderer`).
- Main thread never blocked: REST/Redis via `scheduler.runAsync`, Bukkit/inventory via `runSync`; offline name resolution off-main.
- Menus are **STATIC** (clarified decision) — re-render affected slots after own action; **no** `MenuLiveBus` observer (T037). Override if LIVE is wanted.
- Commit after each task or logical group; stop at any checkpoint to validate.
