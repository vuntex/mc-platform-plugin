# Feature Specification: Scoreboard (Render-Schicht, Slice 1)

**Feature Branch**: `003-scoreboard-render`

**Created**: 2026-06-30

**Status**: Draft

**Input**: User description: Scoreboard Render-Schicht (Slice 1) — Plugin-only `feature.scoreboard`-Modul, das einem Online-Spieler eine kontextabhängige Sidebar zeigt, deren Zeilen live aktuell bleiben. Werte stammen aus den bereits migrierten Plugin-Caches (Economy/Permission). Inventar-Bezug #72 (nur Scoreboard-Teil). TabList, Chat-Format, Toggle-Persistenz, Animation: bewusst NICHT in diesem Slice.

## Migrationsentscheidung *(Kontext)*

**Migrieren: Ja, aber radikal neu geschnitten.** Das Legacy-Feature #72 ist ein RAM-gerendertes Kontext-Scoreboard (Lobby/Mine/Fight/LMS) plus TabList, stark gekoppelt an ein Dutzend Subsysteme, historisch über NMS/§-Codes gebaut.

- **Verhalten 1:1 übernommen**: kontextabhängige Sidebar, die je nach Spieler-Situation ein anderes Layout zeigt; live aktualisierte Werte (Coins, Rang).
- **Technik vollständig verworfen**: kein NMS, keine §-Color-Codes (stattdessen Adventure-Components), kein RAM-als-Wahrheit (Werte kommen aus den schon migrierten Backend-Features Economy/Permission über deren Plugin-Caches), kein Hardcode-Sammelbecken im Renderer.

## Clarifications

### Session 2026-06-30

- Q: Liefert das Plugin-Permission-Modul ein lokales Change-Signal, sodass die Rang-Zeile live aktualisiert werden kann? → A: Ja — die Rang-Zeile MUSS live aktualisiert werden (gleiches Muster wie Coins). Falls das Permission-Modul noch kein lokales Change-Signal bereitstellt, MUSS eines im `feature.permission`-Modul ergänzt werden (innerhalb des Feature-Moduls, ohne generische Klasse zu ändern).
- Q: Welche Zeilen hat das Default-Profil und gibt es ein „lobby"-Profil? → A: Es gibt kein „lobby"-Profil. Slice 1 startet mit genau einem Profil namens „Default" mit dem Blueprint: Header, Rang (Permission), Coins (Economy), Stats (Stub), Footer.
- Q: Was zeigt die Rang-Zeile aus dem Permission-Cache an? → A: Nur den reinen Rang-Anzeigenamen ohne Farbe/Prefix (z. B. „Admin" in Standard-Darstellung).
- Q: Wie verhält sich der Live-Update-Pfad bei mehreren schnell aufeinanderfolgenden Wertänderungen? → A: Bei jedem Event wird der Wert frisch aus dem Cache aufgelöst (last-write-wins, kein Debounce/Scheduler); es wird stets der aktuellste bekannte Wert angezeigt.
- Info (Event-Quellen, vom Nutzer bestätigt): Das **Backend publiziert diese Events bereits**. Economy: `BalanceChangedEvent`. Permission/Ranks: `GRANT_ADDED` / `GRANT_REVOKED` (Rolle oder Permission grant/revoke; Rolle löschen → `GRANT_REVOKED` an alle Holder kaskadiert), `GRANT_EXPIRED` (Auto-Expiry via `GrantExpiryService`), `ROLE_CONFIG_CHANGED` (Permission zu Rolle add/remove, Inheritance add/remove [Feature 006], Rolle (de)aktivieren wenn sich `active` ändert — kaskadiert an alle Holder). Offen (in `/plan` zu verifizieren): ob das **Plugin diese Events bereits consumed** (Subscription/Routing auf der Plugin-Seite), oder ob die Konsumierung plugin-seitig noch ergänzt werden muss.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Kontextabhängiges Scoreboard mit echten Werten (Priority: P1)

Ein Online-Spieler sieht beim Spielen eine Sidebar, die seinem aktuellen Kontext entspricht. In Slice 1 existiert genau ein Profil namens „Default" mit dem Blueprint Header, Rang, Coins, Stats, Footer. Die Zeilen zeigen echte Werte aus den vorhandenen Plugin-Caches: seinen Rang (Permission) und seine Coins (Economy). Wo ein echtes Feature noch nicht migriert ist (Stats/Liga/Season), steht eine strukturell vollwertige Platzhalter-Zeile.

**Why this priority**: Dies ist der Kern des Slice — ohne ein sichtbares, korrekt befülltes Scoreboard liefert das Feature keinen Wert. Es ist allein lauffähig und demonstrierbar (Spieler joint, sieht sein Board) und bildet die Grundlage, auf der Live-Updates (US2) und Kontext-Selektion (US3) aufsetzen.

**Independent Test**: Ein Spieler joint den Server; ohne erfüllte Sonderbedingung erscheint das Default-Profil mit korrektem Rang und korrekten Coins (gegen die Plugin-Caches geprüft) sowie mindestens einer Platzhalter-Zeile. Vollständig testbar ohne dass Live-Updates oder Regionen existieren.

**Acceptance Scenarios**:

1. **Given** ein Spieler ohne erfüllte Sonderbedingung joint nach abgeschlossenem Permission-/Economy-Warmup, **When** sein Scoreboard aufgebaut wird, **Then** sieht er das „Default"-Profil (Zeilen: Header, Rang, Coins, Stats-Stub, Footer) mit Rang (aus Permission-Cache) und Coins (aus Economy-Cache) als echte Werte.
2. **Given** ein Profil enthält eine Stub-Zeile für ein noch nicht migriertes Feature (z. B. „Kills: 0"), **When** das Scoreboard gerendert wird, **Then** erscheint die Stub-Zeile als strukturell vollwertige Zeile mit fester Platzhalter-Angabe.
3. **Given** ein noch nicht migriertes Feature wird später als echter Provider angebunden, **When** nur die Provider-Zuordnung der betroffenen Zeile getauscht wird, **Then** ändert sich ausschließlich der Inhalt dieser einen Zeile — Renderer, Zeilen-IDs und übrige Zeilen bleiben unverändert.

---

### User Story 2 - Live-aktualisierte Werte ohne Flackern (Priority: P2)

Ändert sich ein angezeigter Wert eines Spielers (z. B. seine Coins durch Web, Admin oder In-Game-Aktion), aktualisiert sich genau die betroffene Zeile seines Scoreboards — ohne das ganze Board neu aufzubauen und ohne sichtbares Flackern.

**Why this priority**: „Live" ist das namensgebende Versprechen gegenüber dem alten RAM-Board und der Hauptgrund, warum ein eigenes Render-Modul nötig ist. Es baut zwingend auf US1 auf (es braucht ein bestehendes Board mit adressierbaren Zeilen), liefert aber eigenständigen, demonstrierbaren Mehrwert.

**Independent Test**: Bei bestehendem Scoreboard wird eine Balance-Änderung des Spielers ausgelöst (z. B. über das vorhandene Balance-Änderungs-Event); die Coins-Zeile aktualisiert sich sichtbar, alle anderen Zeilen bleiben unverändert und flackerfrei.

**Acceptance Scenarios**:

1. **Given** ein Spieler hat ein aktives Scoreboard, **When** seine Balance sich ändert (Quelle Web, Admin oder In-Game), **Then** wird ausschließlich seine Coins-Zeile über das vorhandene Event neu aufgelöst und geschrieben, ohne Neuaufbau des gesamten Scoreboards.
2. **Given** eine einzelne Zeile wird live aktualisiert, **When** der neue Wert geschrieben wird, **Then** behält die Zeile ihre stabile Position/ID und es entsteht kein sichtbares Flackern anderer Zeilen.
3. **Given** ein Spieler verlässt den Server, **When** sein Leave verarbeitet wird, **Then** werden alle für ihn angelegten Subscriptions/Ressourcen sauber abgemeldet (keine Updates an abgemeldete Spieler).

---

### User Story 3 - Bedingungsgesteuerte Profil-Auswahl (Priority: P3)

Befindet sich ein Spieler in einem Kontext, der eine konfigurierte Bedingung erfüllt (in Slice 1: eine Test-Region über einen Region-Stub), zeigt sein Scoreboard das passende Profil statt des Default-Profils. Greift keine Bedingung, gilt das Default-Profil als Fallback.

**Why this priority**: Die Kontextabhängigkeit ist konzeptuell wichtig, aber für einen ersten lauffähigen Slice nachrangig — ein einziges Default-Profil liefert bereits Wert (US1). Die Selektions-Achse wird hier als erweiterbares Muster etabliert (erste-passende-Regel + Default), mit Region als erster, gegen einen Stub laufender Bedingung.

**Independent Test**: Der Region-Stub wird so gesetzt, dass er eine konfigurierte Test-Region meldet; der betroffene Spieler sieht daraufhin das zugehörige Profil statt des Default. Wird der Stub zurückgesetzt, erscheint wieder das Default-Profil. Testbar ohne echtes Region-System.

**Acceptance Scenarios**:

1. **Given** der Region-Stub meldet eine konfigurierte Test-Region, **When** das Profil des Spielers aufgelöst wird, **Then** greift die zugehörige Regionsbedingung und der Spieler sieht das passende Profil statt des Default.
2. **Given** keine der definierten Bedingungen ist erfüllt, **When** das Profil aufgelöst wird, **Then** erhält der Spieler das Default-Profil als Fallback.
3. **Given** mehrere Bedingungen könnten zutreffen, **When** der Resolver durchläuft, **Then** gewinnt die erste passende Regel in der geordneten Regelliste.

---

### Edge Cases

- **Kalter Cache beim Join**: Was passiert, wenn Permission-/Economy-Warmup beim Join-Zeitpunkt noch nicht abgeschlossen ist? → Das Scoreboard wird erst nach abgeschlossenem Warmup aufgebaut; vorher werden keine falschen/leeren Echtwerte angezeigt.
- **Wert ändert sich für offline/gerade gehenden Spieler**: Ein Balance-Event trifft ein, während der Spieler nicht mehr online ist → keine Schreiboperation, Subscription bereits abgemeldet.
- **Schnelle Folge von Wertänderungen**: Mehrere Events in kurzer Folge → jede betroffene Zeile löst den Wert frisch aus dem Cache auf (last-write-wins, kein Debounce); angezeigt wird stets der aktuellste Wert (FR-007a).
- **Permission-Änderung ohne lokales Change-Signal**: Stellt das Plugin-Permission-Modul noch kein lokales Change-Event bereit, wird dieses im `feature.permission`-Modul ergänzt (FR-006a), sodass die Rang-Zeile live aktualisiert wird. Bis ein Spieler ein erstes Signal erhält, gilt der beim Join aufgelöste Rang.
- **Zeile ohne Provider-Wert**: Liefert ein Provider (vorübergehend) keinen Wert, bleibt die Zeile strukturell erhalten und zeigt einen definierten Platzhalter statt zu verschwinden (verhindert Positions-/ID-Verschiebungen).
- **Profil-Wechsel zur Laufzeit** (Region betreten/verlassen): Der Wechsel adressiert Zeilen über stabile IDs, sodass gemeinsame Zeilen nicht flackern.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUSS Scoreboard-Profile als im Code deklarierte Konstanten bereitstellen, wobei jedes Profil eine geordnete Liste von Zeilen-Beiträgen ist.
- **FR-002**: Jede Scoreboard-Zeile MUSS eine stabile Zeilen-ID besitzen (nicht nur einen Positionsindex), sodass Live-Updates die richtige Zeile adressieren und Reihenfolge-Änderungen nicht flackern.
- **FR-003**: Zeileninhalte MÜSSEN aus Content-Providern stammen (nicht aus String-Konkatenation im Renderer). Economy und Permission MÜSSEN als echte Provider angebunden sein; Stats/Liga/Season MÜSSEN als Stub-Provider (feste Platzhalter-Zeile) realisierbar sein.
- **FR-003a**: Die Rang-Zeile MUSS ausschließlich den reinen Rang-Anzeigenamen aus dem Permission-Cache zeigen — ohne Farbe oder Prefix (Standard-Darstellung). Rang-Farbe/-Prefix sind Teil des Chat-Format-Folge-Slice und in Slice 1 nicht enthalten.
- **FR-004**: Das System MUSS einen Bedingung→Profil-Resolver bereitstellen: eine geordnete Liste von `(Bedingung → Profil)`-Regeln, bei der die erste passende Regel gewinnt und ein Default-Profil als Fallback dient.
- **FR-005**: Das System MUSS Region als erste Bedingung gegen einen Region-Provider-**Stub** unterstützen; die Bedingungs-Abstraktion MUSS generisch bleiben, sodass weitere Prädikate (z. B. „Event läuft", „Permission X", „Welt Y") ohne Resolver-Änderung ergänzbar sind.
- **FR-006**: Das System MUSS bei einer Balance-Änderung die betroffene Coins-Zeile des betreffenden Spielers neu auflösen und schreiben — ohne Neuaufbau des gesamten Scoreboards. Auslöser ist der vorhandene Balance-Event-Pfad über den bestehenden EventBus (vom `feature.economy`-Modul konsumiert); das Scoreboard wird über die daraus folgende Live-Benachrichtigung neu aufgelöst (Mechanik in plan.md §3).
- **FR-006a**: Das System MUSS bei einer Rang-/Permission-Änderung die Rang-Zeile **jedes betroffenen Online-Spielers** live neu auflösen und schreiben (gleiches Muster wie FR-006). Die Change-Signale (`GRANT_ADDED` / `GRANT_REVOKED` / `GRANT_EXPIRED` / `ROLE_CONFIG_CHANGED`) werden vom Backend bereits **pro betroffenem Holder** publiziert; eine rollenbezogene Änderung erreicht das Plugin also als je ein **spielerbezogenes** Signal pro Online-Holder. Eine **Fan-out-Logik im Scoreboard ist daher nicht nötig** — die Rang-Zeile wird je betroffener UUID neu aufgelöst. Das Plugin consumed diese Signale bereits im `feature.permission`-Modul (Cache-Reload); für die Live-Aktualisierung der Rang-Zeile MUSS dieses Modul nach dem Reload eine Live-Benachrichtigung auslösen (additiv, ohne generische Klasse zu ändern — siehe FR-012).
- **FR-007**: Das System MUSS Live-Updates flackerfrei und über die stabile Zeilen-ID adressiert ausführen (nicht über vollständiges Objective-Neuschreiben des Boards).
- **FR-007a**: Bei jedem eingehenden Wert-Event MUSS die betroffene Zeile den Wert frisch aus dem zuständigen Cache auflösen (last-write-wins, idempotent). Es gibt KEIN Debounce/Coalescing und keinen Scheduler in Slice 1; bei schnellen Wertwechseln wird stets der aktuellste bekannte Wert angezeigt.
- **FR-008**: Das System MUSS das Scoreboard in Adventure-Components rendern und pro Spieler in das Bukkit-Scoreboard schreiben (kein NMS, keine §-Color-Codes).
- **FR-009**: Das System MUSS beim Join eines Spielers dessen Scoreboard nach abgeschlossenem Permission-/Economy-Warmup aufbauen und beim Leave alle zugehörigen Ressourcen/Subscriptions sauber abmelden (LIVE-Disziplin analog Menü-Framework/Constitution).
- **FR-010**: Das System MUSS *Selektion* (welches Profil — Bedingungs-Resolver) und *Komposition* (welche Zeilen — Provider-Liste je Profil) als zwei getrennte, orthogonale Achsen behandeln und nicht vermischen.
- **FR-011**: Das System DARF KEIN neues Backend-Modul, KEINE `plugin-protocol`-Änderung, KEINEN neuen Channel und KEINEN neuen REST-Endpoint erzeugen; es konsumiert ausschließlich vorhandene Plugin-Caches.
- **FR-012**: Das Feature MUSS sich über die `FeatureRegistry` anstecken (ein `Feature`-Eintrag), OHNE eine **generische Klasse** (`platform`/`transport`/Menu-Generik) zu ändern. **Additive, rein lesende Ergänzungen an den Geschwister-Features `feature.economy` und `feature.permission` (Read-Ports auf deren vorhandene Caches) sowie eine additive Live-Benachrichtigung im Permission-Modul sind zulässig** und folgen aus FR-003/FR-006a sowie §4 („vorhandene Plugin-Caches konsumieren"); sie ändern kein Bestandsverhalten. Stellt sich heraus, dass eine **generische** Klasse (Transport/Platform/Menu-Generik) angefasst werden müsste, ist die Arbeit zu STOPPEN und ein Muster-Leck zu melden (Constitution).
- **FR-013**: Das System MUSS einen Austausch einer Zeile innerhalb eines Profils (im Code) ermöglichen, ohne dass andere Zeilen ihre ID oder Position verlieren.

### Bewusst NICHT in Scope (Slice 1)

- **TabList** — teilt Team-Farben/Prefix-Logik mit Scoreboard, aber eigener Render-Pfad. Folge-Slice.
- **Chat-Format** (Rang-Farbe/-Prefix im Chat) — hängt nur an Permission, sauber isolierbar. Folge-Slice.
- **Toggle-Persistenz** (Scoreboard an/aus pro Spieler) — persistentes Spieler-Datum, gehört backend-seitig (Settings/Toggles #9, noch nicht migriert). Slice 1: **Scoreboard immer an.**
- **Zähl-Animation + Sound bei Balance-Änderung** — Currency-Display-Effekt im `feature.economy`-Modul, kein Scoreboard-Feature. Folge-Slice.
- **Backend-Config der Profile / Runtime-Editing / Webinterface** — Profile sind im Plugin-Code definiert (die *Definition* lebt im Code, die *Werte* kommen aus Providern).
- **Echtes Region-System** — nur der Region-Provider-Stub.
- **Mine/Fight/LMS-Kontexte als echte Bedingungen** — dürfen Stub-Profile/-Bedingungen sein.

### Key Entities

- **Scoreboard-Profil**: Eine im Code deklarierte, benannte, geordnete Liste von Zeilen-Beiträgen (in Slice 1: das Profil „Default"). Repräsentiert ein Layout für einen bestimmten Spieler-Kontext.
- **Scoreboard-Zeile**: Ein Eintrag mit stabiler Zeilen-ID und einer Provider-Zuordnung. Die ID ist von Position und Inhalt entkoppelt.
- **Content-Provider**: Quelle für den Inhalt einer Zeile (echt: Economy, Permission; Stub: Stats/Liga/Season/Region). Austauschbar, ohne den Renderer zu berühren.
- **Bedingung (ScoreboardCondition)**: Prädikat, das prüft, ob ein Profil auf einen Spieler zutrifft (erste Implementierung: RegionCondition gegen Region-Provider-Stub).
- **Bedingung→Profil-Regel**: Geordnetes Paar `(Bedingung → Profil)`; die geordnete Regelliste plus Default-Profil bildet den Resolver-Input.
- **Region-Provider (Stub)**: Liefert die aktuelle (Test-)Region eines Spielers; wird später durch das echte Region-System ersetzt, ohne den Resolver zu ändern.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Spieler ohne Sonderbedingung sieht nach dem Join das Default-Profil mit Rang und Coins als echte (mit den Plugin-Caches übereinstimmende) Werte in 100 % der Fälle.
- **SC-002**: Eine Balance-Änderung spiegelt sich in der Coins-Zeile des betroffenen Spielers wider, während keine andere Zeile ihren Inhalt oder ihre Position ändert (verifizierbar pro Update).
- **SC-002a**: Eine Rang-/Permission-Änderung spiegelt sich live in der Rang-Zeile des betroffenen Spielers wider, während keine andere Zeile ihren Inhalt oder ihre Position ändert (verifizierbar pro Update).
- **SC-003**: Ein Live-Update verursacht kein sichtbares Flackern des Scoreboards (kein Verschwinden/Neuaufbau anderer Zeilen).
- **SC-004**: Der Austausch eines Stub-Providers gegen einen echten Provider ändert ausschließlich die Provider-Zuordnung einer Zeile; weder Renderer noch andere Zeilen-IDs/-Positionen werden berührt (an genau einer Codestelle nachweisbar).
- **SC-005**: Das Setzen einer Test-Region über den Stub führt dazu, dass der Spieler das zugehörige Profil statt des Default sieht; Zurücksetzen stellt das Default wieder her.
- **SC-006**: Nach dem Leave eines Spielers erfolgen keine Schreib-/Update-Operationen mehr für diesen Spieler (alle Subscriptions abgemeldet).
- **SC-007**: Es entsteht nachweislich keine `plugin-protocol`-Änderung, kein Backend-Eingriff und keine Änderung an einer generischen Klasse (Transport/Platform) — explizit verifiziert.

## Assumptions

- **Permission-Change-Event (offene Frage P1 der Vorlage — GEKLÄRT 2026-06-30, im Plan verifiziert)**: Die Rang-Zeile wird live aktualisiert (analog Coins, FR-006a). Das Backend publiziert `GRANT_ADDED` / `GRANT_REVOKED` / `GRANT_EXPIRED` / `ROLE_CONFIG_CHANGED` bereits **pro betroffenem Holder** (kein Fan-out im Scoreboard nötig), und das Plugin **consumed** sie bereits im `feature.permission`-Modul (`PermissionLiveUpdater` → Cache-Reload). Offen bleibt nur die additive Live-Benachrichtigung nach dem Reload (plan.md §3, eine Zeile) — keine generische Klasse betroffen.
- **Flicker-Strategie (offene Frage P2 der Vorlage)**: Der flickerfreie, zeilen-ID-adressierte Ansatz (z. B. stabile Slots über Team-Entries, nur Suffix ändern) wird gegenüber vollständigem Objective-Neuschreiben bevorzugt; die konkrete Mechanik wird in `/plan` festgelegt.
- **Default-Profil-Inhalt (offene Frage P3 der Vorlage — GEKLÄRT 2026-06-30)**: Es gibt kein „lobby"-Profil. Slice 1 liefert genau ein Profil namens „Default" mit dem Blueprint: Header, Rang (Permission), Coins (Economy), Stats (Stub), Footer.
- **Doku-Drift Permission-Plugin (offene Frage P4 der Vorlage)**: Der Plugin-seitige Permission-Slice ist in PROGRESS.md / FEATURE_INVENTORY.md noch nicht nachgezogen, wird aber von diesem Slice vorausgesetzt. Vor `/implement` ist dies in PROGRESS.md nachzutragen.
- Die Profile, Bedingungen und Provider sind in Slice 1 **hardcoded im Plugin-Code** (Definition im Code, Werte aus Providern); kein Runtime-Editing.
- Economy- und Permission-Plugin-Caches sind bereits migriert und stehen als Wahrheitsquelle für die Werte zur Verfügung.
- Es existiert ein vorhandener EventBus mit einem Balance-Änderungs-Event, an das sich der Live-Update-Pfad anschließt.
- Region wird in Slice 1 ausschließlich gegen einen Stub aufgelöst; das echte Region-System ersetzt später nur den Provider.

## Bewusste Grenzen / Architektur-Hinweise

- **Plugin-only-Slice.** Anders als Economy/Punishments/Reports hat dieser Slice **keinen** Backend→publish→Plugin-Halbschritt. Er konsumiert nur vorhandene Plugin-Caches (Economy, Permission). Das ist constitution-konform („Backend = Wahrheit", nicht „jedes Feature braucht Backend") und wird in PROGRESS.md explizit als bewusste Grenze vermerkt, damit niemand später einen fehlenden Backend-Teil vermutet.
- **Keine generische Klasse wird geändert.** Das Feature steckt sich über die `FeatureRegistry` an (ein `Feature`-Eintrag). STOPP + Muster-Leck melden, falls Transport/Platform doch angefasst werden müssten.
- **Zwei orthogonale Achsen, sauber getrennt:** *Selektion* (Bedingungs-Resolver) vs. *Komposition* (Provider-Liste je Profil).
- **Region = nur eine Bedingung unter vielen.** Die Bedingungs-Abstraktion ist generisch; Region ist die erste Implementierung.
