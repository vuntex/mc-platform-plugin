package com.mcplatform.plugin.feature.scoreboard.condition;

import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import java.util.Objects;

/**
 * Matches when the player's region snapshot equals a target region (spec FR-005). The snapshot is read
 * from the {@link PlayerContext} (taken via the {@link RegionProvider} when the context was built).
 */
public final class RegionCondition implements ScoreboardCondition {

    private final RegionId target;

    public RegionCondition(RegionId target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    @Override
    public boolean matches(PlayerContext ctx) {
        return ctx.region().map(target::equals).orElse(false);
    }
}
