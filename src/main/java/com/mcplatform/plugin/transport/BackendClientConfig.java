package com.mcplatform.plugin.transport;

import java.time.Duration;

/**
 * Tuning for {@link HttpBackendClient}: base URL, connect/request timeouts, and the retry budget for
 * idempotent calls. {@code maxRetries} is the number of EXTRA attempts after the first (so total
 * attempts = {@code maxRetries + 1}); only idempotent calls and transient failures are retried.
 */
public record BackendClientConfig(
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxRetries,
        Duration retryBackoff) {

    public BackendClientConfig {
        // Normalise: drop a single trailing slash so expanded paths (which start with '/') join cleanly.
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    /** Sensible defaults: 5s connect/request timeout, 2 retries, 200ms backoff. */
    public static BackendClientConfig defaults(String baseUrl) {
        return new BackendClientConfig(
                baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(5), 2, Duration.ofMillis(200));
    }
}
