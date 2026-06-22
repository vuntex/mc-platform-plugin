package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the client-side report cooldown gate. */
class ReportCooldownTest {

    @Test
    void freshReporterIsNotCoolingDown() {
        ReportCooldown cooldown = new ReportCooldown();
        assertFalse(cooldown.isCoolingDown(UUID.randomUUID()));
    }

    @Test
    void markedReporterIsCoolingDownWithinTheWindow() {
        ReportCooldown cooldown = new ReportCooldown();
        UUID reporter = UUID.randomUUID();
        cooldown.markReported(reporter);
        assertTrue(cooldown.isCoolingDown(reporter), "just-marked reporter is on cooldown");
    }

    @Test
    void cooldownIsPerReporter() {
        ReportCooldown cooldown = new ReportCooldown();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cooldown.markReported(a);
        assertTrue(cooldown.isCoolingDown(a));
        assertFalse(cooldown.isCoolingDown(b), "another player is unaffected");
    }
}
