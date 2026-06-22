package com.mcplatform.plugin.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Proof of the generic keyed cooldown utility, using an injected clock to verify expiry. */
class CooldownsTest {

    @Test
    void isActiveOnlyWithinTheWindow() {
        long[] clock = {1_000L};
        Cooldowns cooldowns = new Cooldowns(() -> clock[0]);
        UUID who = UUID.randomUUID();

        assertFalse(cooldowns.isActive(who, "x", 1_000L), "never marked");
        cooldowns.mark(who, "x");
        assertTrue(cooldowns.isActive(who, "x", 1_000L));
        clock[0] += 999L;
        assertTrue(cooldowns.isActive(who, "x", 1_000L), "still inside window");
        clock[0] += 1L;
        assertFalse(cooldowns.isActive(who, "x", 1_000L), "window elapsed");
    }

    @Test
    void throttleSuppressesRepeatsThenAllowsAfterWindow() {
        long[] clock = {0L};
        Cooldowns cooldowns = new Cooldowns(() -> clock[0]);
        UUID who = UUID.randomUUID();

        assertFalse(cooldowns.throttle(who, "appeal", 30_000L), "first is allowed");
        assertTrue(cooldowns.throttle(who, "appeal", 30_000L), "repeat within window is suppressed");
        clock[0] += 30_000L;
        assertFalse(cooldowns.throttle(who, "appeal", 30_000L), "allowed again after the window");
    }

    @Test
    void ownersAndTypesAreIndependent() {
        Cooldowns cooldowns = new Cooldowns(() -> 0L);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cooldowns.mark(a, "t1");
        assertTrue(cooldowns.isActive(a, "t1", 1_000L));
        assertFalse(cooldowns.isActive(a, "t2", 1_000L), "other type unaffected");
        assertFalse(cooldowns.isActive(b, "t1", 1_000L), "other owner unaffected");
    }
}
