# Phase 0 Research: Reports Plugin-Slice

Alle in der Spec markierten Risiken/Unbekannten wurden gegen den realen Code/Artefakt aufgelöst. Jede Entscheidung mit Begründung und verworfenen Alternativen.

## 1. Protocol-Artefakt: ist `com.mcplatform.protocol.report` vorhanden?

- **Decision**: Ja — direkt aus dem geteilten Artefakt konsumieren, kein `protocol`-Eingriff.
- **Befund** (`~/.m2/.../plugin-protocol-0.1.0-SNAPSHOT.jar`, gebaut 2026-06-22 20:54):
  - `ReportEndpoints.CREATE : EndpointDescriptor<CreateReportRequest, ReportResponse>`
  - `ReportEndpoints.LIST_OPEN : EndpointDescriptor<Void, ReportResponse[]>` (Array-Response)
  - `ReportEndpoints.CHANGE_STATUS : EndpointDescriptor<ChangeStatusRequest, ReportResponse>` (Pfad-Template mit `{id}`)
  - `CreateReportRequest(UUID reporter, UUID target, String category, String detail, List<ChatMessage> chatContext)`
  - `ChatMessage(UUID sender, String text, long timestampEpochMilli)`
  - `ChangeStatusRequest(String newStatus, UUID handledBy)`
  - `ReportResponse(UUID id, UUID reporter, UUID target, String category, String detail, String status, long createdAtEpochMilli, UUID lastHandledBy, long lastStatusChangeAtEpochMilli, List<ChatMessage> chatContext, long version)`
  - `ReportChangedEvent(UUID reportId, UUID reporter, UUID target, String category, String status, String changeType, long timestampEpochMilli)` — **ohne** chatContext
  - `ReportChangedEventCodec.INSTANCE` (implements `MessageCodec<ReportChangedEvent>`), `ReportChannels.CHANGED`
- **Precondition**: `./gradlew build --refresh-dependencies` (Artefakt liegt bereits in Maven Local).
- **Alternatives rejected**: eigene DTOs/Pfad-Strings im Feature — verstößt gegen G3/G8 (Pfad-Strings, Contract-Drift).

## 2. 403/429 sauber behandeln — ohne generischen Eingriff?

- **Decision**: Feature-lokal über `BackendException.statusCode()` unterscheiden. **Keine** neue Exception-Subklasse, **kein** Eingriff in `HttpBackendClient`/`BackendException`.
- **Befund**: `BackendException.fromStatus(status, body)` mappt 400→`BadRequest`, 404→`NotFound`, 409→`Conflict`, 422→`InsufficientFunds`, und **alles übrige (inkl. 403 und 429) → `BackendError(status, body, …)`**. `BackendError` trägt `statusCode()` und `responseBody()`. Damit liefert ein 403 ein `BackendError` mit `statusCode()==403`, ein 429 eines mit `statusCode()==429`.
- **Konsequenz**: `ReportFormat.errorMessage(Throwable)` schaut auf `instanceof BackendException be` und `be.statusCode()`:
  - 422 → „ungültige Meldung“ (Self-Report/leeres/zu langes Detail/unbekannte Kategorie/Chat zu groß)
  - 429 → Cooldown-/Wartezeit-Hinweis (Sekunden ggf. aus `responseBody()`)
  - 403 → „keine Berechtigung“
  - 404 → „Report nicht gefunden“
  - 409 → „ungültiger Statuswechsel / konkurrierende Änderung“
  - 5xx / `statusCode()==0` (Transport) → „später erneut versuchen“
- **Hinweis**: Würde man 403/429 als *typisierte* Subklassen wünschen, wäre das eine Änderung an `BackendException` (sealed) — das **wäre** ein Muster-Leck. Bewusst vermieden; `statusCode()`-Inspektion ist der etablierte, ausreichende Weg (vgl. `EconomyMenuText.transferError` prüft Status/Body ähnlich).

## 3. Live-Events: funktioniert `subscribe` ohne generische Änderung?

- **Decision**: Ja. `context.eventBus().subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, handler)` in `ReportFeature.onEnable` — exakt das Economy/Punishment-Muster.
- **Befund**: `McPlatformPlugin` konstruiert `new LettuceEventBus(PlatformProtocol.create(), …)`. `PlatformProtocol.create()` registriert **alle** Codecs (inkl. Report). `EventDispatcher.deliver` ruft `protocol.decode(wire, codec)` und liefert via `scheduler.runSync` auf den Main-Thread. Version-Awareness/Staleness ist im Dispatcher/`FeatureCache` vorhanden.
- **Alternatives rejected**: eigenes `MessageProtocol` im Feature bauen (G5-Verstoß, Doppel-Registrierung).

## 4. Texteingabe für den Grund — es gibt kein Input-UI

- **Decision**: **Chat-Input-Prompt** als feature-lokale Mechanik (vom Nutzer in /speckit-clarify bestätigt; MENU_DESIGN-Fallback).
- **Mechanik**:
  - `ReportReasonPrompt` hält `Map<UUID, Pending(target, category, createdAtMillis)>`.
  - `ReportChatInputListener` `@EventHandler(priority = EventPriority.LOWEST)` auf `AsyncPlayerChatEvent`: hat der Sender einen Pending-Eintrag → `event.setCancelled(true)`; ist der Text das Abbruch-Wort (z. B. „abbrechen“) → Pending entfernen + Abbruch-Feedback; sonst Text = Grund → Pending entfernen → `scheduler.runAsync` REST-CREATE → `scheduler.runSync` Bestätigung.
  - Da der Listener auf `LOWEST` läuft und cancelt, sieht ihn der Ring-Buffer-Listener (`MONITOR`, `ignoreCancelled=true`) nicht → die Grund-Nachricht landet **nicht** im öffentlichen Ring.
- **Alternatives rejected**: Anvil-GUI-Input (net-neue Framework-Mechanik, mehr Test, Risiko Richtung generischer Menü-Klassen — G7-Grauzone); Command-Argument `/report <p> <grund>` (schlechtere UX, kein freies Editieren).
- **Threading-Achtung**: `AsyncPlayerChatEvent` ist async → in den Listenern **nur** `event.setCancelled/​getMessage` (erlaubt) und Ring-Append (thread-safe); jede Bukkit-/Menü-Aktion über `scheduler.runSync`.

## 5. Chat-Ringpuffer: Speisung & Scope (in /speckit-clarify gesetzt)

- **Decision**: **Globaler** RAM-Ring der letzten ~20 **öffentlichen** Nachrichten (je `ChatMessage` mit Absender-UUID). Beim CREATE wird der gesamte aktuelle Snapshot als `chatContext` mitgeschickt.
- **Mechanik**: `ChatRingBuffer` = bounded (Kapazität 20) thread-safe Struktur (z. B. `ArrayDeque` hinter `synchronized` oder Lock; Append + `snapshot()` als unveränderliche Liste). `PublicChatListener` `@EventHandler(priority = MONITOR, ignoreCancelled = true)` → baut `new ChatMessage(uuid, event.getMessage(), now)`; `now` via `System.currentTimeMillis()` (im async Listener ok). `ignoreCancelled=true` schließt gemutete/gecancelte (z. B. Punishment-Mute) und Grund-Eingaben aus.
- **PMs ausgeschlossen**: `/msg` o. ä. sind separate Commands, kein `AsyncPlayerChatEvent` → natürlich nicht erfasst (FR-008).
- **Alternatives rejected**: Per-Spieler-Ring (Brief-Wortlaut „pro Spieler“) — verliert umgebenden Kontext, mehr Speicher/Komplexität; vom Nutzer zugunsten des globalen Fensters verworfen.

## 6. Pattern-Leak-Audit — generische Klassen unangetastet?

Für jeden im Auftrag genannten Baustein geprüft. **Kein Leck.**

| Generische Klasse | Geplante Nutzung | Änderung nötig? |
|---|---|---|
| `BackendClient`/`HttpBackendClient` | `call`/`callIdempotent` + Query-Overload mit `ReportEndpoints.*` | **Nein** — nur Aufruf |
| `BackendException` | `statusCode()`/`responseBody()` inspizieren | **Nein** — 403/429 fallen bereits in `BackendError` |
| `EventBus`/`LettuceEventBus`/`EventDispatcher` | `subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, …)` | **Nein** — Report-Codec via `PlatformProtocol.create()` schon registriert |
| `FeatureRegistry`/`PluginFeature`/`FeatureContext` | `implements PluginFeature` + 1 `.register(…)` | **Nein** — 1 additive Zeile am Composition-Root (sanktioniert), Interface unverändert |
| `MenuBuilder`/`MenuManager`/`LiveBinding`/`MenuLiveBus` | `list/panel/dialog`, `.live(…)`, `renderPage`, `ConfirmDialog`, `open`, `liveBus().notifyChange` | **Nein** — vorhandene Primitive decken Auswahl-, Inbox- und Confirm-Flows |
| `PlatformScheduler` | `runAsync`/`runSync` | **Nein** |
| `MessageEnvelope`/`MessageProtocol`/`protocol`-Artefakt | nur indirekt über `EventBus` | **Nein** |

**Einzige Bestandsberührungen** (additiv, kein generischer Logik-Eingriff, durch den Brief sanktioniert): `McPlatformPlugin` (+1 `.register`), `plugin.yml` (+2 Commands, +2 Permission-Nodes). Sollte sich während der Umsetzung wider Erwarten ein generischer Eingriff aufdrängen → **STOPP & melden** (z. B. falls 403/429 doch typisiert gebraucht würden, oder ein Menü-Primitive fehlt).

## 7. Idempotenz der REST-Aufrufe

- **Decision**: `CREATE` und `CHANGE_STATUS` → `call()` (nicht auto-retry): CREATE hat kein `transactionId`-Feld (nicht idempotent abgesichert; 429-Cooldown schützt ohnehin); CHANGE_STATUS ist versions-/übergangsabhängig (409 bei Konflikt). `LIST_OPEN` (GET) → `callIdempotent()` (sicher wiederholbar).
- **Alternatives rejected**: CREATE idempotent — DTO bietet keinen Idempotenz-Schlüssel; Auto-Retry könnte Doppel-Reports erzeugen.

## 8. Inbox-Cache & Live-Refresh-Modell

- **Decision**: `OpenReportCache` (reportId → `ReportResponse`, versions-bewusst über `ReportResponse.version()`). Quelle der Wahrheit für die Liste = `LIST_OPEN`; das Detail (Grund + chatContext) kommt direkt aus dem bereits geladenen `ReportResponse` (LIST_OPEN liefert chatContext mit) — **kein** zusätzlicher Fetch beim Öffnen des Details.
- **Live**: `ReportChangedEvent` trägt **kein** Detail/chatContext → der Updater triggert (debounced) einen erneuten `LIST_OPEN`, ersetzt den Cache und ruft `menus.liveBus().notifyChange(INBOX_TOPIC)`; `LiveBinding.onChange` rendert aus dem Cache neu. „Latest request wins“-Guard (Sequenznummer) verhindert, dass eine ältere LIST_OPEN-Antwort eine neuere überschreibt.
- **Alternatives rejected**: Cache rein aus Events füllen — Event hat kein Detail/chatContext, würde Inbox-Detail unvollständig machen.
