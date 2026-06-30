package com.mcplatform.plugin.feature.scoreboard.condition;

import java.util.Objects;

/** A (condition → profileId) rule. Ordered in the resolver; first match wins (spec FR-004). */
public record ConditionRule(ScoreboardCondition condition, String profileId) {

    public ConditionRule {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(profileId, "profileId");
    }
}
