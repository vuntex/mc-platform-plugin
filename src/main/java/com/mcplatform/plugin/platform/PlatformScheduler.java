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
}
