package com.mcplatform.plugin.feature;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.PluginConfig;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.EventBus;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Everything a {@link PluginFeature} needs, handed in at {@link PluginFeature#onEnable}. Features
 * depend only on these platform/transport seams — never on Bukkit globals or concrete I/O. Bukkit
 * listener/command registration is offered as thin adapters here, so the {@code JavaPlugin} instance
 * never leaks into feature code.
 */
public final class FeatureContext {

    private final JavaPlugin plugin;
    private final BackendClient backend;
    private final EventBus eventBus;
    private final PlatformScheduler scheduler;
    private final PluginConfig config;
    private final Logger logger;

    public FeatureContext(JavaPlugin plugin, BackendClient backend, EventBus eventBus,
                          PlatformScheduler scheduler, PluginConfig config, Logger logger) {
        this.plugin = plugin;
        this.backend = backend;
        this.eventBus = eventBus;
        this.scheduler = scheduler;
        this.config = config;
        this.logger = logger;
    }

    /** Generic REST client over {@code plugin-protocol} endpoints. */
    public BackendClient backend() {
        return backend;
    }

    /** Read-only live-update bus (Redis Pub/Sub). */
    public EventBus eventBus() {
        return eventBus;
    }

    /** Thread-hopping abstraction; all I/O async, Bukkit API on sync. */
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    /** Backend base URL + Redis settings from config.yml. */
    public PluginConfig config() {
        return config;
    }

    public Logger logger() {
        return logger;
    }

    /** Register a Bukkit event listener for this plugin (adapter — hides the plugin reference). */
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /** Set the executor for a command declared in plugin.yml (adapter — hides the plugin reference). */
    public void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            logger.severe("Command '" + name + "' missing from plugin.yml – it will not work.");
        }
    }
}
