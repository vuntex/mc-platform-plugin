package com.mcplatform.plugin.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Generic, reusable keyed cooldown tracker (RAM only). Each entry is identified by an {@code owner}
 * (e.g. a player UUID or name) plus a {@code type} label, so unrelated cooldowns never collide — one
 * shared instance can gate "report again", "mute appeal message", "X per 30s", … Thread-safe; not
 * persistent (a restart clears it).
 *
 * <p>Replaces the ad-hoc {@code checkGeneralCooldown(name, type, ms, save)} pattern with three explicit
 * operations: {@link #isActive} (read), {@link #mark} (write), and {@link #throttle} (the common
 * "do this at most once per window" gate).
 */
public final class Cooldowns {

    private record Key(Object owner, String type) {
    }

    private final Map<Key, Long> lastAt = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    /** Test seam: inject a clock so expiry is verifiable without sleeping. */
    Cooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    /** True if {@code (owner, type)} was last marked within the past {@code cooldownMillis}. */
    public boolean isActive(Object owner, String type, long cooldownMillis) {
        Long last = lastAt.get(new Key(owner, type));
        return last != null && clock.getAsLong() - last < cooldownMillis;
    }

    /** Record that {@code (owner, type)} happened now (starts/refreshes its cooldown). */
    public void mark(Object owner, String type) {
        lastAt.put(new Key(owner, type), clock.getAsLong());
    }

    /**
     * Gate an action: if {@code (owner, type)} is still cooling down, return {@code true} (suppress) and
     * change nothing; otherwise mark it now and return {@code false} (proceed). So
     * {@code if (!cooldowns.throttle(player, "mute_appeal", 30_000L)) sendLink();} sends at most once
     * per 30s.
     */
    public boolean throttle(Object owner, String type, long cooldownMillis) {
        if (isActive(owner, type, cooldownMillis)) {
            return true;
        }
        mark(owner, type);
        return false;
    }

    /** Forget a single {@code (owner, type)} cooldown (e.g. after a manual reset). */
    public void clear(Object owner, String type) {
        lastAt.remove(new Key(owner, type));
    }
}
