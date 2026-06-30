# Data Model — Scoreboard Render-Schicht Plugin-Slice (Phase 1)

Alle Typen sind **feature-lokal** und Bukkit-frei, außer wo Bukkit für Rendering/Identität nötig ist (`BukkitScoreboardHandle`, `PlayerContext`-Player-Bezug). Keine Persistenz; Wahrheit bleibt in den Economy/Permission-Caches.

## Konsumierte Contract-/Bestandstypen (unverändert)

Aus `com.mcplatform.protocol.*` (read-only):
- `BalanceChangedEvent(UUID playerUuid, long balance, String currencyCode, long version)` — getragen über `mc:economy:balance`. (Vom Economy-Feature verarbeitet; Scoreboard liest den **Wert via Port**, nicht das Event direkt.)
- `PermissionChangedEvent(UUID playerUuid, String changeType, long timestampEpochMilli)` — `mc:permission:changed`. Kein Display-Payload; `ROLE_CONFIG_CHANGED` kommt pro Holder.
- `RoleDisplay(String displayName, String color, String prefix, String suffix, String tabListColor, String tabListIcon, String displayIcon)` — Slice 1 nutzt **nur** `displayName()` (plain, FR-003a).

Aus dem Plugin (Bestand, unverändert genutzt):
- `feature.economy`: `FeatureCache<UUID,Long> balances` (privat; via `EconomyReadPort` gelesen).
- `feature.permission`: `PermissionCache` → `PlayerPermissionsView(Set<String> effective, RoleDisplay display)` (warm; via `PermissionReadPort` gelesen).

## Additive Ports an Geschwister-Features

### EconomyReadPort (in feature.economy)
```
final class EconomyReadPort {
    OptionalLong current(UUID player);                  // Cache-Read, sync (leer wenn kalt)
    CompletableFuture<OptionalLong> load(UUID player);  // cache-first → REST-fallback, füllt Cache
}
```
- Liest/füllt den **bestehenden** `balances`-Cache (kein zweiter Cache). REST-fallback = `EconomyEndpoints.GET_BALANCE` (bestehend), Logik wie `BalanceCommand`.
- **Validierung:** `load` nie blockierend; Ergebnis auf Main via Scheduler weitergegeben.

### PermissionReadPort (in feature.permission)
```
final class PermissionReadPort {
    Optional<String> currentRankName(UUID player);      // warm: PlayerPermissionsView.display().displayName()
}
```
- Liest den **bestehenden** `PermissionCache`. Cold → `Optional.empty()` (Platzhalter; PreLogin-Warmup garantiert warm).

## Feature-lokale Typen (feature.scoreboard)

### LineId (Value Object)
```
record LineId(String value) {}   // stabile Identität, unabhängig von Position; z. B. "rank", "coins"
```
- **Validierung:** `value` nicht null/leer; Gleichheit über `value`.

### LineProvider (Port)
```
interface LineProvider { Component resolve(PlayerContext ctx); }   // synchron, liest Read-Port/Statik
```
Implementierungen: `EconomyLineProvider(EconomyReadPort)`, `PermissionLineProvider(PermissionReadPort)`, `StaticLineProvider(Component)`, `StubLineProvider(Component fixed)`.

### ScoreboardLine
```
record ScoreboardLine(LineId id, LineProvider provider) {}
```

### ScoreboardProfile
```
record ScoreboardProfile(String id, List<ScoreboardLine> lines) {}  // geordnet; Position → Bukkit-score
```
- **Validierung:** `id` eindeutig im Catalog; `LineId`s innerhalb eines Profils eindeutig.

### RenderedLine
```
record RenderedLine(LineId id, Component component) {}   // Renderer-Output
```

### PlayerContext
```
final class PlayerContext { UUID player(); Optional<RegionId> region(); }
```
- Region-Snapshot vom `StubRegionProvider`; trägt nur, was Conditions/Provider brauchen.

### RegionId / RegionProvider (Port) + Stub
```
record RegionId(String value) {}
interface RegionProvider { Optional<RegionId> currentRegion(UUID player); }
final class StubRegionProvider implements RegionProvider { /* konfigurierbar leer | Test-Region */ }
```

### ScoreboardCondition / ConditionRule / ProfileResolver
```
interface ScoreboardCondition { boolean matches(PlayerContext ctx); }
record ConditionRule(ScoreboardCondition condition, String profileId) {}
final class ProfileResolver {                 // geordnete Rules; erste-passende; sonst Default
    ScoreboardProfile resolve(PlayerContext ctx);
}
final class RegionCondition implements ScoreboardCondition { /* matcht konkrete RegionId */ }
```

### ProfileCatalog
```
final class ProfileCatalog {                  // id→Profil + Default-Referenz
    ScoreboardProfile byId(String id);
    ScoreboardProfile defaultProfile();        // "Default"
}
```

### BukkitScoreboardHandle (Bukkit-nah, gekapselt)
```
final class BukkitScoreboardHandle {
    void install(List<RenderedLine> initial);  // Objective + Team-Entry-Slots (via runSync)
    void update(LineId id, Component value);    // nur Team-Prefix des Slots → flickerfrei
    void teardown();                            // Spieler-Scoreboard zurücksetzen
}
```
- **Zustandsregel:** Slot↔`LineId`-Zuordnung stabil über die Sitzung; Position bestimmt `score`, nicht die ID (AC-5).

## Lebenszyklus-Invarianten

- Pro online Spieler genau ein `BukkitScoreboardHandle` + ein `MenuLiveBus.LiveHandle`.
- Leave ⇒ `LiveHandle.close()` (Observer 0) **und** `handle.teardown()` (FR-009/AC-6).
- Live-Update: `liveBus.notifyChange(uuid)` ⇒ Re-Resolve nur der dynamischen Zeilen (Coins/Rang) aus den Ports; last-write-wins, kein Debounce (FR-007a).
