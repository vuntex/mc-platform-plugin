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
 * TEMPORARY (testing only): on join, CREDIT every player a fixed amount so the economy/scoreboard can be
 * exercised. Remove before production.
 *
 * <p>The credit is DEFERRED a couple of seconds after join: doing it in the join tick races the session's
 * account/balance initialisation for brand-new players, and the backend's optimistic lock then 409s
 * ("concurrency_conflict — exceeded 5 retries"). Waiting until the account exists avoids that contention.
 */
public final class TestJoinCoinsListener implements Listener {

    private static final long TEST_BALANCE = 50_000L;
    private static final long DELAY_TICKS = 40L; // ~2s after join

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
        // One-shot deferred task: run once after the delay, then cancel itself.
        AutoCloseable[] handle = new AutoCloseable[1];
        handle[0] = scheduler.runSyncTimer(() -> {
            close(handle[0]);
            credit(player, name);
        }, DELAY_TICKS, DELAY_TICKS);
    }

    private void credit(UUID player, String name) {
        backend.call(EconomyEndpoints.CREDIT,
                        new AmountRequest(TEST_BALANCE, null, "PLUGIN:test-join-bonus"),
                        player.toString(), currency)
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null || response == null) {
                        Throwable cause = unwrap(error);
                        int status = cause instanceof BackendException be ? be.statusCode() : -1;
                        logger.log(Level.WARNING, "[test-join-bonus] CREDIT " + TEST_BALANCE
                                + " failed for " + name + " (" + player + "), status=" + status, cause);
                        return;
                    }
                    cache.put(response.player(), response.balance(), response.version());
                    logger.info("[test-join-bonus] credited " + TEST_BALANCE + " " + currency + " for "
                            + name + " → " + response.balance() + " (v" + response.version() + ")");
                }));
    }

    private static void close(AutoCloseable handle) {
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception ignored) {
                // best-effort cancel of the one-shot timer
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
