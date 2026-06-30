package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.condition.RegionId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What conditions and providers need about a player at resolve/render time: the player's UUID and a
 * region snapshot (taken once via the {@code RegionProvider} when the context is built). Bukkit-free so
 * resolution/rendering stay unit-testable. In Slice 1 the region snapshot is static (stub).
 */
public record PlayerContext(UUID player, Optional<RegionId> region) {

    public PlayerContext {
        Objects.requireNonNull(player, "player");
    }

    public static PlayerContext of(UUID player, Optional<RegionId> region) {
        return new PlayerContext(player, region);
    }
}
