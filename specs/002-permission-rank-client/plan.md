# Implementation Plan: Permission-/Rank-System — Plugin-Slice (Paper-1.21-Client)

**Branch**: `002-permission-rank-client` | **Date**: 2026-06-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-permission-rank-client/spec.md`

## Summary

Vierter `PluginFeature` (`feature.permission`), rein additiv auf der etablierten generischen Infrastruktur. Drei Säulen: (1) ein **version-aware Live-Cache** der effektiven Permissions + `RoleDisplay` je online Spieler — die **bestehende** generische `FeatureCache<UUID, V>` wird wiederverwendet, gefüttert über `GET …/effective` beim Join und gezielt invalidiert durch `mc:permission:changed`; (2) ein **optimistisches, feingranulares Gate**, das diesen Cache liest (Cold-Cache = neutral durchlassen, Backend `403` ist die Wahrheit); (3) zwei **STATIC**-Staff-Menüs über `/ranks` (Rollen-CRUD + Rollen-Permissions) und `/cp <Spieler>` (spielerbezogene Vergaben, online **und** offline). Der opake `display_icon`-String wird **im Feature** als reine, testbare Übersetzung interpretiert.

**Technischer Ansatz**: Komplett additiv im neuen Package `feature.permission` + **einem** `FeatureRegistry`-Eintrag. **ABER:** Eine Anforderung lässt sich NICHT ohne Eingriff in eine generische Klasse erfüllen — siehe **⚠️ Muster-Leck** unten. Das opake-Icon-Mapping verlangt `material:<MATERIAL>` (beliebiges Material) und `head-texture:<texture>` (Custom-Head mit Textur); das generische Icon-Modell (`Icon`-Enum = geschlossene Semantik-Menge, `IconSpec` trägt nur `skinOwner`-UUID, `MenuRenderer` rendert nur Enum-Material + Spielerkopf-Skin) kann beides heute **nicht** ausdrücken. Nur `head-player:<uuid>` passt ohne Änderung. Daher: **STOPP & gemeldet**, mit empfohlenem, eng begrenztem additivem Vor-Refactor (Phase R0). Alle übrigen Bausteine werden unverändert wiederverwendet.

## ⚠️ Muster-Leck (STOPP — Entscheidung erforderlich vor Implementierung)

**Wo:** `display_icon`-Rendering (FR-020). **Betroffene generische Klassen:** `platform/menu/IconSpec`, `platform/menu/MenuRenderer` (und ggf. `Icon`).

**Befund (verifiziert am Code):**
- `Icon` ist ein **geschlossenes Enum** (Bedeutung → festes 1.21-Material, MENU_DESIGN §3.1). Es kann **kein beliebiges** Material ausdrücken.
- `IconSpec` trägt: semantisches `Icon`, Name, Lore, **`skinOwner` (UUID)**, glow. Es gibt **keine** rohe-Material- und **keine** Textur-Variante.
- `MenuRenderer.toStack()` baut den `ItemStack` aus `MenuStyle.material(icon.icon())` (Enum) und setzt für Köpfe nur `SkullMeta.setOwningPlayer(getOfflinePlayer(skinOwner))`. **Kein** roher Material-Override, **keine** Textur-Profile.
- `MenuItem` nimmt nur `IconSpec` — es gibt **keinen** Raw-`ItemStack`-Notausgang, über den das Feature die Generik umgehen könnte.

**Daraus folgt:**

| `display_icon`-Präfix | Mit heutiger Generik renderbar? |
|---|---|
| `head-player:<uuid>` | ✅ Ja — `IconSpec.head(uuid, …)` (keine Änderung) |
| `material:<MATERIAL>` | ❌ Nein — Enum kann beliebiges Material nicht tragen |
| `head-texture:<texture>` | ❌ Nein — `IconSpec`/Renderer kennen keine Textur |
| `null` / unbekannt | ✅ Ja — Default-`Icon` (keine Änderung) |

Da die Spec (FR-020) **alle drei** Präfixe verlangt, kann das Feature die Anforderung **nicht** erfüllen, ohne die generische Render-Schicht zu erweitern. Das ist der im Auftrag benannte STOPP-Fall.

**Auflösung — Phase R0 (vom Auftraggeber freigegeben, 2026-06-23): additiver Raw-`ItemStack`-Notausgang in der Render-Schicht; das Feature baut den `ItemStack`.**

Der Auftraggeber hat die Richtung präzisiert: Ein **`IconResolver` (String → `ItemStack`)** lebt **im Feature** (eine Stelle) und baut den Kopf/Material selbst über die Paper-`PlayerProfile`-API (kein NMS). Das Menü muss daher einen **fertig gebauten `ItemStack`** annehmen können — den Notausgang, den es heute nicht hat. Phase R0 ist damit:
- `IconSpec`: **ein** optionales Feld `baseItem` (`ItemStack`, nullable) + Factory `IconSpec.ofItem(ItemStack base, MenuText name, List<LoreLine> lore)`. Bestehende Enum-/Head-Factories und die Bukkit-freie Konstruktion bleiben unverändert (Feld default `null`) → **rückwärtskompatibel**. (Trade-off: `IconSpec` kann optional einen Bukkit-`ItemStack` tragen; der reine Enum-Pfad bleibt server-frei testbar, nur der neue Pfad braucht Bukkit — und wird vom `IconResolver` versorgt, der ohnehin Bukkit hat.)
- `MenuRenderer.toStack()`: additiver Zweig — bei `baseItem != null` `stack = baseItem.clone()` statt `new ItemStack(MenuStyle.material(...))`; Name/Lore/Italic-aus/Flags werden wie gehabt darüber gelegt. Das Skull-Profil steckt bereits im `baseItem` (vom Resolver gesetzt) → der Renderer braucht **keine** Profil-Logik.

So liegt die `PlayerProfile`-Logik (Schreiben = Kopf bauen) im Feature-`IconResolver` und ist symmetrisch zur Lese-Seite des Befehls `/rank toDisplayIcon` (Textur aus dem Item in der Hand extrahieren). **Beide Richtungen teilen die Präfix-Konstanten in EINER Klasse `DisplayIconFormat`**, damit sie nie auseinanderlaufen.

Begründung der Generik-Berührung: Ein vom Feature gelieferter `ItemStack`-Icon-Notausgang ist eine **allgemeine** Menü-Fähigkeit (künftige Cosmetics-/Custom-GUI-Slices brauchen sie ebenso), kein Permission-Spezifikum. Additiv, ändert kein bestehendes Verhalten. Siehe `research.md §Pattern-Leak-Audit`.

**Verworfene Alternative (kein Generik-Eingriff):** Nur `head-player:<uuid>` echt rendern; `material:`/`head-texture:` auf das Default-Icon zurückfallen — verletzt FR-020. Verworfen.

**Verworfene Alternative (Renderer baut aus Strings):** `IconSpec` trägt `rawMaterial`/`headTexture`-Strings, der `MenuRenderer` baut Material **und** Kopf-Profil. Hält `IconSpec` Bukkit-frei, **legt aber die `PlayerProfile`-Schreiblogik in die Render-Schicht** — unsymmetrisch zur Lese-Seite des Befehls und gegen den ausdrücklichen Wunsch „IconResolver (String→ItemStack) im Feature, eine Stelle". Verworfen zugunsten des Notausgangs.

## Technical Context

**Language/Version**: Java 21 (Paper API `1.21.x`, `compileOnly`)

**Primary Dependencies** (alle vorhanden): `com.mcplatform:plugin-protocol` (geteiltes Artefakt mit `com.mcplatform.protocol.permission.*` — **wird nicht verändert**, nur lesend konsumiert), Adventure (in Paper gebündelt), Gson (geshaded, hinter `JsonCodec`), Lettuce (geshaded, hinter `EventBus`).

**Storage**: Keine Persistenz. Backend ist Source of Truth. Einziger lokaler Zustand: RAM — `FeatureCache<UUID, PlayerPermissionsView>` (effektive Permissions + `RoleDisplay`), feature-lokal, flüchtig.

**Testing**: JUnit 5 (`useJUnitPlatform()`), Muster wie `EconomyBalancesTest`/`ReportFormatTest`/`MenuBuilderTest`. Schwerpunkt der Pure-Unit-Tests: `DisplayIconFormat` (Präfix-Parsing/-Format), `PermissionGate` (Cache/Cold-Cache/Node), Cache-Apply (Version), `PermissionFormat` (DE-Labels, Fehler→Meldung). Bukkit-nahe Tests (Resolver-Roundtrip, `/rank`-String-Bau, Renderer-baseItem) gegen ein Paper-Test-Harness/`RecordingMenuView`.

**Target Platform**: Paper-1.21-Server-JVM (Java 21).

**Project Type**: Single Gradle module (Bukkit/Paper-Plugin) — Shadow-JAR.

**Performance Goals**: Main-Thread NIE blockieren (alle REST/Redis async, Bukkit-Hops via Scheduler). Live-Reload nach Ereignis < wenige Sekunden (SC-001).

**Constraints**: Adventure-Components only (keine §-Codes); kein NMS/Reflection; nur Menü-Framework; `protocol` unverändert; generische Klassen unverändert **außer** dem gemeldeten, freizugebenden Phase-R0-Vor-Refactor.

**Scale/Scope**: Kleines Team; einige Dutzend Rollen; online Spieler im zweistelligen Bereich.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` ist ein **unausgefülltes Template**. Bindende Leitplanken sind die Architektur-Regeln aus dem Auftrag, `MENU_DESIGN.md` und der etablierten Plugin-Praxis (`PROGRESS.md`). Als Gates geprüft:

| Gate (de-facto-Constitution) | Status | Begründung |
|---|---|---|
| **G1 — „Ein Anstecken": neues Package + EIN Registry-Eintrag** | ✅ PASS | Nur `feature.permission/*` neu; einzige Bestands-Edits: 1 `.register(new PermissionFeature(menus))`-Zeile in `McPlatformPlugin` + additive `plugin.yml`-Deklarationen. |
| **G2 — Plugin = reiner Client (keine DB/Resolver/direkte Redis-Reads)** | ✅ PASS | Schreiben via `BackendClient`/REST, Live-Lesen via `EventBus`/Pub-Sub; lokal nur RAM-Cache. Resolver bleibt Backend. |
| **G3 — Writes über `EndpointDescriptor`, keine Pfad-Strings** | ✅ PASS | Nur `PermissionEndpoints.*` aus dem Artefakt. |
| **G4 — JSON-(De)Serialisierung an der bestehenden EINEN Stelle** | ✅ PASS | In `HttpBackendClient`/`JsonCodec`; Feature sieht nur typisierte Records. |
| **G5 — Live decode über dieselbe `PlatformProtocol.create()`** | ✅ PASS | `EventBus` ist mit `PlatformProtocol.create()` verdrahtet; Feature ruft nur `subscribe(PermissionChannels.CHANGED, PermissionChangedEventCodec.INSTANCE, …)`. **Annahme R1:** Der Permission-Codec ist in `PlatformProtocol.create()` registriert (in research.md zu verifizieren). |
| **G6 — Main-Thread NIE blockieren (Scheduler-Abstraktion)** | ✅ PASS | REST/Redis via `scheduler.runAsync`; Bukkit/Inventory via `scheduler.runSync`. Offline-Name-Auflösung off-main (Punishment-Muster). |
| **G7 — generische FeatureCache/EventBus/BackendClient/MenuBuilder/PluginFeature/Scheduler unverändert** | ✅ PASS | Alle sechs explizit genannten Bausteine werden **unverändert** wiederverwendet (Reuse-Matrix in research.md §Reuse). |
| **G8 — Adventure/MenuBuilder nach MENU_DESIGN, STATIC sauber** | ✅ PASS | UI über `MenuBuilder`/`MenuText`/`IconSpec`/`Lore`/`ConfirmDialog`; Menüs STATIC, Re-render nach eigener Aktion. |
| **G9 — `protocol` (Backend-Contract) unverändert** | ✅ PASS | Nur lesend konsumiert. |
| **G10 — Render-Schicht (Icon/IconSpec/MenuRenderer) für `display_icon` ausreichend** | ❌ FAIL → mitigiert durch **Phase R0 (freigegeben)** | Generisches Icon-Modell kann `material:`/`head-texture:` nicht ausdrücken (siehe Muster-Leck). Auflösung: additiver Raw-`ItemStack`-Notausgang — **vom Auftraggeber am 2026-06-23 freigegeben**. Eintrag in Complexity Tracking. |

**Ergebnis:** Alle Gates PASS **außer G10**, das durch den gemeldeten, eng begrenzten additiven Vor-Refactor (Phase R0, freigegeben) aufgelöst wird. **G10 ist kein Eingriff in die sechs unter G7 geschützten Bausteine** — `IconSpec`/`MenuRenderer` gehören zur Render-Schicht, nicht zu den Kern-Generika; dennoch als Muster-Leck behandelt und vorgelegt.

## Project Structure

### Documentation (this feature)

```text
specs/002-permission-rank-client/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, pattern-leak audit, reuse matrix, cache-version strategy
├── data-model.md        # Phase 1: cache view, RoleIcon model, gate model, DTO mapping
├── contracts/           # Phase 1: consumed REST/event contract + command/permission/icon surface
│   ├── permission-endpoints.md
│   ├── commands-permissions.md
│   └── icon-mapping.md
├── quickstart.md        # Phase 1: build, run-against-backend, manual verify
├── checklists/
│   └── requirements.md  # (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/mcplatform/plugin/
├── platform/
│   ├── McPlatformPlugin.java          # EDIT (additive): +1 .register(new PermissionFeature(menus))
│   └── menu/
│       ├── IconSpec.java              # EDIT (Phase R0, additive): +baseItem (ItemStack, nullable) +ofItem(...) factory
│       └── MenuRenderer.java          # EDIT (Phase R0, additive): baseItem != null → clone() then apply name/lore
├── transport/                        # REUSE as-is (FeatureCache, BackendClient, EventBus, BackendException)
└── feature/
    └── permission/                    # NEW — entire feature lives here
        ├── PermissionFeature.java       # PluginFeature id "permission": onEnable wires all below
        ├── PlayerPermissionsView.java   # immutable: Set<String> effective + RoleDisplay (cache value)
        ├── PermissionCache.java         # thin wrapper over FeatureCache<UUID, PlayerPermissionsView>: load/apply/evict
        ├── PermissionLoader.java        # async GET …/effective → put(cache, view, version); main-thread-safe
        ├── PermissionJoinListener.java  # PlayerJoinEvent → load; PlayerQuitEvent → evict
        ├── PermissionLiveUpdater.java   # Consumer<PermissionChangedEvent>: online → reload(uuid); offline → ignore
        ├── PermissionGate.java          # cache-based optimistic check: has(uuid, node); cold-cache → neutral(true)
        ├── DisplayIconFormat.java       # SHARED, PURE: prefix constants + parse(String)→Parsed + format helpers (used by BOTH directions)
        ├── IconResolver.java            # String → ItemStack (Bukkit): material/head-texture/head-player via PlayerProfile; junk → visible fallback
        ├── IconExtractor.java           # ItemStack → String (Bukkit): vanilla→material:, custom head→head-texture:<base64> via PlayerProfile
        ├── PermissionFormat.java        # DE labels, weight/expiry render, error(status)→message (403/404/409/422/429/5xx)
        ├── RankCommand.java             # /rank toDisplayIcon → IconExtractor(in-hand) → click-to-copy Adventure component (no backend call)
        ├── RanksCommand.java            # /ranks → gate → open RoleListMenu (STATIC)
        ├── RoleListMenu.java            # STATIC paginated role list (icons via IconResolver→IconSpec.ofItem) → detail / create
        ├── RoleDetailMenu.java          # STATIC: edit role fields + role-permissions (add/remove); delete via ConfirmDialog
        ├── ControlPanelCommand.java     # /cp <Spieler> → resolve name→UUID (off-main) → open PlayerGrantsMenu (STATIC)
        ├── PlayerGrantsMenu.java        # STATIC: active roles + direct perms (icons via IconResolver); grant/revoke (optional expiry/reason)
        └── PermissionInput.java         # anvil/chat input helpers per MENU_DESIGN §4.6 (role name, perm string, duration)

src/main/resources/plugin.yml          # EDIT (additive): +commands ranks, cp, rank (usage; gating is cache-based, see contracts)

src/test/java/com/mcplatform/plugin/feature/permission/
├── DisplayIconFormatTest.java         # PURE: parse all prefixes, null, unknown prefix, malformed uuid, empty payload, multi-colon payload
├── IconRoundTripTest.java             # resolver(extractor(item)) ≈ item per prefix; junk string → fallback icon (MockBukkit/Paper test harness)
├── PermissionGateTest.java            # has-node / lacks-node / cold-cache-neutral
├── PermissionCacheTest.java           # version-aware apply; offline-ignore; evict
├── PermissionLiveUpdaterTest.java     # online → reload; offline → no load; changeType handling incl. unknown
├── PermissionFormatTest.java          # DE labels + error→message mapping
├── RankCommandTest.java               # vanilla item → material:<NAME>; custom head → head-texture:<base64>; empty hand → hint
├── RoleListMenuTest.java              # pagination + icon per role + empty-list marker (RecordingMenuView)
└── PlayerGrantsMenuTest.java          # grant/revoke buttons, confirm for revoke, STATIC re-render after action

src/test/java/com/mcplatform/plugin/platform/menu/
└── MenuRendererIconTest.java          # Phase R0: baseItem clone path applies name/lore; null baseItem → enum path unchanged
```

**Structure Decision**: Single Gradle module; das gesamte Feature liegt in `feature/permission/`. Bestands-Berührungen: **additiv** (ein Registry-Eintrag, Command-/Permission-Deklarationen) **plus** der gemeldete, freizugebende additive Phase-R0-Vor-Refactor an `IconSpec`/`MenuRenderer`. Schichtung wie etabliert: `platform → transport → feature.permission → protocol (shared)`.

## Phasenüberblick (Implementierung)

**Phase R0 — Vor-Refactor (freigegeben):** `IconSpec` + `MenuRenderer` additiv um den Raw-`ItemStack`-Notausgang (`baseItem` + `ofItem(...)` + clone-Pfad) erweitern; `MenuRendererIconTest`. Liefert die generische Fähigkeit, auf der das Feature-`IconResolver`-Ergebnis ins Menü gelangt.

Danach entlang der Story-Prioritäten (jede Stufe eigenständig lauffähig/testbar):

1. **P2-Fundament — Cache & Live (US2):** `PlayerPermissionsView` → `PermissionCache` (über generische `FeatureCache`) → `PermissionLoader` → `PermissionJoinListener` → `PermissionLiveUpdater` → `subscribe(PermissionChannels.CHANGED, …)` in `PermissionFeature.onEnable`. Relog-freier Effekt.
2. **P1 — Icon-Beidrichtung (US4, gemeinsame Basis):** `DisplayIconFormat` (PURE, geteilte Präfixe + parse) + Test → `IconResolver` (String→`ItemStack`, Fallback) → `IconExtractor` (`ItemStack`→String) → `RankCommand` (`/rank toDisplayIcon`, click-to-copy) + `IconRoundTripTest`/`RankCommandTest` + `plugin.yml`(rank). Hängt an R0.
3. **P1 — Rollen-Verwaltung (US1, `/ranks`):** `PermissionFormat` → `RoleListMenu` (Icons via `IconResolver`→`IconSpec.ofItem`) → `RoleDetailMenu` (CRUD + Rollen-Permissions, Delete via `ConfirmDialog.critical()`) → `RanksCommand` + `plugin.yml`(ranks).
4. **P1 — Spieler-Control-Panel (US1, `/cp <Spieler>`):** `PermissionInput` → `PlayerGrantsMenu` (GRANT/REVOKE_ROLE, GRANT/REVOKE_PERMISSION, optional Ablauf/Grund) → `ControlPanelCommand` (Name→UUID off-main, offline-fähig) + `plugin.yml`(cp).
5. **P2 — Gate (US3):** `PermissionGate` an die Command-/Menü-Einstiege koppeln (feingranulare Nodes; Cold-Cache neutral; `403`-Fehlerpfad über `PermissionFormat`).

Registrierung als Letztes: **eine** `.register(new PermissionFeature(menus))`-Zeile in `McPlatformPlugin` (nach `ReportFeature`, vor `HubFeature`).

Details zu DTO-Mapping, Cache-Version-Strategie, Reuse-Matrix und Icon-Mapping: `research.md`, `data-model.md`, `contracts/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Phase R0: additiver Raw-`ItemStack`-Notausgang in `IconSpec` + `MenuRenderer`** (generische Render-Schicht) | FR-020 verlangt `material:<MATERIAL>` und `head-texture:<texture>`; das geschlossene `Icon`-Enum + nur-`skinOwner`-`IconSpec` können das nicht ausdrücken, und `MenuItem` bietet heute keinen Raw-`ItemStack`-Notausgang. Der vom Auftraggeber gewünschte `IconResolver` (String→`ItemStack`, im Feature) braucht genau diesen Notausgang, damit der fertige `ItemStack` ins Menü gelangt. | „Nur `head-player` + Default" verletzt FR-020. „Renderer baut aus `rawMaterial`/`headTexture`-Strings" hält `IconSpec` Bukkit-frei, legt aber die `PlayerProfile`-Schreiblogik in die Render-Schicht — unsymmetrisch zur Lese-Seite des `/rank`-Befehls und gegen den ausdrücklichen „eine Stelle im Feature"-Wunsch. Der Notausgang ist additiv, rückwärtskompatibel, generisch nützlich (Cosmetics/Custom-GUIs). |
