# MENU_DESIGN.md

**Stil-Brief für alle Inventar-Menüs des MineChaos-Plugins (1.21, Adventure).**

Dieses Dokument beschreibt **Design und UX** – nicht die Implementierung. Es ist die verbindliche
Referenz, die jedes künftige Feature-Menü einhält, und der fachliche Input für das Menü-Framework
in der `platform`-Schicht. Es enthält bewusst keinen Code und keine veraltete 1.8.9-Technik
(NMS, Reflection, `§`-Farbcodes, Material-Durability-Tricks). Wo unten ein Slot, eine Farbe oder
ein Verhalten festgelegt wird, ist das eine **Konvention**, kein technischer Hinweis.

---

## 1. Design-Prinzipien

Unsere Menüs sind **ruhig, symmetrisch und vorhersehbar**. Jeder Bildschirm hat einen klaren
Rahmen, ein zentrales Kontext-Item ganz oben in der Mitte und einen festen Platz für Navigation
unten. Ein Spieler, der ein Menü kennt, kennt alle: „Zurück" ist immer links unten, „Schließen"
immer unten in der Mitte, der Bestätigen-Knopf immer grün, der Abbrechen-Knopf immer rot. Inhalt
steht zentriert, nie gegen einen Rand gedrückt. Leere Slots sind nie „kaputt" wirkende Lücken,
sondern bewusst gefüllter Rahmen.

Zweitens sind unsere Menüs **selbsterklärend durch Sprache, nicht durch Raten**. Jedes
interaktive Item endet mit einem Hinweis, was ein Klick bewirkt („Klicke, zum Öffnen."). Farbe
trägt Bedeutung: Grün heißt immer bestätigen/aktivieren, Rot immer abbrechen/deaktivieren, Gold
hebt konkrete Werte und Spielernamen hervor. Diese Konsistenz ist wichtiger als optische
Abwechslung – ein Menü darf langweilig aussehen, solange es nie überrascht.

---

## 2. Layout-System

### 2.1 Standard-Größen

| Größe | Zeilen | Verwendung |
|-------|--------|------------|
| **54** | 6 | **Standard.** Haupt-, Listen-, Options- und Detail-Menüs. Im Zweifel diese Größe. |
| **27** | 3 | Schlanke Dialoge mit genau einer Entscheidung (Bestätigung, einfache Auswahl). |
| 9 / 18 / 36 / 45 | 1–5 | Nur in begründeten Ausnahmen. Nicht der Normalfall. |

Regel: **Inhaltsmenüs sind 6 Zeilen.** Unter 6 Zeilen gehen wir nur bei echten Mini-Dialogen.
Eine Liste mit zwei Einträgen bekommt trotzdem 6 Zeilen – Größe folgt dem Menü-*Typ*, nicht der
momentanen Datenmenge.

### 2.2 Rahmen-Muster (Border)

- Der Rahmen besteht aus einem **neutralen Füller-Item** (siehe §3, „Füller") mit leerem Namen.
- **6-Zeilen-Menüs:** Die **oberste Zeile** (Slots 0–8) ist immer Rahmen. Die Seitenränder und
  die unterste Zeile werden je nach Menü-Typ gefüllt (Listen: voller Seitenrahmen; einfache
  Menüs: nur oben + unten).
- **3-Zeilen-Dialoge:** **voller Rahmen** rundherum – die komplette obere und untere Zeile sowie
  die beiden Rand-Slots der Mittelzeile. Nur die innere Zeile trägt Inhalt.
- Der Füller ist neutral (grau). Eine **Akzentfarbe der obersten Zeile** kodiert optional die
  Stimmung des Menüs (siehe §3.4).

### 2.3 Slot-Konventionen (feste Plätze)

Diese Plätze sind reserviert und werden in keinem Menü anders belegt:

| Rolle | 54er-Menü | 27er-Dialog | Pflicht? |
|-------|-----------|-------------|----------|
| **Header / Kontext-Item** | Slot **4** | Slot **4** | **Immer.** Erklärt „Was ist dieses Menü". Standardmäßig nicht klickbar (nur Feedback-Ton). **Darf** in einzelnen Menüs interaktiv sein (z. B. „klicke für Details"). |
| **Zurück** (zum Eltern-Menü) | Slot **48** | Slot **18** | Wenn ein Eltern-Menü existiert. |
| **Schließen** | Slot **49** | Slot **22** | Immer (sofern kein Zurück, dann zentral). |
| **Seite zurück** (Paginierung) | Slot **45** | – | Nur paginierte Menüs, nur wenn Vorseite existiert. |
| **Seite vor** (Paginierung) | Slot **53** | – | Nur paginierte Menüs, nur wenn Folgeseite existiert. |
| **Footer-Zone** | Slots 46, 47, 50, 51, 52 | – | **Vorerst leer (Rahmen).** Reserviert für globale Buttons (Shop, Support, Status), die später wieder eingeführt werden. Features belegen diese Slots **nicht**. |

> **Wichtig zu „Zurück" vs. „Seite zurück":** Das sind **zwei verschiedene Konzepte mit zwei
> verschiedenen Icons**. „Zurück" verlässt das aktuelle Menü Richtung Eltern-Menü (Icon: Tür /
> Rücksprung-Pfeil, neutraler Ton). „Seite zurück" blättert innerhalb derselben Liste (Icon:
> Links-Pfeil). Sie dürfen sich optisch nie gleichen und liegen auf verschiedenen Slots (48 vs.
> 45).

### 2.4 Inhalts- & Navigationszonen

```
54er-Standardmenü (6 Zeilen)            54er-paginiertes Menü (7×4-Raster)
 0  1  2  3 [4] 5  6  7  8   ← Border    0  1  2  3 [4] 5  6  7  8   ← Border + Header
 9 10 11 12 13 14 15 16 17               9 ░░ ░░ ░░ ░░ ░░ ░░ ░░ 17   ← Rand | Inhalt | Rand
18 .. Inhaltsbereich (9–44) .. 26       18 ░░ ░░ ░░ ░░ ░░ ░░ ░░ 26
27 ............................ 35      27 ░░ ░░ ░░ ░░ ░░ ░░ ░░ 35
36 ............................ 44      36 ░░ ░░ ░░ ░░ ░░ ░░ ░░ 44
45[◄][ ][ ][48←][49✕][ ][ ][ ]53►       45[◄] ......nav...... [49✕] ..[53►]
```

- **Zentrierung:** Inhalt wird symmetrisch um die Mittelspalte (Slots 4 → 13 → 22 → 31 → 40 → 49)
  angeordnet. Paarige Aktionen liegen gespiegelt links/rechts der Mitte (z. B. „−" links, „+"
  rechts vom Wert).
- **Einzelne Aktionsitems** in nicht-paginierten Menüs: zentriert in der zweiten/dritten Reihe
  platzieren (bevorzugte Plätze: 22 als Zentrum, dann 20/24, dann 21/22/23 usw.), nie an den
  oberen Rand gedrückt.

### 2.5 Bestätigungs-Dialog (Referenz-Layout)

Der Confirm-Dialog ist der wichtigste fixe Bauplan und gilt als Vorlage:

```
27er-Dialog (3 Zeilen)
 0  1  2  3 [4] 5  6  7  8     Border, Slot 4 = Objekt der Bestätigung
 9 10[11]12 13 14[15]16 17     Slot 11 = Bestätigen (grün), Slot 15 = Abbrechen (rot)
18 19 20 21 22 23 24 25 26     Slot 18 = Zurück (falls Eltern), Slot 22 = Schließen
```

- **Bestätigen:** Slot **11**, grünes Icon, Titel „Bestätigen", Lore beschreibt die Konsequenz.
- **Abbrechen:** Slot **15**, rotes Icon, Titel „Abbrechen".
- **Objekt der Aktion:** Slot **4** zeigt, *worauf* sich die Bestätigung bezieht (z. B. der zu
  löschende Clan, der zu bannende Spieler).
- **Rest:** voller Rahmen.
- **Zweistufige Sicherung:** Bei kritischen, irreversiblen Aktionen (Löschen, Bann, Zurücksetzen)
  verlangt der Bestätigen-Knopf einen **Doppelklick**; der Lore-Hinweis lautet dann
  „Doppelklicke, zum Bestätigen." statt „Klicke, …". Standard-Bestätigungen bleiben einfacher
  Klick.

---

## 3. Item- & Icon-Sprache

### 3.1 Bedeutung → Icon (Material-Mapping)

Icons werden **nach Bedeutung gewählt, nicht nach Hübschheit**. Diese Tabelle ist die
Soll-Vorgabe; sie nennt das semantische Konzept und ein passendes 1.21-Material. Wo ein echtes
Custom-Icon sinnvoller ist, siehe §5.3.

| Bedeutung | Icon (1.21-Material) |
|-----------|----------------------|
| Füller / Rahmen | `GRAY_STAINED_GLASS_PANE` (leerer Name) |
| Schließen | `BARRIER` |
| Zurück (Eltern-Menü) | Rücksprung-Icon (Custom-Head „arrow-back" / `OAK_DOOR`) |
| Seite zurück / vor | Pfeil-Icons (Custom-Heads „arrow-left" / „arrow-right") |
| Bestätigen | grünes Icon (Custom-Head „check" / `LIME_DYE`) |
| Abbrechen | rotes Icon (Custom-Head „cross" / `RED_DYE`) |
| Wert erhöhen / verringern | „+/−"-Icons (Custom-Heads „plus" / „minus") |
| Gesperrt / nicht freigeschaltet | `IRON_BARS` |
| Spieler | `PLAYER_HEAD` (Skin des Spielers) |
| Info / Statistik | `BOOK` |
| Verwaltung / Bearbeiten | `WRITABLE_BOOK` |
| Verlauf / Logs | `BOOK` |
| Texteingabe / Wertanzeige | `OAK_SIGN` |
| Lagerung / Anfragen-Posteingang | `CHEST` |
| Clan-/Gruppen-Identität | `WHITE_BANNER` (mit Muster/Farbe) |
| Geschützter Truhen-Inhalt | `ENDER_CHEST` |
| Einladen / Beitreten | `OAK_DOOR` bzw. `IRON_HELMET` |
| Toggle „an/aus" | Icon, dessen Material/Variante den Zustand spiegelt (z. B. Dye grün vs. grau) |
| Teleport | `BEACON` |
| Gefahr / Bestrafung | `IRON_SWORD` (Attribute ausblenden) |
| Hinzufügen (Eingabe öffnen) | `ANVIL` |
| Globale / serverweite Einstellung | `COMMAND_BLOCK` / `COMPARATOR` |

Wenn eine neue Bedeutung kein passendes Mapping hat: ein bestehendes Konzept wiederverwenden, das
ihm am nächsten kommt, statt ein beliebiges Material zu greifen. Neue Einträge gehören in diese
Tabelle.

### 3.2 Titel-Konventionen

- **Menü-Titel:** Kurz, beschreibend, **fett**, in der Sektionsfarbe (§3.4). Format: ein Substantiv
  oder eine kurze Phrase – „Clan ChaosKrieger", „Globale Einstellungen", „Freunde". Kein
  abschließendes Satzzeichen.
- **Live-Werte im Titel** sind erlaubt und erwünscht, wenn ein Menü einen zentralen veränderlichen
  Wert hat (z. B. „Anzahl: 5"). Ändert sich der Wert durch Interaktion, **aktualisiert sich der
  Titel** (siehe §6 Wert-Editor).
- **Item-Namen:** Standardmäßig **weiß + fett**. Akzentfarbe-fett nur, wenn das Item eine besondere
  Rolle hat (grün für „Bestätigen", rot für „Abbrechen", die Sektionsfarbe für das Header-Item).

### 3.3 Lore-Aufbau

Lore folgt **immer** dieser Reihenfolge, von oben nach unten:

```
1.  Beschreibung        (1–3 Zeilen, neutral/grau: was ist das, was bewirkt es)
2.  (Leerzeile)
3.  Aktuelle Werte       (optional: "Status: …", "Slots: …" – Wert in Gold/Akzent)
4.  (Leerzeile)
5.  Aktions-Hinweis      (letzte Zeile, immer vorhanden bei klickbaren Items)
```

**Der Aktions-Hinweis** ist die strengste Konvention im ganzen System:

- Aufbau: `<Klick-Art>` (in **Cue-Farbe**, §3.4) + Bindetext (neutral) + `<Verb>` (grün für
  positiv, rot für destruktiv) + abschließender Punkt.
- Beispiele (sprachlich, nicht farbcodiert dargestellt):
  - „**Klicke**, zum **öffnen**."
  - „**Rechtsklick**, um Spieler **hinzuzufügen**."
  - „**Linksklick**: +5 · **Rechtsklick**: −5 · **Droppe** für eigene Anzahl."
  - „**Doppelklicke**, zum **bestätigen**." (kritische Aktionen)
- Hat ein Item mehrere Klick-Arten, bekommt **jede ihre eigene Hinweiszeile**. Der Spieler muss
  nie raten, was Rechtsklick tut.
- Nicht klickbare Anzeige-Items (z. B. das Header-Item, reine Wertanzeigen) haben **keinen**
  Aktions-Hinweis.

### 3.4 Farb-/Ton-Sprache (semantische Tokens)

Farben werden als **benannte Tokens** verwendet, nicht als beliebige Werte. Das Framework
definiert sie einmal zentral (als Adventure-Styles, siehe §5); Features referenzieren das Token,
nie einen rohen Farbwert. Die Hex-Werte sind die 2026er-Festlegung.

| Token | Bedeutung | Farbe |
|-------|-----------|-------|
| `title` | Item-Name (Standard) | Weiß `#FFFFFF`, fett |
| `body` | Beschreibungstext | Grau `#AAAAAA` |
| `cue` | Klick-Art im Aktions-Hinweis | Aqua `#00AAAA` |
| `positive` | bestätigen / aktivieren / online | Grün `#55FF55` |
| `negative` | abbrechen / deaktivieren / Fehler / offline | Rot `#FF5555` |
| `danger` | irreversible Aktion (Löschen, Bann) | Dunkelrot `#AA0000` |
| `entity` | Spielernamen, konkrete Werte, „ändern" | Gold `#FFAA00` |
| `special` | Premium/geschützt (Clan-Kiste, globale Settings) | Lila `#AA00AA` |
| `info` | Info-/Statistik-Header | Hellblau `#55FFFF` |
| `muted` | Bullet/Verweis-Zeilen (z. B. Links) | Dunkelgrau `#555555` |

**Sektionsfarben** (Menü-Titel + optionale Top-Border-Akzentfarbe) kodieren das Thema:
- Neutral / allgemein → `body`-grau oder dezenter Akzent
- Gefahr / Bestätigung → `negative`/`danger`-rot
- Premium / Verwaltung → `special`-lila
- Community (Clan, Freunde) → `entity`-gold bzw. `info`/Pink-Akzent

Faustregel der Trennung: **Titel = weiß-fett, Wert = grau mit Gold-Akzent, Hinweis = Aqua-Cue.**
Diese drei Ebenen sind in jedem Item visuell unterscheidbar.

---

## 4. Interaktions-Muster

### 4.1 Bestätigung (Confirm)
Eigenständiger 27er-Dialog nach dem Layout aus §2.5. Grün/Slot 11 = ausführen, Rot/Slot 15 =
abbrechen, Objekt in Slot 4. Kritische Aktionen erfordern Doppelklick. Nach Bestätigung kehrt der
Flow zum auslösenden Menü oder zum Eltern-Menü zurück, mit kurzer Erfolgsmeldung (§4.5).

### 4.2 Toggle (an/aus)
Ein einzelnes Item repräsentiert beide Zustände. Konvention:
- Die Lore zeigt den **aktuellen Zustand** als Wertzeile („Status: aktiv").
- Der Aktions-Hinweis nennt das **Gegenteil** des aktuellen Zustands, farblich passend: ist es
  aktiv → „Klicke, zum **deaktivieren**." (rot); ist es inaktiv → „Klicke, zum **aktivieren**."
  (grün).
- Optisch spiegelt das Icon den Zustand (z. B. grünes vs. graues Dye), nicht nur der Text.
- Feedback: Aktivieren = aufsteigender Ton, Deaktivieren = absteigender Ton (§4.5).

### 4.3 Mehrfach-Klick-Items
Ein Item kann Links-, Rechts-, Shift- und Drop-Klick unterschiedlich belegen (z. B. Wert ±,
Eingabe öffnen, Untermenü öffnen). **Bedingung:** jede belegte Klick-Art hat ihre eigene
Lore-Hinweiszeile. Nicht belegte Klick-Arten tun nichts (kein versehentliches Auslösen).

### 4.4 Paginierung
- **Raster:** 7 Spalten × 4 Reihen = **28 Einträge pro Seite** im Inhaltsbereich eines
  54er-Menüs (Slots 10–16, 19–25, 28–34, 37–43), mit Rahmen ringsum. *(Begründung: gleichmäßiges,
  zentriertes Raster mit Rand; genug Dichte, ohne den Rahmen-Charakter zu verlieren.)*
- **Blättern:** „Seite zurück" Slot 45, „Seite vor" Slot 53. Ein Pfeil wird **nur angezeigt, wenn
  es die jeweilige Seite gibt** – keine ausgegrauten Dummy-Pfeile, keine toten Klicks.
- **Zustand:** Beim erneuten Öffnen einer Liste startet sie auf Seite 1. Blättern wechselt nur den
  Inhaltsbereich; Header, Rahmen und Navigation bleiben stehen.
- Leere Liste: Inhaltsbereich bleibt Rahmen/leer; ein dezentes Hinweis-Item in der Mitte
  („Keine Einträge vorhanden.") statt eines kaputt wirkenden leeren Rasters.

### 4.5 Klick-Feedback
Jeder bedeutsame Klick gibt sofortiges Feedback – primär als **Ton**, sekundär als **Actionbar**:
- **Erfolg** (Aktion ausgeführt) → heller Bestätigungs-Ton.
- **Fehler / nicht erlaubt** (z. B. Untergrenze erreicht, keine Berechtigung) → tiefer Fehlerton +
  Actionbar-Erklärung („Die Slots können nicht unter 1 sein.").
- **Hinzufügen / Aktivieren** → aufsteigender Ton. **Entfernen / Deaktivieren** → absteigender Ton.
- **Neutraler Navigations-Klick** (Menü öffnen/wechseln) → leiser neutraler Klick-Ton.
- **Klick auf nicht-interaktives Item** (Header, Anzeige) → dezenter neutraler Ton, sonst nichts.
- Transiente Rückmeldungen laufen über die **Actionbar**, nicht über den Chat. Der Chat wird nur
  für dauerhafte/wichtige Bestätigungen genutzt.

### 4.6 Eingabe-Flows
Für Wert-/Texteingaben gilt eine klare Rangfolge (2026er-Festlegung):
- **Primär: Amboss-Eingabe (Anvil-GUI).** Für kurze Eingaben – Zahlen, Namen, einzeilige Texte.
  Der Flow bleibt im GUI-Kontext, kein Wechsel in den Chat. Eingabe wird validiert; bei
  ungültiger Eingabe bleibt der Amboss offen + Fehler-Feedback (§4.5). Erfolg → zurück ins
  aufrufende Menü.
- **Sekundär: Schild-Eingabe.** Optional für mehrzeilige Kurztexte, wo das visuell passt.
- **Fallback: Chat-Eingabe (Conversation).** Nur für längere/mehrzeilige Inhalte (z. B. mehrzeilige
  MoTD), bei denen Amboss/Schild zu eng sind. Muss eine **Abbruch-Eskape** anbieten (klares
  „brich mit … ab") und nach Abschluss/Abbruch automatisch ins Menü zurückführen.
- **Wert-Editor (kein Freitext nötig):** zentrale Wertanzeige (`OAK_SIGN`) flankiert von „−"
  (links) und „+" (rechts); optional „Droppe für eigene Anzahl" → Amboss. Der Wert wird sowohl im
  Item als auch im **Menü-Titel** live aktualisiert. Grenzen lösen Fehler-Feedback aus.

---

## 5. Adventure- / 1.21-Vorgaben

So bauen wir Menüs 2026 – verbindlich:

### 5.1 Components statt Farbcodes
- **Alle** Titel, Item-Namen und Lore-Zeilen sind **Adventure-`Component`s**. `§`-Codes,
  `ChatColor` und Legacy-Strings sind **verboten**.
- Farben kommen ausschließlich aus den **semantischen Tokens** (§3.4), zentral als Adventure-Styles
  definiert. Ein Feature schreibt „Token `positive`", nie einen rohen Hex- oder Farbnamen.
- **Italic-Default abschalten:** Item-Namen und Lore explizit ohne Kursiv rendern (Adventure setzt
  bei Custom-Namen sonst Kursiv). Das ist Teil der zentralen Style-Definition, nicht Sache jedes
  Features.
- Reichere Palette: Wir dürfen die vollen RGB-Werte der Tokens nutzen – die alten 16 Codes sind
  keine Grenze mehr. Trotzdem bleibt die **Anzahl der Tokens klein und semantisch**; kein
  Farb-Wildwuchs.

### 5.2 Hover & Klick-Möglichkeiten
- **Hover:** Detail-Informationen, die die Lore aufblähen würden, können als **Hover-Text** an
  einem Item-Namen hängen (z. B. vollständige Statistik beim Überfahren). Lore bleibt damit kurz
  und auf das Wesentliche fokussiert.
- **Klick-Events** an Components nutzen wir für **Chat-/Prompt-Nachrichten** (z. B. anklickbarer
  „ändern"-Vorschlag bei Texteingabe, Discord-/TS-Links). **Innerhalb** eines Inventars werden
  Klicks weiterhin über das Menü-Framework verarbeitet, nicht über Component-Klick-Events.

### 5.3 Icon-Optionen (CustomModelData / item_model)
- Semantische Icons, die es als Vanilla-Material nicht sauber gibt (Pfeile, Häkchen, Kreuz, +/−,
  Marken-Symbole), werden über **`CustomModelData` bzw. die `item_model`-Component** auf einem
  Basis-Item realisiert. Ziel: ein **konsistentes, eigenes Icon-Set** ohne externe
  HeadDatabase-Abhängigkeit und ohne Durability-Tricks.
- Jedes semantische Icon hat eine **stabile Kennung** (z. B. „icon.arrow-left", „icon.check"), die
  zentral registriert ist. Features referenzieren die Kennung, nie eine rohe Modell-Nummer.
- **Spielerköpfe** (`PLAYER_HEAD` mit Skin) bleiben das Mittel der Wahl für „dieser konkrete
  Spieler". Generische Custom-Heads als Icons sind durch CustomModelData abgelöst.
- **Materialnamen sind 1.21-Namen** (geflasht): kein `INK_SACK:8`, sondern `*_DYE`; kein
  `SKULL_ITEM:3`, sondern `PLAYER_HEAD`; usw. Durability als Bedeutungsträger ist abgeschafft.

### 5.4 Titel-Updates
- Menü-Titel können sich zur Laufzeit ändern (Live-Werte, §3.2) über die nativen Adventure-/
  Paper-Mittel. Kein Paket-Gebastel.

---

## 6. Live- vs. Static-Menüs

Jedes Menü ist entweder **STATIC** oder **LIVE**. Das ist eine bewusste Entscheidung pro Menü, kein
Zufall.

### STATIC (Default)
- Das Menü zeigt den Datenstand vom **Moment des Öffnens**. Ändern sich die zugrunde liegenden
  Daten danach im Hintergrund, bleibt die Anzeige stehen, bis der Spieler das Menü neu öffnet oder
  durch eine eigene Aktion ein gezieltes Neu-Rendern auslöst.
- **Wann STATIC:** Konfigurations- und Bearbeitungs-Menüs, Auswahllisten, alles, dessen Daten sich
  primär durch die eigene Interaktion des Spielers ändern.
- Vorteil: einfach, kein Beobachtungs-Overhead.

### LIVE (opt-in)
- Das Menü **abonniert Änderungen** an den für es relevanten Daten und rendert **nur die
  betroffenen Slots** neu, **solange es offen ist**. Beim Schließen wird das Abo **sauber
  abgemeldet** (kein Leak, keine hängende Beobachtung).
- Die Datenänderungen kommen über **denselben bestehenden Pub/Sub-Pfad** wie bei Economy und
  Punishments (FeatureCache, gefüttert per EventBus) – **kein neuer Mechanismus**. Ein LIVE-Menü
  ist ein Konsument dieses vorhandenen Stroms.
- **Wann LIVE:** Menüs, die sich durch *fremde* Ereignisse in Echtzeit ändern sollen – z. B. ein
  Kontostand, der durch eine Transaktion woanders steigt; eine Anfragen-Liste, in die gerade ein
  neuer Eintrag eintrifft; ein Spieler-Status (online/offline), der sich live aktualisiert.
- **Regeln für LIVE:**
  - Nur **betroffene Slots** neu rendern, nie das ganze Inventar neu aufbauen (kein Flackern, kein
    Cursor-Reset).
  - Updates am Inventar laufen **im Main-Thread**; das Datenholen davor async.
  - Header, Rahmen und Navigation sind in der Regel statisch; live ist nur der Datenanteil.

### Threading-Grundsatz (gilt für beide)
- **Inventar-Operationen immer im Main-Thread.** Das **Daten-Holen** (REST über den BackendClient)
  läuft **async**; danach wird der Menü-Aufbau bzw. das Slot-Update via Scheduler zurück in den
  Main-Thread gereicht. **Der Main-Thread wird nie blockierend auf Daten gewartet.**
- Solange Daten noch laden, darf ein Menü mit Platzhaltern/Lade-Items öffnen und die betroffenen
  Slots nachziehen, sobald die Daten da sind – das ist der Normalfall, kein Sonderweg.

---

## 7. Checkliste für ein neues Feature-Menü

Ein Menü ist „konventionskonform", wenn:

- [ ] Größe 54 (oder 27 für einen echten Mini-Dialog).
- [ ] Oberste Zeile ist Rahmen; Header-/Kontext-Item liegt auf Slot 4.
- [ ] „Zurück" auf 48 (falls Eltern-Menü), „Schließen" auf 49 – mit den richtigen, getrennten Icons.
- [ ] Paginierte Liste nutzt das 7×4-Raster; Seiten-Pfeile auf 45/53, nur wenn die Seite existiert.
- [ ] Footer-Zone (46,47,50,51,52) bleibt vorerst Rahmen – keine Feature-Buttons dort.
- [ ] Alle Texte sind Adventure-Components mit semantischen Tokens; kein `§`, kein `ChatColor`,
      Italic aus.
- [ ] Jedes klickbare Item endet mit einem Aktions-Hinweis; jede belegte Klick-Art hat ihre Zeile.
- [ ] Icons folgen der Bedeutungs-Tabelle (§3.1) bzw. dem registrierten Custom-Icon-Set.
- [ ] Kritische Aktionen laufen über den Confirm-Dialog (Doppelklick bei irreversibel).
- [ ] Klick-Feedback (Ton + ggf. Actionbar) ist vorhanden.
- [ ] STATIC vs. LIVE ist bewusst gewählt; LIVE meldet sich beim Close sauber ab.
- [ ] Datenholen async, Inventar-Operationen im Main-Thread; Main nie blockiert.
