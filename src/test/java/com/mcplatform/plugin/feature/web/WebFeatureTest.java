package com.mcplatform.plugin.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mcplatform.plugin.feature.FeatureRegistry;
import com.mcplatform.plugin.feature.PluginFeature;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Proves {@code feature.web} plugs in as a single feature ("ein Anstecken"): one stable id that occupies
 * exactly one registry slot. No {@code EventBus}/transport seam is involved — the constructor takes only
 * the two URL templates, so registering it cannot touch any generic transport class.
 */
class WebFeatureTest {

    private final Logger logger = Logger.getLogger("test");

    private static WebFeature newFeature() {
        return new WebFeature("https://web.example.com/set-password?token={token}",
                "https://web.example.com/reset-password?token={token}");
    }

    @Test
    void hasAStablePluginFeatureId() {
        PluginFeature feature = newFeature();
        assertEquals("web", feature.id());
    }

    @Test
    void registersAsExactlyOnePlug() {
        FeatureRegistry registry = new FeatureRegistry(logger).register(newFeature());
        // A second feature claiming the same id is rejected → "web" is a single, unique plug.
        assertThrows(IllegalArgumentException.class, () -> registry.register(newFeature()));
    }
}
