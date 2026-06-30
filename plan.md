# Implementation Plan: Scoreboard (Render-Schicht, Slice 1)

> **Spec-Kit-Schritt:** `/speckit.plan` — WIE gebaut wird (Schichten, Datenmodell, Wiederverwendung,
> protocol-Ergänzungen). Ordnet sich unter spec.md.
> **Branch:** `002-scoreboard` · **Repo:** `mc-platform-plugin`

---

## 0. Architektur-Einordnung (was dieser Plan NICHT anfasst)

- **Kein Backend-Modul, keine `plugin-protocol`-Änderung, kein Channel, kein REST.** Reiner
  Consumer vorhandener Plugin-Caches (`feature.economy`, `feature.permission`).
- **Kein Eingriff in `platform`/`transport`.** Anschluss ausschließlich über einen neuen
  `Feature`-Eintrag in der `FeatureRegistry`. Sollte sich beim Bauen zeigen, dass Transport/Platform
  doch angefasst werden müssten → STOPP, als Muster-Leck melden (Constitution).
- **Reuse vor Neubau:** EventBus (für `BalanceChangedEvent`), Scheduler-Abstraktion (Main-Thread-
  Übergabe), das `Feature`-Interface, der Permission-Resolver-Port des Plugins. Nichts davon wird
  neu gebaut.

---

## 1. Modul-Struktur (`feature.scoreboard`)

```
feature/scoreboard/
├── ScoreboardFeature           ← Feature-Interface-Impl; Anstech-Punkt (Registry)
│
├── model/
│   ├── ScoreboardProfile       ← benanntes Profil: id + geordnete List<ScoreboardLine>
│   ├── ScoreboardLine          ← stabile lineId + LineProvider (Wertquelle) + statische Teile
│   ├── LineId                  ← Value Object (stabile ID, kein Index)
│   └── RenderedLine            ← lineId + aufgelöste Adventure-Component (Renderer-Output)
│
├── profile/
│   ├── Profiles                ← die hardcoded Profil-KONSTANTEN (lobby = Default, test_event)
│   └── ProfileCatalog          ← Lookup id→Profil; hält Default-Profil-Referenz
│
├── condition/
│   ├── ScoreboardCondition     ← Prädikat: matches(PlayerContext) : boolean
│   ├── ConditionRule           ← (ScoreboardCondition → ProfileId)
│   ├── ProfileResolver         ← geordnete Rules; erste-passende; Default-Fallback
│   ├── RegionCondition         ← erste Bedingung; fragt RegionProvider
│   └── RegionProvider          ← PORT (Stub-Impl in Slice 1)
│       └── StubRegionProvider  ← liefert konfigurierten Testwert / "keine Region"
│
├── provider/
│   ├── LineProvider            ← PORT: resolve(PlayerContext) : Component
│   ├── EconomyLineProvider     ← echt: liest feature.economy-Cache (Coins)
│   ├── PermissionLineProvider  ← echt: liest Permission-Resolver (Rang)
│   ├── StaticLineProvider      ← fester Text (Header/Footer/Trenner)
│   └── StubLineProvider        ← fester Platzhalter (Stats/Liga/Season) — Muster für Austausch
│
├── render/
│   ├── ScoreboardRenderer      ← löst Profil-Zeilen gegen Provider auf → schreibt Bukkit-Scoreboard
│   ├── BukkitScoreboardHandle  ← pro Spieler: Objective + Team-Entry-Slots (Flicker-Strategie P2)
│   └── PlayerContext           ← Spieler + Region-Snapshot + (was Conditions/Provider brauchen)
│
└── lifecycle/
    ├── ScoreboardJoinListener  ← Join → Context bauen → Profil resolven → initial render
    ├── ScoreboardLeaveListener ← Leave → Handle abbauen, Subscriptions/Refs abmelden
    └── BalanceLineUpdater      ← hört EventBus(BalanceChangedEvent) → betroffene Zeile neu rendern
```

**Designprinzip durchgezogen:** *Selektion* (condition/) und *Komposition* (model/+provider/) sind
getrennte Pakete. Der Renderer kennt nur „Profil → Zeilen → Components", nicht die Bedingungslogik.

---

## 2. Kernabstraktionen (Detail)

### ScoreboardLine + LineId
Eine Zeile = **stabile `LineId`** + ein `LineProvider`. Die `LineId` ist die Identität über die
Zeit (für Live-Update-Adressierung und Flicker-Vermeidung), unabhängig von der Position. Reihenfolge
ergibt sich aus der Listen-Position im Profil; das Bukkit-`score` (Zeilen-Höhe) wird aus der Position
abgeleitet, NICHT aus der ID. → Position ändern bricht keine ID; ID bleibt stabil bei Umsortierung.

### LineProvider (Port)
`Component resolve(PlayerContext ctx)`. Eine Zeile fragt ihren Provider. Austausch Stub→echt = die
eine Zeilen-Konstante zeigt auf einen anderen Provider. **Renderer bleibt unberührt** (AC-4).

### ScoreboardProfile + Profiles (hardcoded, sauber)
`Profiles.LOBBY` etc. sind Code-Konstanten. „Sauber hardcoded" = deklarative Zeilenliste, KEIN
String-Bau:
```
LOBBY = profile("lobby",
    line("header", static("» MC Platform «")),
    line("sep1",   static(" ")),
    line("rank",   permissionRankProvider),
    line("coins",  economyCoinsProvider),
    line("stats",  stub("Kills: 0")),          // bis #8 (Stats) migriert ist
    line("sep2",   static(" ")),
    line("footer", static("play.example.net")));
```
`TEST_EVENT` = abweichendes Profil zum Beweis der Bedingungs-Selektion (AC-2).

### ProfileResolver (Selektion)
Geordnete `List<ConditionRule>`; `resolve(ctx)` nimmt das Profil der ersten matchenden Rule, sonst
`Default` (lobby). Rule-Reihenfolge = Priorität. Region steht als erste Rule; weitere Prädikate
(Event/Permission/Welt) später ohne Resolver-Änderung anhängbar.

### RegionProvider (Port, Stub in Slice 1)
`Optional<RegionId> currentRegion(Player p)`. `StubRegionProvider` liefert konfigurierbar entweder
leer (→ Default-Profil) oder eine Test-Region (→ TEST_EVENT-Profil), damit AC-2 testbar ist. Dein
echtes Region-System ersetzt später **nur** diese Klasse.

---

## 3. Flicker-Strategie (Entscheidung zu P2)

**Gewählt: Team-Entry-Slots, kein Objective-Neuschreiben.**

Begründung: Bukkit-Sidebar flackert, wenn man Einträge entfernt/neu hinzufügt. Stattdessen: feste,
unsichtbare Entry-Strings als stabile Slots (ein Slot pro Zeilen-Position), der sichtbare Text liegt
im **Team-Prefix/-Suffix** dieses Slots. Ein Zeilen-Update ändert nur Prefix/Suffix des betroffenen
Slots → kein Flackern, und es adressiert exakt die `LineId`-zu-Slot-Zuordnung (passt 1:1 zu „stabile
Zeilen-ID", AC-3). `BukkitScoreboardHandle` kapselt diese Mechanik vollständig; der Renderer denkt
nur in `(LineId, Component)`.

Konsequenz: `BalanceLineUpdater` ruft `handle.update(LineId.COINS, newComponent)` statt das ganze
Board neu zu bauen.

---

## 4. Live-Update-Pfad

- **Coins (echt live, AC-3):** `BalanceLineUpdater` subscribt beim EventBus auf `BalanceChangedEvent`.
  Bei Event für Spieler P: Economy-Cache ist bereits aktualisiert (bestehender Pfad) → Updater löst
  NUR die Coins-Zeile via `EconomyLineProvider` neu auf und schreibt sie über das Handle.
  Main-Thread-Übergabe über die Scheduler-Abstraktion (EventBus-Dispatch läuft async).
- **Rang (P1, defensiv gebaut):** Wenn das Plugin-Permission-Modul ein lokales Change-Signal liefert
  (Callback/Channel), hängt ein analoger `PermissionLineUpdater` daran. **Falls nicht vorhanden,
  degradiert es sauber:** Rang-Zeile wird beim Join und bei Region/Profil-Wechsel aufgelöst, nicht
  live. → P1-„glaube schon" blockiert nichts; nur die Live-Aktualisierung der Rang-Zeile hängt daran.
  **Verifikationspunkt in /tasks:** prüfen, ob das Signal existiert; Updater nur dann verdrahten.

---

## 5. Lifecycle

- **Join:** `PlayerContext` bauen (Region-Snapshot via Stub) → `ProfileResolver.resolve` → Handle
  anlegen (Objective + Slots) → alle Zeilen initial rendern. Reihenfolge nach Permission/Economy-
  Warmup (der existiert bereits im Plugin beim Join).
- **Leave:** Handle abbauen, Updater-Subscriptions/Referenzen für den Spieler entfernen (LIVE-Menü-
  Disziplin der Constitution: beim Close abmelden).
- **Profil-Wechsel zur Laufzeit (Region ändert sich):** in Slice 1 minimal — bei Bedarf re-resolve
  beim nächsten Trigger. Voller reaktiver Region-Wechsel kommt mit dem echten Region-System;
  Slice-1-Stub kann statisch bleiben (dokumentierte Grenze).

---

## 6. Threading / Constitution-Konformität

- EventBus-Dispatch ist async; jede Bukkit-Scoreboard-Mutation wird über die Scheduler-Abstraktion
  auf den Main-Thread übergeben. **Main-Thread nie blockieren.**
- Keine REST-/Redis-Reads im Feature-Code direkt — Coins/Rang kommen aus den vorhandenen
  Feature-Caches, nicht aus eigenem Backend-Zugriff.

---

## 7. Test-Strategie (Schichten)

- **model/profile (rein, JDK):** Profil-Definition, LineId-Stabilität, Zeilen-Austausch (AC-5) —
  Position ändert Score, nicht ID.
- **condition (rein, JDK):** `ProfileResolver` — erste-passende, Default-Fallback (AC-1/AC-2),
  Rule-Priorität. `RegionCondition` gegen Stub (leer → Default; Testregion → TEST_EVENT).
- **provider (rein/Fakes):** `StubLineProvider`-Austausch gegen Fake-Echt-Provider ändert nur die
  Zeilen-Zuordnung, Renderer-Output strukturell gleich (AC-4).
- **render (Fake-Handle):** Renderer löst Profil→`List<RenderedLine>` korrekt auf; `BukkitScoreboard-
  Handle` über ein Fake/Mock getestet (kein echter Server nötig) — `update(lineId, …)` trifft den
  richtigen Slot.
- **lifecycle (Fake-EventBus):** `BalanceLineUpdater` rendert bei Fake-`BalanceChangedEvent` genau
  die Coins-Zeile neu (AC-3); Join legt Handle an, Leave meldet ab (AC-6).
- **Negativ/Grenzen:** `SET`-artiger Sprung verändert nur den Zielwert (keine Animation hier — die
  ist Folge-Slice); kein Backend-Artefakt entsteht (AC-7, als Doku-/Review-Check).

---

## 8. Definition of Done (verfeinert in /tasks)

Wie spec.md §6, plus: Flicker-Strategie = Team-Slots umgesetzt; P1-Verifikation dokumentiert
(Live-Rang verdrahtet ODER bewusst degradiert); `./gradlew build` (Plugin) grün; PROGRESS.md (Plugin)
+ FEATURE_INVENTORY.md #72-Teilabhaken; P4 (Permission-Plugin-Nachtrag) erledigt.

---

## 9. Risiken / bewusst offen

- **R1 (P1):** Permission-Live-Signal evtl. nicht vorhanden → Rang nicht live. Mitigation: sauberer
  Degrade (s. §4). Kein Slice-Blocker.
- **R2:** Team-Entry-Flicker-Mechanik hat Bukkit-Eigenheiten (max. Entry-Länge, Farb-Reset zwischen
  Prefix/Suffix bei Legacy — hier irrelevant, da Adventure). In `BukkitScoreboardHandle` kapseln und
  per Fake testen; echter Server-Smoke-Test beim „es lebt"-Moment.
- **R3:** Region-Stub statisch → kein reaktiver Profilwechsel in Slice 1. Bewusste, dokumentierte
  Grenze; kommt mit echtem Region-System.
