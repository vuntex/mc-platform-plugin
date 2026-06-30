package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.condition.RegionCondition;
import com.mcplatform.plugin.feature.scoreboard.condition.RegionId;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** RegionCondition matches only when the context's region snapshot equals the target (AC-2). */
class RegionConditionTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private final RegionCondition condition = new RegionCondition(RegionId.of("r1"));

    @Test
    void emptyRegionDoesNotMatch() {
        assertFalse(condition.matches(new PlayerContext(PLAYER, Optional.empty())));
    }

    @Test
    void matchingRegionMatches() {
        assertTrue(condition.matches(new PlayerContext(PLAYER, Optional.of(RegionId.of("r1")))));
    }

    @Test
    void otherRegionDoesNotMatch() {
        assertFalse(condition.matches(new PlayerContext(PLAYER, Optional.of(RegionId.of("r2")))));
    }
}
