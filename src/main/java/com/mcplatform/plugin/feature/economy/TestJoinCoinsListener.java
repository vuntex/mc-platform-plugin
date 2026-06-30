package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.AmountRequest;
import com.mcplatform.protocol.economy.EconomyEndpoints;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TEMPORARY (testing only): on join, SET every player's balance to a fixed amount so the economy/scoreboard
 * can be exercised. Remove before production — this overwrites real balances on every join.
 *
 * <p>Logs the outcome (incl. the backend status on failure) so a silent failure for a specific player is
 * diagnosable.
 */
public final class TestJoinCoinsListener implements Listener {

    private static final long TEST_BALANCE = 50_000L;

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final FeatureCache<UUID, Long> cache;
    private final String currency;
    private final Logger logger;

    public TestJoinCoinsListener(BackendClient backend, PlatformScheduler scheduler,
                                 FeatureCache<UUID, Long> cache, String currency, Logger logger) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.currency = currency;
        this.logger = logger;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        backend.call(EconomyEndpoints.SET,
                        new AmountRequest(TEST_BALANCE, null, "PLUGIN:test-join-bonus"),
                        player.toString(), currency)
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null || response == null) {
                        Throwable cause = unwrap(error);
                        int status = cause instanceof BackendException be ? be.statusCode() : -1;
                        logger.log(Level.WARNING,
                                "[test-join-bonus] SET " + TEST_BALANCE + " failed for " + name + " ("
                                        + player + "), status=" + status, cause);
                        return;
                    }
                    cache.put(response.player(), response.balance(), response.version());
                    logger.info("[test-join-bonus] set " + response.balance() + " " + currency + " for "
                            + name + " (v" + response.version() + ")");
                }));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
