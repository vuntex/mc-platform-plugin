# Research — Permission-/Rank-System Plugin-Slice (Phase 0)

Entscheidungen, Reuse-Audit und das Muster-Leck. Format je Punkt: **Decision / Rationale / Alternatives**.

## §Pattern-Leak-Audit (die zentrale Erkenntnis)

**Decision:** Das `display_icon`-Rendering benötigt einen **additiven Vor-Refactor (Phase R0)** an der generischen Render-Schicht `IconSpec` + `MenuRenderer`. Ohne ihn ist FR-020 nicht erfüllbar. Vorgelegt zur Freigabe.

**Befund (am Code verifiziert):**
- `platform/menu/Icon` ist ein geschlossenes Enum (Bedeutung→festes Material).
- `platform/menu/IconSpec` = `(Icon icon, MenuText name, List<LoreLine> lore, UUID skinOwner, boolean glow)`. Köpfe nur über `skinOwner` (UUID).
- `platform/menu/MenuRenderer.toStack()` nutzt `MenuStyle.material(icon.icon())` + für Köpfe `SkullMeta.setOwningPlayer(getOfflinePlayer(skinOwner))`. Kein roher Material-Override, keine Textur.
- `platform/menu/MenuItem` akzeptiert nur `IconSpec` — kein Raw-`ItemStack`-Notausgang.

**Mapping-Matrix:** `head-player:<uuid>` ✅ ohne Änderung (`IconSpec.head`); `null`/unbekannt ✅ Default-Icon; `material:<MATERIAL>` ❌; `head-texture:<texture>` ❌.

**R0 (freigegeben 2026-06-23) — Raw-`ItemStack`-Notausgang; das Feature baut den `ItemStack`:**
- `IconSpec` +`baseItem:ItemStack` (nullable, default `null`) + Factory `IconSpec.ofItem(base, name, lore)`. Enum-/Head-Factories unverändert → der reine Pfad bleibt server-frei testbar; nur der neue Pfad trägt Bukkit (versorgt vom `IconResolver`).
- `MenuRenderer.toStack()`: `baseItem != null` → `stack = baseItem.clone()` statt `new ItemStack(MenuStyle.material(...))`; Name/Lore/Italic-aus/Flags wie gehabt darüber. Kein Profil-Code im Renderer (steckt im `baseItem`).

**Rationale:** Der Auftraggeber will den `IconResolver` (String→`ItemStack`) **im Feature** (eine Stelle), damit die `PlayerProfile`-Schreiblogik (Kopf bauen) symmetrisch zur Lese-Seite des `/rank`-Befehls liegt und beide dieselbe Paper-API + dieselben Präfix-Konstanten teilen. Dafür muss das Menü einen fertigen `ItemStack` annehmen → genau dieser Notausgang. Additiv, generisch nützlich (Cosmetics/Custom-GUIs), ändert kein bestehendes Verhalten.

**Alternatives:**
- *Generik einfrieren, nur `head-player` + Default* → verletzt FR-020. Verworfen.
- *Renderer baut aus `rawMaterial`/`headTexture`-Strings* → hält `IconSpec` Bukkit-frei, legt aber die `PlayerProfile`-Schreiblogik in die Render-Schicht (unsymmetrisch zur Lese-Seite, gegen „eine Stelle im Feature"). Verworfen.
- *`material:`→nächstes semantisches `Icon` mappen* → verfehlt den Zweck. Verworfen.

## §Icon-Beidrichtung (Resolver schreibt, Befehl liest — geteiltes Format)

**Decision:** Eine geteilte, **reine** Klasse `DisplayIconFormat` hält die Präfix-Konstanten (`material:`, `head-texture:`, `head-player:`) + `parse(String)` + Format-Helfer. Beide Richtungen nutzen sie, damit sie nie auseinanderlaufen:
- **Schreiben — `IconResolver` (String→`ItemStack`, Feature, Bukkit):** `material:<NAME>` → `new ItemStack(Material.valueOf(NAME))`; `head-texture:<tex>` → `PLAYER_HEAD` + `SkullMeta` mit `PlayerProfile`, Textur über `ProfileProperty("textures", base64)` (Paper-API, **kein NMS**); `head-player:<uuid>` → Kopf via `PlayerProfile` des Spielers; `null`/unbekannt/ungültiges Material/Parse-Fehler → **sichtbares Fallback-Icon** (`BARRIER`/`PAPER`), nie Crash.
- **Lesen — `IconExtractor` (`ItemStack`→String, Feature, Bukkit) für `/rank toDisplayIcon`:** Custom-Head (PLAYER_HEAD mit Profil-Textur) → `head-texture:<base64>` (Textur aus dem `PlayerProfile`/`ProfileProperty`); sonst Vanilla-Item → `material:<TYPE.name()>`.
- `/rank toDisplayIcon` liest das Item in der Hand, ruft `IconExtractor`, sendet das Ergebnis als **click-to-copy** Adventure-Komponente (`ClickEvent.copyToClipboard(...)`). **Kein Backend-Call, kein Schreibpfad** — der String wird im Webinterface gesetzt; das Plugin schreibt das Icon nie zurück (String bleibt backend-opak).

**Rationale:** Genau der Auftrag — ein Format, zwei Richtungen, dieselbe Paper-`PlayerProfile`-API von beiden Seiten; Roundtrip-Sicherheit per Test.

**Alternatives:** Format-Strings in beiden Klassen doppeln (Drift-Risiko) → verworfen. NMS/Reflection für Texturen → verboten.

**Hinweis Befehlsname:** `/rank` (Lese-Werkzeug) liegt nah an `/ranks` (Rollen-Verwaltung) — bewusst dem Auftrag folgend getrennt gehalten; bei Bedarf später als Subcommand zusammenführbar.

## §Reuse — generische Bausteine unverändert

| Baustein (generisch) | Wiederverwendung | Änderung? |
|---|---|---|
| `transport/FeatureCache<K,V>` | `FeatureCache<UUID, PlayerPermissionsView>`; `put(uuid,view,version)`/`get`/`remove`/`version` | **Nein** |
| `transport/BackendClient` | `call(PermissionEndpoints.*, body, query, pathVars…)` + `callIdempotent` | **Nein** |
| `transport/EventBus` | `subscribe(PermissionChannels.CHANGED, PermissionChangedEventCodec.INSTANCE, updater)` | **Nein** |
| `transport/BackendException` | `statusCode()` für 403/404/409/422/429/5xx → `PermissionFormat` | **Nein** |
| `feature/PluginFeature`+`FeatureRegistry`+`FeatureContext` | neuer `PermissionFeature`, 1 `.register(...)` | **Nein** |
| `platform/PlatformScheduler` | `runAsync` (REST/Redis, Name-Auflösung) / `runSync` (Bukkit/Inventory) | **Nein** |
| `platform/menu/MenuBuilder`+`MenuManager`+`ConfirmDialog`+`Pagination`+`MenuText`+`Lore`+`Token`+`Feedback` | Menüaufbau/-routing, Confirm (Delete), Pagination 7×4 | **Nein** |
| `platform/menu/IconSpec`+`MenuRenderer` | Icon-Rendering | **JA — Phase R0 (additiv, freigegeben): `baseItem`-Notausgang** |

## §Cache-Version-Strategie

**Decision:** `FeatureCache.put(uuid, view, version)` mit `version = triggerndes PermissionChangedEvent.timestampEpochMilli`; Join-Initial-Load nutzt `System.currentTimeMillis()` zum Ladezeitpunkt.

**Rationale:** `GET …/effective` (PlayerPermissionsResponse) trägt **kein** Sequenz-/Versionsfeld. Da jedes Ereignis einen vollständigen `/effective`-Reload auslöst, ordnet die Ereignis-Zeitstempel-Version konkurrierende/auswärts eintreffende Antworten korrekt (neuere gewinnt) — genau wofür die version-aware `FeatureCache` da ist. Idempotent bei Re-Delivery desselben Stempels.

**Alternatives:** Monotoner lokaler Zähler (verliert Ereignis-Ordnung bei Out-of-order-Antworten); Backend um Sequenz erweitern (Contract-Änderung, verboten).

## §Live-Handling (`mc:permission:changed`)

**Decision:** `PermissionLiveUpdater` (`Consumer<PermissionChangedEvent>`): wenn `Bukkit.getPlayer(uuid)` online → `PermissionLoader.reload(uuid, event.timestampEpochMilli())`; sonst **ignorieren**. Alle vier `changeType` (und unbekannte) → identische Voll-Invalidierung der UUID. `ROLE_CONFIG_CHANGED` kommt vom Backend bereits pro Halter als je ein Ereignis → keine Sonderlogik. Online-Check + Reload-Dispatch laufen main-thread-sicher; der REST-Load selbst async.

**Rationale:** Spec FR-003/004/005; minimaler, gezielter Reload; kein Massen-Refresh.

**Alternatives:** Pro `changeType` differenzierte Teil-Updates (unnötig, da `/effective` ohnehin alles liefert); Offline-Vormerken (vom Auftraggeber als „ignorieren" geklärt).

## §Optimistisches Gate

**Decision:** `PermissionGate` liest die `PermissionCache`: `has(uuid, node)` = `cache.get(uuid).map(v → v.effective().contains(node)).orElse(true)` — **Cold-Cache (kein Eintrag) → `true` (neutral durchlassen)**; vorhandener Eintrag ohne Node → `false` (optimistisch sperren). Feingranulare Nodes pro Aktion (siehe `contracts/commands-permissions.md`). Echte Autorität bleibt Backend: jeder Write-Pfad behandelt `403` über `PermissionFormat`.

**Rationale:** Spec FR-008/009/010/011 + Klärung (feingranular, Cold-Cache neutral). Das Gate liest bewusst den **feature-eigenen Cache**, nicht Bukkit `hasPermission`.

**Alternatives:** Bukkit `hasPermission` (würde eine Brücke effectivePermissions→PermissibleBase verlangen — größerer Platform-Eingriff, vom Spec-Scope nicht gefordert; als künftige Integration notiert); harte `plugin.yml`-Permission auf `/ranks`/`/cp` (würde op-only erzwingen und das Cache-Gate aushebeln) — daher **keine** blockierende `plugin.yml`-Permission auf diesen Commands.

## §Name→UUID-Auflösung für `/cp <Spieler>` (offline-fähig)

**Decision:** Etabliertes Punishment-Muster spiegeln: online via `Bukkit.getPlayerExact(name)` (main thread); sonst **off-main** (`scheduler.runAsync`) via `Bukkit.getOfflinePlayer(name)` (blockierender Mojang-Lookup) → UUID; danach `runSync` Menü öffnen. Nicht auflösbar → verständliche Fehlermeldung, kein Menü (FR-027, Edge Case).

**Rationale:** `/cp` muss offline Spieler erreichen; `getOfflinePlayerIfCached` (Economy) genügt nur für „schon gesehen". Der blockierende Lookup darf nie auf dem Main-Thread laufen.

**Alternatives:** Backend-Lookup (kein solcher Endpunkt im Slice); nur online (vom Auftraggeber verworfen).

## §Protocol-Verfügbarkeit (G5 aufgelöst)

**Decision:** Keine Annahme nötig — **verifiziert**: `PlatformProtocol.create()` registriert `PermissionChangedEventCodec.INSTANCE` (neben Economy/Punishment/Report). Der `EventBus` ist damit bereits permission-fähig; das Feature ruft nur `subscribe(...)`.

**Rationale:** Direkt im Artefakt `com.mcplatform.protocol.core.PlatformProtocol` geprüft.

## §STATIC-Menüs (kein MenuLiveBus)

**Decision:** `/ranks` und `/cp`-Menüs sind STATIC (MENU_DESIGN §6); nach eigener Schreibaktion gezielt `view.setSlot(...)`/`refresh()` re-rendern. Kein Abo auf `mc:permission:changed` im Menü.

**Rationale:** Spec-Klärung (STATIC); Verwaltungs-/Edit-Menüs ändern sich primär durch eigene Interaktion; vermeidet die LIVE/MenuLiveBus-Komplexität.

**Alternatives:** LIVE-Menü (verworfen vom Auftraggeber; größerer Aufwand).

## §DELETE-mit-Body / actor-Übergabe (Transport-Reuse-Check)

**Decision:** `REVOKE_PERMISSION` ist `DELETE` mit `RevokePermissionRequest`-Body; `REVOKE_ROLE`/`DELETE_ROLE` sind `DELETE` mit Pfad-Var + `actor` als **Query-Param**. Der bestehende `BackendClient.call(endpoint, body, query, pathVars…)`-Overload (Query-Map) und der DELETE-Pfad sind vorhanden (Query-Param-Builder wurde bereits für Punishment ergänzt). **In Implementierung zu verifizieren:** dass `HttpBackendClient` für `DELETE` einen Request-Body sendet; falls nicht, ist das ein **separat zu meldendes** Transport-Muster-Leck (nicht in diesem Slice stillschweigend ändern).

**Rationale:** `RolePermissionRequest`/`RevokePermissionRequest` tragen `actor`; `GrantRoleRequest`/`GrantPermissionRequest` ebenso im Body. Pfad-Revokes tragen `actor` als Query.

**Alternatives:** keine — Contract ist fix.
