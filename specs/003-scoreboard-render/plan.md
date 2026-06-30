# Implementation Plan: Scoreboard (Render-Schicht, Slice 1)

**Branch**: `003-scoreboard-render` | **Date**: 2026-06-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-scoreboard-render/spec.md`

## Summary

Ein neuer, rein additiver `PluginFeature` (`feature.scoreboard`), der jedem online Spieler eine kontextabhängige Sidebar in Adventure-Components zeigt. In Slice 1 existiert **genau ein Profil „Default"** (Header, Rang, Coins, Stats-Stub, Footer). Werte werden — wie in der Spec gefordert — **aus den vorhandenen Plugin-Caches** (`feature.economy`, `feature.permission`) konsumiert, nicht in einer Parallel-Cache nachgebaut. Live-Aktualisierung von Coins **und** Rang läuft über die **bereits existierende** `MenuLiveBus`-Beobachtungs-Schicht (der einzige Observer-Hook im Codebase), nicht über eine eigene Transport-Subscription im Scoreboard. Flickerfreiheit (P2) über **Team-Entry-Slots**.

**Technischer Ansatz** — additiv, aber **breiter als „ein Registry-Eintrag"**, weil die Spec ausdrücklich das *Konsumieren der bestehenden Caches* verlangt: neben dem neuen Package `feature.scoreboard` braucht es **zwei kleine, additive Read-Ports** an den Geschwister-Features (Economy/Permission) und **eine additive `liveBus().notifyChange()`-Zeile** in der Permission-Reload-Vervollständigung. **Keine generische Klasse (Transport/Platform/Menu-Generik) wird geändert** — die `MenuLiveBus` wird nur über ihre öffentliche API *wiederverwendet*. Damit ist der Constitution-STOPP (Muster-Leck) **nicht** ausgelöst; die Geschwister-Berührung ist Feature-Layer-Verdrahtung (Präzedenz: `WebFeature`-Konstruktor-Injektion aus der Composition-Root). Siehe **§3 Integrations-Architektur** und **§10 Surface-Notiz**.

## Technical Context

**Language/Version**: Java 21 (Paper API `1.21.x`, `compileOnly`).

**Primary Dependencies** (alle vorhanden, nichts Neues): generische Plugin-Infrastruktur — `PluginFeature` / `FeatureRegistry` / `FeatureContext`, `EventBus` / `EventDispatcher` (Multi-Subscriber pro Channel — verifiziert), `PlatformScheduler` (Main-Thread-Hop), `FeatureCache<K,V>`, `platform.menu.MenuLiveBus` (Observer-Hook), Adventure (in Paper gebündelt). `plugin-protocol` wird **nur lesend** konsumiert (`com.mcplatform.protocol.economy.*`, `…permission.*`) — **unverändert**.

**Storage**: Keine Persistenz, kein eigener Cache als Wahrheit. Werte kommen aus den Caches von `feature.economy` (`FeatureCache<UUID,Long>`) und `feature.permission` (`PermissionCache` → `PlayerPermissionsView`). Einziger eigener Laufzeit-Zustand: pro online Spieler ein `BukkitScoreboardHandle` + der aktive Profil-/Render-Zustand + ein `MenuLiveBus.LiveHandle`.

**Testing**: JUnit 5 (`useJUnitPlatform()`), Muster wie `EconomyBalancesTest` / `PermissionLiveUpdaterTest` / `FeatureRegistryTest`. Bukkit-frei testbar durch Fake-`PlatformScheduler` (`task.run()`), Fake-Ports und ein Fake-/Recording-`ScoreboardHandle`. Kein MockBukkit (Codebase-Konvention: Logik hängt nur an Interfaces).

**Target Platform**: Paper-1.21-Server-JVM (Java 21).

**Project Type**: Single Gradle module (Bukkit/Paper-Plugin) — Shadow-JAR.

**Performance Goals**: Main-Thread NIE blockieren — alle REST async (`scheduler.runAsync` / `BackendClient`-Futures), jede Bukkit-Scoreboard-Mutation auf Main via `scheduler.runSync`. Live-Update einer Zeile in < wenige Ticks nach Cache-Apply.

**Constraints**: Adventure-Components only (keine §-Codes); kein NMS/Reflection; `plugin-protocol` unverändert; **keine generische Klasse geändert**; Geschwister-Features nur **additiv** berührt (Read-Ports + 1 Notify-Zeile).

**Scale/Scope**: Kleines Team; ein Profil + ein Stub-Test-Profil; online Spieler im zweistelligen Bereich.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` ist ein **unausgefülltes Template**. Bindende Leitplanken sind die Architektur-Regeln aus spec.md §4, der etablierten Plugin-Praxis (`PROGRESS.md`) und dem Muster der bisherigen fünf `PluginFeature`s. Als Gates geprüft:

| Gate (de-facto-Constitution) | Status | Begründung |
|---|---|---|
| **G1 — Anstecken über die `FeatureRegistry`** | ✅ PASS | Neues Package `feature.scoreboard/*`; Bestands-Edit: `.register(new ScoreboardFeature(menus, economyPort, permissionPort))` in `McPlatformPlugin`. **Keine** `plugin.yml`-Command-Ergänzung (Scoreboard ist join-/event-getrieben, hat keine Commands). |
| **G2 — Keine generische Klasse (Transport/Platform/Menu-Generik) geändert** | ✅ PASS | `EventBus`/`EventDispatcher`/`PlatformScheduler`/`FeatureCache`/`MenuLiveBus` werden **nur über ihre öffentliche API genutzt**, nicht modifiziert. Kein STOPP. |
| **G3 — Plugin = reiner Client (keine DB/Resolver/direkte Redis-Reads)** | ✅ PASS | Kein eigener Backend-Zugriff für „Wahrheit"; Werte aus den vorhandenen Feature-Caches. Economy-Read-Port nutzt den **bestehenden** cache-first+REST-fallback-Pfad (`BalanceCommand`-Muster). |
| **G4 — Kein neues Backend-Modul / Protocol / Channel / Endpoint** | ✅ PASS | Konsumiert ausschließlich vorhandene `economy`/`permission`-Protocol-Typen, -Endpoints und -Channels read-only (FR-011, AC-7). |
| **G5 — Threading: Main-Thread nie blockieren** | ✅ PASS | REST async; Bukkit-Scoreboard-Mutationen via `scheduler.runSync`; `MenuLiveBus.notifyChange` läuft bereits auf Main (EventDispatcher liefert dort). |
| **G6 — LIVE-Disziplin: Subscriptions beim Verlassen abmelden** | ✅ PASS | `LiveHandle.close()` + Handle-Abbau im Leave-Listener (Muster wie `MenuManager`-Cleanup), FR-009/AC-6. |
| **G7 — „Additiv, kein Bestandsverhalten geändert"** | ⚠️ PASS-mit-Notiz | Zwei additive Read-Ports + eine additive Notify-Zeile an Geschwister-Features (siehe §10). Kein bestehendes Verhalten geändert; im Sinne von spec.md §4 („konsumiert vorhandene Caches") gewollt. Transparent dokumentiert. |

**Ergebnis:** Alle Gates PASS. G7 mit dokumentierter, spec-konformer Geschwister-Berührung (additiv, kein Generik-Eingriff). Kein Muster-Leck-STOPP.

## Project Structure

### Documentation (this feature)

```text
specs/003-scoreboard-render/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 — Entscheidungen, Reuse-Audit, verifizierte Befunde
├── data-model.md        # Phase 1 — feature-lokale Typen + konsumierte Protocol-Typen + Ports
├── quickstart.md        # Phase 1 — Bauen/Testen + manuelle Verifikation
├── contracts/           # Phase 1 — interne Ports + konsumierte (read-only) Endpoints/Channels
│   ├── ports.md
│   └── consumed-endpoints-and-channels.md
├── checklists/
│   └── requirements.md  # aus /speckit-specify
└── tasks.md             # /speckit-tasks (NICHT von /speckit-plan erzeugt)
```

### Source Code (repository root)

```text
plugin/src/main/java/com/mcplatform/plugin/
├── feature/scoreboard/                 # NEU — der gesamte Slice
│   ├── ScoreboardFeature.java          # PluginFeature-Impl; Anstech-Punkt; onEnable verdrahtet alles
│   ├── model/
│   │   ├── LineId.java                 # Value Object: stabile ID (kein Index)
│   │   ├── ScoreboardLine.java         # LineId + LineProvider
│   │   ├── ScoreboardProfile.java      # id + geordnete List<ScoreboardLine>
│   │   └── RenderedLine.java           # LineId + aufgelöste Component
│   ├── profile/
│   │   ├── Profiles.java               # baut die Profil-Definition(en) im Code (Default, Test-Event)
│   │   └── ProfileCatalog.java         # id→Profil-Lookup + Default-Referenz
│   ├── condition/
│   │   ├── ScoreboardCondition.java    # Prädikat: matches(PlayerContext)
│   │   ├── ConditionRule.java          # (Condition → ProfileId)
│   │   ├── ProfileResolver.java        # erste-passende + Default-Fallback
│   │   ├── RegionCondition.java        # erste Bedingung; fragt RegionProvider
│   │   ├── RegionProvider.java         # PORT
│   │   └── StubRegionProvider.java     # Slice-1-Stub (leer | Test-Region)
│   ├── provider/
│   │   ├── LineProvider.java           # PORT: Component resolve(PlayerContext)
│   │   ├── EconomyLineProvider.java    # liest EconomyReadPort (Coins)
│   │   ├── PermissionLineProvider.java # liest PermissionReadPort (Rang-Anzeigename, plain)
│   │   ├── StaticLineProvider.java     # fester Text (Header/Footer/Trenner)
│   │   └── StubLineProvider.java       # fester Platzhalter (Stats) — Austausch-Muster
│   ├── render/
│   │   ├── ScoreboardRenderer.java     # Profil→List<RenderedLine>; schreibt via Handle
│   │   ├── BukkitScoreboardHandle.java # pro Spieler: Objective + Team-Entry-Slots (Flicker P2)
│   │   └── PlayerContext.java          # Spieler-UUID + Region-Snapshot (für Conditions)
│   └── lifecycle/
│       ├── ScoreboardJoinListener.java # Join → Context → resolve → Handle → initial render + liveBus.observe
│       └── ScoreboardLeaveListener.java# Leave → Handle abbauen + LiveHandle.close()
│
├── feature/economy/
│   └── EconomyReadPort.java            # NEU (additiv) — current(uuid)+load(uuid); cache-first+REST-fallback
├── feature/permission/
│   ├── PermissionReadPort.java         # NEU (additiv) — currentRankName(uuid) aus warmem Cache
│   └── PermissionLoader.java           # GEÄNDERT (1 Zeile additiv) — nach cache.apply: liveBus.notifyChange(uuid)
└── platform/McPlatformPlugin.java      # GEÄNDERT — Ports bauen + .register(new ScoreboardFeature(...))

plugin/src/test/java/com/mcplatform/plugin/feature/scoreboard/   # NEU — Unit-Tests (s. §8)
```

**Structure Decision**: Single Gradle module (Plugin). Der Slice lebt vollständig in `feature/scoreboard/`; die einzigen Bestands-Touches sind die zwei additiven Read-Ports, die eine additive Notify-Zeile und die Registry-Verdrahtung in der Composition-Root.

## 0. Verifizierte Codebase-Befunde (Grundlage des Plans)

Am Code geprüft (nicht angenommen):

1. **`EventDispatcher` erlaubt mehrere Subscriber pro Channel** — `Map<String, CopyOnWriteArrayList<Registration>>`; `register` *hängt an*, `dispatch` liefert an **alle** typ-passenden Registrierungen. → Eine eigene Scoreboard-Subscription wäre technisch möglich, ist aber **nicht nötig** (siehe §3).
2. **Economy-Cache ist *lazy*** — `EconomyFeature` hat **keinen** Join-Hook; Cache füllt sich über `/balance` (cache-first+REST-fallback) und das `mc:economy:balance`-Event. → Beim Join eines Spielers ist die Coins-Cache evtl. **kalt** → der Coins-Read muss REST-fallback können.
3. **Permission-Cache ist *warm***  — `PermissionWarmupListener` lädt fail-closed im **PreLogin**. → Rang-Read ist beim Join **warm** und synchron lesbar (`PermissionCache.get → PlayerPermissionsView.display().displayName()`).
4. **Economy notifiziert bereits `liveBus`** — nach `EconomyBalances.apply(...)`: `menus.liveBus().notifyChange(playerUuid)`. **Permission notifiziert NICHT** — `PermissionLoader.load` macht `cache.apply` im `runSync`, aber **ohne** `notifyChange`. → Für Live-Rang fehlt **eine** additive Notify-Zeile.
5. **`PermissionLoader.reload` ist async** — REST `EFFECTIVE` → `whenComplete → runSync(cache.apply)`. → Ein synchrones „Cache nach dem Event lesen" (wie im Entwurf) wäre **stale**; das Re-Render muss **nach** dem Apply getriggert werden (genau das leistet die Notify-Zeile, da sie im selben `runSync` nach `apply` läuft).
6. **`PermissionChangedEvent` trägt kein Display** — nur `(playerUuid, changeType, timestampEpochMilli)`; **`ROLE_CONFIG_CHANGED` kommt pro Holder** (im Code kommentiert) → **kein** Fan-out im Scoreboard nötig (FR-006a-Kaskade ist backend-seitig je Spieler aufgelöst).
7. **Kein Bukkit-Scoreboard im Codebase** — `org.bukkit.scoreboard.*` wird nirgends genutzt; `BukkitScoreboardHandle` ist Greenfield (gut kapselbar/testbar).

## 1. Kernabstraktionen

### LineId + ScoreboardLine
Zeile = **stabile `LineId`** (Identität über Zeit, für Live-Adressierung & Flicker-Vermeidung) + ein `LineProvider`. Reihenfolge = Listen-Position im Profil; das Bukkit-`score` (Zeilenhöhe) wird aus der Position abgeleitet, **nicht** aus der ID. Position ändern bricht keine ID (FR-002, AC-5).

### LineProvider (Port) — **synchron**
`Component resolve(PlayerContext ctx)`. Liest den **aktuell bekannten** Wert aus dem jeweiligen Read-Port (warm/Cache) und rendert die Component. Kein I/O im Provider — das async Laden orchestriert der Lifecycle (§4). Austausch Stub→echt = die Zeilen-Definition zeigt auf einen anderen Provider; **Renderer unberührt** (AC-4, FR-013).

### ScoreboardProfile + Profiles (im Code deklariert, mit injizierten Providern)
**Abweichung vom Entwurf:** Profile sind **nicht** class-load-`static final`-Konstanten, weil die echten Provider injizierte Ports brauchen. Stattdessen baut `Profiles` die Definition **deklarativ bei `onEnable`** mit den injizierten Provider-Instanzen — weiterhin „Definition im Code, kein String-Bau" (FR-001):
```
Default = profile("Default",
    line(HEADER, static("» MC Platform «")),
    line(SEP1,   static(" ")),
    line(RANK,   permissionRankProvider),   // plain display name, FR-003a
    line(COINS,  economyCoinsProvider),
    line(STATS,  stub("Kills: 0")),          // bis Stats migriert ist
    line(SEP2,   static(" ")),
    line(FOOTER, static("play.example.net")));
```
`TEST_EVENT` = abweichendes Profil zum Beweis der Bedingungs-Selektion (AC-2/US3).

### ProfileResolver (Selektion)
Geordnete `List<ConditionRule>`; `resolve(ctx)` nimmt das Profil der ersten matchenden Rule, sonst `Default`. Region ist die erste Rule; weitere Prädikate später ohne Resolver-Änderung (FR-004/FR-005).

### RegionProvider (Port, Stub in Slice 1)
`Optional<RegionId> currentRegion(Player p)`. `StubRegionProvider` liefert konfigurierbar leer (→ Default) oder eine Test-Region (→ `TEST_EVENT`), damit AC-2 testbar ist. Das echte Region-System ersetzt später **nur** diese Klasse.

## 2. Read-Ports (Konsum der bestehenden Caches)

Die Spec verlangt: Werte aus den **vorhandenen** Economy/Permission-Caches, keine Parallel-Cache. Dafür zwei kleine, additive Ports:

- **`EconomyReadPort`** (in `feature.economy`): `OptionalLong current(UUID)` (Cache-Read, sync) + `CompletableFuture<OptionalLong> load(UUID)` (cache-first → REST-fallback, füllt den Economy-Cache; **wiederverwendet** die `BalanceCommand`-Logik). Konstruiert in `EconomyFeature.onEnable` über denselben `balances`-Cache; aus der Composition-Root an `ScoreboardFeature` gereicht.
- **`PermissionReadPort`** (in `feature.permission`): `Optional<String> currentRankName(UUID)` — liest den **warmen** `PermissionCache` → `PlayerPermissionsView.display().displayName()` (plain, FR-003a). Cold-Cache → `Optional.empty()` (→ neutraler Platzhalter; PreLogin-Warmup garantiert warm).

Beide Ports sind reine **Lese**-Sichten auf bestehende Caches — keine neue Wahrheit, kein neuer Channel.

## 3. Integrations-Architektur (Live-Update) — die zentrale Entscheidung

**Gewählt: `MenuLiveBus` als geteilter Post-Apply-Re-Render-Trigger; Werte aus den Read-Ports. Das Scoreboard hat KEINE eigene Transport-Subscription.**

Ablauf:
- **Initial (Join):** `permissionRankProvider` liest den warmen Cache → sofort gerendert. `economyCoinsProvider` rendert zunächst einen Platzhalter; der Lifecycle ruft `economyPort.load(uuid).thenAccept(coins → scheduler.runSync(updateCoinsSlot))` → Coins-Slot füllt sich, sobald da (kein Flackern, Team-Slot-Update).
- **Live Coins:** `EconomyFeature` ruft (unverändert) nach `apply` `liveBus.notifyChange(uuid)`. → Scoreboard-Observer re-resolved die Coins-Zeile aus `economyPort.current(uuid)` (jetzt frisch) → Slot-Update.
- **Live Rang:** `PermissionLoader` ruft (NEU, 1 Zeile, im selben `runSync` **nach** `cache.apply`) `liveBus.notifyChange(uuid)`. → Scoreboard-Observer re-resolved die Rang-Zeile aus `permissionPort.currentRankName(uuid)` (jetzt frisch) → Slot-Update. **Das löst die async-Reload-Race aus Befund §0.5**, weil der Trigger erst nach dem Apply feuert.

Warum `MenuLiveBus` statt eigener Channel-Subscription:
- Es ist der **einzige Observer-Hook** im Codebase (FeatureCache/EventBus haben keinen — bewusste Design-Entscheidung, Memory „menu-framework-architecture-holds"). Eigene Subscription würde für Coins funktionieren (Event trägt `balance`), für **Rang aber nicht** (Event trägt kein Display + Reload ist async). `MenuLiveBus` macht beide Pfade symmetrisch und nutzt die Caches als Wahrheit.
- `notifyChange` läuft auf dem Main-Thread; der Observer kapselt sich dennoch defensiv mit `scheduler.runSync`, falls ein künftiger Notifier off-main feuert.

**Verworfene Alternative — eigene Channel-Subscription + Selbst-Projektion:** Scoreboard subscribt selbst `mc:economy:balance` + `mc:permission:changed`, hält eine eigene `FeatureCache`, liest Coins aus dem Event-Payload und lädt Rang per eigenem `/effective`-REST. *Pro:* null Geschwister-Touch. *Contra:* baut eine **Parallel-Cache** (verletzt spec.md §4 „konsumiert vorhandene Caches"), doppelter `/effective`-REST je Änderung, async-Race beim Rang über die Cache-Variante. **Verworfen** zugunsten der spec-konformen Cache-Konsum-Variante. (Falls strikt **kein** Geschwister-Touch gewünscht ist, ist dies der dokumentierte Rückfall.)

## 4. Flicker-Strategie (Entscheidung zu P2)

**Gewählt: Team-Entry-Slots, kein Objective-Neuschreiben.** Pro Zeilen-Position ein fester, unsichtbarer Entry-String als stabiler Slot; sichtbarer Text liegt im **Team-Prefix** (Paper-1.21: `Team#prefix(Component)` — Adventure, keine §-Codes). Ein Zeilen-Update ändert nur den Prefix des betroffenen Slots → kein Flackern, exakte `LineId`→Slot-Adressierung (AC-3). `BukkitScoreboardHandle` kapselt die Mechanik vollständig; der Renderer denkt nur in `(LineId, Component)`. Konsequenz: `handle.update(LineId.COINS, component)` statt Board-Neuaufbau.

## 5. Lifecycle

- **Join** (`ScoreboardJoinListener`): `PlayerContext` bauen (Region-Snapshot via Stub) → `ProfileResolver.resolve` → `BukkitScoreboardHandle` anlegen (Objective + Slots, via `runSync`) → alle Zeilen initial rendern (Rang warm, Coins async via `economyPort.load`) → `liveBus.observe(uuid, reRender)` registrieren, `LiveHandle` halten. Reihenfolge nach Permission-PreLogin-Warmup (garantiert) — Economy lazy, daher `load` statt Cache-Annahme.
- **Leave** (`ScoreboardLeaveListener`): `LiveHandle.close()` (Observer entfernen, kein Leak) + `handle.teardown()` (Spieler-Scoreboard zurücksetzen). FR-009/AC-6.
- **Profil-Wechsel zur Laufzeit (Region):** in Slice 1 minimal — Stub ist statisch; voller reaktiver Wechsel kommt mit dem echten Region-System (dokumentierte Grenze, R3).

## 6. Threading / Constitution-Konformität

- REST (`economyPort.load`) async über `BackendClient`-Futures; jede Bukkit-Scoreboard-Mutation via `scheduler.runSync`. Main-Thread nie blockieren.
- `MenuLiveBus.notifyChange` läuft bereits auf Main (EventDispatcher `runSync`); Observer kapselt dennoch `runSync`.
- Keine direkten Backend-Reads als „Wahrheit" im Render-Pfad — Werte aus den Feature-Caches (Economy-Port nutzt den bestehenden cache-first+REST-fallback).

## 7. Bidirektionale Datenflüsse (Übersicht)

```
BalanceChangedEvent ─(economy subscriber)→ EconomyBalances.apply(cache) → liveBus.notifyChange(uuid)
PermissionChangedEvent ─(permission subscriber)→ PermissionLoader.reload → cache.apply → liveBus.notifyChange(uuid)   [+1 Zeile NEU]
                                                                                              │
ScoreboardJoinListener.observe(uuid) ─────────────────────────────────────────────────────────┘
        │ reRender(uuid): resolve dynamische Zeilen via Ports → handle.update(LineId, Component)
        └ initial: permissionPort.currentRankName(uuid)  +  economyPort.load(uuid).thenAccept(...)
```

## 8. Test-Strategie (Schichten, Bukkit-frei wo möglich)

- **model/profile (pure JDK):** Profil-Definition, `LineId`-Stabilität, Zeilen-Austausch (AC-5) — Position ändert Score, nicht ID.
- **condition (pure JDK):** `ProfileResolver` — erste-passende + Default-Fallback (AC-1/US3), Rule-Priorität; `RegionCondition` gegen `StubRegionProvider` (leer→Default, Testregion→TEST_EVENT).
- **provider (Fake-Ports):** `EconomyLineProvider`/`PermissionLineProvider` rendern aus Fake-`EconomyReadPort`/`PermissionReadPort`; Stub-Austausch ändert nur die Zuordnung, Renderer-Output strukturell gleich (AC-4); Rang ist **plain** (FR-003a); leerer Port → Platzhalter (Edge Case).
- **render (Fake/Recording-Handle):** `ScoreboardRenderer` löst Profil→`List<RenderedLine>` korrekt auf; `handle.update(lineId, …)` trifft den richtigen Slot; last-write-wins / kein Debounce (FR-007a).
- **lifecycle (Fake-liveBus + Fake-scheduler):** `observe`/`reRender` aktualisiert genau die dynamischen Zeilen bei `notifyChange` (AC-3 für Coins **und** Rang); Leave schließt `LiveHandle` → `observerCount == 0` (AC-6); Coins-`load`-Future füllt den Slot.
- **Ports (additiv, pure/Fake):** `EconomyReadPort` cache-first vs. REST-fallback (Fake-BackendClient); `PermissionReadPort` warm→Name, cold→empty.
- **Permission-Notify (Regression):** Test, dass `PermissionLoader` nach `apply` `notifyChange(uuid)` aufruft (die neue Zeile) — und bei REST-Fehler **nicht**.
- **Negativ/Grenzen:** kein Backend-Artefakt (AC-7, Review-Check); offline/leavender Spieler → kein Update (Handle/Observer bereits abgemeldet).

## 9. Definition of Done (verfeinert in /tasks)

Wie spec.md §6 (Success Criteria SC-001…SC-007), plus: Flicker = Team-Slots umgesetzt; Live-Coins **und** Live-Rang über `liveBus` verdrahtet (inkl. der einen Permission-Notify-Zeile); Ports getestet; `./gradlew build` (Plugin) grün; **keine** generische Klasse geändert (verifiziert); `PROGRESS.md` (Plugin) + `FEATURE_INVENTORY.md` (#72-Teilabhaken) nachgezogen; **P4** (Permission-Plugin-Slice in `PROGRESS.md` nachtragen) erledigt.

## 10. Surface-Notiz (Geschwister-Berührung — bewusst, transparent)

Dieser Slice ist additiv, aber **nicht** „nur ein Registry-Eintrag". Berührte Bestands-Dateien (alle additiv, **kein** Generik-Eingriff, **kein** Bestandsverhalten geändert):
1. `feature/economy/EconomyReadPort.java` — **neu** (Lese-Port auf den vorhandenen `balances`-Cache).
2. `feature/permission/PermissionReadPort.java` — **neu** (Lese-Port auf den vorhandenen `PermissionCache`).
3. `feature/permission/PermissionLoader.java` — **+1 Zeile** `liveBus.notifyChange(uuid)` nach `cache.apply` (Symmetrie zu Economy; ohne sie ist Live-Rang nicht möglich, FR-006a).
4. `platform/McPlatformPlugin.java` — Ports bauen + `.register(new ScoreboardFeature(...))`.

Begründung: spec.md §4 verlangt **Konsum der vorhandenen Caches** statt Parallel-Cache; das erzwingt minimale Lese-Ports an den Quell-Features. Es ist **kein** Muster-Leck (keine `platform`/`transport`/Menu-Generik geändert; `MenuLiveBus` nur genutzt). Constitution-STOPP **nicht** ausgelöst. Wer strikt null Geschwister-Touch will: §3 „Verworfene Alternative" ist der dokumentierte Rückfall (Selbst-Projektion, akzeptiert dann doppelten REST + leichte Spec-Abweichung).

## 11. Risiken / bewusst offen

- **R1 — Permission-Notify-Zeile nötig:** Live-Rang braucht die eine additive `notifyChange`-Zeile in `PermissionLoader`. Mitigation: minimal, getestet, symmetrisch zu Economy. Falls der Auftraggeber Geschwister-Touch ablehnt → §3-Rückfall.
- **R2 — Team-Entry-Mechanik:** Bukkit-Eigenheiten (Entry-Eindeutigkeit, Prefix-Länge). In `BukkitScoreboardHandle` kapseln, per Fake testen; echter Server-Smoke-Test beim „es lebt"-Moment (Paper-1.21: Adventure-Team-Prefix, kein Legacy-Reset-Problem).
- **R3 — Region-Stub statisch:** kein reaktiver Profilwechsel in Slice 1 (US3 testet den Resolver, nicht Live-Region-Wechsel). Bewusste, dokumentierte Grenze; kommt mit echtem Region-System.
- **R4 — Economy-Cold-Cache beim Join:** Coins-Slot zeigt kurz Platzhalter bis `load`-Future zurück ist. Akzeptabel (kein Flackern, Slot-Update). Dokumentiert.

## Complexity Tracking

> Keine Constitution-Verletzung, die zu rechtfertigen wäre. G7 (Geschwister-Read-Ports) ist spec-induziert (§4 „Caches konsumieren") und additiv; in §10 transparent gemacht, daher hier keine Ausnahme-Zeile nötig.
