package com.mcplatform.plugin.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper-backed {@link PlatformScheduler}. Thin adapter over the Bukkit scheduler — the testable
 * logic lives in feature code that depends on the {@link PlatformScheduler} interface.
 */
public final class PaperPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public PaperPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public AutoCloseable runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return handle::cancel;
    }
}
