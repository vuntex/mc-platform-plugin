# Tasks: Scoreboard (Render-Schicht, Slice 1)

> **Spec-Kit-Schritt:** `/speckit.tasks` — phasierte, geordnete Tasks. Ordnet sich unter plan.md.
> **Branch:** `002-scoreboard` · **Repo:** `mc-platform-plugin`
> Reihenfolge: rein/JDK-testbare Kerne zuerst (Domäne der Render-Schicht), dann Bukkit-Anbindung,
> dann Lifecycle/Live, zuletzt Doku. Jede Phase endet lauffähig/grün, bevor die nächste beginnt.

---

## Phase 0 — Vorbereitung & Verifikation

- [ ] **T0.1** Branch `002-scoreboard` im Plugin-Repo; `specs/002-scoreboard/{spec,plan,tasks}.md` ablegen.
- [ ] **T0.2 (P1-Verifikation):** Im Plugin-Permission-Modul prüfen, ob ein lokales Rang-Change-Signal
      existiert (Callback/Channel). Ergebnis in plan.md §4 / tasks notieren.
      → JA: T5.3 verdrahtet Live-Rang. NEIN: T5.3 entfällt, Rang bleibt join-/wechsel-aufgelöst (dokumentieren).
- [ ] **T0.3** Bestätigen, welche Reads die vorhandenen Caches anbieten: Economy-Coins-Lesezugriff
      und Permission-Rang-Lesezugriff (Methoden-Signaturen festhalten, gegen die die Provider bauen).

## Phase 1 — Modell-Kern (rein JDK, kein Bukkit)

- [ ] **T1.1** `LineId` (Value Object, stabile ID, equals/hashCode). Test: Gleichheit, Stabilität.
- [ ] **T1.2** `LineProvider` (Port: `Component resolve(PlayerContext)`). `PlayerContext` zunächst
      minimal (Spieler-Ref + Region-Snapshot-Slot). *(Component = Adventure; rein als Typ, kein Render.)*
- [ ] **T1.3** `ScoreboardLine` (LineId + LineProvider) und `ScoreboardProfile` (id + geordnete
      `List<ScoreboardLine>`). Test: Profil hält Reihenfolge; LineId bleibt bei Umsortierung stabil (**AC-5**).
- [ ] **T1.4** `RenderedLine` (LineId + aufgelöste Component).
- [ ] **T1.5** `StaticLineProvider` + `StubLineProvider` (fester Text). Test: liefern konstante Component.

## Phase 2 — Selektion (rein JDK)

- [ ] **T2.1** `ScoreboardCondition` (Prädikat) + `ConditionRule` (Condition→ProfileId).
- [ ] **T2.2** `ProfileResolver` (geordnete Rules, erste-passende, Default-Fallback).
      Test: keine Rule matcht → Default (**AC-1**); erste matchende gewinnt; Priorität per Reihenfolge.
- [ ] **T2.3** `RegionProvider` (Port) + `StubRegionProvider` (konfigurierbar leer / Testregion).
- [ ] **T2.4** `RegionCondition` (matcht, wenn Stub eine konfigurierte Region liefert).
      Test: leer → kein Match (→ Default); Testregion → Match (→ TEST_EVENT) (**AC-2**).

## Phase 3 — Profile (hardcoded, sauber)

- [ ] **T3.1** Echte Provider (Read-Anbindung an vorhandene Caches):
      `EconomyLineProvider` (Coins) + `PermissionLineProvider` (Rang). Test mit Fake-Caches:
      liefern erwartete Component aus dem Cache-Wert.
- [ ] **T3.2** `Profiles.LOBBY` (Default) = Header/Sep/Rang/Coins/Stats-Stub/Sep/Footer (P3 bestätigt)
      und `Profiles.TEST_EVENT` (abweichend, für AC-2). `ProfileCatalog` (id→Profil, Default-Ref).
      Test: Catalog-Lookup, Default vorhanden, Stub-Zeile strukturell vollwertig (**AC-4**).
- [ ] **T3.3** Provider-Austausch-Beweis: Stub-Stats gegen Fake-Echt-Provider tauschen → nur die
      Zeilen-Zuordnung ändert sich, Renderer-Pfad unberührt (**AC-4**, Test).

## Phase 4 — Render & Bukkit-Anbindung

- [ ] **T4.1** `ScoreboardRenderer`: Profil → `List<RenderedLine>` (jede Zeile via Provider auflösen).
      Test gegen Fake-Provider: korrekte Zeilen/Reihenfolge.
- [ ] **T4.2** `BukkitScoreboardHandle`: Objective + Team-Entry-Slots (**Flicker-Strategie P2**);
      Methoden `initialRender(List<RenderedLine>)` und `update(LineId, Component)` (nur Slot-Suffix).
      Test über Fake/Mock-Scoreboard: `update` trifft den richtigen Slot, kein Voll-Neuaufbau (**AC-3**).
- [ ] **T4.3** Adventure-Components durchgängig (keine §-Codes). Smoke: Render erzeugt gültige Slots.

## Phase 5 — Lifecycle & Live

- [ ] **T5.1** `ScoreboardJoinListener`: Join → `PlayerContext` (Region via Stub) → `ProfileResolver`
      → Handle anlegen → initial render. Reihenfolge nach vorhandenem Economy/Permission-Warmup.
- [ ] **T5.2** `ScoreboardLeaveListener`: Leave → Handle abbauen, Updater-Refs/Subscriptions abmelden
      (**AC-6**). Test (Fake-Lifecycle): nach Leave keine Referenz/kein Update mehr.
- [ ] **T5.3** `BalanceLineUpdater`: EventBus-Subscription auf `BalanceChangedEvent` → betroffene
      Coins-Zeile via Handle `update(LineId.COINS, …)`; Main-Thread-Übergabe via Scheduler.
      Test (Fake-EventBus): Event → genau Coins-Zeile neu, kein Voll-Render (**AC-3**).
- [ ] **T5.4 (nur wenn T0.2 = JA):** `PermissionLineUpdater` analog für Live-Rang. Sonst übersprungen
      + in plan.md/PROGRESS.md als degradiert dokumentiert.
- [ ] **T5.5** `ScoreboardFeature` (Feature-Interface): registriert Listener/Updater, hängt sich an
      EventBus; **EINE** Zeile in der `FeatureRegistry` (Anstech-Punkt). **Verifizieren: keine
      generische Klasse (platform/transport) geändert (AC-7).**

## Phase 6 — Verifikation & Doku

- [ ] **T6.1** `./gradlew build` (Plugin) grün (alle Phasen-Tests).
- [ ] **T6.2** Manueller „es lebt"-Smoke auf echtem Paper-Node: Join → Sidebar mit echtem Rang+Coins;
      Web/Admin-`SET` → Coins-Zeile zählt (springt) live mit, kein Flackern; Stub-Stats-Zeile sichtbar.
- [ ] **T6.3 (P4):** Plugin-seitigen Permission-Stand in PROGRESS.md nachtragen (lokal vorhanden,
      Doku-Drift schließen) — *vor* dem Abschluss, da Grundlage dieses Slices.
- [ ] **T6.4** PROGRESS.md (Plugin): Scoreboard-Slice-Abschnitt mit **bewussten Grenzen**
      (Plugin-only, keine protocol-Änderung, kein Backend; kein Toggle, keine Animation, keine
      TabList/Chat; Region-Stub; P1-Ergebnis). FEATURE_INVENTORY.md #72 als teil-migriert markieren.
- [ ] **T6.5** Review-Check gegen DoD (spec.md §6 / plan.md §8): keine `plugin-protocol`-Änderung,
      kein neuer Channel/REST, kein Backend-Eingriff — explizit bestätigt.

---

## Abhängigkeits-/Reihenfolge-Logik
Phase 1–3 sind rein JDK und ohne Bukkit voll testbar (das Gros der Logik). Phase 4 bringt die
Bukkit-Mechanik (Flicker-Slots) hinter ein kapselndes Handle, gegen Fakes testbar. Phase 5
verdrahtet Lifecycle/Live. Phase 6 beweist „es lebt" und schließt Doku/DoD inkl. P4-Nachtrag.

## Folge-Slices (explizit NICHT hier)
TabList · Chat-Format · Toggle-Persistenz (braucht Settings #9 backend) ·
**Currency-Zähl-Animation + Sound** (im `feature.economy`-Modul, Scoreboard nur als Senke) ·
echtes Region-System (ersetzt `StubRegionProvider`).
