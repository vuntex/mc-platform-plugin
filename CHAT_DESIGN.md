# Chat-Message-Design

Leitfaden für **Chat-Nachrichten** im Plugin (Pendant zu `MENU_DESIGN.md`, nur für Chat statt GUI).
Alles über **Adventure Components** — keine §-Codes. Helfer: `platform.text.ChatDesign` (Styling) +
`platform.text.Messages` (zentrale Standard-Texte).

## Grundprinzipien
1. **Prefix trägt die Identität:** Feature-Name + `>`, **alles fett und in einer Farbe**, dann der Text
   (z. B. `COINS> …`).
2. **Grau ist neutral, Farben sind semantisch** (s. Tabelle). Fließtext grau; Farbe bedeutet etwas.
3. **Konsistenz vor Variation** — überall dasselbe Schema, zentral über `ChatDesign`/`Messages`.
4. **Deutsch, Du-Form, freundlich-direkt.** Zahlen mit deutschem Tausenderpunkt (`1.234.567`).
5. **Werte hervorheben:** Beträge/Zahlen gelb, Spielernamen gold, Aktion grün, Fehler rot.

## Farb-Rollen
| Rolle | Adventure | `ChatDesign` |
|---|---|---|
| Fließtext | `GRAY` | `TEXT` |
| Wert / Zahl / Betrag | `YELLOW` | `VALUE` |
| Spielername | `GOLD` | `NAME` |
| Erfolg / positive Aktion | `GREEN` | `SUCCESS` |
| Fehler / Warnung | `RED` | `ERROR` |
| Sekundär / Trenner / Klammern | `DARK_GRAY` | `MUTED` |
| Akzent (Links, Hinweise) | `AQUA` | `ACCENT` |
| Fett (Prefix/Emphasis) | `BOLD` | — |

## Struktur
```
<NAME> (fett, eine Farbe)  <Fließtext grau, mit farbigen Werten>
```
- **Trenner/Symbole:** `>` ist Teil des Prefix (z. B. `COINS>`); `→` (von→an), `·` / `( … )` für Zusätze.
- **Alert-Symbole:** `⚠` (Warnung, rot), `✔` / `✖` (ok/abgelehnt).
- **Klickbar:** `ClickEvent.runCommand/openUrl` + `HoverEvent.showText(<kursiver Hinweis>)`.

## Zentrale Standard-Nachrichten (`Messages`)
Wiederkehrende Texte liegen zentral, damit sie überall identisch sind:
- `noPermission()` – „Dir fehlt die Berechtigung dafür."
- `playersOnly()` – „Diesen Befehl können nur Spieler nutzen."
- `playerNotFound(name)` / `playerNotOnline(name)`
- `unknownCommand()` – für unbekannte Befehle
- `backendError()` – generischer Backend-Fehler

## Beispiele
- Alert: `ECONOMY> ⚠ 75.000 Coins von Vuntex → jonas5475 · 12% des Umlaufs`
- Erfolg: `Du hast 1.000 Coins an Steve gesendet.` (Aktion grün, Zahl/Name hervorgehoben)
- Fehler: `Dir fehlt die Berechtigung dafür.` (rot)

## Zahlen / Namen
- Beträge: `ChatDesign.number(long)` → `50000` → `50.000`.
- Spielernamen in `NAME` (gold). Rang-Farbe/-Prefix im **Chat-Format** (`feature.chat`).

## Chat-Format (Rang-Prefix)
`feature.chat` rendert Spieler-Chat über Paper's `AsyncChatEvent#renderer`:
`<Rang-Prefix><Name in Rang-Farbe><grau ›> <Nachricht>`. Prefix/Farbe kommen aus dem warmen
Permission-Cache (`PermissionReadPort.currentDisplay`); fehlt etwas, wird neutral (weiß) gerendert.
Prefix darf Legacy-`&`/§-Codes enthalten (werden geparst).

## Verwendung
```java
import static com.mcplatform.plugin.platform.text.ChatDesign.*;
Component msg = prefix("Economy", ERROR)
    .append(value("75.000 COINS")).append(text(" von ")).append(name("Vuntex"));
```
`prefix(name, color)` setzt `name>` (alles fett, eine Farbe); alle weiteren `append(...)` erben **kein** Fett.

## Action Bar (`platform.ActionBars`)
Kurze, **transiente Toasts** in der Hotbar-Zeile, **immer mit passendem Sound** gepaart — für sofortiges
„hat geklappt / hat nicht geklappt". One-shot (kein Refresh, der Client blendet nach ein paar Sekunden
aus), last-writer-wins.
- `ActionBars.success(player, component)` – heller Pickup-Ding.
- `ActionBars.error(player, component)` – tiefer „Nein"-Ton.
- `ActionBars.info(player, component)` – leiser Klick.

**Wann Chat, wann Action Bar?**
- **Action Bar:** kurze, transiente Einzeiler (Validierungsfehler, kleine Bestätigungen) — z. B. `/pay`
  „Ungültiger Betrag", „nicht genug Coins", „nicht gefunden".
- **Chat:** Datensätze, mehrzeilige oder klickbare Nachrichten (Transfers/Quittungen, Bestätigungs-Prompts,
  Alerts, Broadcasts).
- **Persistenter Status** (Sonderfall, kein One-shot): bewusst pro Tick neu gesendete Action Bar — z. B.
  die Staff-Anzeige im Wartungsmodus. Das ist Absicht, nicht das Toast-Muster.

## Migration
Neue Nachrichten folgen direkt diesem Schema. Bestehende werden schrittweise auf `ChatDesign`/`Messages`
umgestellt.
