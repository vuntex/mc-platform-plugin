package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEvent;

import java.util.UUID;

/**
 * Pure mapping between {@code plugin-protocol} economy events and the generic
 * {@link FeatureCache}{@code <UUID, Long>} the feature keeps (player UUID → balance). No Bukkit, no
 * I/O — so the live-update logic is unit-testable. Filters to the slice's single currency;
 * version-awareness is the cache's job ({@link FeatureCache#put} keeps the newer version).
 *
 * <p>The cache is filled lazily (REST fallback in {@code /balance}) and by these live events — there is
 * no join warmup: establishing the backend session belongs to the platform session gate, not economy.
 */
final class EconomyBalances {

    private EconomyBalances() {
    }

    /** Apply a live balance-changed event (version-checked) when it concerns the given currency. */
    static void apply(FeatureCache<UUID, Long> cache, BalanceChangedEvent event, String currency) {
        if (event.currencyCode().equals(currency)) {
            cache.put(event.playerUuid(), event.balance(), event.version());
        }
    }
}
