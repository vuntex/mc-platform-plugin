# Data Model — Permission-/Rank-System Plugin-Slice (Phase 1)

Alle Typen sind **feature-lokal** und Bukkit-frei (außer wo Bukkit für Auflösung/Rendering nötig ist). Das Backend bleibt Source of Truth; persistiert wird nichts.

## Consumed Contract Types (aus `com.mcplatform.protocol.permission`, unverändert)

- `PlayerPermissionsResponse(UUID player, List<ActiveGrant> roles, List<ActiveGrant> permissions, List<String> effectivePermissions, RoleDisplay display)` — Antwort von `EFFECTIVE` und allen Grant-/Revoke-Schreibcalls.
- `ActiveGrant(String label, Long expiresAtEpochMilli, UUID issuedBy, String reason)` — `expiresAtEpochMilli == null` = permanent.
- `RoleDisplay(String displayName, String color, String prefix, String suffix, String tabListColor, String tabListIcon, String displayIcon)` — `displayIcon` ist der **hier** zu interpretierende opake String.
- `RoleResponse(long id, String name, String displayName, String color, String prefix, String suffix, String tabListColor, String tabListIcon, String displayIcon, int weight, boolean teamRank, boolean active, boolean isDefault, List<String> permissions)`.
- `RoleRequest(name, displayName, color, prefix, suffix, tabListColor, tabListIcon, displayIcon, int weight, boolean teamRank, boolean active, UUID actor)` — Create/Update; `isDefault` nie über API.
- `RolePermissionRequest(String permission, UUID actor)`; `GrantRoleRequest(long roleId, Long expiresInSeconds, String reason, UUID actor)`; `GrantPermissionRequest(String permission, Long expiresInSeconds, String reason, UUID actor)`; `RevokePermissionRequest(String permission, String reason, UUID actor)`.
- `PermissionChangedEvent(UUID playerUuid, String changeType, long timestampEpochMilli)`; `PermissionChannels.CHANGED` = `mc:permission:changed`; `PermissionChangedEventCodec.INSTANCE`.

## Feature-lokale Typen

### PlayerPermissionsView (Cache-Wert)
```
record PlayerPermissionsView(Set<String> effective, RoleDisplay display)
```
- Abgeleitet aus `PlayerPermissionsResponse`: `effective = Set.copyOf(effectivePermissions)`, `display = display`.
- Immutable; im `FeatureCache<UUID, PlayerPermissionsView>`. **Validierung:** `effective` nie null (leeres Set bei fehlend).

### PermissionCache (dünner Wrapper)
- Hält `FeatureCache<UUID, PlayerPermissionsView>` (generisch, unverändert).
- `void apply(UUID, PlayerPermissionsView, long version)` → `cache.put`.
- `Optional<PlayerPermissionsView> get(UUID)`; `void evict(UUID)` → `cache.remove`.

### DisplayIconFormat (SHARED, PURE — eine Quelle der Wahrheit für beide Richtungen)
```
final class DisplayIconFormat
  static final String MATERIAL_PREFIX     = "material:"
  static final String HEAD_TEXTURE_PREFIX = "head-texture:"
  static final String HEAD_PLAYER_PREFIX  = "head-player:"
  static Parsed parse(String displayIcon)        // pure
  static String material(String materialName)    // -> "material:" + name
  static String headTexture(String base64)       // -> "head-texture:" + base64

  sealed interface Parsed permits Material, HeadTexture, HeadPlayer, Invalid
    record Material(String materialName)
    record HeadTexture(String texture)   // base64
    record HeadPlayer(UUID uuid)
    record Invalid()                     // null / kein ':' / unbekannt / kaputtes payload / kaputte UUID
```
- `parse`: am **ersten** `:` splitten; bekanntes Präfix + nicht-leeres payload → passender Record (`head-player` zusätzlich UUID-parsebar, sonst `Invalid`); sonst `Invalid`.
- Bukkit-frei → 100 % unit-testbar. **Beide** Richtungen (Resolver, Extractor) referenzieren ausschließlich diese Konstanten/Helfer → kein Drift.

### IconResolver (Feature, Bukkit — String → ItemStack, EINE Stelle)
- `ItemStack resolve(String displayIcon)` über `DisplayIconFormat.parse`:
  - `Material` → `new ItemStack(Material.valueOf(name))`; `IllegalArgumentException` → Fallback-Icon.
  - `HeadTexture` → `PLAYER_HEAD`; `SkullMeta` mit `PlayerProfile`, Textur via `ProfileProperty("textures", base64)` (Paper-API, kein NMS).
  - `HeadPlayer` → `PLAYER_HEAD` mit `PlayerProfile` der UUID.
  - `Invalid`/jede Exception → **sichtbares Fallback-Icon** (`BARRIER` oder `PAPER`), nie Crash, nie leerer Slot (FR-021).
- Liefert einen **nackten** `ItemStack` (Material/Profil gesetzt, ohne Menü-Name/Lore); das Menü hängt Name/Lore über `IconSpec.ofItem(...)` an.

### IconExtractor (Feature, Bukkit — ItemStack → String, für `/rank toDisplayIcon`)
- `String toDisplayIcon(ItemStack inHand)` — genau zwei Ausgaben:
  - Textur-base64 aus `PlayerProfile`/`ProfileProperty("textures")` extrahierbar → `DisplayIconFormat.headTexture(base64)`.
  - sonst (Vanilla **oder** textureloser PLAYER_HEAD) → `DisplayIconFormat.material(inHand.getType().name())`.
  - Kein `head-player:<uuid>` aus dem Werkzeug (Slice-Entscheidung).
- Reine Lese-Operation; **kein** Backend-Call, **kein** Schreiben des Icons.

### Menü-Einbindung
- `RoleListMenu`/`PlayerGrantsMenu`: `IconSpec.ofItem(iconResolver.resolve(role.displayIcon()), MenuText.name(...), Lore...)` → `MenuItem`. Das Default-/Fallback-Verhalten liegt vollständig im `IconResolver`.

### PermissionGate
- `boolean has(UUID, String node)` → siehe research §Gate (Cold-Cache → `true`).
- Reine Logik über `PermissionCache`; unit-testbar mit einem Fake-Cache.

### PermissionFormat (PURE, DE)
- Rollen-Label/Weight/teamRank-Marker, Grant-Ablauf („permanent" / „läuft ab in …"), Grund.
- `error(int statusCode) → MenuMessage/Component`: 403 „keine Berechtigung", 404 „nicht gefunden", 409 „Konflikt/veraltet", 422 „ungültig", 429 „zu schnell — bitte warten", 5xx „Backend-Fehler".

## DTO-Mapping (Aktion → Endpunkt → Antwort)

| UI-Aktion | Endpoint | Request | Antwort → Verwendung |
|---|---|---|---|
| `/ranks` Liste | `LIST_ROLES` (GET) | – | `RoleResponse[]` → `RoleListMenu` (Icon je Rolle) |
| Rolle anlegen | `CREATE_ROLE` (POST) | `RoleRequest(actor)` | `RoleResponse` |
| Rolle bearbeiten | `UPDATE_ROLE` (PUT `{id}`) | `RoleRequest(actor)` | `RoleResponse` |
| Rolle löschen | `DELETE_ROLE` (DELETE `{id}`) | – (actor als Query) | `Void` → ConfirmDialog.critical() |
| Rollen-Perm + | `ADD_ROLE_PERMISSION` (POST `{id}/permissions`) | `RolePermissionRequest` | `RoleResponse` |
| Rollen-Perm − | `REMOVE_ROLE_PERMISSION` (DELETE `{id}/permissions`) | `RolePermissionRequest` | `RoleResponse` |
| `/cp` Spielerstand | `EFFECTIVE` (GET `{uuid}/effective`) | – | `PlayerPermissionsResponse` → `PlayerGrantsMenu` |
| Rang vergeben | `GRANT_ROLE` (POST `{uuid}/roles`) | `GrantRoleRequest` | `PlayerPermissionsResponse` |
| Rang entziehen | `REVOKE_ROLE` (DELETE `{uuid}/roles/{roleId}`) | – (actor als Query) | `PlayerPermissionsResponse` |
| Perm vergeben | `GRANT_PERMISSION` (POST `{uuid}/permissions`) | `GrantPermissionRequest` | `PlayerPermissionsResponse` |
| Perm entziehen | `REVOKE_PERMISSION` (DELETE `{uuid}/permissions`) | `RevokePermissionRequest` (Body!) | `PlayerPermissionsResponse` |

Schreibcalls liefern `PlayerPermissionsResponse` → das `/cp`-Menü re-rendert STATIC die betroffenen Slots aus der Antwort (kein zweiter GET nötig).

## Cache-Lebenszyklus

- **Join** (`PlayerJoinEvent`): `PermissionLoader.load(uuid, System.currentTimeMillis())` async → `apply`.
- **Event** (`mc:permission:changed`, online): `reload(uuid, event.timestampEpochMilli())` async → `apply` (version-aware).
- **Event** (offline): ignorieren.
- **Quit** (`PlayerQuitEvent`): `evict(uuid)`.

`actor` (Staff-UUID) = `sender.getUniqueId()`; Konsole → fester Sentinel (vgl. Punishment `CONSOLE`), da alle Schreibcalls `actor` verlangen.
