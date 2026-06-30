package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** TEST FOCUS: Gate liest Cache (T009). */
class PermissionGateTest {

    private static final String NODE = "mcplatform.permission.roles.manage";

    @Test
    void entryWithNodeIsAllowed() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();
        cache.apply(p, new PlayerPermissionsView(Set.of(NODE), null), 1L);

        assertTrue(new PermissionGate(cache).has(p, NODE));
    }

    @Test
    void entryWithoutNodeIsDenied() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();
        cache.apply(p, new PlayerPermissionsView(Set.of("some.other.node"), null), 1L);

        assertFalse(new PermissionGate(cache).has(p, NODE));
    }

    @Test
    void coldCacheStrictDenies() {
        PermissionCache cache = new PermissionCache();
        // no entry for this player at all → strict deny (PreLogin warmup guarantees a warm cache in-world)
        assertFalse(new PermissionGate(cache).has(UUID.randomUUID(), NODE));
    }

    @Test
    void globalWildcardGrantsEverything() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();
        cache.apply(p, new PlayerPermissionsView(Set.of("*"), null), 1L);

        assertTrue(new PermissionGate(cache).has(p, NODE));
        assertTrue(new PermissionGate(cache).has(p, "anything.else"));
    }

    @Test
    void ancestorWildcardGrantsTheSubtree() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();
        cache.apply(p, new PlayerPermissionsView(Set.of("mcplatform.permission.*"), null), 1L);

        assertTrue(new PermissionGate(cache).has(p, NODE)); // mcplatform.permission.roles.manage
        assertTrue(new PermissionGate(cache).has(p, "mcplatform.permission.grants.manage"));
        assertFalse(new PermissionGate(cache).has(p, "mcplatform.economy.pay"));
    }

    @Test
    void topLevelWildcardGrantsDeeperNodes() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();
        cache.apply(p, new PlayerPermissionsView(Set.of("mcplatform.*"), null), 1L);

        assertTrue(new PermissionGate(cache).has(p, NODE));
    }
}
