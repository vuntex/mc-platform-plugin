package com.mcplatform.plugin.feature.scoreboard.condition;

import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

/**
 * Predicate deciding whether a profile applies to a player. Generic by design: Region is the first
 * implementation; "event running", "permission X", "world Y" are future predicates needing no resolver
 * change (spec FR-005).
 */
public interface ScoreboardCondition {

    boolean matches(PlayerContext ctx);
}
