# Quickstart — Scoreboard Render-Schicht Plugin-Slice

## Voraussetzungen

- Bestehende Plugin-Features `feature.economy` und `feature.permission` lauffähig (Caches + Live-Subscriptions).
- `plugin-protocol`-Artefakt mit `com.mcplatform.protocol.economy.*` + `…permission.*` in Maven Local (unverändert konsumiert).
- Java 21, Paper-1.21-Server; `config.yml` zeigt auf Backend-URL + Redis.

## Bauen & Testen

```bash
./gradlew test            # Unit-Tests (Resolver, Provider, Renderer, Lifecycle, Ports)
./gradlew build           # Shadow-JAR nach build/libs/
```

Schwerpunkt-Tests:
- `ProfileResolverTest` (PURE) — erste-passende + Default-Fallback, Rule-Priorität (AC-1/US3).
- `RegionConditionTest` (PURE) — Stub leer→Default, Testregion→TEST_EVENT (AC-2).
- `ScoreboardLineTest` / `ProfileCatalogTest` (PURE) — LineId-Stabilität, Zeilen-Austausch, Position→score (AC-5).
- `LineProviderTest` (Fake-Ports) — Coins/Rang aus Fake-Ports; Rang **plain** (FR-003a); leerer Port→Platzhalter; Stub-Austausch ändert nur Zuordnung (AC-4).
- `ScoreboardRendererTest` (Recording-Handle) — `update(lineId,…)` trifft den richtigen Slot; last-write-wins (FR-007a).
- `ScoreboardLifecycleTest` (Fake-liveBus + Fake-scheduler) — `notifyChange` re-rendert Coins **und** Rang (AC-3); Leave → `observerCount==0` (AC-6); Coins-`load`-Future füllt Slot.
- `EconomyReadPortTest` / `PermissionReadPortTest` — cache-first vs. REST-fallback; warm→Name, cold→empty.
- `PermissionLoaderNotifyTest` (Regression) — nach `apply` `notifyChange(uuid)`; bei REST-Fehler **kein** Notify.

## Manuelle Verifikation (Server-Smoke)

1. **AC-1 (Default):** Spieler joinen → Sidebar „Default" mit Header/Footer, korrektem **Rang** (plain) und **Coins** (echter Wert). 
2. **AC-3 (Live-Coins):** Coins ändern (`/pay`, Admin, Web) → nur die Coins-Zeile aktualisiert sich, kein Flackern.
3. **Live-Rang:** Rolle/Grant via `/cp`/`/ranks` ändern → nur die Rang-Zeile aktualisiert sich live (dank Permission-Notify-Zeile).
4. **AC-2 (Selektion):** `StubRegionProvider` auf Test-Region setzen → Spieler sieht `TEST_EVENT`-Profil; zurücksetzen → wieder „Default".
5. **AC-6 (Leave):** Spieler leaven → keine weiteren Updates; `liveBus.totalObservers()` fällt entsprechend.
6. **AC-7 (kein Backend-Eingriff):** `git diff` zeigt **keine** Änderung an `plugin-protocol`, `platform/*`, `transport/*` (außer den additiven Geschwister-Read-Ports + 1 Permission-Zeile — siehe plan.md §10).

## Done-Checks
- `./gradlew build` grün; alle o. g. Tests grün.
- Kein Generik-Eingriff (verifiziert per `git diff`).
- `PROGRESS.md` + `FEATURE_INVENTORY.md` (#72 Teil) nachgezogen; **P4** (Permission-Plugin-Slice-Nachtrag) erledigt.
