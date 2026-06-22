# Phase 1 Data Model: Reports Plugin-Slice

Alle Wire-Typen stammen aus `com.mcplatform.protocol.report` (geteiltes Artefakt, **unverändert**). Das Plugin definiert nur feature-lokale RAM-Strukturen. Keine Persistenz.

## Wire-Typen (konsumiert, nicht definiert)

| Typ | Felder | Verwendung im Client |
|---|---|---|
| `CreateReportRequest` | `reporter:UUID, target:UUID, category:String, detail:String, chatContext:List<ChatMessage>` | Body für `CREATE` |
| `ChangeStatusRequest` | `newStatus:String, handledBy:UUID` | Body für `CHANGE_STATUS` |
| `ReportResponse` | `id, reporter, target:UUID; category, detail, status:String; createdAtEpochMilli:long; lastHandledBy:UUID; lastStatusChangeAtEpochMilli:long; chatContext:List<ChatMessage>; version:long` | Antwort von CREATE/CHANGE_STATUS; Elemente von `LIST_OPEN` |
| `ChatMessage` | `sender:UUID, text:String, timestampEpochMilli:long` | Ring-Inhalt + chatContext-Element + Detail-Anzeige |
| `ReportChangedEvent` | `reportId, reporter, target:UUID; category, status, changeType:String; timestampEpochMilli:long` | Live-Event; **kein** detail/chatContext |

**Kanonische Enum-Wire-Werte** (Anzeige-Labels DE in `ReportFormat`):
- Kategorie: `CHEATING`, `BELEIDIGUNG`, `SPAM_WERBUNG`, `TEAMING_BUG_ABUSE`, `SONSTIGES`
- Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `REJECTED`
- changeType: `CREATED`, `STATUS_CHANGED`

## Feature-lokale Strukturen (RAM, neu)

### `ChatRingBuffer`
- **Zweck**: globaler Schnappschuss-Speicher der letzten ~20 öffentlichen Nachrichten.
- **Inhalt**: bounded (Kapazität = 20) FIFO von `ChatMessage`.
- **Operationen**: `add(ChatMessage)` (verdrängt ältestes bei Überlauf); `snapshot(): List<ChatMessage>` (unveränderliche Kopie, älteste→neueste).
- **Nebenläufigkeit**: thread-safe (Append aus async Chat-Listener; `snapshot()` aus async Command-Pfad).
- **Lebensdauer**: nicht persistent; leer nach Neustart.

### `OpenReportCache`
- **Zweck**: lokaler Lese-Cache offener Reports für die Inbox-Darstellung (Backend bleibt Wahrheit).
- **Inhalt**: `reportId(UUID) → ReportResponse`, versions-bewusst (nur übernehmen, wenn `version` ≥ vorhandene). Optional gestützt auf das vorhandene generische `FeatureCache<UUID, ReportResponse>`.
- **Befüllung**: `LIST_OPEN(staff=me)` beim Inbox-Open und bei jedem Live-Refresh (ersetzt Inhalt; „latest request wins“-Sequenz-Guard).
- **Lesen**: Inbox-Liste & Detail rendern ausschließlich aus diesem Cache.

### `ReportReasonPrompt`
- **Zweck**: schwebende Grund-Eingaben.
- **Inhalt**: `reporterUUID → Pending(target:UUID, category:String, createdAtMillis:long)`.
- **Operationen**: `begin(reporter, target, category)`, `take(reporter): Optional<Pending>` (entfernt), `cancel(reporter)`.
- **Aufräumen**: `ReportSession` (PlayerQuitListener) entfernt Pending beim Disconnect; optionaler TTL-Cutoff über `createdAtMillis`.

## Status-State-Machine (UI-seitig durchgesetzt; Backend autoritativ via 409)

```
OPEN ──▶ IN_PROGRESS ──▶ RESOLVED
  │            └────────▶ REJECTED
  └────────────────────▶ REJECTED
```

- Detail-Menü zeigt nur die für den aktuellen Status erlaubten Buttons:
  - `OPEN`   → Buttons „In Bearbeitung“ (→IN_PROGRESS), „Ablehnen“ (→REJECTED)
  - `IN_PROGRESS` → Buttons „Erledigt“ (→RESOLVED), „Ablehnen“ (→REJECTED)
  - `RESOLVED`/`REJECTED` → keine Aktionsbuttons (in diesem Slice keine Lese-Sicht/Reopen)
- Jeder Statuswechsel: `ConfirmDialog` → `CHANGE_STATUS` mit `ChangeStatusRequest(newStatus, handledBy=actor)`.
- Backend lehnt unzulässige/konkurrierende Übergänge mit `409` ab → `ReportFormat`-Meldung, Ansicht bleibt konsistent.

## Validierungsregeln (Client-Sicht; Quelle = Backend)

- `target` muss auflösbar sein und ≠ Reporter (Self-Report → Backend 422; Client kann früh hinten anstellen, verlässt sich aber auf Backend).
- `detail` nicht leer; Längen-/Inhaltsgrenzen → Backend 422.
- `chatContext` darf leer/`null` sein (FR-009); Obergrenze durch Ring-Kapazität (~20) → praktisch kein „Chat zu groß“ (422).
- Cooldown bei zu schnellem erneutem Melden → Backend 429.

## Display-Namen (UUID → Anzeige)

DTOs tragen nur UUIDs; Menüs und der Live-Ping zeigen aber Reporter/Ziel namentlich. Auflösung über einen kleinen Helper `ReportFormat.playerName(UUID)` (Main-Thread): zuerst `Bukkit.getPlayer(uuid)` (online), sonst `Bukkit.getOfflinePlayer(uuid).getName()` (Bukkit-Cache), Fallback = kurze UUID (erste 8 Zeichen, vgl. `EconomyHistoryFormat.shortId`). Im Menü zusätzlich `IconSpec.head(uuid, name, lore)` für den Spielerkopf. Keine blockierenden Lookups; reine Cache-/Online-Abfrage.

## Mapping-Übersicht (Feature → Wire)

- `ReportCommand` + `ReportCategoryMenu` + `ReportReasonPrompt` → `CreateReportRequest(reporter=sender, target, category=wire, detail=chatReason, chatContext=ring.snapshot())`.
- `ReportInboxMenu` → `LIST_OPEN` (query `staff=<viewerUuid>`) → `ReportResponse[]` → `OpenReportCache`.
- `ReportDetailMenu` Status-Button → `CHANGE_STATUS` (pathVar `reportId`) Body `ChangeStatusRequest(newStatus, handledBy=viewerUuid)`.
- `ReportLiveUpdater` ← `ReportChangedEvent` → (CREATED) `ReportNotifier` + Inbox-Refresh; (STATUS_CHANGED) Inbox-Refresh.
