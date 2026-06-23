# Contract — Consumed REST & Event Surface (`com.mcplatform.protocol.permission`)

**Konsumiert, nie verändert.** Pfade/Methoden stammen aus `PermissionEndpoints`; das Feature referenziert nur die Konstanten, nie rohe Strings.

## REST-Endpunkte

| Konstante | Methode | Pfad | Request | Response |
|---|---|---|---|---|
| `LIST_ROLES` | GET | `/api/permission/roles` | – | `RoleResponse[]` |
| `CREATE_ROLE` | POST | `/api/permission/roles` | `RoleRequest` | `RoleResponse` |
| `UPDATE_ROLE` | PUT | `/api/permission/roles/{id}` | `RoleRequest` | `RoleResponse` |
| `DELETE_ROLE` | DELETE | `/api/permission/roles/{id}` | – (actor als Query) | `Void` |
| `ADD_ROLE_PERMISSION` | POST | `/api/permission/roles/{id}/permissions` | `RolePermissionRequest` | `RoleResponse` |
| `REMOVE_ROLE_PERMISSION` | DELETE | `/api/permission/roles/{id}/permissions` | `RolePermissionRequest` | `RoleResponse` |
| `GRANT_ROLE` | POST | `/api/permission/players/{uuid}/roles` | `GrantRoleRequest` | `PlayerPermissionsResponse` |
| `REVOKE_ROLE` | DELETE | `/api/permission/players/{uuid}/roles/{roleId}` | – (actor als Query) | `PlayerPermissionsResponse` |
| `GRANT_PERMISSION` | POST | `/api/permission/players/{uuid}/permissions` | `GrantPermissionRequest` | `PlayerPermissionsResponse` |
| `REVOKE_PERMISSION` | DELETE | `/api/permission/players/{uuid}/permissions` | `RevokePermissionRequest` (Body) | `PlayerPermissionsResponse` |
| `EFFECTIVE` | GET | `/api/permission/players/{uuid}/effective` | – | `PlayerPermissionsResponse` |

**Aufruf-Konventionen:**
- Alle über `BackendClient.call(...)` async (Main-Thread nie blockiert).
- `actor` (Staff-UUID): in Bodies, die ihn führen (`RoleRequest`, `RolePermissionRequest`, `Grant*Request`, `RevokePermissionRequest`); bei Pfad-Revokes (`DELETE_ROLE`, `REVOKE_ROLE`) als **Query-Param** via `call(endpoint, null, Map.of("actor", uuid), pathVars…)`.
- Schreibcalls sind **nicht** über `callIdempotent` zu retryen, sofern nicht idempotent (POST GRANT erzeugt Vergabe). Lesepfade (`LIST_ROLES`, `EFFECTIVE`) dürfen `callIdempotent`.
- **Verifizieren in Impl:** `HttpBackendClient` sendet bei `DELETE` einen Body (`REVOKE_PERMISSION`). Falls nicht → separates Transport-Muster-Leck melden (siehe research §DELETE-mit-Body).

## Fehler → Nutzer (über `PermissionFormat.error(status)`)

| Status | Bedeutung | UI |
|---|---|---|
| 403 | keine Berechtigung (Autorität!) | „Dazu fehlt dir die Berechtigung." |
| 404 | Rolle/Spieler/Grant fehlt | „Nicht gefunden." |
| 409 | Konflikt / veralteter Stand | „Konflikt — bitte erneut öffnen." |
| 422 | ungültige Eingabe | „Ungültige Eingabe." |
| 429 | Rate-Limit | „Zu schnell — bitte kurz warten." |
| 5xx | Backend-Fehler | „Backend nicht erreichbar." |

## Event

- Channel `PermissionChannels.CHANGED` = `mc:permission:changed`.
- Payload `PermissionChangedEvent(playerUuid, changeType, timestampEpochMilli)`; `changeType ∈ {GRANT_ADDED, GRANT_REVOKED, GRANT_EXPIRED, ROLE_CONFIG_CHANGED}` (+ unbekannte tolerieren).
- Decode via `PermissionChangedEventCodec.INSTANCE` (bereits in `PlatformProtocol.create()` registriert).
- Abo: `context.eventBus().subscribe(PermissionChannels.CHANGED, PermissionChangedEventCodec.INSTANCE, permissionLiveUpdater)`.
