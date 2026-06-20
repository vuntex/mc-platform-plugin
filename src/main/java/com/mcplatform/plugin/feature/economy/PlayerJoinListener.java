package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.session.PlayerRequest;
import com.mcplatform.protocol.session.SessionEndpoints;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * On join: call the backend session endpoint (player upsert + default-balance init incl. the 100
 * COINS start bonus, see backend PROGRESS) and warm the local cache from the returned balances, so
 * the player's first {@code /balance} answers from memory. The call is idempotent (the backend
 * dedupes the upsert + deterministic init txn ids) → {@code callIdempotent}. Result handled on the
 * main thread via the scheduler (Prinzip 5).
 */
public final class PlayerJoinListener implements Listener {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final FeatureCache<UUID, Long> cache;
    private final String currency;
    private final Logger logger;

    public PlayerJoinListener(BackendClient backend, PlatformScheduler scheduler,
                              FeatureCache<UUID, Long> cache, String currency, Logger logger) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.currency = currency;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        backend.callIdempotent(SessionEndpoints.JOIN, new PlayerRequest(name), uuid.toString())
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null || response == null) {
                        logger.log(Level.WARNING, "Session join failed for " + name + " (" + uuid + ")", error);
                        return;
                    }
                    EconomyBalances.warm(cache, response, currency);
                }));
    }
}
