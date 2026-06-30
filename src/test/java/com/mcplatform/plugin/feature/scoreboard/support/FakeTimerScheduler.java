package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.platform.PlatformScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Test scheduler that runs {@code runSync}/{@code runAsync} inline and lets the test drive repeating
 * timers manually via {@link #tick()}. Closing a timer's handle removes it (so a self-cancelling task
 * stops). Lets the coin animation be advanced tick-by-tick deterministically.
 */
public final class FakeTimerScheduler implements PlatformScheduler {

    private final List<Runnable> timers = new ArrayList<>();

    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }

    @Override
    public AutoCloseable runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        timers.add(task);
        return () -> timers.remove(task);
    }

    /** Run every active timer once (a copy guards against self-removal during iteration). */
    public void tick() {
        for (Runnable task : new ArrayList<>(timers)) {
            task.run();
        }
    }

    public int activeTimers() {
        return timers.size();
    }
}
