package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hard permission warmup at {@link AsyncPlayerPreLoginEvent} (off the main thread), hung on the same
 * availability-before-security line as the {@code BackendSessionGate}: before a player ever enters the
 * world we load their effective permissions and write them into the {@link PermissionCache}, blocking
 * this login thread on the result (the main thread is never blocked).
 *
 * <p><b>Fail-closed</b>, exactly like the session gate: backend down / timeout / empty response →
 * {@link AsyncPlayerPreLoginEvent#disallow} (hard kick). A player therefore never reaches the world with
 * a cold permission cache — which is what lets {@link PermissionGate} drop its old "optimistic
 * neutral-allow" and treat a cold cache as a bug.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} and only when the login is still {@code ALLOWED}: the session
 * gate ({@code LOWEST}) and the ban gate ({@code NORMAL}) have already decided, so we never warm — and
 * never cache — a player who is about to be refused, and "session first, then permissions" holds. On any
 * partial failure the login is refused; no half-warmed state slips into the world.
 *
 * <p>This handles only the <em>cold start</em>. Runtime changes (grant/revoke/expiry during the session)
 * stay on the independent {@code mc:permission:changed} live push — both mechanisms coexist.
 */
public final class PermissionWarmupListener implements Listener {

    private static final long LOOKUP_TIMEOUT_SECONDS = 6L;

    private final BackendClient backend;
    private final PermissionCache cache;
    private final Logger logger;

    public PermissionWarmupListener(BackendClient backend, PermissionCache cache, Logger logger) {
        this.backend = backend;
        this.cache = cache;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return; // session/ban gate already refused — don't warm a player who won't enter
        }
        if (!warmup(event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§cBerechtigungen konnten nicht geladen werden.\n§7Bitte versuche es in Kürze erneut.");
        }
    }

    /**
     * Blocking warmup: load the player's effective permissions and write them into the cache. Returns
     * {@code true} on success, {@code false} on any backend failure (logged). No Bukkit — unit-testable.
     * Safe to block here: this runs on the async PreLogin thread, never the main thread.
     */
    boolean warmup(UUID uuid) {
        try {
            PlayerPermissionsResponse response = backend
                    .callIdempotent(PermissionEndpoints.EFFECTIVE, null, uuid.toString())
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response == null) {
                throw new IllegalStateException("empty effective response");
            }
            cache.apply(uuid, PlayerPermissionsView.from(response), System.currentTimeMillis());
            return true;
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "Permission warmup failed for " + uuid + " – refusing login (fail-closed)", ex);
            return false;
        }
    }
}
