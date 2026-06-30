package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a player's effective permissions + display from the backend ({@code GET …/effective}) into the
 * {@link PermissionCache}. The REST call runs async via the {@link PlatformScheduler}; the cache write
 * hops back to the main thread. The main thread is never blocked on I/O (spec FR-002).
 */
public final class PermissionLoader {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionCache cache;
    private final Logger logger;

    public PermissionLoader(BackendClient backend, PlatformScheduler scheduler,
                            PermissionCache cache, Logger logger) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.logger = logger;
    }

    /** Load (or refresh) the player's view, recording it at {@code version} (version-aware cache). */
    public void load(UUID player, long version) {
        backend.call(PermissionEndpoints.EFFECTIVE, null, player.toString())
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null) {
                        logger.log(Level.FINE, "Failed to load effective permissions for " + player, error);
                        return;
                    }
                    if (response != null) {
                        cache.apply(player, PlayerPermissionsView.from(response), version);
                    }
                }));
    }

    /** Alias for {@link #load} used by the live updater on {@code mc:permission:changed}. */
    public void reload(UUID player, long version) {
        load(player, version);
    }
}
