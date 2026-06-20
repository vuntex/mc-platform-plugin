package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Login gate. {@link AsyncPlayerPreLoginEvent} already fires OFF the main thread, so we fetch the
 * player's active punishments via the generic {@link BackendClient} and block this (non-main) login
 * thread on the result — the main thread is never blocked (Prinzip 5). An active TEMPBAN/PERMABAN
 * disallows the login with the reason (and, for a tempban, the remaining duration). When the player is
 * allowed in, the fresh list warms the local {@link FeatureCache} so the chat mute and live-revoke can
 * work without another round-trip (analog the economy join warmup).
 *
 * <p>Fail-open: if the backend lookup errors/times out we log and ALLOW the login — a backend hiccup
 * must not lock the whole server out. (The backend remains the authority; this only governs the brief
 * window where it is unreachable.)
 */
public final class PunishmentLoginListener implements Listener {

    private static final long LOOKUP_TIMEOUT_SECONDS = 6L;

    private final BackendClient backend;
    private final FeatureCache<UUID, PunishmentSnapshot> cache;
    private final Logger logger;

    public PunishmentLoginListener(BackendClient backend,
                                   FeatureCache<UUID, PunishmentSnapshot> cache, Logger logger) {
        this.backend = backend;
        this.cache = cache;
        this.logger = logger;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        try {
            PunishmentResponse[] active = backend
                    .call(PunishmentEndpoints.LIST_ACTIVE, null, uuid.toString())
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (active == null) {
                active = new PunishmentResponse[0];
            }

            Optional<PunishmentResponse> ban = Punishments.firstActiveBan(active);
            if (ban.isPresent()) {
                PunishmentResponse b = ban.get();
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        PunishmentFormat.banScreen(b.type(), b.reason(), b.expiresAtEpochMilli(),
                                System.currentTimeMillis()));
                return; // refused — do not cache a player who is not coming in
            }

            // Allowed → warm the cache so chat enforcement + live-revoke have local state.
            Punishments.warm(cache, uuid, active);
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "Punishment login lookup failed for " + uuid + "; allowing login (fail-open)", ex);
        }
    }
}
