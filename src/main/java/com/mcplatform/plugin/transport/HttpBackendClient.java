package com.mcplatform.plugin.transport;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.core.HttpMethod;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@link BackendClient} over the JDK {@link HttpClient}. Every call runs off the main thread via
 * {@link PlatformScheduler#runAsync} (so the Bukkit main thread is never blocked), performs a
 * blocking request + bounded retries, and completes a {@link CompletableFuture}. The result/error is
 * delivered off-main; features hop back to the main thread via {@link PlatformScheduler#runSync}
 * where they touch Bukkit API (Prinzip 5).
 *
 * <p>URL and method come solely from the {@link EndpointDescriptor}; (de)serialization uses its
 * {@code requestType}/{@code responseType} through the {@link JsonCodec}. A {@link Void} response
 * type or an empty body (e.g. {@code 204 No Content} for {@code UPSERT_PLAYER}) yields {@code null}
 * without any parse attempt. Non-2xx and transport failures map to {@link BackendException}.
 */
public final class HttpBackendClient implements BackendClient {

    private final HttpClient httpClient;
    private final JsonCodec json;
    private final PlatformScheduler scheduler;
    private final BackendClientConfig config;

    public HttpBackendClient(JsonCodec json, PlatformScheduler scheduler, BackendClientConfig config) {
        this.json = json;
        this.scheduler = scheduler;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build();
    }

    @Override
    public <REQ, RES> CompletableFuture<RES> call(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars) {
        // GET is the only method that is idempotent without an explicit caller assertion.
        return dispatch(endpoint, body, endpoint.method() == HttpMethod.GET, Map.of(), pathVars);
    }

    @Override
    public <REQ, RES> CompletableFuture<RES> call(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, Map<String, String> query, String... pathVars) {
        return dispatch(endpoint, body, endpoint.method() == HttpMethod.GET, query, pathVars);
    }

    @Override
    public <REQ, RES> CompletableFuture<RES> callIdempotent(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, String... pathVars) {
        return dispatch(endpoint, body, true, Map.of(), pathVars);
    }

    private <REQ, RES> CompletableFuture<RES> dispatch(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, boolean idempotent,
            Map<String, String> query, String... pathVars) {
        CompletableFuture<RES> future = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            try {
                future.complete(sendWithRetries(endpoint, body, idempotent, query, pathVars));
            } catch (BackendException ex) {
                future.completeExceptionally(ex);
            } catch (RuntimeException ex) {
                future.completeExceptionally(
                        BackendException.transportFailure("Unexpected client error: " + ex.getMessage(), ex));
            }
        });
        return future;
    }

    private <REQ, RES> RES sendWithRetries(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, boolean idempotent,
            Map<String, String> query, String... pathVars) {
        int maxAttempts = idempotent ? config.maxRetries() + 1 : 1;
        BackendException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return sendOnce(endpoint, body, query, pathVars);
            } catch (BackendException ex) {
                last = ex;
                boolean retryable = ex instanceof BackendException.BackendError;
                if (!idempotent || !retryable || attempt == maxAttempts) {
                    throw ex;
                }
                backoff(attempt);
            }
        }
        throw last; // unreachable: the loop either returns or throws on the last attempt
    }

    private <REQ, RES> RES sendOnce(
            EndpointDescriptor<REQ, RES> endpoint, REQ body, Map<String, String> query, String... pathVars) {
        String path = endpoint.expand((Object[]) pathVars);
        URI uri = URI.create(config.baseUrl() + path + queryString(query));

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json");

        BodyPublisher publisher;
        if (body == null) {
            publisher = BodyPublishers.noBody();
        } else {
            builder.header("Content-Type", "application/json");
            publisher = BodyPublishers.ofString(json.toJson(body), StandardCharsets.UTF_8);
        }
        applyMethod(builder, endpoint.method(), publisher);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            // Includes HttpTimeoutException (request timeout) and connection failures → transient.
            throw BackendException.transportFailure(
                    endpoint.method() + " " + uri + " failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BackendException.transportFailure(
                    endpoint.method() + " " + uri + " interrupted", ex);
        }

        int status = response.statusCode();
        if (status / 100 != 2) {
            throw BackendException.fromStatus(status, response.body());
        }
        if (endpoint.responseType() == Void.class) {
            return null;
        }
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        return json.fromJson(responseBody, endpoint.responseType());
    }

    /** Build a {@code ?k=v&k2=v2} string from {@code query}; skips null/blank values, URL-encodes both. */
    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            sb.append(sb.isEmpty() ? '?' : '&')
                    .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void applyMethod(HttpRequest.Builder builder, HttpMethod method, BodyPublisher publisher) {
        switch (method) {
            case GET -> builder.GET();
            case DELETE -> builder.DELETE();
            case POST -> builder.POST(publisher);
            case PUT -> builder.PUT(publisher);
            case PATCH -> builder.method("PATCH", publisher);
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(config.retryBackoff().toMillis() * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BackendException.transportFailure("Retry backoff interrupted", ex);
        }
    }
}
