package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEvent;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.session.SessionJoinResponse;

import org.junit.jupiter.api.Test;

/**
 * Proves the economy↔cache mapping (join warmup + live-update apply) against the real
 * {@code plugin-protocol} DTOs, no Bukkit: only the slice currency is taken, and version-awareness is
 * honored via the generic {@link FeatureCache}.
 */
class EconomyBalancesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TXN = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private BalanceChangedEvent event(String currency, long balance, long version) {
        return new BalanceChangedEvent(PLAYER, currency, "CREDITED", 10L, balance, version,
                TXN, "PLUGIN:test", null, 1_700_000_000_000L);
    }

    @Test
    void warmTakesOnlyTheSliceCurrency() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        SessionJoinResponse response = new SessionJoinResponse(PLAYER, "Steve", true, List.of(
                new BalanceResponse(PLAYER, "COINS", 100L, 1L),
                new BalanceResponse(PLAYER, "GEMS", 5L, 1L)));

        EconomyBalances.warm(cache, response, "COINS");

        assertEquals(100L, cache.get(PLAYER).orElseThrow()); // 100 COINS start bonus (V3 migration)
        assertEquals(1L, cache.version(PLAYER).orElseThrow());
        assertEquals(1, cache.size()); // GEMS ignored for the COINS slice
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
