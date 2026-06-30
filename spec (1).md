# Feature Spec: Scoreboard (Render-Schicht, Slice 1)

> **Spec-Kit-Schritt:** `/speckit.specify` — WAS gebaut wird (Verhalten, Scope, „migrieren wir das überhaupt?").
> **Branch:** `002-scoreboard`
> **Repo:** `mc-platform-plugin` (Plugin-only — siehe Bewusste Grenzen)
> **Inventar-Bezug:** #72 (Scoreboard/Tab) — nur der Scoreboard-Teil. TabList, Chat-Format,
> Toggle-Persistenz, Animation: bewusst NICHT in diesem Slice (siehe Folge-Slices).

---

## 1. Migrieren wir das — und in welchem Umfang?

**Ja, aber radikal neu geschnitten.** Das Legacy-Feature #72 ist ein RAM-gerendertes
Kontext-Scoreboard (Lobby/Mine/Fight/LMS) plus TabList, stark gekoppelt an ein Dutzend Subsysteme,
historisch über NMS/§-Codes gebaut.

**Verhalten 1:1 übernommen:** kontextabhängige Sidebar, die je nach Spieler-Situation ein anderes
Layout zeigt; live aktualisierte Werte (Coins, Rang).

**Technik vollständig verworfen:** kein NMS, keine §-Color-Codes (Adventure-Components),
kein RAM-als-Wahrheit (Werte kommen aus den schon migrierten Backend-Features Economy/Permission
über deren Plugin-Caches), kein Hardcode-Sammelbecken im Renderer.

**Bewusst aus Slice 1 ausgeschlossen** (jeweils eigener Folge-Slice):
- **TabList** — teilt Team-Farben/Prefix-Logik mit Scoreboard, aber eigener Render-Pfad. Folge-Slice.
- **Chat-Format** (Rang-Farbe/-Prefix im Chat) — hängt nur an Permission, sauber isolierbar. Folge-Slice.
- **Toggle-Persistenz** (Scoreboard an/aus pro Spieler) — ist ein persistentes Spieler-Datum und
  gehört backend-seitig (Settings/Toggles #9, noch nicht migriert). Slice 1: **Scoreboard immer an.**
- **Zähl-Animation + Sound bei Balance-Änderung** — ist KEIN Scoreboard-Feature, sondern ein
  Currency-Display-Effekt im `feature.economy`-Modul. Eigener Folge-Slice. Das Scoreboard ist dort
  nur eine Senke, die einen animierten statt fixen Wert anzeigen kann.

---

## 2. Scope (WAS Slice 1 liefert)

Ein **rein Plugin-seitiges** `feature.scoreboard`-Modul, das einem Online-Spieler eine
kontextabhängige Sidebar zeigt, deren Zeilen live aktuell bleiben.

### In Scope
1. **Scoreboard-Profile** als im Code deklarierte Konstanten — jedes Profil eine geordnete Liste
   von Zeilen-Beiträgen.
2. **Stabile Zeilen-IDs** — jede Zeile hat eine ID (nicht nur einen Index), damit Live-Updates die
   richtige Zeile adressieren und Reihenfolge-Änderungen nicht flackern.
3. **Content-Provider-Abstraktion** — Zeileninhalte stammen aus Providern, nicht aus String-Konkat
   im Renderer. Economy + Permission echt angebunden; Stats/Liga/Season als Stub-Provider
   (feste Platzhalter-Zeile), bis die echten Features migriert sind.
4. **Bedingung→Profil-Resolver** — geordnete Liste von `(ScoreboardCondition → Profil)`-Regeln,
   erste passende gewinnt, Default-Profil als Fallback. Region ist die erste Bedingung, gegen einen
   `RegionProvider`-**Stub** (eigenes Region-System kommt später, ersetzt nur den Provider).
5. **Live-Update-Pfad** — bei `BalanceChangedEvent` (vorhandener EventBus) wird die betroffene
   Zeile des Spielers neu aufgelöst und geschrieben; bei Permission-Änderung analog (sofern das
   Plugin-Permission-Modul ein Change-Event liefert — siehe Offene Frage P1).
6. **Render in Adventure-Components**, geschrieben ins Bukkit-Scoreboard pro Spieler.

### Explizit NICHT in Scope (Slice 1)
- TabList, Chat-Format, Toggle, Animation/Sound (s.o.).
- Backend-Config der Profile / Runtime-Editing / Webinterface — Profile sind **hardcoded im
  Plugin-Code**. (Nicht „hardcoded" im Sinne von String-Bau — die *Definition* lebt im Code, die
  *Werte* kommen aus Providern.)
- Echtes Region-System — nur der `RegionProvider`-Stub.
- Mine/Fight/LMS-Kontexte als echte Bedingungen — dürfen Stub-Profile/-Bedingungen sein.

---

## 3. Verhalten (akzeptanz-orientiert)

- **AC-1 (Default-Profil):** Ein Spieler ohne passende Bedingung sieht das Default-Profil (z. B.
  „lobby") mit Rang (Permission) und Coins (Economy) als echte Werte.
- **AC-2 (Bedingungs-Selektion):** Liefert der `RegionProvider`-Stub eine konfigurierte Test-Region,
  greift die zugehörige `RegionCondition` und der Spieler sieht das passende Profil statt des Default.
- **AC-3 (Live-Coins):** Ändert sich die Balance (Web/Admin/In-Game), aktualisiert sich die
  Coins-Zeile des betroffenen Spielers über den EventBus, ohne Neuaufbau des ganzen Scoreboards
  und ohne Flackern (Adressierung über Zeilen-ID).
- **AC-4 (Stub-Zeile):** Eine Stats-Zeile zeigt den festen Platzhalter (z. B. „Kills: 0") und ist
  strukturell eine vollwertige Zeile — Austausch gegen einen echten Provider später ändert NUR die
  eine Provider-Zuordnung, nicht den Renderer.
- **AC-5 (Zeile austauschen):** Eine Zeile in einem Profil kann durch eine andere ersetzt werden
  (im Code), ohne dass andere Zeilen ihre ID/Position verlieren.
- **AC-6 (Join/Leave):** Beim Join bekommt der Spieler sein Scoreboard (nach Permission/Economy-
  Warmup); beim Leave werden Ressourcen/Subscriptions sauber abgemeldet (LIVE-Menü-Disziplin
  analog Constitution).
- **AC-7 (kein Backend-Eingriff):** Kein neues Backend-Modul, keine `plugin-protocol`-Änderung,
  kein neuer Channel, kein REST-Endpoint entsteht.

---

## 4. Bewusste Grenzen / Architektur-Hinweise

- **Plugin-only-Slice.** Anders als Economy/Punishments/Reports hat dieser Slice **keinen
  Backend→publish→Plugin-Halbschritt.** Er konsumiert nur vorhandene Plugin-Caches (Economy,
  Permission). Das ist constitution-konform („Backend = Wahrheit", nicht „jedes Feature braucht
  Backend") und wird in PROGRESS.md explizit als bewusste Grenze vermerkt, damit niemand später
  einen fehlenden Backend-Teil vermutet.
- **Keine generische Klasse wird geändert.** Das Feature steckt sich über die `FeatureRegistry` an
  (ein `Feature`-Eintrag). Falls sich beim Plan herausstellt, dass Transport/Platform doch angefasst
  werden müssten → STOPP + Muster-Leck melden (Constitution).
- **Zwei orthogonale Achsen, sauber getrennt:** *Selektion* (welches Profil — Bedingungs-Resolver)
  vs. *Komposition* (welche Zeilen — Provider-Liste je Profil). Nicht vermischen.
- **Region = nur eine Bedingung unter vielen.** Die Bedingungs-Abstraktion ist generisch; Region ist
  die erste Implementierung. „Event läuft", „Permission X", „Welt Y" sind künftige Prädikate ohne
  Resolver-Änderung.

---

## 5. Offene Fragen (vor /plan klären)

- **P1 — Permission-Change-Event im Plugin:** Liefert das Plugin-Permission-Modul ein lokales
  Change-Signal (Channel/Callback), wenn sich ein Rang live ändert? Falls ja → Rang-Zeile live wie
  Coins. Falls nein → Rang-Zeile wird beim Join/Region-Wechsel aufgelöst und bis dahin nicht live
  aktualisiert (akzeptabel für Slice 1, aber explizit zu entscheiden).
- **P2 — Flicker-Strategie:** Bukkit-Scoreboard-Zeilen über Team-Entries (stabile Slots, nur Suffix
  ändern) ODER Objective-Neuschreiben? Team-Entry-Ansatz ist flickerfrei und passt zu „stabile
  Zeilen-ID", ist aber mehr Mechanik. Entscheidung gehört in /plan, hier nur markiert.
- **P3 — Profil-Inhalte konkret:** Welche Zeilen hat „lobby" genau, welche das Test-Event-Profil?
  Vorschlag für Default „lobby": Header, Rang, Coins, Stats-Stub, Footer. Bestätigen/anpassen in
  /plan oder hier.
- **P4 — Doku-Drift Permission-Plugin:** Der Plugin-seitige Permission-Slice ist in PROGRESS.md /
  FEATURE_INVENTORY.md nicht nachgezogen, wird aber von diesem Slice vorausgesetzt. Vor /implement
  in PROGRESS.md nachtragen, sonst referenziert die Spec eine offiziell nicht existente Grundlage.

---

## 6. Definition of Done (für den Gesamt-Slice, finalisiert in /tasks)

- Profil/Bedingung/Provider-Abstraktion implementiert; Profile als Code-Konstanten mit stabilen
  Zeilen-IDs.
- Economy + Permission als echte Provider angebunden; mind. ein Stub-Provider als Muster.
- `RegionCondition` gegen `RegionProvider`-Stub; Default-Profil-Fallback.
- Live-Coins über EventBus, flickerfrei, zeilen-ID-adressiert.
- Join/Leave sauber (Subscription-Abmeldung beim Leave).
- Tests grün (Schichtung in /plan/tasks festgelegt): Profil-Auflösung, Bedingungs-Resolver
  (erste-passende + Default), Provider-Stub-Austauschbarkeit, Live-Update-Pfad (Fake-Event),
  Join/Leave-Lifecycle.
- `./gradlew build` (Plugin) grün.
- **Keine** `plugin-protocol`-Änderung, **kein** Backend-Eingriff — explizit verifiziert.
- PROGRESS.md (Plugin-Stand) + FEATURE_INVENTORY.md (#72 Teil-Abhaken) nachgezogen; P4-Nachtrag erledigt.
