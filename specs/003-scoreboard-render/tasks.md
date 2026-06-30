# Tasks: Scoreboard (Render-Schicht, Slice 1)

**Branch**: `003-scoreboard-render` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Input**: Design documents from `specs/003-scoreboard-render/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests**: INCLUDED — spec.md §6 (Success Criteria) und plan.md §8 fordern grüne Test-Schichten als DoD.

**Organization**: Tasks sind nach User Story gruppiert (US1=P1, US2=P2, US3=P3), damit jede Story unabhängig implementier- und testbar ist. US1 ist die MVP.

## Path Conventions

Single Gradle module (Paper-Plugin). Quellcode unter `plugin/src/main/java/com/mcplatform/plugin/`, Tests unter `plugin/src/test/java/com/mcplatform/plugin/`. Alle Pfade unten relativ zum Repo-Root.

> **Reihenfolge-Prinzip:** rein/JDK-testbare Kerne zuerst, dann Bukkit-Anbindung hinter einem kapselnden Handle, dann Lifecycle/Live, zuletzt Doku. Jede Phase endet grün, bevor die nächste beginnt.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Package-Struktur anlegen. Keine `plugin.yml`-Änderung (Scoreboard hat keine Commands).

- [x] T001 Package-Struktur `feature/scoreboard/{model,profile,condition,provider,render,lifecycle}` unter `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/` anlegen
- [x] T002 [P] Test-Package `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/` anlegen

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Story-agnostische Render-Primitive (Modell + Renderer + Bukkit-Handle). MUSS vor allen Stories fertig sein. Vollständig ohne echte Provider testbar.

- [x] T003 [P] `LineId` Value Object (stabile ID, equals/hashCode) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/model/LineId.java`
- [x] T004 [P] `LineProvider`-Port (`Component resolve(PlayerContext)`) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/provider/LineProvider.java` und minimaler `PlayerContext` (UUID + optionaler Region-Snapshot) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/render/PlayerContext.java`
- [x] T005 `ScoreboardLine` (LineId+LineProvider), `ScoreboardProfile` (id + geordnete `List<ScoreboardLine>`), `RenderedLine` (LineId+Component) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/model/`
- [x] T006 [P] `StaticLineProvider` + `StubLineProvider` (feste Component) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/provider/`
- [x] T007 `ScoreboardRenderer` (Profil → `List<RenderedLine>`, jede Zeile via Provider) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/render/ScoreboardRenderer.java`
- [x] T008 `BukkitScoreboardHandle` (Objective + Team-Entry-Slots, Flicker-Strategie P2; `install(List<RenderedLine>)`, `update(LineId, Component)`, `teardown()`; Bukkit-Mutation via `scheduler.runSync`) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/render/BukkitScoreboardHandle.java`
- [x] T009 [P] Test `ScoreboardModelTest` (LineId-Gleichheit/Stabilität; Profil hält Reihenfolge; LineId stabil bei Umsortierung — **AC-5**) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardModelTest.java`
- [x] T010 [P] Test `ScoreboardRendererTest` (Fake-Provider: korrekte Zeilen/Reihenfolge; `Component`-Output) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardRendererTest.java`
- [x] T011 [P] Test `BukkitScoreboardHandleTest` (Recording/Fake-Handle-Abstraktion: `update(lineId,…)` trifft genau den Slot, kein Voll-Neuaufbau; `teardown` meldet ab) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/BukkitScoreboardHandleTest.java`

**Checkpoint**: Modell + Renderer + Handle grün (gegen Fakes). Noch keine echten Werte, kein Lifecycle.

---

## Phase 3: User Story 1 — Kontextabhängiges Scoreboard mit echten Werten (Priority: P1) 🎯 MVP

**Goal**: Spieler joint → „Default"-Profil (Header, Rang, Coins, Stats-Stub, Footer) mit echtem Rang (plain) und echten Coins aus den vorhandenen Caches.

**Independent Test**: Spieler joinen ohne Sonderbedingung → Default-Profil mit korrektem Rang + Coins (gegen Caches) + struktureller Stub-Zeile; ohne Live/Region.

### Implementation (US1)

- [x] T012 [P] [US1] `EconomyReadPort` (`OptionalLong current(UUID)` + `CompletableFuture<OptionalLong> load(UUID)`, cache-first → REST-fallback über `EconomyEndpoints.GET_BALANCE`, liest/füllt den bestehenden `balances`-Cache; Logik wie `BalanceCommand`) in `plugin/src/main/java/com/mcplatform/plugin/feature/economy/EconomyReadPort.java`
- [x] T013 [P] [US1] `PermissionReadPort` (`Optional<String> currentRankName(UUID)` aus warmem `PermissionCache` → `RoleDisplay.displayName()`, plain; cold→empty) in `plugin/src/main/java/com/mcplatform/plugin/feature/permission/PermissionReadPort.java`
- [x] T014 [US1] `EconomyLineProvider` (liest `EconomyReadPort.current`, Platzhalter wenn leer) + `PermissionLineProvider` (liest `PermissionReadPort.currentRankName`, **plain**, FR-003a) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/provider/`
- [x] T015 [US1] `Profiles.build(providers)` → Profil „Default" (Header/Sep/Rang/Coins/Stats-Stub/Sep/Footer) und `ProfileCatalog` (id→Profil + Default-Ref) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/profile/`
- [x] T016 [US1] `ScoreboardJoinListener` (Join → `PlayerContext` → **Default-Profil** (noch ohne Resolver) → `BukkitScoreboardHandle.install`; Rang warm sync, Coins via `economyPort.load(uuid).thenAccept(runSync(update))`) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/lifecycle/ScoreboardJoinListener.java`
- [x] T017 [US1] `ScoreboardLeaveListener` (Leave → `handle.teardown()`) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/lifecycle/ScoreboardLeaveListener.java`
- [x] T018 [US1] `ScoreboardFeature implements PluginFeature` (`id()="scoreboard"`; `onEnable`: Catalog bauen, Join/Leave-Listener registrieren) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardFeature.java`
- [x] T019 [US1] Composition-Root: in `plugin/src/main/java/com/mcplatform/plugin/platform/McPlatformPlugin.java` `EconomyReadPort`/`PermissionReadPort` bauen (aus Economy/Permission-Feature) und `.register(new ScoreboardFeature(menus, economyPort, permissionPort))` ergänzen

### Tests (US1)

- [x] T020 [P] [US1] Test `EconomyReadPortTest` (cache-first vs. REST-fallback gegen Fake-`BackendClient`; füllt Cache) in `plugin/src/test/java/com/mcplatform/plugin/feature/economy/EconomyReadPortTest.java`
- [x] T021 [P] [US1] Test `PermissionReadPortTest` (warm→Name, cold→empty) in `plugin/src/test/java/com/mcplatform/plugin/feature/permission/PermissionReadPortTest.java`
- [x] T022 [P] [US1] Test `LineProviderTest` (Economy/Permission aus Fake-Ports; Rang **plain** FR-003a; leerer Port→Platzhalter) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/LineProviderTest.java`
- [x] T023 [P] [US1] Test `ProfileCatalogTest` (Default vorhanden, Stub-Zeile strukturell vollwertig — **AC-4**; Provider-Austausch Stub→Fake-Echt ändert nur die Zuordnung, Renderer unberührt — **AC-4**/FR-013) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ProfileCatalogTest.java`
- [x] T024 [US1] Test `ScoreboardJoinFlowTest` (Fake-Ports + Fake-scheduler + Recording-Handle: Join rendert Default mit Rang sofort + Coins nach `load`-Future — **AC-1**) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardJoinFlowTest.java`

**Checkpoint**: MVP — Spieler sieht beim Join ein korrekt befülltes Default-Scoreboard. US1 unabhängig demonstrierbar.

---

## Phase 4: User Story 2 — Live-aktualisierte Werte ohne Flackern (Priority: P2)

**Goal**: Coins **und** Rang aktualisieren sich live (genau die betroffene Zeile, flickerfrei); Leave meldet sauber ab.

**Independent Test**: Bei bestehendem Board Balance ändern → Coins-Zeile aktualisiert; Rolle/Grant ändern → Rang-Zeile aktualisiert; andere Zeilen unverändert; Leave → keine Updates mehr.

### Implementation (US2)

- [x] T025 [US2] Re-Render-Pfad: in `ScoreboardJoinListener` (`…/lifecycle/ScoreboardJoinListener.java`) `liveBus.observe(uuid, () -> scheduler.runSync(() -> reRenderDynamicLines(uuid)))` registrieren und `LiveHandle` pro Spieler halten; `reRenderDynamicLines` re-resolved Coins+Rang via Ports → `handle.update(LineId, Component)` (last-write-wins, kein Debounce — FR-007a)
- [x] T026 [US2] In `ScoreboardLeaveListener` (`…/lifecycle/ScoreboardLeaveListener.java`) `LiveHandle.close()` ergänzen (Observer entfernen) zusätzlich zum `handle.teardown()` — **AC-6**/FR-009
- [x] T027 [US2] **Permission-Notify (additiv, 1 Zeile)**: in `plugin/src/main/java/com/mcplatform/plugin/feature/permission/PermissionLoader.java` nach `cache.apply(...)` im `runSync` `liveBus.notifyChange(player)` aufrufen; dafür `MenuLiveBus` in `PermissionLoader`/`PermissionFeature` durchreichen (Economy macht dies bereits) — löst die async-Reload-Race (research.md §Live-Update)

### Tests (US2)

- [x] T028 [P] [US2] Test `ScoreboardLiveTest` (Fake-`MenuLiveBus` + Fake-scheduler + Recording-Handle: `notifyChange(uuid)` → genau Coins+Rang neu, übrige Zeilen unverändert, kein Voll-Render — **AC-3**, SC-002/SC-002a/SC-003) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardLiveTest.java`
- [x] T029 [P] [US2] Test `ScoreboardLeaveTest` (Leave → `LiveHandle.close()` → `observerCount==0`; danach `notifyChange` ohne Effekt — **AC-6**/SC-006) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ScoreboardLeaveTest.java`
- [x] T030 [P] [US2] Regression-Test `PermissionLoaderNotifyTest` (nach erfolgreichem `apply` → `notifyChange(uuid)`; bei REST-Fehler → **kein** Notify) in `plugin/src/test/java/com/mcplatform/plugin/feature/permission/PermissionLoaderNotifyTest.java`

**Checkpoint**: Live-Coins + Live-Rang flickerfrei, Leave leak-frei. US1+US2 zusammen lauffähig.

---

## Phase 5: User Story 3 — Bedingungsgesteuerte Profil-Auswahl (Priority: P3)

**Goal**: Erfüllt ein Spieler eine Bedingung (Slice 1: Test-Region via Stub), sieht er das passende Profil statt Default; sonst Default-Fallback.

**Independent Test**: Stub-Region setzen → Spieler sieht `TEST_EVENT`; zurücksetzen → wieder `Default`.

### Implementation (US3)

- [x] T031 [P] [US3] `ScoreboardCondition`-Port + `ConditionRule` (Condition→ProfileId) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/condition/`
- [x] T032 [P] [US3] `RegionId` + `RegionProvider`-Port + `StubRegionProvider` (konfigurierbar leer / Test-Region) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/condition/`
- [x] T033 [US3] `ProfileResolver` (geordnete Rules, erste-passende, Default-Fallback) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/condition/ProfileResolver.java`
- [x] T034 [US3] `RegionCondition` (matcht konfigurierte `RegionId` via `RegionProvider`) in `plugin/src/main/java/com/mcplatform/plugin/feature/scoreboard/condition/RegionCondition.java`
- [x] T035 [US3] `Profiles.build` um `TEST_EVENT`-Profil erweitern + im `ProfileCatalog` registrieren (`…/profile/`)
- [x] T036 [US3] `ScoreboardJoinListener` (`…/lifecycle/ScoreboardJoinListener.java`) umverdrahten: statt „immer Default" jetzt `ProfileResolver.resolve(ctx)`; `PlayerContext` trägt Region-Snapshot vom `StubRegionProvider`; `ScoreboardFeature` (`ScoreboardFeature.java`) baut Resolver+Rules+Stub und injiziert sie

### Tests (US3)

- [x] T037 [P] [US3] Test `ProfileResolverTest` (keine Rule→Default — **AC-1**; erste matchende gewinnt; Priorität per Reihenfolge) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/ProfileResolverTest.java`
- [x] T038 [P] [US3] Test `RegionConditionTest` (Stub leer→kein Match→Default; Test-Region→Match→TEST_EVENT — **AC-2**/SC-005) in `plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/RegionConditionTest.java`

**Checkpoint**: Alle drei Stories unabhängig demonstrierbar; Resolver erweiterbar ohne Renderer-Änderung.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Build/Smoke/DoD und Doku-Nachzug (inkl. P4).

- [x] T039 `./gradlew build` (Plugin) grün — alle Phasen-Tests
- [ ] T040 Manueller „es lebt"-Smoke auf Paper-1.21 (quickstart.md): Join→Default mit echtem Rang+Coins; Balance-`SET`→Coins live, kein Flackern; Rolle/Grant→Rang live; Stub-Region→`TEST_EVENT`; Leave→keine Updates
- [x] T041 [P] **AC-7-Verifikation**: `git diff` bestätigt **keine** `plugin-protocol`-Änderung, **kein** neuer Channel/Endpoint/Backend, **keine** generische Klasse (`platform`/`transport`/Menu-Generik) geändert — nur additive Geschwister-Ports + 1 Permission-Zeile (plan.md §10) — Befund in `specs/003-scoreboard-render/quickstart.md` notieren
- [x] T042 [P] **P4 (Doku-Drift)**: Plugin-seitigen Permission-Slice in `PROGRESS.md` nachtragen (Grundlage dieses Slices) — *vor* Abschluss
- [x] T043 [P] `PROGRESS.md` (Plugin): Scoreboard-Slice-Abschnitt mit bewussten Grenzen (Plugin-only, keine protocol-Änderung, kein Backend; kein Toggle/Animation/TabList/Chat; Region-Stub; Live über `MenuLiveBus`-Reuse + additive Permission-Notify). `FEATURE_INVENTORY.md` #72 als teil-migriert markieren
- [x] T044 Review-Check gegen DoD (spec.md §6 / plan.md §9): SC-001…SC-007 erfüllt; Surface-Notiz §10 bestätigt

---

## Dependencies & Execution Order

- **Setup (Ph1)** → **Foundational (Ph2)** blockieren alles.
- **US1 (Ph3)** hängt an Ph2. **MVP-Stop hier möglich.**
- **US2 (Ph4)** hängt an US1 (braucht das bestehende Board + Join/Leave/Handle).
- **US3 (Ph5)** hängt an US1 (verdrahtet die Profil-Selektion in den Join-Pfad um); unabhängig von US2.
- **Polish (Ph6)** nach den angestrebten Stories.
- Story-Reihenfolge: US1 → (US2 ∥ US3, beide nur von US1 abhängig) → Polish.

## Parallel Opportunities

- **Ph2**: T003, T004, T006 parallel; Tests T009/T010/T011 parallel nach ihren Quellen.
- **US1**: T012 ∥ T013 (verschiedene Features); Tests T020/T021/T022/T023 parallel.
- **US2**: Tests T028/T029/T030 parallel.
- **US3**: T031 ∥ T032; Tests T037 ∥ T038.
- **Ph6**: T041/T042/T043 parallel (verschiedene Dateien).

## Implementation Strategy

- **MVP** = Phase 1+2+3 (US1): ein korrekt befülltes Default-Scoreboard beim Join. Lieferbar/demonstrierbar ohne US2/US3.
- **Inkrement 2** = +US2 (Live). **Inkrement 3** = +US3 (Selektion). Danach Polish/DoD.
- **STOPP-Wächter** (jede Phase): sobald eine generische Klasse (`platform`/`transport`/Menu-Generik) geändert werden müsste → anhalten, Muster-Leck melden (Constitution). Erwartet wird das **nicht** (nur additive Geschwister-Ports + 1 Permission-Zeile, plan.md §10).

## Folge-Slices (explizit NICHT hier)

TabList · Chat-Format (Rang-Farbe/-Prefix) · Toggle-Persistenz (braucht Settings #9 backend) · Currency-Zähl-Animation + Sound (im `feature.economy`-Modul, Scoreboard nur Senke) · echtes Region-System (ersetzt `StubRegionProvider`).
