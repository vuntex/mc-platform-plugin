# MC Platform — Backend & Plugin

> Professionelles Minecraft-Backend für einen 1.21 Server (Paper) mit Velocity-Netzwerk-Fähigkeit.
> Verwaltung von User-Daten (Coins etc.) und Konfiguration über ein Webinterface, performant, gecached,
> live für Online-User und korrekt für Offline-User.

**Status:** Backend-Skeleton, Redis-Schema/Pub-Sub und die Economy-Operationen CREDIT/DEBIT/SET **und TRANSFER** stehen (event-sourced, idempotent, optimistic-locked) — Abschnitt 9, Schritte 1, 2, 3 und 4 erledigt (Details im Abschnitt „Status" am Ende). Nächster Schritt: den „es lebt"-Vertical-Slice gegen einen laufenden Paper-Node end-to-end verifizieren, dann Webinterface/SSE + Config-UI (Schritt 5).

---

## 1. Ziele & Anforderungen

- **Plattform:** Paper 1.21 (NICHT Folia — siehe Entscheidung unten). Velocity-Proxy von Anfang an eingeplant, erst läuft aber nur ein Paper-Node.
- **Erwartete Last:** 200+ Spieler gleichzeitig (Peak).
- **Webinterface:** User-Daten verwalten (Coins etc.), Server-Konfiguration ändern.
- **Live-Updates** für Online-User, **korrekte Persistenz** für Offline-User.
- **Maximale Sicherheit + lückenloses Logging** bei Economy (Geld-ähnliche Werte).
- **Priorität:** saubere Architektur von Anfang an (DDD, Hexagonal).

---

## 2. Architektur-Überblick

```
┌──────────────┐         ┌─────────────────────┐
│ Webinterface │◀──SSE──▶│  Spring Boot API    │
│  (Next.js)   │──REST──▶│  (1..N Nodes)       │
└──────────────┘         └──────┬──────────────┘
                                │
                ┌───────────────┼────────────────┐
                ▼               ▼                ▼
         ┌──────────┐   ┌──────────────┐  ┌──────────┐
         │PostgreSQL│   │    Redis     │  │ (später) │
         │  (SoT)   │   │ Cache+PubSub │  │  Kafka?  │
         └──────────┘   └──────┬───────┘  └──────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                 ▼
      ┌──────────────┐ ┌──────────────┐  ┌──────────────┐
      │ Velocity     │ │ Paper Node 1 │  │ Paper Node 2 │
      │ Plugin       │ │ (Plugin)     │  │ (Plugin)     │
      └──────────────┘ └──────────────┘  └──────────────┘
```

**Kernprinzip:** Das Spring-Boot-Backend ist die *Single Source of Truth*. Das Plugin ist nur ein Client —
keine direkte DB-Anbindung im Plugin, kein Spring im Plugin.

### Rollen der Komponenten
- **PostgreSQL** — Persistenz, Source of Truth (Event Store + Projektionen + Config).
- **Spring Boot** — Geschäftslogik, REST (CRUD) + SSE (Live-Updates). 1..N Nodes.
- **Redis** — (a) Cache heißer Daten (Balances aktiver Spieler), (b) Pub/Sub für Live-Updates & Cache-Invalidierung über alle Nodes/Server hinweg.
- **Paper Plugin** — Spiel-seitiger Client, liest/schreibt übers Backend, hört auf Redis-Events.
- **Velocity** — Proxy; Server-Switch-Events via plugin messaging.

---

## 3. Wichtige Entscheidungen (mit Begründung)

### Paper statt Folia
Folia parallelisiert die Welt-Simulation über Region-Threads — lohnt erst bei ~500–1000+ auf *einem*
Server, bricht das Plugin-Ökosystem und erzwingt ein fundamental anderes Programmiermodell.
Unser Performance-Problem liegt nicht im Tick, sondern im Daten-Layer (Coins, I/O, Live-Updates) —
das lagern wir ins Backend + Redis aus. Skalierung später horizontal über mehr Paper-Nodes hinter
Velocity, nicht über Folia.

### Economy event-sourced, Rest state-stored
Coins brauchen Audit-Trail, Idempotenz, Concurrency-Sicherheit → Event Store + Projektion.
Config & Player-Stammdaten sind simples CRUD (ändern sich selten, keine Historie nötig).

### Maximale Sicherheit: strikt synchrone Debits
Jede Ausgabe (DEBITED) geht durchs Backend, Guthaben-Prüfung in der DB-Transaktion (optimistic
locking über `version`). Kein optimistisches Abziehen im Plugin. Reads dürfen optimistisch aus Redis
kommen. Jede Transaktion wird als Event geloggt (lückenloser Audit-Trail).

### Geld nie als Float
`balance` ist `BIGINT`. Bei Nachkommastellen in kleinster Einheit rechnen (wie Cents),
`currency.decimal_places` sagt nur dem UI, wo das Komma steht.

### UUID-zentrisch
Jeder FK zeigt auf `player.uuid`. Name ist nur ein Cache-Feld mit Timestamp. Damit ist
„online vs. offline" für die Datenhaltung irrelevant.

---

## 4. Tech-Stack

| Layer | Wahl | Begründung |
|-------|------|-----------|
| Plugin-API | Paper 1.21 | Async-Scheduler, moderne API |
| Plugin↔Backend | Redis Pub/Sub + REST | Live ohne Polling; REST als Fallback/Command |
| Cache-Client | Lettuce | Async, Netty-basiert, Spring-Default |
| Postgres-Access | jOOQ | Volle Query-Kontrolle bei hohem Durchsatz |
| Realtime Web | SSE | Reicht für unidirektionale Updates, simpler als WS |
| Migrations | Flyway | Versionierte Schema-Migration |
| Build | Gradle (Kotlin DSL) | |
| Server-Switch | Velocity plugin messaging | |

---

## 5. Modul-Struktur (Backend, Multi-Module Gradle)

```
mc-platform-backend/
├── core-domain/        ← reine Geschäftslogik, KEIN Framework
│   ├── economy/        ← Coins als Aggregate, Transactions als Events
│   ├── player/         ← Player-Identity (UUID-zentrisch)
│   └── config/         ← Server-Config-Modell
├── application/        ← Use Cases, Ports (Hexagon-Mitte)
├── infra-persistence/  ← Postgres-Adapter (jOOQ + Flyway)
├── infra-cache/        ← Redis-Adapter (Lettuce), Pub/Sub
├── api-rest/           ← Controller, DTOs
├── api-realtime/       ← SSE/WebSocket
└── plugin-protocol/    ← geteilte DTOs für Plugin↔Backend (separat publizierbar)
```

Plugin ist ein **separates Repo** (`mc-platform-plugin/`), zieht nur `plugin-protocol`
(zunächst als Git-Submodule).

---

## 6. Datenmodell (PostgreSQL)

### ER-Überblick
```
player (1) ──< economy_event (N) >── currency (1)
player (1) ──< player_balance (N) >── currency (1)
server_config ──< config_audit
```

### player — Stammdaten (state-stored)
```sql
CREATE TABLE player (
    uuid             UUID PRIMARY KEY,
    name             VARCHAR(16) NOT NULL,         -- Cache, kann veralten
    name_updated_at  TIMESTAMPTZ NOT NULL,
    first_seen       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_player_name_lower ON player (LOWER(name));
```

### currency — Währungen als Config
```sql
CREATE TABLE currency (
    code            VARCHAR(32) PRIMARY KEY,       -- 'COINS', 'GEMS'
    display_name    VARCHAR(64) NOT NULL,
    symbol          VARCHAR(8),
    decimal_places  SMALLINT NOT NULL DEFAULT 0,   -- 0 = ganze Coins
    default_balance BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### economy_event — Event Store (append-only)
```sql
CREATE TABLE economy_event (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no     BIGSERIAL NOT NULL,            -- globale Ordnung
    player_uuid     UUID NOT NULL REFERENCES player(uuid),
    currency_code   VARCHAR(32) NOT NULL REFERENCES currency(code),
    event_type      VARCHAR(32) NOT NULL,          -- CREDITED|DEBITED|SET|TRANSFER_OUT|TRANSFER_IN
    amount          BIGINT NOT NULL,               -- immer positiv; Richtung über event_type
    balance_after   BIGINT NOT NULL,               -- Stand nach diesem Event
    transaction_id  UUID NOT NULL,                 -- Idempotenz-Schlüssel
    source          VARCHAR(64) NOT NULL,          -- 'WEB','PLUGIN:shop','SYSTEM:mobkill'
    metadata        JSONB,                         -- frei: shop_id, reason, correlation_id...
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction UNIQUE (transaction_id)
);
CREATE INDEX idx_event_player_currency ON economy_event (player_uuid, currency_code, sequence_no);
CREATE INDEX idx_event_created ON economy_event (created_at);
```
- `transaction_id` UNIQUE = Idempotenz-Garantie (doppelte Events knallen in den Constraint → ignorieren).
- `amount` immer positiv, Richtung über `event_type` (keine Vorzeichen-Bugs).
- `balance_after` redundant mit Absicht: Verlauf anzeigbar ohne Neu-Falten; eingebaute Konsistenzprüfung.
- `source` macht den Audit-Trail brauchbar.

### player_balance — Projektion (Snapshot)
```sql
CREATE TABLE player_balance (
    player_uuid    UUID NOT NULL REFERENCES player(uuid),
    currency_code  VARCHAR(32) NOT NULL REFERENCES currency(code),
    balance        BIGINT NOT NULL DEFAULT 0,
    version        BIGINT NOT NULL DEFAULT 0,      -- sequence_no des letzten Events
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_uuid, currency_code)
);
```
Schreibvorgang in EINER DB-Transaktion: Event inserten → `UPDATE player_balance ... WHERE version = :expected`
(optimistic locking). Version-Mismatch → konkurrierende Änderung → retry. Projektion jederzeit aus
Event Store neu aufbaubar (Recovery, Migration).

### server_config — Konfiguration übers Webinterface
```sql
CREATE TABLE server_config (
    config_key   VARCHAR(128) PRIMARY KEY,         -- 'economy.starting_coins'
    value        JSONB NOT NULL,                   -- typflexibel
    value_type   VARCHAR(16) NOT NULL,             -- INT|STRING|BOOL|LIST|OBJECT
    scope        VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',  -- GLOBAL|SERVER:lobby...
    description  TEXT,
    updated_by   VARCHAR(64),
    version      BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE config_audit (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL,
    old_value    JSONB,
    new_value    JSONB,
    changed_by   VARCHAR(64) NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 7. Economy Event-Typen (Domain)

| Event-Typ | Bedeutung | amount-Semantik |
|-----------|-----------|-----------------|
| CREDITED | Coins gutgeschrieben | positiv, addiert |
| DEBITED | Coins abgezogen (Guthaben-Prüfung!) | positiv, subtrahiert |
| SET | Stand direkt gesetzt (Admin) | neuer absoluter Wert |
| TRANSFER_OUT | an anderen Spieler gesendet | positiv, subtrahiert |
| TRANSFER_IN | von anderem Spieler erhalten | positiv, addiert |

- `SET`: einziger Fall, wo `balance_after` nicht aus `before ± amount` folgt (Admin-Override).
- Transfer = zwei Events mit gemeinsamer `correlation_id` (in metadata), damit beide Seiten zusammengehören.

---

## 8. Caching-Strategie

- **Online-User:** Join → Balance aus Postgres in Redis laden (Warmup) → Reads gegen Redis →
  Writes IMMER durchs Backend (Postgres + Redis-Update + Pub/Sub-Event). Leave → finaler Sync, TTL auf Key.
- **Offline-User:** kein aktiver Cache; bei Bedarf (Webinterface) direkt aus Postgres, ggf. kurz gecached.
- **Live-Updates:** Backend published bei jeder Änderung ein Pub/Sub-Event; alle Backend-Nodes + Plugins
  subscriben → sofortige Cache-Invalidierung/Update über das ganze Netzwerk. Kein Polling.

### Redis-Key-Schema & Pub/Sub-Format (implementiert, Schritt 2)
- **Hot-Balance-Cache** (backend-intern, in `infra-cache`): Redis-HASH
  `mc:bal:{playerUuid}:{currencyCode}` mit Feldern `balance` (BIGINT als String) und `version`
  (= `sequence_no` des zuletzt angewandten `economy_event`). TTL `null` für aktive Spieler, beim
  Leave TTL setzen. Das Plugin liest diese Keys **nie** direkt — immer über das Backend.
- **Pub/Sub-Channel** (geteilter Contract, in `plugin-protocol.economy`): `mc:economy:balance`
  (`EconomyChannels.BALANCE`), gebaut über die Konvention `Channels.of("economy","balance")`
  (`mc:{feature}:{topic}`).
- **Event** `BalanceChangedEvent` (in `plugin-protocol.economy`, dependency-frei): `playerUuid`,
  `currencyCode`, `eventType` (String), `amount`, `balance` (=balance_after), `version`,
  `transactionId`, `source`, `correlationId`, `timestampEpochMilli`.
- **Wire-Format** (seit Schritt-1-Refactor feature-generisch, siehe Status-Abschnitt): jede Nachricht
  ist ein `MessageEnvelope` `v<n>|<messageType>|<payload>` (dependency-frei, pipe-getrennt). Für
  Balance: `messageType = economy.balance-changed`, der `payload` ist `BalanceChangedEventCodec`s
  10-Feld-Wire `playerUuid|currencyCode|eventType|amount|balance|version|transactionId|source|correlationId|ts`.
  String-Felder sind URL-encoded (UTF-8), `|` kann also nie in einem Feld vorkommen; das Envelope-Parsing
  splittet mit Limit 3, sodass Payload-Pipes erhalten bleiben. `version` trägt die globale Ordnung für
  Staleness-Checks. (Vor dem Refactor: flaches `v1|<11 Felder>` ohne messageType.)
- **Transport** (`infra-cache`, pur Lettuce): `RedisCacheAdapter.publish(channel, msg)` /
  `subscribe(channel, handler)` arbeiten auf rohen Strings; (De-)Serialisierung macht der Codec.
  `BalanceCache` kapselt put/get/version/evict auf dem HASH.

---

## 9. Nächste Schritte (Reihenfolge)

1. **Backend-Skeleton** generieren: Gradle-Multi-Module, Flyway-Migrations (Schema oben),
   docker-compose (Postgres + Redis), Domain-Aggregates, Redis-Config. Ziel: `docker-compose up` läuft.
2. **Redis-Key-Schema + Pub/Sub-Event-Format** definieren und implementieren.
3. **Plugin-Skeleton** (separates Repo, Paper 1.21) gegen Backend.
4. **Vertical Slice:** Join → Balance-Warmup → `/balance` im Spiel zeigt Wert. „Es lebt"-Moment.
5. Danach in die Breite: Webinterface (Next.js), weitere Economy-Operationen, Config-UI.

---

## Offene Punkte / später entscheiden
- `plugin-protocol`-Verteilung: **Variante B (Maven Local) ist umgesetzt** als Vorstufe — siehe
  Status-Abschnitt. Offen bleibt nur der spätere Schritt auf eine **private Maven-Registry**.
- Brauchen wir Kafka? Erst wenn Redis Pub/Sub als Event-Transport nicht mehr reicht (vermutlich lange nicht).
- Velocity plugin-messaging-Format für Server-Switch.
- Auth fürs Webinterface (vermutlich JWT/OAuth — du hast das bei Steuerfertig/League Vault schon gemacht).

---

## Status (Stand: 2026-06-20)

**Schritt 1 aus Abschnitt 9 (Backend-Skeleton) ist erledigt und lauffähig.**

### Was steht
- **Multi-Module-Gradle-Build** (Kotlin DSL, Java-21-Toolchain, Spring Boot 3.5.15) mit allen
  acht Modulen aus Abschnitt 5. Gemeinsame Konfiguration über das Convention-Plugin
  `mcplatform.java-conventions` in `buildSrc/`.
- **Abhängigkeitsrichtung** wird dadurch erzwungen, dass jedes Modul in seiner `build.gradle.kts`
  nur die erlaubten Dependencies deklariert:
  - `core-domain`, `plugin-protocol`: keine Dependencies außer JDK.
  - `application` → `core-domain`. `infra-persistence` → application + jOOQ/Flyway/Postgres (kein Spring).
  - `infra-cache` → application + Lettuce (kein Spring). `api-rest`/`api-realtime` → application + Spring Web;
    `api-rest` zusätzlich → `plugin-protocol` (geteilte, dependency-freie REST-DTOs, seit Schritt 2).
  - `app` verdrahtet alles, enthält die `main`-Klasse + Spring-Config.
- **Flyway-Migrationen** unter `infra-persistence/src/main/resources/db/migration`:
  `V1__initial_schema.sql` (alle Tabellen/Constraints/Indizes aus Abschnitt 6), `V2__seed_currency.sql`
  (Default-Währung `COINS`).
- **jOOQ-Codegen** via `dev.monosoul.jooq-docker` (8.0.26): generiert beim Build aus dem von Flyway
  migrierten Schema (Wegwerf-Postgres-16-Container). Keine laufende DB nötig, nur Docker.
- **docker-compose.yml**: Postgres 16 + Redis 7 mit Healthchecks, Volumes, Credentials aus `.env`
  (`.env.example` vorhanden). Beide Container kommen `healthy` hoch (verifiziert).
- **application.yml** mit Profilen `local` (Default) und `test`, Verbindungsdaten aus Env-Variablen.
- **Health-Endpoint** `GET /actuator/health` → 200 (Actuator).
- **Smoke-Test** (`app/.../SmokeTest.java`): Testcontainers Postgres + Redis, Flyway läuft, prüft
  Context-Load, Existenz aller Tabellen, Seed-`COINS` und `/actuator/health` == 200.
- `./gradlew build` läuft **grün** (inkl. Codegen + Smoke-Test).

### Bewusste Skeleton-Grenzen (noch KEINE Geschäftslogik)
- Generierte jOOQ-Klassen sind vorhanden, aber es ist noch **kein `DSLContext` in Spring verdrahtet**
  und es gibt keine Repository-Adapter — kommt mit der ersten Economy-Operation.
- `infra-cache` (reiner Lettuce-Adapter, framework-frei) ist **als einziger Redis-Pfad** über
  `CacheConfig` im `app`-Modul als Bean verdrahtet; ein eigener `RedisHealthIndicator` pingt darüber
  (`spring-boot-starter-data-redis` wurde entfernt — kein zweiter Lettuce-Pool). Verbindung wird lazy
  aufgebaut, d. h. der App-Start gelingt auch bei abwesendem Redis, der Health-Check zeigt dann DOWN.
  Konfiguration: `mcplatform.redis.{host,port,password}`.
- `api-rest`/`api-realtime` enthalten nur Platzhalter (`/api/ping`, leerer SSE-Stream).

### Technische Notizen / Stolpersteine
- **jOOQ-Version:** Das Codegen-Plugin erzeugt 3.21-Code; die Spring-Boot-BOM managt ein älteres
  3.19.x. `app` erzwingt daher per `resolutionStrategy.force` jOOQ `3.21.6` als Runtime-Version,
  damit generierter Code und Runtime identisch sind.
- **Port 5432:** Falls lokal belegt (eigenes Postgres), in `.env` `DB_PORT` umsetzen — compose und
  `local`-Profil teilen sich denselben Wert.

### Schritt 2 erledigt (Redis-Key-Schema + Pub/Sub-Format)
- Key-Schema, Channel, `BalanceChangedEvent` + dependency-freier `BalanceChangedEventCodec` und der
  `BalanceCache` sind implementiert — Details siehe Unterabschnitt in Abschnitt 8.
- `BalanceCache` ist als Spring-Bean in `app` (`CacheConfig`) verdrahtet.
- Tests grün: Codec-Roundtrip (inkl. Delimiter-/Unicode-Fall) in `plugin-protocol`,
  Redis-Roundtrip (ping, Balance-Cache, Pub/Sub) via Testcontainers in `infra-cache`.

### Schritt 4 erledigt (Economy-Vertical-Slice, erste Geschäftslogik)
Vollständiger Schreib-/Lesepfad CREDIT/DEBIT/SET durch alle Schichten:
- **core-domain:** `Balance`-Aggregat (credit/debit/set, niemals direkte Mutation — gibt
  `PendingEconomyEvent` zurück), `Money` (BIGINT), `TransactionId`, `AppliedEconomyEvent`,
  `InsufficientFundsException`. Guthabenprüfung lebt hier.
- **application:** Ports `EconomyEventStore`, `PlayerRepository`, `BalanceCachePort`,
  `BalanceEventPublisher` (+ `AppendResult`, `ConcurrencyConflictException`) und der Use Case
  `EconomyService` — lädt Projektion → Domäne rechnet/prüft → append mit Optimistic Locking
  (Retry bei Konflikt) → Cache-Update + Publish (beides best-effort nach Commit).
- **infra-persistence:** `JooqEconomyRepository` schreibt in EINER jOOQ-Transaktion: Idempotenz-Check
  über `transaction_id` → Event inserten (DB vergibt `sequence_no`) → `player_balance` per
  `WHERE version = :expected` projizieren (Version-Mismatch → `ConcurrencyConflictException`).
  `JooqPlayerRepository` (Upsert). Kein Spring — Transaktionen laufen über jOOQ auf der DataSource.
- **infra-cache:** `RedisBalanceCacheAdapter` implementiert `BalanceCachePort` auf dem HASH.
- **app (Composition Root):** `DSLContext`-Bean über die Spring-DataSource, Repo-/Service-Beans,
  `RedisBalanceEventPublisher` (mappt `AppliedEconomyEvent` → `plugin-protocol`-Wire → Redis-Publish;
  liegt hier, weil nur `app` sowohl plugin-protocol als auch infra-cache sehen darf).
- **api-rest:** `GET /api/players/{uuid}/balances/{currency}`, `POST .../credit|debit|set`,
  `PUT /api/players/{uuid}` (Upsert); `EconomyExceptionHandler` → 422 (insufficient funds) / 409 (conflict).
- **Tests grün:** Domain-Regeln, `EconomyService` (Retry/Insufficient/Read-Cache) mit Fakes,
  jOOQ-Integration (Optimistic Lock + Idempotenz, Testcontainers-Postgres), und ein End-to-End-Test
  in `app` (REST → Postgres → Cache → Pub/Sub, inkl. 422-Pfad).

### TRANSFER erledigt (zwei Events, gemeinsame correlation_id)
- **core-domain:** `TransferId` (correlation id; leitet deterministisch die beiden Leg-`TransactionId`s
  ab → Replay trifft dieselben Idempotenz-Keys), `Transfer.prepare` (Domain-Service: prüft distinct
  player + gleiche Währung + Funds, erzeugt TRANSFER_OUT/TRANSFER_IN), `PendingEconomyEvent`/
  `AppliedEconomyEvent` tragen jetzt eine nullable `correlationId`.
- **infra-persistence:** `transfer(...)` schreibt beide Legs + projiziert beide Balances in EINER
  jOOQ-Transaktion, beide per Optimistic Lock. `correlation_id` landet in `economy_event.metadata`
  (JSONB). Projektions-Updates laufen in deterministischer Spieler-Reihenfolge (UUID-sortiert) →
  kein Deadlock zwischen gespiegelten A→B/B→A-Transfers.
- **application/api-rest:** `EconomyService.transfer` (Retry bei Konflikt, publisht beide Legs),
  `POST /api/players/{from}/balances/{currency}/transfer` (Body: `to`, `amount`, optional
  `correlationId`, `source`). Self-Transfer/Currency-Mismatch → 400.
- **plugin-protocol:** `BalanceChangedEvent` + Codec um `correlationId` erweitert (Wire jetzt 11
  Felder, weiterhin `v1` — noch kein externer Consumer). Beide Transfer-Legs werden published, über
  die correlation_id korrelierbar.
- **Idempotenz robuster:** Der Check ist jetzt **prüfungs-first** im `EconomyService` (per
  `findByTransactionId` / `findTransfer`) — ein Replay (auch eines DEBIT, dessen Funds-Check sonst
  gegen die bereits reduzierte Balance scheitern würde) liefert das gespeicherte Ergebnis zurück,
  ohne die Domäne erneut zu rechnen. In der DB-Transaktion bleibt ein zweiter Check als Race-Schutz.
- **Tests grün:** `TransferTest` (Domain), `EconomyServiceTest` (Transfer + Idempotenz-Replay,
  DEBIT-Replay), jOOQ-Integration (atomarer Transfer, correlation_id in metadata, idempotenter Replay),
  app-E2E (Alice→Bob über REST).

### Technische Notizen
- **`-parameters`-Flag:** Controller liegen in `api-rest` (kein Spring-Boot-Plugin). Spring MVC braucht
  Parameter-Namen für `@PathVariable`; daher compiliert das `mcplatform.java-conventions`-Plugin alle
  Module mit `-parameters`.
- **Idempotenz vollständig (auch bei Gleichzeitigkeit):** Drei Ebenen — (1) prüfungs-first im
  `EconomyService`, (2) `lookup` am Transaktionsanfang im Repo, (3) als Race-Schutz fängt das Repo die
  `IntegrityConstraintViolationException` auf `uq_transaction` ab: bei zwei *zeitgleichen* Requests
  blockiert der Verlierer am Unique-Index bis der Gewinner committet, bekommt dann 23505 → das Repo
  liest das committete Ergebnis frisch nach und gibt es als idempotenten Replay zurück (kein Fehler,
  kein Doppelbuchen). Für Transfer analog über beide Legs. Belegt durch zwei Nebenläufigkeits-Tests
  (8 Threads, CyclicBarrier) für append und transfer — Ergebnis: genau eine Anwendung, kein Fehler.
  Hinweis: Der Catch muss NACH dem Rollback in einer frischen Query nachlesen, weil eine Postgres-
  Transaktion nach einem Constraint-Fehler vergiftet ist.

### Player Session Join erledigt (Spieler betritt Server → Identity + Default-Balances)
Eigener Use Case in der **Player-/Session-Domäne** (NICHT Economy), idempotent und sicher unter
parallelen Joins mehrerer Nodes (Velocity später).
- **Endpoint:** `POST /api/players/{uuid}/session/join`, Body `{ "name": "<spielername>" }`
  (wiederverwendet `PlayerRequest`). Antwort `SessionJoinResponse` = `{player, name, created, balances[]}`,
  wobei jeder Balance-Eintrag das bestehende `BalanceResponse` ist (kein paralleles DTO).
- **api-rest:** `PlayerSessionController` (dünn, nur Mapping) — bewusst getrennt vom `EconomyController`.
- **application:** `PlayerSessionService` (Use Case) orchestriert: Player-Upsert → bei Neuanlage je
  konfigurierter Währung initialisieren → Balances einsammeln. Ergebnis-Record `SessionJoin`.
  Neuer Outbound-Port `CurrencyRepository` (+ `CurrencyDefault`) liefert die konfigurierten Währungen
  mit ihrem `default_balance`.
- **Idempotenz & Atomarität (Variante A, bewusst gewählt):** Der Player-Upsert ist EIN atomares
  Statement (`INSERT … ON CONFLICT (uuid) DO UPDATE SET name, name_updated_at=now(), last_seen=now()`,
  `RETURNING (xmax = 0)`) und ist die alleinige Autorität für „neu?" — unter parallelen Joins gewinnt
  genau ein INSERT. Die Default-Balances entstehen über den **bestehenden** `EconomyService.credit`
  (event-sourced, eigene atomare TX), Quelle `source = "SYSTEM:initial"`. Der Idempotenz-Schlüssel
  wird **deterministisch** aus `(player, currency)` abgeleitet (`TransactionId.forInitialBalance`,
  `UUID.nameUUIDFromBytes`), sodass ein versehentlicher Doppel-Init am `uq_transaction`-Constraint
  abprallt statt doppelt gutzuschreiben. Kein Single-TX-Umbau des getesteten Economy-Stores
  (Reuse statt parallele Economy-Logik).
- **Startwert ist Config, kein Code:** kommt aus `currency.default_balance` (BIGINT, pro Währung),
  übers Webinterface per `UPDATE currency` änderbar. `default_balance > 0` → ein CREDITED-Event;
  `default_balance == 0` → KEIN Event (kein sinnloses CREDITED 0), aber eine konsistente
  `player_balance`-0-Zeile via neuem `EconomyEventStore.ensureZeroBalance` (Projektion bei
  Abwesenheit als 0/Version 0 anlegen — folding von null Events ist 0, also kein Direkt-Mutate).
- **infra-persistence:** `JooqPlayerRepository.upsertReturningWhetherNew` (xmax-Trick; `save`
  delegiert dorthin), `JooqCurrencyRepository`, `JooqEconomyRepository.ensureZeroBalance`.
- **app:** `CurrencyRepository`- und `PlayerSessionService`-Beans verdrahtet (`PersistenceConfig`,
  neuer `PlayerConfig`).
- **Tests grün:** `PlayerSessionJoinTest` (Testcontainers Postgres+Redis, REST + jOOQ-Assertions):
  erster Join → player + player_balance + genau ein `SYSTEM:initial`-CREDITED; zweiter Join →
  keine Doppel-Events/-Coins, aber name/name_updated_at/last_seen aktualisiert; `default_balance == 0`
  → 0-Zeile ohne Event. `./gradlew build` läuft grün.

### plugin-protocol lokal publizierbar (Variante B, Vorstufe zur privaten Registry)
- `plugin-protocol/build.gradle.kts` wendet jetzt zusätzlich `maven-publish` an (`java-library`
  kommt weiterhin aus dem Convention-Plugin) und definiert eine `MavenPublication`
  (`from(components["java"])`). group/version werden unverändert aus dem Convention-Plugin geerbt
  (`com.mcplatform` / `0.1.0-SNAPSHOT`), artifactId = Modulname `plugin-protocol`.
- `./gradlew :plugin-protocol:publishToMavenLocal` läuft grün; Artefakte (JAR + POM + Gradle-
  Module-Metadata) liegen unter `~/.m2/repository/com/mcplatform/plugin-protocol/0.1.0-SNAPSHOT/`.
- **Architektur-Check bestätigt:** Der publizierte POM enthält **keinen** `<dependencies>`-Block —
  das Modul ist framework-frei (kein Spring/jOOQ/Lettuce, nur JDK; Test-Deps werden nicht publiziert),
  konform zu Abschnitt 5.
- Alltags-Workflow dokumentiert in der README: Protokoll geändert →
  `:plugin-protocol:publishToMavenLocal` → im Plugin-Repo `build --refresh-dependencies`.
- Sonst wurde nichts geändert: nur das eine Modul angefasst, keine weiteren Build-Skripte/Module.

### COINS-Start-Bonus konfiguriert (Default-Startwert 100, via Migration)
- Neue Flyway-Migration `V3__set_coins_default.sql` (nächste freie Version nach V1/V2; keine
  bestehende Migration verändert): `UPDATE currency SET default_balance = 100 WHERE code = 'COINS'`.
- **Richtiger Ort, nicht hartcodiert:** Der Join-Service liest den Startwert über
  `CurrencyRepository` aus `currency.default_balance` — der Wert lebt also als Config-Datum und bleibt
  übers Webinterface (`UPDATE currency`) anpassbar, ohne Code-Änderung. Der Service bleibt unverändert.
- **Verifiziert (`CoinsStartBonusTest`, Testcontainers, ohne Override des Defaults):**
  (1) `currency.default_balance` für COINS == 100 nach Flyway. (2) Neuer Spieler joint mit balance=100
  und genau einem `SYSTEM:initial`-CREDITED-Event (amount=100). (3) Bestehender Spieler (Balance-Zeile
  bei 0 vor dem Bonus) wird **nicht rückwirkend** beschenkt — Join liefert `created=false`, keine
  Init-Gutschrift, balance bleibt 0, keine Events. Die Migration fasst nur `currency` an, nie
  `player_balance`. `./gradlew build` läuft grün.

### plugin-protocol feature-generisch gemacht (Schritt 1 des Plugin-Refactors)
Der Contract trägt jetzt JEDES künftige Feature über denselben Weg statt das Economy-Pipe-Format
zu duplizieren. Economy-Verhalten unverändert, nur über die neue Schicht geführt.
- **core-Schicht (`plugin-protocol.core`, generisch, kein Feature-Import):**
  `MessageEnvelope` (Framing `v<n>|messageType|payload`, `parse` splittet mit Limit 3 → Payload-Pipes
  überleben), `MessageCodec<T>` (Interface: `messageType`/`encodePayload`/`decodePayload`),
  `MessageProtocol` (löst **Routing** messageType→Codec **und Versionierung** EINMAL: `encode`,
  `decode(wire)`, typsicheres `decode(wire,codec)`, `peek`), `Channels` (Konvention `mc:{feature}:{topic}`).
- **economy-Schicht (`plugin-protocol.economy`):** `BalanceChangedEvent` (Package-Move, sonst gleich),
  `BalanceChangedEventCodec implements MessageCodec<BalanceChangedEvent>` (die früheren `v1`-Felder sind
  jetzt der versionslose 10-Feld-Payload INNERHALB des Envelopes; Version lebt im Envelope),
  `EconomyChannels.BALANCE` via `Channels.of(...)`.
- **Einziger Anstech-Punkt:** `PlatformProtocol.create()` registriert alle Feature-Codecs (heute nur
  Economy) — Backend (`RedisBalanceEventPublisher`) und Plugin bauen ihr Protocol daraus. Neues Feature
  = ein Codec hier + ein `XChannels`-Konstante + Event + Codec.
- **Wire bewusst geändert** (kein externer Consumer laut PROGRESS): Routing braucht den `messageType`
  auf dem Wire, daher `v1|economy.balance-changed|<10-Feld-Payload>` statt flachem `v1|<11 Felder>`.
  `protocolVersion` bleibt bei `1`. In einem Golden-Test gepinnt, damit jede künftige Wire-Änderung
  ein bewusster, reviewter Edit ist.
- **Tests grün:** `MessageEnvelopeTest` (Framing/Pipe-Erhalt/Reject), `MessageProtocolTest` (Routing mit
  zwei Fake-Codecs, Versions-/Typ-/Dup-Reject), `BalanceChangedEventCodecTest` (Envelope-Roundtrip ohne/
  mit correlationId, Delimiter+Unicode, **Golden-Wire**, Reject), und die app-E2E `EconomyVerticalSliceTest`
  dekodiert den neuen Wire über `PlatformProtocol.create().decode(...)` (Beweis: Backend-Consumer liest
  weiter). `./gradlew build` grün; publizierter POM weiterhin **ohne** `<dependencies>`.

### REST-Contract in plugin-protocol genormt (Schritt 2 des Plugin-Refactors)
Die REST-DTOs Plugin/Web↔Backend sind jetzt **geteilte Quelle** im Contract statt nur in `api-rest`;
plus eine generische, framework-freie Endpunkt-Beschreibung, damit der Client (Prompt 4) keine Pfade
als magische Strings streut.
- **DTOs (dependency-frei, JDK-only) im Contract:** `protocol.economy` → `BalanceResponse`,
  `AmountRequest`, `TransferRequest`, `TransferResponse`; `protocol.session` → `PlayerRequest`,
  `SessionJoinResponse` (enthält `List<BalanceResponse>`). Feldnamen = Wire-Contract; **kein** JSON im
  Modul.
- **Endpunkt-Beschreibung (generisch, `protocol.core`):** `HttpMethod`-Enum +
  `EndpointDescriptor<REQ,RES>(method, pathTemplate, requestType, responseType)` mit `expand(pathVars…)`
  (füllt `{uuid}`/`{currency}` JDK-only). Feature-Registries `EconomyEndpoints`
  (GET_BALANCE/CREDIT/DEBIT/SET/TRANSFER) und `SessionEndpoints` (UPSERT_PLAYER=PUT 204, JOIN). Neues
  Feature = eigene `XEndpoints`-Konstanten über denselben Descriptor.
- **Geteilte Quelle umgesetzt (Option A, bewusst):** Die alten `api-rest`-DTO-Records sind gelöscht;
  `api-rest` hängt jetzt an `:plugin-protocol` und benutzt dessen Records direkt. Die domänen-gekoppelte
  Abbildung (war als Factory-/Helper-Methoden auf den Records) liegt jetzt in dünnen Backend-Mappern
  `api/rest/support/EconomyMapper` + `SessionMapper` — die protocol-Records bleiben pure Daten. JSON
  unverändert (Jackson nutzt Record-Komponenten, paketunabhängig) → **kein API-Verhalten geändert**.
- **Proof-Weg begründet:** protocol bleibt JSON-frei, daher kein JSON-Test dort. Da es nach Option A nur
  noch EINEN DTO-Typ gibt, wäre „Feldabgleich Backend↔protocol" eine Tautologie → stattdessen
  JSON-Roundtrip-Test im `app` (`@JsonTest`, Spring-Jackson, kein Container): `RestDtoJsonContractTest`
  (4 grün) prüft exakte Feldnamen + De-/Serialisierung je DTO. In `plugin-protocol` ein **rein-JDK**
  `EndpointDescriptorTest` (3 grün) für `expand`/Typen/Reject. Zusätzlicher Live-Beweis: die
  bestehenden E2E-Suiten (`EconomyVerticalSliceTest`, `PlayerSessionJoinTest`, `CoinsStartBonusTest`)
  deserialisieren echtes HTTP-JSON jetzt in die protocol-DTOs → grün = kompatibel.
- `./gradlew build` grün; publizierter `plugin-protocol`-POM weiterhin **ohne** `<dependencies>`.

### Schritt 3 erledigt (Plugin-Skeleton — separates Repo `mc-platform-plugin`)
Das Paper-1.21-Plugin steht als reiner Client auf dem geteilten Contract. Es liegt im **separaten
Repo `mc-platform-plugin/`** (Gradle Kotlin DSL, Java-21-Toolchain wie das Backend) und zieht
`plugin-protocol` über **Maven Local** (`com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT`, `mavenLocal()`
im Repo-Setup, Variante B). Der Contract wird per `com.gradleup.shadow` in die Plugin-JAR gebündelt,
weil Papers Library-Loader Maven-Local-Artefakte nicht erreicht.

### Was steht
- **Drei Schichten, strikt getrennt** (Abhängigkeit: feature → transport/platform → `plugin-protocol`,
  nie zurück):
  - `…plugin.platform` — die einzige Bukkit/Paper-Berührung: `McPlatformPlugin` (JavaPlugin-Main +
    Composition Root), `PlatformScheduler` (Interface `runSync`/`runAsync`) + Paper-Impl,
    `PluginConfig`, sowie Listener-/Command-Adapter am `FeatureContext`. Features rufen nie
    `Bukkit.getScheduler()` o. Ä. direkt.
  - `…plugin.transport` — generisch & feature-agnostisch: `BackendClient`, `EventBus`,
    `FeatureCache<K,V>` (+ Impls); JSON gekapselt hinter `JsonCodec` (Gson) — weder Features noch
    `plugin-protocol` sehen Gson.
  - `…plugin.feature` — `PluginFeature`-SPI, `FeatureContext`, `FeatureRegistry`; `feature.economy`
    als erstes echtes Feature.
- **Generischer `BackendClient` (REST):** `HttpBackendClient` über JDK-`HttpClient`, jeder Call async
  via `PlatformScheduler` (Main-Thread nie blockiert). URL + Methode kommen **ausschließlich** aus dem
  `EndpointDescriptor` (`ep.method()` + `ep.expand(pathVars…)`), (De)Serialisierung aus
  `requestType()/responseType()` — keine Pfade als magische Strings. Genormtes Fehler-Modell
  `BackendException` (422 InsufficientFunds / 409 Conflict / 400 BadRequest / 404 NotFound /
  5xx+Transport BackendError, Body durchgereicht), passend zum `EconomyExceptionHandler`. Timeouts
  (connect + request) konfigurierbar; Retry **nur idempotent**: GET automatisch, Writes nur über
  `callIdempotent` (Caller garantiert stabile `transactionId`/`correlationId`, Backend dedupliziert).
- **Generischer `EventBus` (Live-Updates, lesend):** `LettuceEventBus` subscribt auf
  protocol-Channels (Start: `EconomyChannels.BALANCE` / `mc:economy:balance`); Roh-Strings →
  `PlatformProtocol`-Decode → Routing nach `messageType` an getypte Handler. **Version-aware** über das
  generische `FeatureCache.put` (out-of-order-Event überschreibt einen neueren Stand nie, vgl.
  Abschnitt 8). Lettuce-Auto-Reconnect + automatisches **Resubscribe** nach (Re)connect. Das Plugin
  **subscribt nur** — kein Publish, kein direkter `mc:bal:*`-HASH-Zugriff; Initialwerte kommen per REST
  beim Join.
- **`FeatureRegistry`:** hält die Feature-Liste, enabled in Registrierungsreihenfolge, disabled
  umgekehrt, Fehler isoliert (ein kaputtes Feature reißt die anderen nicht mit). **Der eine
  Anstech-Punkt.**
- **Economy als erstes `PluginFeature`** (zugleich die Plugin-Seite von Schritt 4): `PlayerJoinListener`
  (→ `SessionEndpoints.JOIN`, Cache-Warmup aus `SessionJoinResponse.balances` inkl. 100-COINS-Bonus),
  `/balance` (cache-first, REST-Fallback `EconomyEndpoints.GET_BALANCE`), Subscription auf
  `mc:economy:balance` (version-checked Cache-Update) und `PlayerQuitListener` (Cache-Evict, **kein**
  Redis-Write). Der Balance-Cache ist eine Instanz des generischen `FeatureCache` — nichts
  Economy-Eigenes (beweist, dass das Muster trägt).

### „So steckt man ein neues Feature an" (Beispiel `cosmetics`)
1. Neues Package `…plugin.feature.<x>` mit `XFeature implements PluginFeature`: `id()` +
   `onEnable(context)`, das seine Listener/Commands/Subscriptions ausschließlich über den
   `FeatureContext` (`backend()`, `eventBus()`, `scheduler()`, `registerListener/registerCommand`)
   registriert.
2. **Eine Zeile** in der `FeatureRegistry` in `McPlatformPlugin`: `.register(new XFeature())`.
3. Nur falls neues Wire nötig: im `plugin-protocol` Channel-Konstante + Event/`MessageCodec` +
   DTOs/`XEndpoints`, und den Codec in `PlatformProtocol.create()`.
Kein Anfassen von `platform`/`transport`/Main (außer der einen `.register`-Zeile).

### Bewusste Grenzen
- Die Akzeptanz „es lebt" (Schritt 4: Join → Warmup → `/balance` live) ist vollständig verdrahtet,
  aber **noch nicht gegen einen laufenden Paper-Node + Backend + Redis end-to-end verifiziert** —
  Laufzeit-Check steht aus.
- Slice auf eine Währung (`COINS`); Cache keyed per Spieler-UUID. Mehrwährung später.
- Über die einfache idempotente Retry-Policy hinaus kein Circuit-Breaker.
- `plugin-protocol`-Verteilung weiterhin Maven Local (Variante B); private Registry später.

### Technische Notizen
- Fat-JAR via `com.gradleup.shadow`: `plugin-protocol` gebündelt (nicht relocated — eigener
  Namespace), Lettuce/Netty/Reactor/Gson relocated unter `com.mcplatform.plugin.libs.*` (kein Clash mit
  Papers oder anderen Plugins' Kopien). Gson + slf4j von Lettuce exkludiert (Paper stellt sie bereit).
- Threading strikt: alle I/O async (`HttpClient`/Lettuce), Übergänge in den Bukkit-Main nur über
  `PlatformScheduler`.
- `plugin.yml`: `main = com.mcplatform.plugin.platform.McPlatformPlugin`, `balance`-Command,
  `api-version: '1.21'`. `config.yml`: `backend.base-url` + Timeouts/Retries, `redis.host/port/password`.

### Tests grün (`./gradlew build`, 31 Tests)
- `FeatureCacheTest` — version-aware: neueres `version` gewinnt, älteres verworfen, gleiches idempotent.
- `FeatureRegistryTest` — Enable-Reihenfolge, Disable umgekehrt, ein im `onEnable` werfendes Feature
  isoliert (andere laufen).
- `HttpBackendClientTest` — lokaler JDK-`HttpServer`: GET-Deserialisierung, 422/409/404/400-Mapping,
  204 ohne Body-Parse, Retry idempotent (GET / `callIdempotent`) vs. kein Retry (Write via `call`),
  Request-Timeout → Retry.
- `EventDispatcherTest` — Decode/Routing, out-of-order via `FeatureCache`, unparsbar/fremder Typ
  isoliert.
- `LettuceEventBusIT` — Testcontainers-Redis: publish→getyptes Event (Roundtrip über denselben
  protocol-Codec wie das Backend), out-of-order verändert Cache nicht, Resubscribe nach erzwungenem
  `CLIENT KILL TYPE pubsub`. (Docker-gated via `assumeTrue` → grün/skipped ohne Docker.)
- `EconomyBalancesTest` — Warmup nimmt nur die Slice-Währung, version-checked Apply.

### Nächster Schritt
- **Schritt 4 — Laufzeit-Verifikation („es lebt"):** Plugin auf einem Paper-1.21-Node gegen das per
  `docker-compose` laufende Backend + Redis: Join → `session/join` → Cache-Warmup → `/balance` zeigt
  100 COINS → eine REST-/Web-seitige Balance-Änderung pusht live über `mc:economy:balance` in den
  In-Game-Cache, `/balance` zeigt den neuen Wert ohne Rejoin.
- **Danach (Schritt 5):** SSE-Live-Push ans Webinterface (subscribt ebenfalls auf
  `mc:economy:balance`) und Config-CRUD/-Audit; in die Breite weitere Economy-Operationen und Features
  (jeweils über denselben FeatureRegistry-Anstech-Punkt).

## 10. Menü-Framework (Inventar-GUIs, Adventure) + Feature-Menüs

Umsetzung von `MENU_DESIGN.md` als wiederverwendbares Framework in `platform/menu` — **ohne Änderung
an einer einzigen generischen Klasse** (FeatureContext, FeatureRegistry, PluginFeature, FeatureCache,
EventBus, BackendClient, PlatformScheduler bleiben unangetastet). Der `MenuManager` wird im
Composition-Root (`McPlatformPlugin`) erzeugt, einmal als Listener registriert und per Konstruktor in
die Features injiziert — das ist der Anstech-Punkt, kein Sonderweg.

### Kern-Framework
- **Reines Modell (Bukkit-frei, unit-testbar):** `Menu` (Größe/Titel/Slot→`MenuItem`, Routing),
  `MenuItem` (Icon + Handler **pro `ClickAction`** — kein globales switch), `MenuBuilder`
  (Border-Füllung, feste Slots 4/48/49/45/53, 7×4-Paginierung), `ConfirmDialog` (§2.5; kritisch =
  Doppelklick), `Pagination`, `MenuLayout`, semantische Tokens/Icons/Feedback, `Lore`/`LoreLine`
  (mehrfarbiger Aktions-Hinweis §3.3), `PlayerPickerMenu` (geteilter paginierter Spieler-Picker).
- **Bukkit/Adventure-Schicht (nur in-game, nie von Tests geladen):** `MenuManager` (DER eine
  Click/Close/Drag-Listener, cancelt immer, routet, LIVE-Sub/-Unsub), `MenuRenderer`, `MenuStyle`
  (Token→Farbe, Icon→Material, Feedback→Sound, Component-Bau, Italic aus), `MenuHolder`,
  `BukkitMenuView`. `compileOnly` paper-api ⇒ Tests laufen ohne Server, daher die strikte Trennung.
- **LIVE vs. STATIC (§6):** `MenuLiveBus` (keyed Observer-Fan-out) + `OpenMenuTracker` melden beim
  Close sauber ab — kein Observer-Leak (explizit für 200 Spieler getestet). LIVE-Daten kommen über
  denselben EventBus→`FeatureCache`-Strom; das Feature ruft nach dem Cache-Update
  `menus.liveBus().notifyChange(topic)`. **Generik-Lücke gemeldet:** `FeatureCache`/`EventBus` haben
  keinen Observer-/Unsubscribe-Hook, deshalb sitzt die Beobachtung im Menü-Framework statt im Cache.

### Spielerseitig (jeder für sich)
- **Balance-Menü (LIVE, Single-Value):** `/balance` öffnet ein Menü; Wert-Slot aktualisiert sich live
  bei Balance-Änderung. *LIVE, weil Einzelwert, der sich durch fremde Transaktionen ändert.*
- **Transfer-Flow:** `/pay` → Spieler-Picker → `TransferMenu` (Wert-Editor −/+ mit Shift-Schritt,
  §4.6, keine Eingabe neu erfunden) → Confirm-Dialog → `TRANSFER`. **422** (insufficient) und **400**
  (Self-Transfer/ungültig) werden sauber im Menü gezeigt. *STATIC, Wert ändert sich nur durch eigene
  Klicks.*

### Team-seitig (hinter dem Backend-Gate, UI-Gate optimistisch)
- **Punish-Menü:** `/punishmenu [player]` → ggf. Picker → `PunishMenu`: Templates aus `GET /templates`,
  **`canApply=true` interaktiv/hervorgehoben, sonst sichtbar-aber-gesperrt** (`IRON_BARS`, muted);
  inaktive Templates ausgeblendet → Confirm → `from-template`. **403** (keine Berechtigung) / **409**
  (bereits aktiv) sauber im Menü. *STATIC (Snapshot).*
- **History-Menü:** `/punishments <player>` — paginierte Liste, **aktiv vs. abgelaufen** optisch
  getrennt (aktiv: Schwert/Sperr-Icon, rot, klickbar; abgelaufen: Buch, muted, nur Anzeige), Revoke
  über kritischen Confirm-Dialog (Doppelklick), respektiert das Backend-`REVOKE`-Gate (403). *STATIC.*
- **Template-Verwaltung (CRUD):** bewusst **nicht** im Menü umgesetzt — gehört ins Webinterface
  (Backend-Template-CRUD); nur Auswahl/Anwendung läuft übers Plugin-Menü.

### Quer
- **Hub-Menü:** `/menu` zeigt nur erlaubte Einträge (Economy für alle; Strafverwaltung nur mit
  `mcplatform.punish`). Optimistisches UI-Gate; jede echte Aktion bleibt backend-geprüft. Einträge
  starten die jeweiligen Feature-Commands → Hub bleibt von den Feature-Menüs entkoppelt. *STATIC.*

### Threading & Lade-Zustand (alle Menüs)
- Inventar-Ops im Main-Thread; Daten async über `BackendClient`, Aufbau via `PlatformScheduler` zurück
  in Main. Menüs öffnen sofort in „Lade…" und füllen sich, wenn die Daten da sind — Main nie blockiert.

### Tests grün (`./gradlew build`, 113 Tests, 0 Fehler)
- Framework: `MenuLayoutTest`, `PaginationTest`, `MenuBuilderTest`, `MenuRoutingTest`,
  `ConfirmDialogTest`, `MenuLiveBusTest` (+ Leak/200), `OpenMenuTrackerTest`, `PlayerPickerMenuTest`.
- Economy: `BalanceMenuTest` (LIVE-Slot-Update), `EconomyMenuTextTest`, `TransferMenuTest`
  (Wert-Editor-Grenzen, Confirm, **422-Pfad**, Success-Close).
- Punishments: `PunishmentHistoryMenuTest` (Paginierung/Confirm/**403**/Success),
  `PunishMenuTest` (**canApply-Gating**, **403**-Pfad, Success), `PunishmentMenuTextTest` (403/409/404).
- Hub: `HubMenuTest` (berechtigter vs. unberechtigter Spieler → unterschiedliche Einträge).
- **Bestätigt:** Alle Menüs ohne Änderung an generischen Klassen; `MenuManager` ist der einzige
  `InventoryClickEvent`-Listener.
