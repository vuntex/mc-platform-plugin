package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.EconomyEndpoints;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only view that lets other features (e.g. {@code feature.scoreboard}) consume the EXISTING
 * economy balance cache without owning a second cache (spec §4). Additive — it neither changes economy
 * behaviour nor touches any generic class.
 *
 * <ul>
 *   <li>{@link #current} — synchronous cache read (empty when the lazy cache is cold).</li>
 *   <li>{@link #load} — cache-first, else a non-blocking REST read ({@code GET_BALANCE}) that fills the
 *       same {@link FeatureCache} — same path {@code /balance} uses. Used at join because economy fills
 *       lazily (no join warmup) and is cold at that moment.</li>
 * </ul>
 */
public final class EconomyReadPort {

    private final BackendClient backend;
    private final FeatureCache<UUID, Long> cache;
    private final String currency;

    public EconomyReadPort(BackendClient backend, FeatureCache<UUID, Long> cache, String currency) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /** Current cached balance, or empty if not yet known. */
    public OptionalLong current(UUID player) {
        Optional<Long> cached = cache.get(player);
        return cached.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    /** Cache-first, REST-fallback. The future completes off the main thread; never blocks. */
    public CompletableFuture<OptionalLong> load(UUID player) {
        Optional<Long> cached = cache.get(player);
        return cached.map(aLong -> CompletableFuture.completedFuture(OptionalLong.of(aLong))).orElseGet(() -> backend.call(EconomyEndpoints.GET_BALANCE, null, player.toString(), currency)
                .handle((BalanceResponse response, Throwable error) -> {
                    if (error != null || response == null) {
                        return OptionalLong.empty();
                    }
                    cache.put(response.player(), response.balance(), response.version());
                    return OptionalLong.of(response.balance());
                }));
    }
}
