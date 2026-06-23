# Feature Specification: Permission-/Rank-System — Plugin-Slice (Paper-1.21-Client)

**Feature Branch**: `002-permission-rank-client`

**Created**: 2026-06-23

**Status**: Draft

**Input**: User description: "Plugin-Slice für das Permission-/Rank-System (Client-Seite). Das Backend ist fertig und autoritativ (Branch 002-permission-rank-system); dieser Slice ist der reine Paper-Client."

## Overview

Das Backend ist die **alleinige Wahrheit** für Rollen, Rang-/Permission-Vergaben und die effektiven Permissions eines Spielers. Dieser Slice baut **nur den Paper-1.21-Client** auf drei Säulen:

1. **Effektive-Permissions-Live-Cache** — pro online Spieler hält der Client die abgeflachte effektive Permission-Menge **und** die gewählte Rang-Darstellung. Gefüttert über `GET /api/permission/players/{uuid}/effective`, **relog-frei** aktualisiert durch Pub/Sub-Ereignisse auf `mc:permission:changed`.
2. **Optimistisches UI-/Command-Gate** — Befehle und Menü-Einstiege werden anhand des Caches **vorab** freigeschaltet oder gesperrt; das ist reiner Komfort. Das **Backend bleibt Autorität**: ein `403` ist die Wahrheit und überstimmt jede optimistische Anzeige.
3. **Staff-Verwaltungsmenüs** — über zwei dedizierte Befehle (kein Hub-Eintrag in diesem Slice): **`/ranks`** öffnet die Rollen-Verwaltung (Liste, anlegen/bearbeiten/löschen, Rollen-Permissions pflegen); **`/cp <Spieler>`** (Control Panel) öffnet die spielerbezogenen Vergaben (Ränge/Einzel-Permissions vergeben/entziehen) für einen online **oder offline** Spieler. Über das Menü-Framework gemäß `MENU_DESIGN.md` (STATIC-Menüs); Rollen werden visuell über ihr `display_icon` unterschieden.

Schreibzugriffe laufen über REST (BackendClient/EndpointDescriptor), Live-Updates lesend über die EventBus/Redis-Pub-Sub-Anbindung. Der opake `display_icon`-String wird **ausschließlich hier im Plugin** interpretiert — das Backend versteht ihn nie.

## Clarifications

### Session 2026-06-23

- Q: Umfasst dieser erste Slice schon die spieler-sichtbare Darstellung (Prefix/Farbe/Tablist/Chat) oder nur Menü + Gating-Cache + Icon-Rendering im Menü? → A: **Nur Menü + Gating-Cache + Icon-Rendering im Menü.** Die spieler-sichtbare Chat-/Tablist-Darstellung ist ein späterer Cosmetics/Chat-Slice und hier ausdrücklich **out of scope**. Die gewählte Darstellung (`RoleDisplay`) wird gecacht und im Staff-Menü angezeigt, aber **nicht** an Chat/Tablist/Nametag gebunden.
- Q: Wie reagiert der Client auf `mc:permission:changed`? → A: **Gezielt nur den Cache der betroffenen `playerUuid` neu laden** (ein `GET .../effective`). Ist der Spieler **offline**, wird das Ereignis **ignoriert** (kein Laden, kein Eintrag). Ein `ROLE_CONFIG_CHANGED` kommt vom Backend bereits **pro betroffenem Halter** als je ein Ereignis — der Client behandelt jedes wie eine einzelne Spieler-Invalidierung.
- Q: Default-Icon und Verhalten bei `null`/unbekanntem Icon-Präfix? → A: **Vorwärtskompatibel:** Bei `null` **oder** unbekanntem Präfix (Plugin älter als ein neuer Backend-Präfix) rendert der Client ein **definiertes Default-Icon** (neutral, z. B. Namensschild/Buch nach MENU_DESIGN), **statt** zu crashen oder den Eintrag auszulassen. Bekannte Präfixe: `material:<MATERIAL>`, `head-texture:<texture>`, `head-player:<uuid>`.
- Q: Wie verhält sich das optimistische Gate ohne Cache-Treffer (Spieler gerade gejoint, `effective` noch nicht geladen)? → A: **Bis zum ersten erfolgreichen `effective`-Load neutral**: der Client sperrt nicht optimistisch, sondern lässt den Versuch zum Backend durch — das Backend (`403`) entscheidet. Sobald der Cache befüllt ist, gilt das optimistische Gate normal. (Kein „optimistisch sperren" bei Cold-Cache, um neu gejointe Berechtigte nicht fälschlich auszusperren.)
- Q: Wie wählt Staff den Ziel-Spieler für Rang-/Permission-Vergaben? → A: **Über den Befehl `/cp <Spieler>`** (Control Panel) — der Spielername ist Befehls-Argument und funktioniert für **online und offline** Spieler. Kein Online-Kopf-Picker im Menü. Der Client MUSS den Namen zu einer UUID auflösen, um die `{uuid}`-Endpunkte zu treffen. (`/cp` ist als künftiges Control-Panel mit mehreren Optionen gedacht; in diesem Slice nur die Rang-/Permission-Vergaben des genannten Spielers.)
- Q: Wo lebt die nicht-spieler-bezogene Rollen-Verwaltung (Rollen anlegen/bearbeiten/löschen, Rollen-Permissions pflegen)? → A: **Unter einem eigenen Befehl `/ranks`.** `/ranks` öffnet die Rollen-Liste samt CRUD und Pflege der Rollen-Permissions; `/cp <Spieler>` bleibt rein spielerbezogen.
- Q: Soll das Verwaltungsmenü STATIC oder LIVE sein? → A: **STATIC** (MENU_DESIGN §6): Das Menü zeigt den Stand beim Öffnen und rendert nach **eigener** Schreibaktion gezielt die betroffenen Slots neu. Kein Abo auf `mc:permission:changed` im Menü, kein MenuLiveBus für diesen Slice.
- Q: Welcher UI-Permission-Node gated Einstieg/Schreibzugriffe optimistisch? → A: **Feingranular pro Aktion** — getrennte Nodes je Aktionsgruppe (z. B. Rollen verwalten vs. Spieler-Vergaben verwalten), statt eines einzelnen Sammel-Nodes. Das Gate prüft pro Aktion den jeweils zuständigen Node; das Backend bleibt Autorität.
- Q: Wie wird `display_icon` konkret unterstützt (Rendering + ein Werkzeug zum Erzeugen des Strings)? → A: **Zwei Richtungen, geteiltes Format.** (1) Ein **`IconResolver`** (String → ItemStack, eine Stelle im Feature) rendert `material:`/`head-texture:`/`head-player:` über die Paper-`PlayerProfile`-API (kein NMS); `null`/unbekannt/ungültig → **sichtbares Fallback-Icon** (`BARRIER`/`PAPER`), nie Crash. (2) Ein **reines Lese-Werkzeug `/rank toDisplayIcon`** liest das Item in der Hand und gibt denselben präfixierten String als **click-to-copy** Adventure-Komponente aus (kein Backend-Call, kein Schreibpfad). Beide teilen die Präfix-Konstanten in **einer** Klasse. Das Plugin schreibt das Icon **nie** zurück (Setzen passiert im Webinterface).
- Q: Was gibt `/rank toDisplayIcon` aus, wenn ein PLAYER_HEAD ohne eingebettete Textur in der Hand liegt? → A: **Nur zwei Ausgaben.** Lässt sich eine Textur-base64 aus dem `PlayerProfile` extrahieren → `head-texture:<base64>`; sonst (auch ein textureloser Spielerkopf) → `material:PLAYER_HEAD`. Das Werkzeug erzeugt in diesem Slice **kein** `head-player:<uuid>`.

### Update 2026-06-23 (Kaltstart hart gemacht — ersetzt die Q4-Antwort oben)

- Der Permission-Cache wird nun im **`AsyncPlayerPreLoginEvent`** befüllt (fail-closed, harter Kick bei Backend-Fehler), an derselben Verfügbarkeit-vor-Sicherheit-Linie wie der Session-Join. Dadurch hat ein in-world-Spieler **garantiert** einen warmen Cache (FR-002).
- Das **Cold-Cache-Verhalten des Gate** (vorher: „neutral durchlassen", Q4) wird **invertiert**: leerer Cache → **strict-deny + Warnlog** (Bug-Indikator), kein optimistisches Raten mehr (FR-009). Backend bleibt Autorität (`403` überstimmt). Die Wildcard-Logik (`*` / Ancestor-`.*` / exakt) bleibt unverändert.
- Der `mc:permission:changed`-Live-Push (Laufzeit-Änderungen) bleibt **unabhängig** nötig und koexistiert mit dem PreLogin-Warmup (Kaltstart).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff verwaltet Rollen und Vergaben über das Menü (Priority: P1)

Ein Team-Mitglied öffnet mit **`/ranks`** die Rollen-Verwaltung: die Liste aller Rollen — jede visuell über ihr `display_icon` unterschieden — und kann eine **Rolle anlegen, bearbeiten oder löschen** sowie die **Permissions einer Rolle** pflegen (hinzufügen/entfernen). Mit **`/cp <Spieler>`** öffnet es das spielerbezogene Control Panel für einen **online oder offline** Spieler (Name wird zu UUID aufgelöst) und kann dort einen **Rang vergeben/entziehen** und **Einzel-Permissions vergeben/entziehen**, jeweils optional mit Ablaufzeit und Grund. Alle Schreibzugriffe gehen über REST; kritische Aktionen (Rolle löschen) laufen über den Confirm-Dialog mit Doppelklick. Die Menüs sind **STATIC** und rendern nach einer eigenen Schreibaktion die betroffenen Slots gezielt neu.

**Why this priority**: Das ist die sichtbare Kern-Lieferung dieses Slice — ohne Verwaltungsmenü gibt es keinen clientseitigen Mehrwert. Unabhängig gegen das laufende Backend testbar (Backend hält den Zustand auch ohne die anderen Säulen).

**Independent Test**: Mit einem Team-Account `/ranks` öffnen → Rollenliste mit Icons erscheint; eine Rolle anlegen → erscheint im Backend; `/cp <Spieler>` mit einem offline Namen öffnen → Name wird zu UUID aufgelöst, ein Rang vergeben → Backend spiegelt die Vergabe; eine Rolle löschen (Doppelklick-Confirm) → im Backend entfernt.

**Acceptance Scenarios**:

1. **Given** ein Team-Mitglied mit Rollen-Verwaltungs-Berechtigung, **When** es `/ranks` ausführt, **Then** wird die paginierte Rollenliste angezeigt, jede Rolle mit ihrem aus `display_icon` gerenderten Icon, Anzeigename und Gewicht.
2. **Given** die Rollen-Verwaltung ist offen, **When** das Team-Mitglied „Rolle anlegen" wählt und Name/Anzeigename eingibt, **Then** wird die Rolle per REST erstellt und erscheint in der Liste.
3. **Given** eine bestehende Rolle im Detail, **When** das Team-Mitglied eine Permission hinzufügt bzw. entfernt, **Then** wird die Rollen-Konfiguration per REST aktualisiert und die Detailansicht spiegelt den neuen Permissions-Stand.
4. **Given** ein Team-Mitglied führt `/cp <Spieler>` für einen online oder offline Spieler aus, **When** der Name zu einer UUID aufgelöst wurde und ein Rang vergeben wird (optional mit Ablauf/Grund), **Then** wird die Vergabe per REST gesetzt und die Ansicht zeigt den Spieler mit dem neuen Rang.
5. **Given** ein aktiver Rang oder eine Einzel-Permission eines Spielers, **When** das Team-Mitglied „entziehen" wählt, **Then** wird die Vergabe per REST widerrufen und verschwindet aus der Spieleransicht.
6. **Given** eine Rolle soll gelöscht werden, **When** das Team-Mitglied den Confirm-Dialog doppelklickt, **Then** wird die Rolle per REST gelöscht; ein einfacher Klick löscht **nicht**.
7. **Given** das Backend lehnt einen Schreibzugriff ab (`403` mangels Berechtigung), **When** der Versuch erfolgt, **Then** zeigt der Client eine verständliche Fehlermeldung und der lokale Zustand bleibt konsistent — unabhängig davon, ob das optimistische Gate den Einstieg angezeigt hatte.

---

### User Story 2 - Effektive Permissions live im Cache, relog-frei (Priority: P1)

Wenn ein Spieler den Server betritt, lädt der Client dessen effektive Permissions und gewählte Rang-Darstellung über `GET .../effective` in den Cache. Ändert sich etwas am Backend (Rang/Permission vergeben, entzogen, abgelaufen, oder eine Rollen-Konfiguration geändert), veröffentlicht das Backend ein `mc:permission:changed`-Ereignis; der Client lädt **gezielt** den Cache der betroffenen UUID neu — **ohne Relog**. Beim Verlassen des Servers wird der Cache-Eintrag des Spielers freigegeben.

**Why this priority**: Der Cache ist das Lese-Fundament, auf dem Gate (US3) und Menü-Anzeige aufbauen, und liefert für sich den geforderten relog-freien Effekt. Unabhängig testbar über Join + ein publiziertes Ereignis.

**Independent Test**: Spieler joint → `effective` wird einmal geladen, Cache enthält effektive Permissions + Darstellung. Backend publiziert `mc:permission:changed` für diese UUID → Cache wird neu geladen und spiegelt die Änderung. Ereignis für eine **offline** UUID → wird ignoriert, kein Eintrag entsteht.

**Acceptance Scenarios**:

1. **Given** ein Spieler betritt den Server, **When** der Join verarbeitet wird, **Then** wird **asynchron** `effective` geladen und der Cache-Eintrag (effektive Permissions + `RoleDisplay`) befüllt, ohne den Main-Thread zu blockieren.
2. **Given** ein online Spieler ist im Cache, **When** ein `mc:permission:changed` mit seiner UUID eintrifft (changeType `GRANT_ADDED|GRANT_REVOKED|GRANT_EXPIRED|ROLE_CONFIG_CHANGED`), **Then** wird **nur** dessen Cache-Eintrag per `effective` neu geladen.
3. **Given** ein `mc:permission:changed` trifft ein, **When** die betroffene UUID **offline** ist, **Then** wird das Ereignis ignoriert (kein Laden, kein neuer Cache-Eintrag).
4. **Given** ein Spieler verlässt den Server, **When** der Quit verarbeitet wird, **Then** wird sein Cache-Eintrag freigegeben (kein Speicher-Leak über Sessions).
5. **Given** die Pub/Sub-Verbindung war kurz unterbrochen, **When** sie wiederhergestellt ist, **Then** bleiben Cache und Live-Updates über den bestehenden EventBus-/Reconnect-Mechanismus funktionsfähig (kein neuer Mechanismus).

---

### User Story 3 - Optimistisches Gate für Befehle und Menü-Einstiege (Priority: P2)

Befehle und Menü-Einstiege fragen den Cache, ob der Spieler die nötige Permission besitzt, und reagieren **sofort** — ohne auf das Backend zu warten. Das ist Komfort: Das Backend bleibt Autorität, jeder echte Schreibzugriff wird dort geprüft, und ein `403` überstimmt die optimistische Anzeige. Hat der Cache (noch) keinen Treffer für den Spieler (z. B. unmittelbar nach dem Join), verhält sich das Gate **neutral** und lässt den Versuch zum Backend durch.

**Why this priority**: Verbessert die UX (keine sichtbaren Einstiege ohne Berechtigung, sofortiges Feedback), ist aber nicht für die Korrektheit nötig — die Autorität liegt beim Backend. Baut auf US2 auf.

**Independent Test**: Bei befülltem Cache einen permission-pflichtigen Befehl/Einstieg mit und ohne die Permission auslösen → erlaubt bzw. mit verständlicher Meldung abgewiesen; bei leerem Cache (gerade gejoint) → Versuch geht durch, Backend entscheidet.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit befülltem Cache, der die geforderte Permission besitzt, **When** er einen permission-pflichtigen Befehl/Einstieg auslöst, **Then** wird er optimistisch zugelassen.
2. **Given** ein Spieler mit befülltem Cache **ohne** die geforderte Permission, **When** er den Befehl/Einstieg auslöst, **Then** wird er optimistisch abgewiesen, mit verständlicher Meldung; es entsteht kein Backend-Schreibzugriff.
3. **Given** ein Spieler ohne Cache-Treffer (gerade gejoint, `effective` noch nicht geladen), **When** er einen permission-pflichtigen Befehl auslöst, **Then** sperrt das Gate **nicht** optimistisch, sondern lässt den Versuch durch; das Backend entscheidet.
4. **Given** das optimistische Gate hat einen Einstieg angezeigt, **When** der Backend-Schreibzugriff dennoch `403` liefert, **Then** wird die Aktion abgelehnt und der Spieler erhält eine verständliche Meldung (Backend ist die Wahrheit).

---

### User Story 4 - Rollen visuell über display_icon unterscheiden (Priority: P2)

Im Staff-Menü erhält jede Rolle ihr eigenes Icon, das der Client aus dem opaken `display_icon`-String der Rolle ableitet. Das Präfix vor dem Doppelpunkt bestimmt das Rendering: `material:<MATERIAL>` → ItemStack des Materials, `head-texture:<texture>` → Custom-Head mit Textur, `head-player:<uuid>` → Spielerkopf. Bei `null` oder einem **unbekannten** Präfix rendert der Client ein definiertes Default-Icon — so bleibt ein älteres Plugin gegenüber neuen Backend-Präfixen vorwärtskompatibel.

**Why this priority**: Macht die Rollenliste lesbar und ist die im Brief betonte plugin-eigene Interpretationslogik. Wertet US1 auf, ist aber separat testbar.

**Independent Test**: Rollen mit `material:DIAMOND_SWORD`, `head-texture:<...>`, `head-player:<uuid>`, `null` und `foobar:xyz` (unbekannt) anlegen → Menü zeigt jeweils das korrekte Icon bzw. für `null`/unbekannt das Default-Icon; kein Fehler, kein ausgelassener Eintrag.

**Acceptance Scenarios**:

1. **Given** eine Rolle mit `display_icon = material:<MATERIAL>`, **When** die Rollenliste rendert, **Then** zeigt der Eintrag das ItemStack des Materials.
2. **Given** eine Rolle mit `display_icon = head-texture:<texture>`, **When** die Liste rendert, **Then** zeigt der Eintrag einen Custom-Head mit der Textur.
3. **Given** eine Rolle mit `display_icon = head-player:<uuid>`, **When** die Liste rendert, **Then** zeigt der Eintrag den Spielerkopf zu dieser UUID.
4. **Given** eine Rolle mit `display_icon = null`, **When** die Liste rendert, **Then** zeigt der Eintrag das definierte Default-Icon.
5. **Given** eine Rolle mit unbekanntem Präfix (z. B. `banner:red`), **When** die Liste rendert, **Then** zeigt der Eintrag das Fallback-Icon (vorwärtskompatibel, kein Fehler).

---

### User Story 5 - display_icon-String aus einem Item erzeugen (Priority: P2)

Ein Team-Mitglied nimmt das gewünschte Item (Vanilla-Item oder Custom-Head) in die Hand und führt `/rank toDisplayIcon` aus. Der Client übersetzt das Item in den präfixierten `display_icon`-String — `material:<TYP>` für Vanilla-Items, `head-texture:<base64>` für Custom-Heads — und zeigt ihn als **click-to-copy** Chat-Komponente, die ins Webinterface-Feld eingefügt werden kann. Es ist ein reines Lese-Werkzeug: kein Backend-Call, kein Schreiben des Icons.

**Why this priority**: Schließt die Lücke zwischen In-Game-Item und dem opaken String, den das Webinterface erwartet, und nutzt dieselbe Format-Definition wie das Rendering (kein Auseinanderlaufen). Unabhängig testbar; baut auf der geteilten Format-Klasse auf.

**Independent Test**: Ein Vanilla-Item in die Hand nehmen, `/rank toDisplayIcon` → korrekter `material:<TYP>`-String erscheint, anklickbar/kopierbar. Einen Custom-Head in die Hand nehmen → `head-texture:<base64>` mit der Textur des Kopfs. `resolve(extract(item))` ergibt wieder dasselbe Icon (Roundtrip).

**Acceptance Scenarios**:

1. **Given** ein Vanilla-Item in der Hand, **When** `/rank toDisplayIcon` ausgeführt wird, **Then** erscheint `material:<TYP>` als click-to-copy-Komponente; es erfolgt kein Backend-Call.
2. **Given** ein Custom-Head mit Textur in der Hand, **When** der Befehl ausgeführt wird, **Then** erscheint `head-texture:<base64>` mit der korrekten Textur.
3. **Given** eine leere Hand, **When** der Befehl ausgeführt wird, **Then** erscheint ein verständlicher Hinweis (kein Fehler).
4. **Given** ein per Werkzeug erzeugter String, **When** er später als Rollen-`display_icon` gerendert wird, **Then** zeigt der Eintrag dasselbe Item (Roundtrip-Treue).

---

### Edge Cases

- **Cold-Cache-Gate**: Spieler löst Befehl unmittelbar nach Join aus, bevor `effective` geladen ist → Gate neutral, Backend entscheidet (siehe US3).
- **Offline-Ereignis**: `mc:permission:changed` für eine nicht eingeloggte UUID → ignorieren.
- **`ROLE_CONFIG_CHANGED` mit vielen Haltern**: Backend publiziert je ein Ereignis pro Halter; der Client behandelt jedes als einzelne Spieler-Invalidierung (kein Massen-Reload, keine Sonderlogik).
- **`effective`-Load schlägt fehl** (Netzwerk/`5xx`): Cache bleibt ohne/mit altem Eintrag; Gate verhält sich wie bei Cold-Cache (neutral) bzw. behält den alten Stand; späteres Ereignis oder erneuter Join lädt nach.
- **Ungültige/abgelaufene Vergabe im Menü** (`409`/`422`/`404` vom Backend) → verständliche Fehlermeldung, Ansicht bleibt konsistent.
- **Rate-Limit/Transport-Fehler** (`429`/`403`) bei Schreibzugriff → verständliche Meldung; keine optimistische „Erfolg"-Anzeige.
- **Unbekannter `changeType`** im Ereignis → wie eine generische Invalidierung der UUID behandeln (neu laden), nicht crashen.
- **Leere Rollenliste** im Menü → dezentes Hinweis-Item statt kaputt wirkendem Raster (MENU_DESIGN §4.4).
- **Nicht auflösbarer Name bei `/cp <Spieler>`** (unbekannt/nie gesehen) → verständliche Fehlermeldung, kein Menü öffnet sich.
- **Spielerbezogenes Menü offen, während sich der Stand am Backend ändert** → STATIC, daher keine Live-Aktualisierung; nach eigener Aktion oder erneutem `/cp` zeigt das Menü den frischen Stand.
- **`/rank toDisplayIcon` mit textur­losem Spielerkopf oder leerer Hand** → textureloser Kopf → `material:PLAYER_HEAD`; leere Hand → verständlicher Hinweis, kein Fehler.

## Requirements *(mandatory)*

### Functional Requirements

**Live-Cache & Live-Updates**

- **FR-001**: Das System MUSS pro online Spieler einen Cache-Eintrag mit der abgeflachten effektiven Permission-Menge und der gewählten Rang-Darstellung (`RoleDisplay`) führen.
- **FR-002**: Das System MUSS den Cache-Eintrag eines Spielers als **Kaltstart-Warmup im `AsyncPlayerPreLoginEvent`** über `GET .../effective` befüllen — **bevor** der Spieler die Welt betritt, blockierend im async PreLogin-Thread (Main-Thread nie blockiert), **fail-closed**: schlägt der Backend-Load fehl, wird der Login hart abgewiesen (`disallow`/Kick) wie beim fehlenden Session-Join. Ein in der Welt befindlicher Spieler hat damit **garantiert** einen gefüllten Cache.
- **FR-003**: Das System MUSS auf `mc:permission:changed` für eine **online** UUID **genau diesen** Cache-Eintrag per `effective` neu laden.
- **FR-004**: Das System MUSS `mc:permission:changed`-Ereignisse für **offline** UUIDs ignorieren (kein Laden, kein neuer Eintrag).
- **FR-005**: Das System MUSS alle vier `changeType`-Werte (`GRANT_ADDED`, `GRANT_REVOKED`, `GRANT_EXPIRED`, `ROLE_CONFIG_CHANGED`) als Invalidierung der genannten UUID behandeln; bei unbekanntem `changeType` ebenfalls neu laden statt zu scheitern.
- **FR-006**: Das System MUSS den Cache-Eintrag eines Spielers beim Verlassen des Servers freigeben (keine Leaks über Sessions).
- **FR-007**: Das System MUSS die Live-Updates über den bestehenden EventBus-/Redis-Pub-Sub-Pfad konsumieren (kein neuer Transport-Mechanismus) und eine Reconnect-Unterbrechung überstehen.

**Optimistisches Gate**

- **FR-008**: Das System MUSS Befehle und Menü-Einstiege optimistisch anhand des Caches freischalten/sperren, wenn ein Cache-Eintrag vorliegt, und dabei **feingranular pro Aktion** den jeweils zuständigen UI-Permission-Node prüfen (getrennte Nodes für Rollen-Verwaltung vs. spielerbezogene Vergaben), nicht einen einzelnen Sammel-Node.
- **FR-009**: Das System MUSS bei fehlendem Cache-Eintrag (Cold-Cache) **strikt sperren** und eine **Warnung loggen** (Bug-Indikator „warmup gap"). Der PreLogin-Warmup (FR-002) garantiert, dass ein in-world-Spieler nie kalten Cache hat; ein kalter Cache ist daher kein Normalzustand und darf nicht hinter optimistischem Durchwinken verschwinden. (Ersetzt das frühere „neutral durchlassen".)
- **FR-010**: Das System MUSS das Backend als Autorität behandeln: ein `403` überstimmt jede optimistische Anzeige und führt zu einer verständlichen Ablehnung.
- **FR-011**: Das System MUSS bei optimistischer Sperre eine verständliche Meldung ausgeben und **keinen** Backend-Schreibzugriff auslösen.

**Staff-Verwaltungsmenü (Schreibzugriffe)**

- **FR-012**: Team-Mitglieder MÜSSEN über das Menü Rollen **anlegen, bearbeiten und löschen** können (REST: CREATE/UPDATE/DELETE_ROLE).
- **FR-013**: Team-Mitglieder MÜSSEN die **Permissions einer Rolle** hinzufügen/entfernen können (REST: ADD/REMOVE_ROLE_PERMISSION).
- **FR-014**: Team-Mitglieder MÜSSEN einem Spieler einen **Rang vergeben/entziehen** können, optional mit Ablaufzeit und Grund (REST: GRANT_ROLE/REVOKE_ROLE).
- **FR-015**: Team-Mitglieder MÜSSEN einem Spieler **Einzel-Permissions vergeben/entziehen** können, optional mit Ablaufzeit und Grund (REST: GRANT_PERMISSION/REVOKE_PERMISSION).
- **FR-016**: Das System MUSS für jeden Schreibzugriff den handelnden Staff (`actor`) mitführen, wie es der jeweilige Endpunkt verlangt (Body bzw. Query bei Revoke-by-path).
- **FR-017**: Das System MUSS kritische, irreversible Aktionen (Rolle löschen) über den Confirm-Dialog mit **Doppelklick** absichern (MENU_DESIGN §2.5).
- **FR-018**: Das System MUSS Backend-Ablehnungen (`403`/`404`/`409`/`422`/`429`) im Menü als verständliche Fehlermeldung darstellen und die Ansicht konsistent halten.
- **FR-019**: Die Verwaltungsmenüs MÜSSEN dem Menü-Framework und `MENU_DESIGN.md` folgen (Größen, Slot-Konventionen, Paginierung 7×4, Components/Tokens, Klick-Feedback, async laden / Main-Thread für Inventar).
- **FR-026**: Das System MUSS die Rollen-Verwaltung über den Befehl **`/ranks`** und das spielerbezogene Control Panel über **`/cp <Spieler>`** bereitstellen; ein Hub-Menü-Eintrag wird in diesem Slice **nicht** hinzugefügt.
- **FR-027**: Das System MUSS den bei `/cp <Spieler>` übergebenen Namen zu einer UUID auflösen und damit auch **offline** Spieler verwalten können; bei nicht auflösbarem Namen MUSS es eine verständliche Fehlermeldung ausgeben und kein Menü öffnen.
- **FR-028**: Die Verwaltungsmenüs MÜSSEN **STATIC** sein (kein Abo auf `mc:permission:changed`, kein MenuLiveBus); nach einer eigenen Schreibaktion MÜSSEN die betroffenen Slots gezielt neu gerendert werden.

**display_icon-Interpretation (plugin-eigen)**

- **FR-020**: Das System MUSS den opaken `display_icon`-String **im Plugin** zu einem ItemStack auflösen (eine Stelle, `IconResolver`): `material:<NAME>` → ItemStack des Materials, `head-texture:<tex>` → Custom-Head (Textur über Paper-`PlayerProfile`-API, **kein NMS**), `head-player:<uuid>` → Spielerkopf über dessen `PlayerProfile`.
- **FR-021**: Das System MUSS bei `display_icon == null`, unbekanntem Präfix, ungültigem Material, kaputtem Payload **oder** jedem Parse-/Lookup-Fehler ein **sichtbares Fallback-Icon** (`BARRIER`/`PAPER`) rendern — nie ein Crash, nie ein ausgelassener Eintrag (vorwärtskompatibel).
- **FR-022**: Das System DARF die Icon-Mapping-Logik **nicht** an das Backend/den Contract auslagern — sie lebt im Plugin-Feature.
- **FR-029**: Das System MUSS die Präfix-/Format-Definition in **einer** geteilten Klasse halten, die sowohl das Rendering (String→ItemStack) als auch das Lese-Werkzeug (ItemStack→String) nutzt, damit beide Richtungen nie auseinanderlaufen.
- **FR-030**: Das System MUSS den Befehl **`/rank toDisplayIcon`** bereitstellen: liest das Item in der Hand, erzeugt den präfixierten `display_icon`-String und gibt ihn als **click-to-copy** Adventure-Komponente aus. **Kein Backend-Call, kein Schreibpfad.** Genau zwei Ausgaben: Textur extrahierbar → `head-texture:<base64>`; sonst (Vanilla-Item **oder** textureloser Spielerkopf) → `material:<TYP>`. Kein `head-player:<uuid>` aus dem Werkzeug. Leere Hand → verständlicher Hinweis.
- **FR-031**: Das System DARF das `display_icon` **nie** an das Backend zurückschreiben (Setzen erfolgt im Webinterface); der String bleibt backend-opak.

**Architektur-Leitplanken (Constitution)**

- **FR-023**: Das System MUSS Bukkit ausschließlich über die `platform`-Schicht berühren; kein direkter NMS-/Reflection-Zugriff.
- **FR-024**: Das System DARF den Main-Thread **nie** durch REST/Redis blockieren (async Datenholen, Scheduler-Abstraktion für Main-Thread-Arbeit).
- **FR-025**: Das System MUSS als **ein** neues Feature-Package + ein FeatureRegistry-Eintrag ansteckbar sein; muss dafür eine **generische** Klasse (FeatureCache, EventBus, BackendClient, MenuBuilder, PluginFeature, Scheduler) geändert werden, ist das als **Muster-Leck** zu melden (STOPP, ggf. Vor-Refactor) statt es stillschweigend zu ändern.

### Key Entities *(include if feature involves data)*

- **PlayerPermissionsResponse**: Voller Permission-Zustand eines Spielers — aktive Rang-Vergaben, aktive Einzel-Permission-Vergaben, abgeflachte effektive Permission-Menge, gewählte Darstellung. Lese-Quelle des Caches (`effective`).
- **RoleDisplay**: Gewählte Darstellung des Spielers (Anzeigename, Farbe, Prefix/Suffix, Tablist-Farbe/-Icon, `displayIcon`). Im Cache gehalten und im Menü angezeigt; **nicht** an Chat/Tablist gebunden (out of scope).
- **RoleResponse / RoleRequest**: Rolle als Stammdatum (Name, Anzeigename, Farben, `displayIcon`, Gewicht, teamRank, aktiv, isDefault, Permissions) bzw. ihr Erstell-/Änder-Request inkl. `actor`.
- **ActiveGrant**: Eine aktive Vergabe in der Spieler-Sicht — `label` (Rollenname oder Permission-String), optionaler Ablauf (`expiresAtEpochMilli == null` = permanent), `issuedBy`, `reason`.
- **Grant*Request / Revoke*Request**: Vergabe/Widerruf von Rang bzw. Einzel-Permission, jeweils mit optionalem Ablauf/Grund und `actor`.
- **PermissionChangedEvent**: Pub/Sub-Ereignis auf `mc:permission:changed` (`playerUuid`, `changeType`, `timestampEpochMilli`) — Auslöser der gezielten Cache-Invalidierung.
- **display_icon (Wert)**: Opaker `<typ>:<payload>`-String der Rolle; **nur im Plugin** interpretiert.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nach einer Backend-Änderung an den Permissions eines online Spielers spiegelt der Client den neuen Stand **relog-frei** innerhalb weniger Sekunden (gezielter `effective`-Reload), in 100 % der Fälle für online Spieler.
- **SC-002**: Backend-Ereignisse für offline Spieler erzeugen in 100 % der Fälle **keinen** Cache-Eintrag und keinen `effective`-Aufruf.
- **SC-003**: Das optimistische Gate spiegelt bei befülltem Cache die effektive Berechtigung; ein Backend-`403` führt in 100 % der Fälle zur Ablehnung trotz optimistischer Anzeige (Backend ist Autorität).
- **SC-004**: Ein Team-Mitglied kann über `/ranks` eine Rolle anlegen und ihre Permissions pflegen sowie über `/cp <Spieler>` einem **online oder offline** Spieler einen Rang vergeben — vollständig über die Menüs, ohne Konsole/Befehlsketten.
- **SC-005**: Jede Rolle wird im Menü mit einem Icon dargestellt; `null` und unbekannte Präfixe ergeben in 100 % der Fälle das Default-Icon statt eines Fehlers oder fehlenden Eintrags.
- **SC-006**: Der Main-Thread wird durch keinen REST-/Redis-Vorgang dieses Slice blockiert (kein synchrones Warten auf Daten im Main-Thread, messbar in Last-/TPS-Beobachtung).
- **SC-007**: Das Feature wird durch genau ein neues Package + einen FeatureRegistry-Eintrag angesteckt, ohne Änderung an einer generischen Framework-Klasse.

## Assumptions

- **Backend autoritativ & erreichbar**: Branch `002-permission-rank-system` ist fertig; alle genannten Endpunkte, der Channel `mc:permission:changed` und die DTOs stehen im frisch gezogenen `plugin-protocol` bereit.
- **Generische Infrastruktur vorhanden**: FeatureCache, EventBus (Redis Pub/Sub + Reconnect), BackendClient/EndpointDescriptor, MenuBuilder/Menü-Framework, PluginFeature/FeatureRegistry und die Scheduler-Abstraktion existieren bereits (etabliert durch Economy-, Punishment- und Reports-Slice).
- **Cosmetics später**: Die spieler-sichtbare Darstellung (Chat-Prefix/Farbe, Tablist, Nametag) ist ein separater künftiger Slice; hier wird `RoleDisplay` nur gecacht und im Menü gezeigt.
- **Keine Backend-Logik hier**: Der Permission-Resolver bleibt vollständig im Backend; der Client flacht nichts selbst ab, sondern liest die fertige effektive Menge aus `effective`.
- **Keine Account-Verknüpfung Web↔UUID** in diesem Slice.
- **`actor` provisorisch**: Der handelnde Staff wird als UUID mitgeführt; eine spätere Auth-Feature wird ihn aus der Session ableiten (Contract-Hinweis), das ändert diesen Slice nicht.
- **Default-Icon**: Ein neutrales, nach MENU_DESIGN gewähltes Material dient als Fallback (genaues Material wird im Plan/Design festgelegt).

## Out of Scope

- **Spieler-sichtbare Darstellung** in Chat, Tablist, Nametag oder Scoreboard (Prefix/Farbe/Suffix). Späterer Cosmetics/Chat-Slice.
- **Permission-Resolution/-Aggregation** im Client (bleibt Backend; Client liest nur die effektive Menge).
- **Account-Verknüpfung Web↔UUID.**
- **Eigene Datenhaltung/Persistenz** im Plugin für Rollen/Vergaben (Backend ist Source of Truth; Client hält nur einen flüchtigen Live-Cache).

## Weggefallen ggü. altem Plugin (explizit)

- **NMS/Reflection** für Permissions, Köpfe oder Inventare — ersetzt durch die `platform`-Schicht.
- **`§`-Farbcodes/Legacy-Strings** — ersetzt durch Adventure-Components mit semantischen Tokens (MENU_DESIGN §5).
- **Manuelles Inventory-Handling** — ersetzt durch das Menü-Framework.
- **Eigene Daten-/Permission-Haltung** im Plugin (Configs, lokale Rang-Definitionen, eigener Resolver) — ersetzt durch das autoritative Backend + flüchtigen Live-Cache.
