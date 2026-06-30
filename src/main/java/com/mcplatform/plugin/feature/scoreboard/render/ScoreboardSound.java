package com.mcplatform.plugin.feature.scoreboard.render;

import java.util.UUID;

/**
 * Sound effects for the scoreboard (coin count-up). A tiny seam so the animation logic stays Bukkit-free
 * and unit-testable; the Bukkit implementation plays the actual sounds. {@link #NONE} is a silent no-op.
 */
public interface ScoreboardSound {

    /** A short tick played on each count-up step. */
    void coinTick(UUID player);

    /** A richer sound played once when the count-up finishes. */
    void coinComplete(UUID player);

    ScoreboardSound NONE = new ScoreboardSound() {
        @Override
        public void coinTick(UUID player) {
        }

        @Override
        public void coinComplete(UUID player) {
        }
    };
}
