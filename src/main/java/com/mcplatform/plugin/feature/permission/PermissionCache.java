package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.transport.FeatureCache;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-online-player cache of effective permissions + chosen display, built on the GENERIC
 * {@link FeatureCache} (unchanged) — version-aware so an out-of-order live reload can never move a
 * value backwards. The {@code version} is the triggering event's {@code timestampEpochMilli} (or the
 * join-load timestamp), see research.md §Cache-Version-Strategie.
 */
public final class PermissionCache {

    private final FeatureCache<UUID, PlayerPermissionsView> cache = new FeatureCache<>();

    /** Insert/refresh the player's view observed at {@code version}; older versions lose. */
    public void apply(UUID player, PlayerPermissionsView view, long version) {
        cache.put(player, view, version);
    }

    public Optional<PlayerPermissionsView> get(UUID player) {
        return cache.get(player);
    }

    /** Drop the player's entry (on quit) — no leak across sessions. */
    public void evict(UUID player) {
        cache.remove(player);
    }

    public int size() {
        return cache.size();
    }
}
