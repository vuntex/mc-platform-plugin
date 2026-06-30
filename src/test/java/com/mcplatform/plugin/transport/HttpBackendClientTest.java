package com.mcplatform.plugin.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.economy.AmountRequest;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.EconomyEndpoints;
import com.mcplatform.protocol.session.PlayerRequest;
import com.mcplatform.protocol.session.SessionEndpoints;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the generic REST client end-to-end against a real JDK {@link HttpServer} on loopback — no
 * Spring, no running backend. Covers: descriptor-derived URL/method, JSON deserialization, the
 * status→exception mapping, 204 No Content handling, and the retry policy (idempotent retries,
 * non-idempotent does not).
 */
class HttpBackendClientTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TXN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private final JsonCodec json = new GsonJsonCodec();

    /** runSync inline; runAsync on its own thread so the client never relies on the caller thread. */
    private final PlatformScheduler scheduler = new PlatformScheduler() {
        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            Thread thread = new Thread(task, "test-async");
            thread.setDaemon(true);
            thread.start();
        }
    };

    private HttpServer server;
    private String baseUrl;

    // What the server received / will answer.
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> seenMethod = new AtomicReference<>();
    private final AtomicReference<String> seenPath = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "";
    private volatile long handlerSleepMillis = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Cached pool so a slow/sleeping handler never blocks concurrent retry attempts.
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        seenMethod.set(exchange.getRequestMethod());
        seenPath.set(exchange.getRequestURI().getPath());
        try (InputStream in = exchange.getRequestBody()) {
            seenBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (handlerSleepMillis > 0) {
            try {
                Thread.sleep(handlerSleepMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, responseStatus == 204 ? -1 : bytes.length);
        if (responseStatus != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private BackendClient client() {
        return new HttpBackendClient(json, scheduler,
                new BackendClientConfig(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(2),
                        2, Duration.ofMillis(10)));
    }

    private BackendClient clientWithRequestTimeout(Duration requestTimeout) {
        return new HttpBackendClient(json, scheduler,
                new BackendClientConfig(baseUrl, Duration.ofSeconds(2), requestTimeout,
                        2, Duration.ofMillis(10)));
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private Throwable awaitCause(CompletableFuture<?> future) {
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        return thrown.getCause();
    }

    @Test
    void getBalanceSuccessDeserializes() throws Exception {
        responseBody = json.toJson(new BalanceResponse(PLAYER, "COINS", 250L, 12L));

        BalanceResponse result = await(
                client().call(EconomyEndpoints.GET_BALANCE, null, PLAYER.toString(), "COINS"));

        assertEquals("GET", seenMethod.get());
        assertEquals("/api/players/" + PLAYER + "/balances/COINS", seenPath.get());
        assertEquals(new BalanceResponse(PLAYER, "COINS", 250L, 12L), result);
    }

    @Test
    void deleteWithBodySendsTheRequestBody() throws Exception {
        // REVOKE_PERMISSION is DELETE with a body — the body must actually reach the server.
        responseBody = json.toJson(new com.mcplatform.protocol.permission.PlayerPermissionsResponse(
                PLAYER, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null));

        Object result = await(client().call(
                com.mcplatform.protocol.permission.PermissionEndpoints.REVOKE_PERMISSION,
                new com.mcplatform.protocol.permission.RevokePermissionRequest("mcplatform.fly", null, PLAYER),
                PLAYER.toString()));

        assertEquals("DELETE", seenMethod.get());
        assertEquals("/api/permission/players/" + PLAYER + "/permissions", seenPath.get());
        assertTrue(seenBody.get().contains("mcplatform.fly"), seenBody.get());
        assertTrue(result != null);
    }

    @Test
    void deleteWithoutBodySendsNoBody() throws Exception {
        responseStatus = 204;

        await(client().call(com.mcplatform.protocol.permission.PermissionEndpoints.DELETE_ROLE, null,
                java.util.Map.of("actor", PLAYER.toString()), "7"));

        assertEquals("DELETE", seenMethod.get());
        assertEquals("", seenBody.get());
    }

    @Test
    void status422MapsToInsufficientFunds() {
        responseStatus = 422;
        responseBody = "{\"error\":\"insufficient_funds\"}";

        Throwable cause = awaitCause(
                client().callIdempotent(EconomyEndpoints.DEBIT,
                        new AmountRequest(999L, TXN, "PLUGIN:test"), PLAYER.toString(), "COINS"));

        BackendException.InsufficientFunds ex =
                assertInstanceOf(BackendException.InsufficientFunds.class, cause);
        assertEquals(422, ex.statusCode());
        assertTrue(ex.responseBody().contains("insufficient_funds"), ex.responseBody());
    }

    @Test
    void status409MapsToConflict() {
        responseStatus = 409;
        responseBody = "version conflict";

        Throwable cause = awaitCause(
                client().callIdempotent(EconomyEndpoints.SET,
                        new AmountRequest(10L, TXN, "PLUGIN:test"), PLAYER.toString(), "COINS"));

        BackendException.Conflict ex = assertInstanceOf(BackendException.Conflict.class, cause);
        assertEquals(409, ex.statusCode());
        assertTrue(ex.responseBody().contains("conflict"), ex.responseBody());
    }

    @Test
    void status404MapsToNotFound() {
        responseStatus = 404;
        responseBody = "no such player";

        Throwable cause = awaitCause(
                client().call(EconomyEndpoints.GET_BALANCE, null, PLAYER.toString(), "COINS"));

        BackendException.NotFound ex = assertInstanceOf(BackendException.NotFound.class, cause);
        assertEquals(404, ex.statusCode());
    }

    @Test
    void status400MapsToBadRequest() {
        responseStatus = 400;
        responseBody = "self transfer";

        Throwable cause = awaitCause(
                client().call(EconomyEndpoints.GET_BALANCE, null, PLAYER.toString(), "COINS"));

        assertInstanceOf(BackendException.BadRequest.class, cause);
    }

    @Test
    void putPlayer204SucceedsWithoutBodyParse() throws Exception {
        responseStatus = 204; // UPSERT_PLAYER returns 204 No Content

        Void result = await(
                client().callIdempotent(SessionEndpoints.UPSERT_PLAYER,
                        new PlayerRequest("Steve"), PLAYER.toString()));

        assertEquals("PUT", seenMethod.get());
        assertEquals("/api/players/" + PLAYER, seenPath.get());
        assertTrue(seenBody.get().contains("\"name\":\"Steve\""), seenBody.get());
        assertNull(result);
    }

    @Test
    void idempotentGetRetriesOnTransientFailure() {
        responseStatus = 503; // transient → retryable for an idempotent call

        Throwable cause = awaitCause(
                client().call(EconomyEndpoints.GET_BALANCE, null, PLAYER.toString(), "COINS"));

        assertInstanceOf(BackendException.BackendError.class, cause);
        assertEquals(3, requestCount.get()); // 1 + maxRetries(2)
    }

    @Test
    void nonIdempotentWriteDoesNotRetry() {
        responseStatus = 503;

        Throwable cause = awaitCause(
                // plain call() on a POST write → sent exactly once, no retry
                client().call(EconomyEndpoints.CREDIT,
                        new AmountRequest(100L, TXN, "PLUGIN:test"), PLAYER.toString(), "COINS"));

        assertInstanceOf(BackendException.BackendError.class, cause);
        assertEquals(1, requestCount.get());
    }

    @Test
    void idempotentWriteRetriesWhenCallerAsserts() {
        responseStatus = 503;

        Throwable cause = awaitCause(
                // callIdempotent() asserts a stable transactionId → retryable
                client().callIdempotent(EconomyEndpoints.CREDIT,
                        new AmountRequest(100L, TXN, "PLUGIN:test"), PLAYER.toString(), "COINS"));

        assertInstanceOf(BackendException.BackendError.class, cause);
        assertEquals(3, requestCount.get());
    }

    @Test
    void requestTimeoutOnIdempotentCallRetries() {
        responseStatus = 200;
        responseBody = json.toJson(new BalanceResponse(PLAYER, "COINS", 1L, 1L));
        handlerSleepMillis = 600; // longer than the request timeout below

        Throwable cause = awaitCause(
                clientWithRequestTimeout(Duration.ofMillis(150))
                        .call(EconomyEndpoints.GET_BALANCE, null, PLAYER.toString(), "COINS"));

        // Timeout is a transport failure (status 0); idempotent GET retries the full budget.
        BackendException.BackendError ex = assertInstanceOf(BackendException.BackendError.class, cause);
        assertEquals(0, ex.statusCode());
        assertEquals(3, requestCount.get());
    }
}
