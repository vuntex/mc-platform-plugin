package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a player's effective permissions + display from the backend ({@code GET …/effective}) into the
 * {@link PermissionCache}. The REST call runs async via the {@link PlatformScheduler}; the cache write
 * hops back to the main thread. The main thread is never blocked on I/O (spec FR-002).
 *
 * <p>After a successful cache write it invokes an optional {@code onApplied} callback (on the main
 * thread) — used to fan a live notification out to observers (e.g. the scoreboard rank line) AFTER the
 * cache is fresh, which dissolves the async-reload race (scoreboard spec FR-006a). Additive: the legacy
 * 4-arg constructor keeps existing callers unchanged.
 */
public final class PermissionLoader {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionCache cache;
    private final Logger logger;
    private final Consumer<UUID> onApplied;

    public PermissionLoader(BackendClient backend, PlatformScheduler scheduler,
                            PermissionCache cache, Logger logger) {
        this(backend, scheduler, cache, logger, null);
    }

    public PermissionLoader(BackendClient backend, PlatformScheduler scheduler,
                            PermissionCache cache, Logger logger, Consumer<UUID> onApplied) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.logger = logger;
        this.onApplied = onApplied;
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
                        if (onApplied != null) {
                            onApplied.accept(player);
                        }
                    }
                }));
    }

    /** Alias for {@link #load} used by the live updater on {@code mc:permission:changed}. */
    public void reload(UUID player, long version) {
        load(player, version);
    }
}
