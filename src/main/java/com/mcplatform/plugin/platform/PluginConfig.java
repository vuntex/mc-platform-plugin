package com.mcplatform.plugin.platform;

/**
 * Immutable plugin configuration, loaded once from config.yml at enable. Holds the backend base URL
 * and Redis connection settings; handed to features via the {@code FeatureContext}.
 */
public record PluginConfig(
        String backendBaseUrl,
        String redisHost,
        int redisPort,
        String redisPassword) {
}
