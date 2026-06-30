package com.mcplatform.plugin.feature.scoreboard.render;

import org.bukkit.entity.Player;

/** Production factory: a real {@link BukkitScoreboardHandle} per player. */
public final class BukkitScoreboardHandleFactory implements ScoreboardHandleFactory {

    @Override
    public ScoreboardHandle create(Player player) {
        return new BukkitScoreboardHandle(player);
    }
}
