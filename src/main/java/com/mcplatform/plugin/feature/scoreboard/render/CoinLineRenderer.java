package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Renders the coins value line. The production {@link CoinLineAnimator} counts up on a gain (with sound);
 * a plain implementation can just set the value. Abstracted so {@code ScoreboardService} stays free of
 * timer/sound concerns and unit-testable.
 */
public interface CoinLineRenderer {

    /** Show {@code coins} on the line (animated on a gain); empty = not yet loaded (placeholder). */
    void update(UUID player, ScoreboardHandle handle, LineId line, OptionalLong coins);

    /** Drop per-player state and cancel any running animation (on leave). */
    default void clear(UUID player) {
    }
}
