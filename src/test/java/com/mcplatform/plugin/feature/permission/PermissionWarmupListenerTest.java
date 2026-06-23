package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Proves the PreLogin warmup is fail-closed at its testable core: a successful backend load fills the
 * cache and returns {@code true} (login stays allowed); a backend failure returns {@code false} (the
 * listener then hard-kicks) and leaves the cache cold — no half-warmed state. The thin
 * {@code onPreLogin} glue that maps {@code false}→{@code disallow} needs a live server and is verified
 * manually.
 */
class PermissionWarmupListenerTest {

    private final Logger logger = Logger.getAnonymousLogger();

    private static final class FixedBackend implements BackendClient {
        private final CompletableFuture<Object> result;
        FixedBackend(CompletableFuture<Object> result) { this.result = result; }

        @Override
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException("warmup uses callIdempotent");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) result;
        }
    }

    @Test
    void successFillsCacheAndReturnsTrue() {
        UUID uuid = UUID.randomUUID();
        PlayerPermissionsResponse response = new PlayerPermissionsResponse(uuid,
                List.of(), List.of(), List.of("mcplatform.permission.roles.manage"), null);
        PermissionCache cache = new PermissionCache();
        PermissionWarmupListener warmup = new PermissionWarmupListener(
                new FixedBackend(CompletableFuture.completedFuture(response)), cache, logger);

        assertTrue(warmup.warmup(uuid));
        assertTrue(cache.get(uuid).isPresent(), "cache filled before world entry");
        assertTrue(cache.get(uuid).orElseThrow().has("mcplatform.permission.roles.manage"));
    }

    @Test
    void backendFailureReturnsFalseAndLeavesCacheCold() {
        UUID uuid = UUID.randomUUID();
        PermissionCache cache = new PermissionCache();
        PermissionWarmupListener warmup = new PermissionWarmupListener(
                new FixedBackend(CompletableFuture.failedFuture(new RuntimeException("backend down"))),
                cache, logger);

        assertFalse(warmup.warmup(uuid), "fail-closed");
        assertTrue(cache.get(uuid).isEmpty(), "no half-warmed cache entry");
    }

    @Test
    void emptyResponseReturnsFalse() {
        UUID uuid = UUID.randomUUID();
        PermissionCache cache = new PermissionCache();
        PermissionWarmupListener warmup = new PermissionWarmupListener(
                new FixedBackend(CompletableFuture.completedFuture(null)), cache, logger);

        assertFalse(warmup.warmup(uuid), "empty response treated as failure");
        assertTrue(cache.get(uuid).isEmpty());
    }
}
