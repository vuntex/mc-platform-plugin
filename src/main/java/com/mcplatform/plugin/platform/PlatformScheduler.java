package com.mcplatform.plugin.platform;

/**
 * Scheduler abstraction so features never call {@code Bukkit.getScheduler()} directly. All I/O runs
 * via {@link #runAsync}; only the hop back to Bukkit API goes through {@link #runSync}. Keeping this
 * an interface lets features stay testable and the platform stay swappable.
 */
public interface PlatformScheduler {

    /** Run on the server main thread (the only place Bukkit API is safe to touch). */
    void runSync(Runnable task);

    /** Run off the main thread (for blocking I/O such as REST or Redis). */
    void runAsync(Runnable task);

    /**
     * Schedule {@code task} on the main thread, first after {@code delayTicks}, then every
     * {@code periodTicks} (20 ticks ≈ 1s). Returns a handle whose {@link AutoCloseable#close()} cancels
     * it. Default is a no-op handle, so test fakes that never schedule timers need not implement it; the
     * real {@code PaperPlatformScheduler} provides the working timer.
     */
    default AutoCloseable runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return () -> { };
    }
}
