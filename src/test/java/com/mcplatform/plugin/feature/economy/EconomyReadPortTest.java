package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.support.FakeBackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;

import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** EconomyReadPort: cache-first, REST-fallback (fills cache), error → empty. */
class EconomyReadPortTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void cacheFirstSkipsBackend() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        cache.put(PLAYER, 250L, 1L);
        FakeBackendClient backend = new FakeBackendClient();
        EconomyReadPort port = new EconomyReadPort(backend, cache, "COINS");

        assertEquals(OptionalLong.of(250L), port.load(PLAYER).join());
        assertEquals(0, backend.calls); // served from cache — no REST
        assertEquals(OptionalLong.of(250L), port.current(PLAYER));
    }

    @Test
    void restFallbackFillsCache() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        FakeBackendClient backend = new FakeBackendClient()
                .result(new BalanceResponse(PLAYER, "COINS", 500L, 7L));
        EconomyReadPort port = new EconomyReadPort(backend, cache, "COINS");

        assertTrue(port.current(PLAYER).isEmpty()); // cold

        assertEquals(OptionalLong.of(500L), port.load(PLAYER).join());
        assertEquals(1, backend.calls);
        assertEquals(OptionalLong.of(500L), port.current(PLAYER)); // now warm
    }

    @Test
    void errorYieldsEmpty() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        FakeBackendClient backend = new FakeBackendClient().error(new RuntimeException("boom"));
        EconomyReadPort port = new EconomyReadPort(backend, cache, "COINS");

        assertEquals(OptionalLong.empty(), port.load(PLAYER).join());
        assertFalse(cache.get(PLAYER).isPresent());
    }
}
