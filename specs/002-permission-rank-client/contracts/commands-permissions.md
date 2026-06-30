# Contract — Commands & Permission-Gating (Client-Surface)

## Commands (plugin.yml, additiv)

| Command | Usage | plugin.yml `permission:` | Gating |
|---|---|---|---|
| `/ranks` | `/ranks` | **keine** (kein blockierender Bukkit-Node) | Cache-basiertes `PermissionGate` im Executor (feingranular) |
| `/cp` | `/cp <Spieler>` | **keine** | Cache-basiertes `PermissionGate` im Executor |
| `/rank` | `/rank toDisplayIcon` | **keine** | Cache-basiertes `PermissionGate` (UI-Komfort; **unkritisch** — reines Lese-Werkzeug, kein Schreibpfad) |

**Warum keine `plugin.yml`-Permission:** Eine harte Command-Permission würde Bukkit `hasPermission` vor dem Executor erzwingen (default op-only) und das **cache-basierte** optimistische Gate (Spec FR-008/009, Cold-Cache neutral) aushebeln. Stattdessen prüft der Executor das Gate selbst und lässt bei Cold-Cache durch (Backend `403` ist die Wahrheit).

## Feingranulare UI-Permission-Nodes (Cache-geprüft)

| Aktion | Node | Geprüft an |
|---|---|---|
| Rollen-Verwaltung öffnen / CRUD / Rollen-Permissions | `mcplatform.permission.roles.manage` | `/ranks`-Einstieg + jeder Rollen-Schreibbutton |
| Spieler-Vergaben öffnen / Grant/Revoke | `mcplatform.permission.grants.manage` | `/cp`-Einstieg + jeder Grant/Revoke-Button |
| `display_icon`-Lese-Werkzeug | `mcplatform.permission.roles.manage` (wiederverwendet; unkritisch) | `/rank toDisplayIcon` |

- Quelle der Wahrheit für „besitzt Node": `PlayerPermissionsView.effective` (aus Backend `/effective`).
- `PermissionGate.has(uuid, node)`: Eintrag vorhanden + Node fehlt → optimistisch sperren (Feedback + Actionbar, kein Backend-Call). Eintrag fehlt (Cold-Cache) → durchlassen.
- Backend bleibt Autorität: jeder Write behandelt `403` über `PermissionFormat.error(403)`, unabhängig vom Gate.

## Gate-Verhalten je Zustand (FR-008/009/010/011)

| Cache-Zustand | Node im Set? | Gate-Ergebnis |
|---|---|---|
| kein Eintrag (gerade gejoint) | – | **neutral: durchlassen** → Backend entscheidet |
| Eintrag vorhanden | ja | zulassen |
| Eintrag vorhanden | nein | optimistisch sperren (Meldung; kein Write) |
| (jeder) | (jeder) | Write-`403` ⇒ Ablehnung + Meldung (überstimmt Gate) |
