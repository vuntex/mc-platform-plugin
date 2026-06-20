package com.mcplatform.plugin.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Proves the lifecycle contract: enable calls every feature's onEnable in registration order; disable
 * runs in reverse; a feature that throws on enable is isolated and logged while the others still
 * enable and disable cleanly. Fake features ignore the context, so no Bukkit is needed.
 */
class FeatureRegistryTest {

    private final Logger logger = Logger.getLogger("test");

    /** Records lifecycle calls into a shared log; never touches the context. */
    private static final class RecordingFeature implements PluginFeature {
        private final String id;
        private final List<String> log;
        private final boolean failOnEnable;

        RecordingFeature(String id, List<String> log, boolean failOnEnable) {
            this.id = id;
            this.log = log;
            this.failOnEnable = failOnEnable;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void onEnable(FeatureContext context) {
            if (failOnEnable) {
                throw new IllegalStateException("boom:" + id);
            }
            log.add("enable:" + id);
        }

        @Override
        public void onDisable() {
            log.add("disable:" + id);
        }
    }

    @Test
    void enablesInOrderAndDisablesInReverse() {
        List<String> log = new ArrayList<>();
        FeatureRegistry registry = new FeatureRegistry(logger)
                .register(new RecordingFeature("a", log, false))
                .register(new RecordingFeature("b", log, false));

        registry.enableAll(null);
        registry.disableAll();

        assertEquals(List.of("enable:a", "enable:b", "disable:b", "disable:a"), log);
    }

    @Test
    void duplicateIdIsRejected() {
        FeatureRegistry registry = new FeatureRegistry(logger)
                .register(new RecordingFeature("dup", new ArrayList<>(), false));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new RecordingFeature("dup", new ArrayList<>(), false)));
    }

    @Test
    void failingFeatureIsIsolatedAndOthersStillRun() {
        List<String> log = new ArrayList<>();
        FeatureRegistry registry = new FeatureRegistry(logger)
                .register(new RecordingFeature("ok-before", log, false))
                .register(new RecordingFeature("broken", log, true))
                .register(new RecordingFeature("ok-after", log, false));

        registry.enableAll(null);

        // The broken feature never lands in the enabled set; the others enable regardless.
        assertEquals(List.of("ok-before", "ok-after"), registry.enabledIds());
        assertTrue(log.contains("enable:ok-before"));
        assertTrue(log.contains("enable:ok-after"));

        // Disable only touches successfully-enabled features, in reverse.
        registry.disableAll();
        assertEquals(List.of("disable:ok-after", "disable:ok-before"),
                log.subList(log.size() - 2, log.size()));
        assertEquals(List.of(), registry.enabledIds());
    }
}
