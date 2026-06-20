package com.mcplatform.plugin.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the registered {@link PluginFeature}s and drives their lifecycle — the single place a feature
 * is plugged in. Enables in registration order, disables in reverse.
 *
 * <p>Failure isolation: if one feature throws on enable/disable it is logged and skipped, so a broken
 * feature never aborts plugin startup or takes other features down. Disable only touches features
 * that actually enabled.
 */
public final class FeatureRegistry {

    private final Logger logger;
    private final List<PluginFeature> registered = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();
    private final List<PluginFeature> enabled = new ArrayList<>();

    public FeatureRegistry(Logger logger) {
        this.logger = logger;
    }

    /** Register a feature. Rejects null and duplicate ids. Returns {@code this} for chaining. */
    public FeatureRegistry register(PluginFeature feature) {
        Objects.requireNonNull(feature, "feature");
        String id = Objects.requireNonNull(feature.id(), "feature.id()");
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate feature id: " + id);
        }
        registered.add(feature);
        return this;
    }

    /** Enable all registered features in registration order, isolating failures. */
    public void enableAll(FeatureContext context) {
        for (PluginFeature feature : registered) {
            try {
                feature.onEnable(context);
                enabled.add(feature);
                logger.info("Feature enabled: " + feature.id());
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Feature failed to enable, skipping: " + feature.id(), ex);
            }
        }
    }

    /** Disable the successfully-enabled features in reverse order, isolating failures. */
    public void disableAll() {
        for (int i = enabled.size() - 1; i >= 0; i--) {
            PluginFeature feature = enabled.get(i);
            try {
                feature.onDisable();
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Feature failed to disable: " + feature.id(), ex);
            }
        }
        enabled.clear();
    }

    /** Ids of features currently enabled, in enable order (for diagnostics/tests). */
    public List<String> enabledIds() {
        List<String> result = new ArrayList<>(enabled.size());
        for (PluginFeature feature : enabled) {
            result.add(feature.id());
        }
        return Collections.unmodifiableList(result);
    }
}
