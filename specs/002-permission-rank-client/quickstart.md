# Quickstart — Permission-/Rank-System Plugin-Slice

## Voraussetzungen

- Backend-Branch `002-permission-rank-system` läuft (REST + Redis), mit den `/api/permission/*`-Endpunkten und dem Channel `mc:permission:changed`.
- `plugin-protocol`-Artefakt mit `com.mcplatform.protocol.permission.*` in Maven Local (`PermissionChangedEventCodec` ist in `PlatformProtocol.create()` registriert — verifiziert).
- Java 21, Paper-1.21-Server; `config.yml` zeigt auf Backend-URL + Redis.

## Bauen & Testen

```bash
./gradlew test            # Unit-Tests (RoleIconMapper, PermissionGate, Cache, Format, Menüs)
./gradlew build           # Shadow-JAR nach build/libs/
```

Schwerpunkt-Tests:
- `DisplayIconFormatTest` (PURE) — Präfix-Parsing/-Format inkl. null/unbekannt/kaputte UUID/leeres payload → Invalid.
- `PermissionGateTest` (PURE) — has-node / lacks-node / Cold-Cache-neutral.
- `PermissionCacheTest` (PURE) — version-aware apply, offline-ignore, evict.
- `PermissionLiveUpdaterTest` (PURE) — online→reload, offline→kein Load, unbekannter changeType.
- `IconRoundTripTest` / `RankCommandTest` (Paper-Harness) — `resolve(extract(item))`-Roundtrip je Präfix; Müll→Fallback; `/rank`-String-Bau für Vanilla/Custom-Head.
- `MenuRendererIconTest` (Phase R0) — `IconSpec.ofItem` klont base + legt Name/Lore an; `baseItem == null` → Enum-Pfad unverändert.

## Gegen das Backend verifizieren (manuell)

1. JAR in `plugins/`, Server starten; einloggen → Cache lädt `/effective` (Log/Debug prüfen).
2. `/ranks` → Rollenliste mit Icons; eine Rolle anlegen (Name via Anvil) → erscheint; Rollen-Permission hinzufügen/entfernen; Rolle löschen → ConfirmDialog (Doppelklick).
3. `/cp <OnlineSpieler>` → Vergaben sichtbar; Rang vergeben (optional Ablauf/Grund) → Menü re-rendert; Rang entziehen.
4. `/cp <OfflineName>` → Name→UUID-Auflösung; Menü öffnet; nicht auflösbarer Name → Fehlermeldung, kein Menü.
5. **Relog-frei:** während ein Spieler online ist, am Backend einen Rang ändern → Client lädt dessen `/effective` neu (Gate spiegelt die Änderung ohne Relog).
6. **Offline-Event:** Backend-Änderung für einen offline Spieler → kein Cache-Eintrag entsteht.
7. **Autorität:** einem Spieler ohne `roles.manage` im Cache `/ranks`-Schreibbutton zeigen → optimistisch gesperrt; bei Cold-Cache durch → Backend `403` → Fehlermeldung.
8. **Icon-Werkzeug:** ein Vanilla-Item bzw. einen Custom-Head in die Hand nehmen, `/rank toDisplayIcon` → click-to-copy-String (`material:…` bzw. `head-texture:<base64>`) erscheint im Chat; kopieren → ins Webinterface einfügen. Leere Hand → Hinweis.

## Icon-Präfixe zum Durchprobieren

`material:DIAMOND_SWORD`, `head-texture:<base64>`, `head-player:<uuid>`, `null`, `banner:red` (unbekannt → Fallback-Icon `BARRIER`/`PAPER`).

## Wichtig

- **Phase R0 zuerst** (freigegeben): `IconSpec` (`baseItem` + `ofItem`) / `MenuRenderer` (clone-Pfad) additiv erweitern, sonst gelangt der vom `IconResolver` gebaute `ItemStack` nicht ins Menü.
- Head-Texturen ausschließlich über die Paper-`PlayerProfile`-API (kein NMS), Lesen (`/rank`) und Schreiben (`IconResolver`) teilen `DisplayIconFormat`.
- Main-Thread nie blockieren: alle REST/Redis async, Bukkit/Inventory via Scheduler.
- Keine generische Klasse außer dem freigegebenen R0-Eingriff ändern.
