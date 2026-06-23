# Contract — `display_icon` ↔ Bukkit (plugin-eigene Interpretation, zwei Richtungen)

Der opake `display_icon`-String wird **ausschließlich im Plugin** interpretiert (FR-020/021/022). Backend versteht ihn nie; das Plugin schreibt das Icon **nie** zurück (Setzen passiert im Webinterface). Zwei Richtungen teilen **eine** reine Format-Klasse `DisplayIconFormat`, damit sie nie auseinanderlaufen.

## Geteilte Konstanten (`DisplayIconFormat`)

`material:` · `head-texture:` · `head-player:` — Split am **ersten** `:`. `parse(String)` ist Bukkit-frei und unit-getestet.

## Richtung 1 — Schreiben: `IconResolver` (String → ItemStack), Feature/Menü-Schicht, eine Stelle

| Eingabe | ItemStack | Paper-API (kein NMS) |
|---|---|---|
| `material:<NAME>` | `new ItemStack(Material.valueOf(NAME))` | — |
| `head-texture:<tex>` | `PLAYER_HEAD` + `SkullMeta` mit `PlayerProfile`, Textur `ProfileProperty("textures", tex)` | `Bukkit.createProfile`/`SkullMeta.setPlayerProfile` |
| `head-player:<uuid>` | `PLAYER_HEAD` mit `PlayerProfile` der UUID | `OfflinePlayer#getPlayerProfile` / `createProfile(uuid)` |
| `null` / unbekanntes Präfix | **Fallback-Icon** (`BARRIER`/`PAPER`) | — |
| ungültiges Material / kaputte UUID / Parse-Fehler / jede Exception | **Fallback-Icon** | — |

- **Nie Crash, nie leerer Slot** (FR-021). Resultat ist ein nackter ItemStack; Menü-Name/Lore kommen über `IconSpec.ofItem(...)`.
- Genutzt im Rang-/Rollen-Menü, um pro Rolle das Icon zu zeichnen.

## Richtung 2 — Lesen: `/rank toDisplayIcon` (ItemStack → String), reines Werkzeug

- Liest das Item in der Hand des Ausführenden (`IconExtractor`) — **genau zwei Ausgaben**:
  - Textur-base64 aus `PlayerProfile`/`ProfileProperty("textures")` extrahierbar → `head-texture:<base64>`.
  - sonst (Vanilla-Item **oder** textureloser PLAYER_HEAD) → `material:<TYPE.name()>` (textureloser Kopf ⇒ `material:PLAYER_HEAD`).
  - **Kein** `head-player:<uuid>` aus dem Werkzeug (Slice-Entscheidung).
- Ausgabe als **click-to-copy** Adventure-Komponente (`ClickEvent.copyToClipboard(...)`), zum Einfügen ins Webinterface-Feld.
- **Kein Backend-Call, kein Schreibpfad.** Permission-Gate nur UI-Komfort (unkritisch, da nichts geschrieben wird).

## Fallback-Icon

Sichtbares `BARRIER` (oder `PAPER`) — klar als „kein/ungültiges Icon" erkennbar. Niemals Crash, niemals ausgelassener Eintrag.

## Forward-Compatibility

Älteres Plugin + neues, unbekanntes Backend-Präfix → Fallback-Icon (kein Crash). Ungültiger Material-Name → Fallback. Gilt ausschließlich für die Anzeige; der String bleibt im System unverändert (das Plugin schreibt ihn nicht).

## Test-Matrix

**`DisplayIconFormatTest` (PURE):**
- `material:DIAMOND_SWORD` → `Material("DIAMOND_SWORD")`
- `head-texture:eyJ0…` → `HeadTexture("eyJ0…")` (payload mit weiteren `:` bleibt ganz — nur erster Split)
- `head-player:<valid-uuid>` → `HeadPlayer(uuid)`; `head-player:not-a-uuid` → `Invalid`
- `null` / `""` / `"abc"` (kein `:`) / `banner:red` (unbekannt) / `material:` (leer) → `Invalid`

**`IconRoundTripTest` (Paper-Harness):** `resolve(extract(item))` ≈ Ausgangs-Item je Präfix (Vanilla-Material; Custom-Head behält Textur). Müll-String → Fallback-Icon.

**`RankCommandTest`:** Vanilla-Item → `material:<NAME>`; Custom-Head mit Textur → `head-texture:<base64>`; textureloser PLAYER_HEAD → `material:PLAYER_HEAD`; leere Hand → Hinweis (kein NPE).

**`MenuRendererIconTest` (Phase R0):** `IconSpec.ofItem(base, …)` → Renderer klont `base` und legt Name/Lore/Italic-aus an; `baseItem == null` → unveränderter Enum-Pfad.
