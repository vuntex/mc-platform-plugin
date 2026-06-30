# Contracts — Konsumierte Bestands-Schnittstellen (read-only, Phase 1)

Der Slice erzeugt **nichts** Neues an der Protocol-/Transport-Grenze. Er konsumiert ausschließlich Vorhandenes:

## Channels (read-only, nur indirekt)
| Channel | Quelle | Nutzung im Scoreboard |
|---|---|---|
| `mc:economy:balance` | `EconomyFeature` subscribt (Bestand) | **Nicht** direkt subscribt. Economy aktualisiert seinen Cache und ruft `liveBus.notifyChange(uuid)` → Scoreboard re-rendert via Observer. |
| `mc:permission:changed` | `PermissionFeature` subscribt (Bestand) | **Nicht** direkt subscribt. Permission reloadt seinen Cache und ruft (NEU, +1 Zeile) `liveBus.notifyChange(uuid)` → Scoreboard re-rendert via Observer. |

## Endpoints (read-only)
| Endpoint | Nutzung |
|---|---|
| `EconomyEndpoints.GET_BALANCE` | Über `EconomyReadPort.load` als cache-first+REST-fallback (Bestandslogik aus `BalanceCommand`). Kein neuer Endpoint. |
| `PermissionEndpoints.EFFECTIVE` | Bereits vom PreLogin-Warmup genutzt; Scoreboard liest nur den daraus gefüllten **Cache** über `PermissionReadPort`. |

## Observer-Hook (Bestand, wiederverwendet)
| Typ | Nutzung |
|---|---|
| `platform.menu.MenuLiveBus` | `observe(uuid, reRender)` beim Join, `LiveHandle.close()` beim Leave. `notifyChange` wird von Economy (Bestand) und Permission (NEU, +1 Zeile) gefeuert. **Klasse unverändert.** |

## Negativ-Vertrag (verifiziert in /tasks)
- Keine neue `plugin-protocol`-Klasse, kein neuer Channel, kein neuer Endpoint, kein Backend-Modul (FR-011, AC-7).
- Keine generische Klasse (`platform`/`transport`/Menu-Generik) geändert (FR-012, G2).
