# Contract: Consumed Backend REST + Live Event

> **Quelle der Wahrheit = Backend.** Der Client konsumiert diesen Contract über `BackendClient` + `ReportEndpoints` (keine Pfad-Strings) und `EventBus` + `ReportChannels`/`ReportChangedEventCodec`. Nichts hiervon wird im Plugin definiert oder verändert.

## REST-Endpoints (`com.mcplatform.protocol.report.ReportEndpoints`)

### CREATE — Meldung erstellen
- **Descriptor**: `ReportEndpoints.CREATE : EndpointDescriptor<CreateReportRequest, ReportResponse>`
- **Methode/Pfad**: `POST /api/reports` (Pfad steckt im Descriptor)
- **Permission**: offen für alle
- **Client-Aufruf**:
  ```java
  backend.call(ReportEndpoints.CREATE,
      new CreateReportRequest(reporter, target, categoryWire, detail, ring.snapshot()))
    .whenComplete((res, err) -> scheduler.runSync(() -> { /* Bestätigung / ReportFormat.errorMessage */ }));
  ```
- **Fehler**: `422` (Self-Report/leeres-langes Detail/unbekannte Kategorie/Chat zu groß), `429` (Cooldown).

### LIST_OPEN — offene Reports (geteilte Warteschlange)
- **Descriptor**: `ReportEndpoints.LIST_OPEN : EndpointDescriptor<Void, ReportResponse[]>`
- **Methode/Pfad**: `GET /api/reports/open?staff=<uuid>`
- **Permission**: `report.view` (Backend)
- **Client-Aufruf** (Query über den Map-Overload):
  ```java
  Map<String,String> q = new LinkedHashMap<>();
  q.put("staff", viewerUuid.toString());
  backend.callIdempotent(ReportEndpoints.LIST_OPEN, null, q)   // GET ist sicher wiederholbar
    .whenComplete((arr, err) -> scheduler.runSync(() -> { /* OpenReportCache ersetzen + notifyChange */ }));
  ```
- **Semantik**: liefert **alle** offenen Reports; `staff` = Identität des Anfragenden (Permission-/Audit-Kontext, **kein** Filter).
- **Fehler**: `403` (kein `report.view`).

### CHANGE_STATUS — Status setzen
- **Descriptor**: `ReportEndpoints.CHANGE_STATUS : EndpointDescriptor<ChangeStatusRequest, ReportResponse>`
- **Methode/Pfad**: `POST /api/reports/{id}/status` (`{id}` als pathVar)
- **Permission**: `report.handle` (Backend)
- **Client-Aufruf**:
  ```java
  backend.call(ReportEndpoints.CHANGE_STATUS,
      new ChangeStatusRequest(newStatusWire, viewerUuid), reportId.toString())
    .whenComplete((res, err) -> scheduler.runSync(() -> { /* Erfolg → zurück zur Inbox / ReportFormat.errorMessage */ }));
  ```
- **Erlaubte Übergänge**: `OPEN→IN_PROGRESS`, `OPEN→REJECTED`, `IN_PROGRESS→RESOLVED`, `IN_PROGRESS→REJECTED`.
- **Fehler**: `403` (kein `report.handle`), `404` (nicht gefunden), `409` (ungültiger Übergang / konkurrierende Änderung), `422` (ungültiger Status-Wert).

## Live-Event (`com.mcplatform.protocol.report`)

- **Channel**: `ReportChannels.CHANGED` (= `mc:report:changed`)
- **Codec**: `ReportChangedEventCodec.INSTANCE` (bereits via `PlatformProtocol.create()` registriert)
- **Payload**: `ReportChangedEvent(reportId, reporter, target, category, status, changeType, timestampEpochMilli)` — **ohne** chatContext/detail.
- **Client-Subscribe** (in `ReportFeature.onEnable`):
  ```java
  context.eventBus().subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, reportLiveUpdater);
  ```
- **Handler-Verhalten**:
  - `changeType == "CREATED"` → `ReportNotifier` pingt online-Träger von `mcplatform.report.view` (Chat-Zeile + Ton) **und** triggert Inbox-Refresh.
  - `changeType == "STATUS_CHANGED"` → Inbox-Refresh (Cache + `notifyChange`).
  - Versions-/Reihenfolge-Schutz durch Dispatcher/`FeatureCache` bzw. „latest LIST_OPEN wins“.

## Fehler→Nachricht-Mapping (feature-lokal in `ReportFormat`)

| HTTP | Erkennung (Client) | Nutzer-Meldung (DE) |
|---|---|---|
| 422 | `BackendException.InsufficientFunds` **oder** `statusCode()==422` | „Meldung ungültig (z. B. du kannst dich nicht selbst melden / Grund fehlt).“ |
| 429 | `BackendError` mit `statusCode()==429` | „Zu schnell — bitte kurz warten.“ (Sekunden ggf. aus `responseBody()`) |
| 403 | `BackendError` mit `statusCode()==403` | „Keine Berechtigung dafür.“ |
| 404 | `BackendException.NotFound` | „Report nicht gefunden.“ |
| 409 | `BackendException.Conflict` | „Statuswechsel nicht möglich (bereits geändert).“ |
| 5xx/0 | `BackendError` sonst | „Aktuell nicht erreichbar — bitte später erneut.“ |

> 403 und 429 sind **nicht** als eigene `BackendException`-Subklassen modelliert; sie kommen als `BackendError` mit korrektem `statusCode()`. Bewusst feature-lokal behandelt, um die generische `BackendException` (sealed) **nicht** zu ändern.
