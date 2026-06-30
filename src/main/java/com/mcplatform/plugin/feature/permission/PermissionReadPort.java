package com.mcplatform.plugin.feature.permission;

import com.mcplatform.protocol.permission.RoleDisplay;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view that lets other features (e.g. {@code feature.scoreboard}) consume the EXISTING,
 * warm {@link PermissionCache} (PreLogin fail-closed warmup) without owning a second cache (spec §4).
 * Additive — changes no permission behaviour and no generic class.
 *
 * <p>Returns the plain rank display name (spec FR-003a — no color/prefix); empty on a cold cache,
 * which should not happen for an in-world player given the warmup.
 */
public final class PermissionReadPort {

    private final PermissionCache cache;

    public PermissionReadPort(PermissionCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public Optional<String> currentRankName(UUID player) {
        return cache.get(player)
                .map(PlayerPermissionsView::display)
                .map(RoleDisplay::displayName)
                .filter(name -> !name.isBlank());
    }
}
