package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEvent;

import org.junit.jupiter.api.Test;

/**
 * Proves the economy↔cache live-update mapping against the real {@code plugin-protocol} DTOs, no Bukkit:
 * only the slice currency is taken, and version-awareness is honored via the generic {@link FeatureCache}.
 * (There is no join warmup anymore — the session gate owns session establishment; economy fills lazily.)
 */
class EconomyBalancesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TXN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private BalanceChangedEvent event(String currency, long balance, long version) {
        return new BalanceChangedEvent(PLAYER, currency, "CREDITED", 10L, balance, version,
                TXN, "PLUGIN:test", null, 1_700_000_000_000L);
    }

    @Test
    void applyUpdatesOnMatchingCurrencyOnly() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();

        EconomyBalances.apply(cache, event("COINS", 250L, 8L), "COINS");
        assertEquals(250L, cache.get(PLAYER).orElseThrow());

        EconomyBalances.apply(cache, event("GEMS", 999L, 9L), "COINS"); // wrong currency → ignored
        assertEquals(250L, cache.get(PLAYER).orElseThrow());
    }

    @Test
    void applyHonorsVersionStaleness() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();

        EconomyBalances.apply(cache, event("COINS", 250L, 8L), "COINS"); // newer
        EconomyBalances.apply(cache, event("COINS", 999L, 3L), "COINS"); // stale → ignored

        assertEquals(250L, cache.get(PLAYER).orElseThrow());
        assertEquals(8L, cache.version(PLAYER).orElseThrow());
    }
}
