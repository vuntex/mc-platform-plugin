package com.mcplatform.plugin.feature;

/**
 * A self-contained slice of the plugin (economy, cosmetics, stats, ...). A feature registers ALL of
 * its listeners, commands and channel subscriptions in {@link #onEnable(FeatureContext)} — that one
 * method is the single place a feature touches the platform. Adding a feature is: implement this
 * interface + register the instance once in the {@link FeatureRegistry}.
 */
public interface PluginFeature {

    /** Stable, unique id used for logging and de-duplication, e.g. {@code "economy"}. */
    String id();

    /** Wire up the feature: register listeners/commands/subscriptions via the context. */
    void onEnable(FeatureContext context);

    /** Release resources on shutdown. Called in reverse enable order. */
    default void onDisable() {
    }
}
