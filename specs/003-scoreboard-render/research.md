# Research — Scoreboard Render-Schicht Plugin-Slice (Phase 0)

Entscheidungen, Reuse-Audit und am Code verifizierte Befunde. Format je Punkt: **Decision / Rationale / Alternatives**. Alle NEEDS-CLARIFICATION der Spec sind aufgelöst (P1–P4 → siehe unten / spec.md Clarifications).

## §Codebase-Verifikation (Grundlage)

Direkt am Plugin-Quellcode geprüft:

- **`EventDispatcher` = Multi-Subscriber pro Channel.** `Map<String, CopyOnWriteArrayList<Registration>>`, `register` hängt an, `dispatch` liefert an alle typ-passenden Handler. (`transport/EventDispatcher.java`)
- **Economy-Cache lazy, KEIN Join-Hook.** `EconomyFeature` füllt `FeatureCache<UUID,Long>` über `/balance` (cache-first+REST-fallback in `BalanceCommand`) und `mc:economy:balance`. (`feature/economy/EconomyFeature.java`, `BalanceCommand.java`, `EconomyBalances.java`)
- **Economy notifiziert `liveBus` nach Apply:** `menus.liveBus().notifyChange(playerUuid)`.
- **Permission-Cache warm:** `PermissionWarmupListener` lädt fail-closed im PreLogin; `PermissionCache.get → PlayerPermissionsView(effective, RoleDisplay display)`. (`feature/permission/*`)
- **`PermissionLoader.reload` async** (REST `EFFECTIVE` → `whenComplete → runSync(cache.apply)`), **ohne** `liveBus`-Notify.
- **`PermissionChangedEvent(playerUuid, changeType, timestampEpochMilli)`** trägt kein Display; **`ROLE_CONFIG_CHANGED` kommt pro Holder** (Code-Kommentar in `PermissionLiveUpdater`).
- **`MenuLiveBus`** (`platform/menu/MenuLiveBus.java`): `LiveHandle observe(Object topic, Runnable onChange)` + `void notifyChange(Object topic)`; leak-safe `close()`. Der einzige Observer-Hook im Codebase.
- **Kein Bukkit-Scoreboard** irgendwo (`org.bukkit.scoreboard.*` ungenutzt).

## §Reuse-Audit (kein Generik-Eingriff)

**Decision:** Vollständig additiv. Wiederverwendet (unverändert): `PluginFeature`/`FeatureRegistry`/`FeatureContext`, `EventBus`/`EventDispatcher`, `PlatformScheduler`, `FeatureCache`, `MenuLiveBus`, Adventure. Neu: Package `feature.scoreboard/*` + zwei Lese-Ports + eine Notify-Zeile + Composition-Verdrahtung.

**Rationale:** Das Muster „neuer `PluginFeature` ohne Generik-Änderung" hat 5× gehalten (Memory: punishment/menu/web „architecture holds"). Kein `platform`/`transport`/Menu-Generik-Typ wird geändert → **kein Muster-Leck-STOPP**.

**Alternatives:** Neuer generischer Observer-Hook auf `FeatureCache`/`EventBus` → wäre ein Generik-Eingriff (STOPP). Verworfen — `MenuLiveBus` existiert genau dafür und wird wiederverwendet.

## §Live-Update-Architektur (P2-Folgeentscheidung + async-Race)

**Decision:** `MenuLiveBus` als geteilter **Post-Apply**-Re-Render-Trigger (keyed per Spieler-UUID); Werte aus den Read-Ports. Scoreboard hat **keine** eigene Transport-Subscription. Permission bekommt **eine** additive `notifyChange`-Zeile nach `cache.apply`.

**Rationale:** Coins-Live ginge auch via eigener Channel-Subscription (Event trägt `balance`); Rang-Live **nicht** (Event trägt kein Display + Reload async). `MenuLiveBus.notifyChange` feuert **nach** dem Cache-Apply (im selben `runSync`) → der Observer liest stets den frischen Wert → die async-Reload-Race ist strukturell ausgeschlossen. Beide Pfade symmetrisch, Caches bleiben Wahrheit (spec.md §4).

**Alternatives:**
- *Eigene Channel-Subscription + Selbst-Projektion (eigene Cache):* verletzt „Caches konsumieren", doppelter `/effective`-REST, Rang-Race über Cache-Variante. Verworfen (dokumentierter Rückfall bei strikt-null-Geschwister-Touch).
- *Synchron Cache nach Event lesen (Entwurf):* stale, weil Permission-Reload async. Verworfen.

## §Read-Ports (Cache-Konsum)

**Decision:** `EconomyReadPort` (`OptionalLong current(UUID)` + `CompletableFuture<OptionalLong> load(UUID)`, cache-first+REST-fallback, wiederverwendet `BalanceCommand`-Logik) und `PermissionReadPort` (`Optional<String> currentRankName(UUID)` aus warmem Cache). Konstruiert in den jeweiligen Features, aus der Composition-Root an `ScoreboardFeature` gereicht.

**Rationale:** Economy-Cache ist beim Join kalt → `load` mit REST-fallback nötig (sonst leere Coins). Permission-Cache ist warm → sync lesbar. Reine Lese-Sichten, keine neue Wahrheit.

**Alternatives:** Economy einen Join-Warmup geben → invasiver Eingriff in Economys bewusst lazy Design. Verworfen zugunsten cache-first+REST-fallback im Port.

## §Flicker-Strategie (P2)

**Decision:** Team-Entry-Slots (stabiler unsichtbarer Entry je Position; Text im `Team#prefix(Component)`), Update = Prefix-Änderung des Slots. Kein Objective-Neuschreiben.

**Rationale:** Bukkit-Sidebar flackert bei Entry-Add/Remove. Team-Slots sind flickerfrei und passen 1:1 zur stabilen `LineId` (AC-3). Paper-1.21 unterstützt Adventure-Components am Team-Prefix → kein §-Code/Legacy-Reset (FR-008).

**Alternatives:** Objective-Score-Texte direkt → flackert/instabil; Legacy-§-Codes → verboten. Verworfen.

## §Profil-Definition mit injizierten Providern

**Decision:** Profile werden bei `onEnable` deklarativ gebaut (`Profiles.build(providers)`), nicht als class-load-`static final`-Konstanten.

**Rationale:** Echte Provider tragen injizierte Ports → keine Konstante zur Klassenladzeit. „Definition im Code, kein String-Bau" bleibt erfüllt (FR-001).

**Alternatives:** Static-Konstanten mit Service-Locator/Statik für Ports → versteckte globale Abhängigkeit. Verworfen.

## §Spec-Offene-Fragen — Auflösung

- **P1 (Permission-Live-Signal):** Backend publiziert `GRANT_*`/`ROLE_CONFIG_CHANGED` bereits; das Plugin **consumed** sie schon (`PermissionFeature` → `PermissionLiveUpdater` → Cache-Reload). Für Live-Rang fehlt nur die `liveBus`-Notify-Zeile (additiv). `ROLE_CONFIG_CHANGED` kommt pro Holder → kein Fan-out nötig. **Gelöst.**
- **P2 (Flicker):** Team-Entry-Slots. **Gelöst** (s. o.).
- **P3 (Profil-Inhalte):** Ein Profil „Default" — Header, Rang, Coins, Stats-Stub, Footer. **Gelöst** (Clarification).
- **P4 (Doku-Drift):** Permission-Plugin-Slice in `PROGRESS.md` vor `/implement` nachtragen. **Als /tasks-Aufgabe geführt.**
