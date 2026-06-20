package com.mcplatform.plugin.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Proves the version-aware cache contract: a newer version wins, an older version is discarded, and
 * re-delivering the same version is idempotent. Pure unit test — no Bukkit, no I/O.
 */
class FeatureCacheTest {

    @Test
    void storesAndReadsValue() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 100L, 5L);

        assertTrue(cache.get("alice").isPresent());
        assertEquals(100L, cache.get("alice").orElseThrow());
        assertEquals(5L, cache.version("alice").orElseThrow());
    }

    @Test
    void missingKeyIsEmpty() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        assertTrue(cache.get("nobody").isEmpty());
        assertTrue(cache.version("nobody").isEmpty());
    }

    @Test
    void newerVersionWins() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 100L, 5L);
        cache.put("alice", 250L, 8L);

        assertEquals(250L, cache.get("alice").orElseThrow());
        assertEquals(8L, cache.version("alice").orElseThrow());
    }

    @Test
    void olderVersionIsDiscarded() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 250L, 8L);
        cache.put("alice", 999L, 3L); // stale -> ignored

        assertEquals(250L, cache.get("alice").orElseThrow());
        assertEquals(8L, cache.version("alice").orElseThrow());
    }

    @Test
    void sameVersionIsIdempotent() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 100L, 5L);
        cache.put("alice", 777L, 5L); // same version -> first write at that version stays

        assertEquals(100L, cache.get("alice").orElseThrow());
        assertEquals(5L, cache.version("alice").orElseThrow());
    }

    @Test
    void keysAreIndependent() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 100L, 1L);
        cache.put("bob", 50L, 1L);

        assertEquals(100L, cache.get("alice").orElseThrow());
        assertEquals(50L, cache.get("bob").orElseThrow());
        assertEquals(2, cache.size());
    }

    @Test
    void removeDropsEntry() {
        FeatureCache<String, Long> cache = new FeatureCache<>();
        cache.put("alice", 100L, 1L);
        cache.remove("alice");

        assertFalse(cache.get("alice").isPresent());
        assertEquals(0, cache.size());
    }
}
