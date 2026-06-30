package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PermissionCacheTest {

    private static PlayerPermissionsView view(String... perms) {
        return new PlayerPermissionsView(Set.of(perms), null);
    }

    @Test
    void newerVersionWinsAndOlderVersionLoses() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();

        cache.apply(p, view("a"), 100L);
        cache.apply(p, view("b"), 50L); // older → must not overwrite

        assertTrue(cache.get(p).orElseThrow().has("a"));
        assertFalse(cache.get(p).orElseThrow().has("b"));

        cache.apply(p, view("c"), 200L); // newer → wins
        assertTrue(cache.get(p).orElseThrow().has("c"));
    }

    @Test
    void evictRemovesEntryAndGetIsEmptyWhenAbsent() {
        PermissionCache cache = new PermissionCache();
        UUID p = UUID.randomUUID();

        assertTrue(cache.get(p).isEmpty());
        cache.apply(p, view("x"), 1L);
        assertEquals(1, cache.size());
        cache.evict(p);
        assertTrue(cache.get(p).isEmpty());
        assertEquals(0, cache.size());
    }
}
