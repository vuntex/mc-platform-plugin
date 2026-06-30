# Contracts — Interne Ports (Phase 1)

Der Slice exponiert **keine** externen Schnittstellen (keine Commands, kein REST, kein Channel — AC-7/FR-011). Die „Contracts" sind daher (a) die internen Ports, die Austauschbarkeit garantieren, und (b) die read-only konsumierten Bestands-Schnittstellen (siehe `consumed-endpoints-and-channels.md`).

## LineProvider — `Component resolve(PlayerContext)`
- **Vertrag:** synchron, seiteneffektfrei, liest nur aktuellen Wert (Read-Port/Statik) und gibt eine Adventure-`Component` zurück. Kein I/O.
- **Austausch-Garantie (AC-4/FR-013):** Stub→echt = andere Implementierung an derselben `ScoreboardLine`; Renderer/IDs/übrige Zeilen unberührt.
- **Implementierungen:** `EconomyLineProvider`, `PermissionLineProvider` (plain `displayName`, FR-003a), `StaticLineProvider`, `StubLineProvider`.

## EconomyReadPort (feature.economy, additiv)
- `OptionalLong current(UUID)` — Cache-Read (leer wenn kalt), sync.
- `CompletableFuture<OptionalLong> load(UUID)` — cache-first → REST-fallback (`EconomyEndpoints.GET_BALANCE`), füllt den **bestehenden** `balances`-Cache; nie blockierend.
- **Vertrag:** reine Lese-/Lade-Sicht; legt keine neue Wahrheit an.

## PermissionReadPort (feature.permission, additiv)
- `Optional<String> currentRankName(UUID)` — warmer Cache → `RoleDisplay.displayName()`; cold → empty.
- **Vertrag:** reine Lese-Sicht auf `PermissionCache`.

## RegionProvider — `Optional<RegionId> currentRegion(UUID)`
- **Vertrag:** liefert die aktuelle Region oder leer. Slice 1: `StubRegionProvider` (konfigurierbar). Echtes Region-System ersetzt **nur** die Implementierung (FR-005).

## ScoreboardCondition — `boolean matches(PlayerContext)`
- **Vertrag:** reines Prädikat. `ProfileResolver`: geordnete Rules, erste-passende, sonst Default (FR-004).

## BukkitScoreboardHandle (Render-Vertrag)
- `install(initial)` / `update(LineId, Component)` / `teardown()`.
- **Vertrag:** `update` adressiert exakt den Slot der `LineId` (Team-Prefix), flickerfrei; `teardown` meldet sauber ab. Bukkit-Mutationen nur auf Main (`runSync`).
