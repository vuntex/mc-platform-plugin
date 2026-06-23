package com.mcplatform.plugin.platform;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.FeatureRegistry;
import com.mcplatform.plugin.feature.economy.EconomyFeature;
import com.mcplatform.plugin.feature.hub.HubFeature;
import com.mcplatform.plugin.feature.permission.PermissionFeature;
import com.mcplatform.plugin.feature.punishment.PunishmentFeature;
import com.mcplatform.plugin.feature.report.ReportFeature;
import com.mcplatform.plugin.feature.session.SessionFeature;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendClientConfig;
import com.mcplatform.plugin.transport.GsonJsonCodec;
import com.mcplatform.plugin.transport.HttpBackendClient;
import com.mcplatform.plugin.transport.JsonCodec;
import com.mcplatform.plugin.transport.LettuceEventBus;
import com.mcplatform.protocol.PlatformProtocol;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Plugin entry point and composition root. Builds the platform services (scheduler) and transport
 * (REST client, Redis event bus), assembles the {@link FeatureContext}, and starts the
 * {@link FeatureRegistry}.
 */
public final class McPlatformPlugin extends JavaPlugin {

    private FeatureRegistry features;
    private LettuceEventBus eventBus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginConfig config = loadConfig();
        getLogger().info("Backend base URL: " + config.backendBaseUrl()
                + " | Redis: " + config.redisHost() + ":" + config.redisPort());

        // Platform + transport seams.
        PlatformScheduler scheduler = new PaperPlatformScheduler(this);

        JsonCodec json = new GsonJsonCodec();
        BackendClientConfig backendConfig = new BackendClientConfig(
                config.backendBaseUrl(),
                Duration.ofMillis(getConfig().getLong("backend.connect-timeout-ms", 5000L)),
                Duration.ofMillis(getConfig().getLong("backend.request-timeout-ms", 5000L)),
                getConfig().getInt("backend.max-retries", 2),
                Duration.ofMillis(getConfig().getLong("backend.retry-backoff-ms", 200L)));
        BackendClient backend = new HttpBackendClient(json, scheduler, backendConfig);

        this.eventBus = new LettuceEventBus(
                PlatformProtocol.create(),
                config.redisHost(), config.redisPort(), config.redisPassword(),
                scheduler, getLogger());

        FeatureContext context = new FeatureContext(this, backend, eventBus, scheduler, config, getLogger());

        // The ONE central menu listener (MENU_DESIGN). Created here in the composition root and injected
        // into features — no generic platform class (FeatureContext/Registry/...) is touched to add menus.
        MenuManager menus = new MenuManager(this, scheduler);
        menus.register();

        // The ONE place features are plugged in. Add a feature = one more .register(...) line.
        this.features = new FeatureRegistry(getLogger())
                .register(new SessionFeature()) // platform session gate — established first
                .register(new EconomyFeature(menus))
                .register(new PunishmentFeature(menus))
                .register(new ReportFeature(menus))
                .register(new PermissionFeature(menus))
                .register(new HubFeature(menus));
        this.features.enableAll(context);

        // Connect the bus only after features have registered their subscriptions.
        this.eventBus.start();

        getLogger().info("McPlatformPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (eventBus != null) {
            eventBus.close();
        }
        if (features != null) {
            features.disableAll();
        }
        getLogger().info("McPlatformPlugin disabled.");
    }

    private PluginConfig loadConfig() {
        var config = getConfig();
        return new PluginConfig(
                config.getString("backend.base-url", "http://localhost:8080"),
                config.getString("redis.host", "localhost"),
                config.getInt("redis.port", 6379),
                config.getString("redis.password", ""));
    }
}
