package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEvent;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.session.SessionJoinResponse;

import java.util.UUID;

/**
 * Pure mapping between {@code plugin-protocol} economy DTOs/events and the generic
 * {@link FeatureCache}{@code <UUID, Long>} the feature keeps (player UUID → balance). No Bukkit, no
 * I/O — so the warmup and live-update logic is unit-testable. Filters to the slice's single currency;
 * version-awareness is the cache's job ({@link FeatureCache#put} keeps the newer version).
 */
final class EconomyBalances {

    private EconomyBalances() {
    }

    /** Warm the cache from a join response, taking only the given currency. */
    static void warm(FeatureCache<UUID, Long> cache, SessionJoinResponse response, String currency) {
        for (BalanceResponse balance : response.balances()) {
            if (balance.currency().equals(currency)) {
                cache.put(balance.player(), balance.balance(), balance.version());
            }
        }
    }

    /** Apply a live balance-changed event (version-checked) when it concerns the given currency. */
    static void apply(FeatureCache<UUID, Long> cache, BalanceChangedEvent event, String currency) {
        if (event.currencyCode().equals(currency)) {
            cache.put(event.playerUuid(), event.balance(), event.version());
        }
    }
}
