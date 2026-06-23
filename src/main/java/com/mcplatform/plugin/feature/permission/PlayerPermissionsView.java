package com.mcplatform.plugin.feature.permission;

import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RoleDisplay;

import java.util.Set;

/**
 * The cached, immutable per-player view this slice actually needs: the flattened effective permission
 * set (for the optimistic gate) and the chosen {@link RoleDisplay} (for the menus). Derived from the
 * backend's {@link PlayerPermissionsResponse}; the backend stays the source of truth. Bukkit-free.
 */
public record PlayerPermissionsView(Set<String> effective, RoleDisplay display) {

    public PlayerPermissionsView {
        effective = effective == null ? Set.of() : Set.copyOf(effective);
    }

    /** Build the view from a backend {@code /effective} response. */
    public static PlayerPermissionsView from(PlayerPermissionsResponse response) {
        return new PlayerPermissionsView(
                response.effectivePermissions() == null ? Set.of() : Set.copyOf(response.effectivePermissions()),
                response.display());
    }

    /** Whether the player holds the given permission node (exact match against the effective set). */
    public boolean has(String node) {
        return effective.contains(node);
    }
}
