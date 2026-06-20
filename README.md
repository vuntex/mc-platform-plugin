# mc-platform-plugin

A Paper 1.21.10 plugin that acts as a **pure HTTP client** against the
mc-platform Spring Boot economy backend. No DB, no Spring, no Redis, no cache —
just REST.

## What this slice does

- On player join → reports the join to the backend (`POST /session/join`,
  fire-and-forget).
- On `/balance` → reads the player's `COINS` balance from the backend
  (`GET /balances/COINS`) and shows it in chat.

All backend calls run **off the main thread**; player messages are sent back
**on the main thread**. The `HttpClient` is built once and reused.

## Requirements

- Java 21
- A running backend at `http://localhost:8080` (configurable, see below)
- `com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT` published to your local Maven
  repo (`~/.m2`). The backend publishes it (Variante B / mavenLocal).

> Note: this first slice ships its own slim `BalanceResponse` record, because
> the shared `plugin-protocol` module currently only exposes a Pub/Sub
> `BalanceMessage` whose field names don't match the REST payload. The
> dependency is declared so it's ready to adopt later. Because no protocol class
> is loaded at runtime yet, the jar does **not** bundle it — once you actually
> use a shared class at runtime, add a shadow/relocate step.

## Build

```bash
./gradlew build
```

The plugin jar lands in:

```
build/libs/mc-platform-plugin-0.1.0-SNAPSHOT.jar
```

## Install

Copy the jar into your test server's plugins folder:

```bash
cp build/libs/mc-platform-plugin-0.1.0-SNAPSHOT.jar test-server/plugins/
```

Then start (or restart) the Paper server.

## Configure

`plugins/McPlatformPlugin/config.yml` is written on first run:

```yaml
backend:
  base-url: http://localhost:8080
```

Change `base-url` and run `/reload` or restart if your backend lives elsewhere.

## Test

1. Make sure the backend is running on `http://localhost:8080`.
2. Start the Paper server with the jar installed and log in with a client.
3. Type `/balance` in chat — you should see e.g. `Balance: 0 COINS`.

If the backend is down or returns a non-2xx status, you'll see
`Balance gerade nicht verfügbar` and the cause is logged to the server console
(no stacktrace is shown to the player).
