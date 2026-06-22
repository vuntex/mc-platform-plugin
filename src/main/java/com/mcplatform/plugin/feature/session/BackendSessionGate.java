package com.mcplatform.plugin.feature.session;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.session.PlayerRequest;
import com.mcplatform.protocol.session.SessionEndpoints;
import com.mcplatform.protocol.session.SessionJoinResponse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hard backend-session gate — a pure platform/session concern, independent of any feature: a player may
 * only play if the backend can establish their session. At {@link AsyncPlayerPreLoginEvent} (off the
 * main thread) we call {@code SESSION.JOIN} (player upsert in the source of truth) and block this login
 * thread on the result; the main thread is never blocked.
 *
 * <p><b>Fail-closed</b>: backend down / timeout / empty response → the login is refused with a clear
 * message. Rationale: without an established session we cannot vouch for the player's state at all, so we
 * don't let them in source-of-truth-less. This is deliberately the opposite stance of the punishment
 * gate (fail-open) — and a different thing entirely: a banned player is a *valid* session that gets
 * refused; here there is simply no session.
 *
 * <p>Runs at {@link EventPriority#LOWEST} so session validity is decided before any other pre-login
 * handler (e.g. the punishment gate) spends a backend round-trip. The {@code SESSION.JOIN} response body
 * is intentionally unused here — its only job is the upsert + the liveness signal; features that want
 * data load it themselves.
 */
public final class BackendSessionGate implements Listener {

    private static final long LOOKUP_TIMEOUT_SECONDS = 6L;

    private final BackendClient backend;
    private final Logger logger;

    public BackendSessionGate(BackendClient backend, Logger logger) {
        this.backend = backend;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return; // already refused upstream
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            // Idempotent: the backend dedupes the upsert + deterministic init txn ids.
            SessionJoinResponse response = backend
                    .callIdempotent(SessionEndpoints.JOIN, new PlayerRequest(name), uuid.toString())
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response == null) {
                throw new IllegalStateException("empty session response");
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "Backend session join failed for " + name + " (" + uuid + ") – refusing login (fail-closed)", ex);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§cVerbindung zum Server-Backend fehlgeschlagen.\n§7Bitte versuche es in Kürze erneut.");
        }
    }
}
