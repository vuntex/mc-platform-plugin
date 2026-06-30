package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardSound;

import java.util.UUID;

/** Counts sound calls so the coin animation can be asserted without Bukkit. */
public final class RecordingScoreboardSound implements ScoreboardSound {

    public int ticks = 0;
    public int completes = 0;

    @Override
    public void coinTick(UUID player) {
        ticks++;
    }

    @Override
    public void coinComplete(UUID player) {
        completes++;
    }
}
