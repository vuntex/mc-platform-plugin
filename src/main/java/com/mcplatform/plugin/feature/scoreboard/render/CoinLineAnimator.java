package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.platform.PlatformScheduler;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animates the coins line: on a GAIN it counts up from the previously shown value to the new one over a
 * few steps (a soft tick each step, a richer sound at the end); a loss or the first value is set
 * instantly (no count-down, no count-from-zero on join). Bukkit-free — it drives the {@link ScoreboardHandle}
 * and {@link PlatformScheduler}, and delegates sound to {@link ScoreboardSound}, so it's unit-testable.
 *
 * <p>Per player it keeps the currently shown value and at most one running animation; a newer gain cancels
 * the running one and continues from wherever it had counted to.
 */
public final class CoinLineAnimator implements CoinLineRenderer {

    /** Upper bound on count-up steps (small gains use fewer); keeps long animations from crawling. */
    private static final int MAX_STEPS = 20;

    private final PlatformScheduler scheduler;
    private final ScoreboardSound sound;
    private final long periodTicks;
    private final Map<UUID, Long> shown = new ConcurrentHashMap<>();
    private final Map<UUID, AutoCloseable> running = new ConcurrentHashMap<>();

    public CoinLineAnimator(PlatformScheduler scheduler, ScoreboardSound sound, long periodTicks) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sound = Objects.requireNonNull(sound, "sound");
        this.periodTicks = Math.max(1, periodTicks);
    }

    @Override
    public void update(UUID player, ScoreboardHandle handle, LineId line, OptionalLong coins) {
        cancel(player); // a new value supersedes any running animation
        if (coins.isEmpty()) {
            handle.update(line, EconomyLineProvider.PLACEHOLDER);
            return;
        }
        long target = coins.getAsLong();
        Long current = shown.get(player);
        if (current == null || target <= current) {
            // First value or a loss → set instantly (no count-from-zero on join, no count-down).
            set(player, handle, line, target);
            return;
        }
        animate(player, handle, line, current, target);
    }

    private void animate(UUID player, ScoreboardHandle handle, LineId line, long from, long target) {
        long delta = target - from;
        long steps = Math.min(MAX_STEPS, delta);
        long increment = Math.max(1, delta / steps);
        long[] value = {from};

        Runnable tick = () -> {
            value[0] += increment;
            if (value[0] >= target) {
                set(player, handle, line, target);
                sound.coinComplete(player);
                stop(player);
            } else {
                set(player, handle, line, value[0]);
                sound.coinTick(player);
            }
        };
        running.put(player, scheduler.runSyncTimer(tick, periodTicks, periodTicks));
    }

    private void set(UUID player, ScoreboardHandle handle, LineId line, long value) {
        handle.update(line, EconomyLineProvider.coinComponent(value));
        shown.put(player, value);
    }

    private void stop(UUID player) {
        close(running.remove(player));
    }

    private void cancel(UUID player) {
        close(running.remove(player));
    }

    @Override
    public void clear(UUID player) {
        cancel(player);
        shown.remove(player);
    }

    private static void close(AutoCloseable handle) {
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception ignored) {
                // best-effort cancel
            }
        }
    }
}
