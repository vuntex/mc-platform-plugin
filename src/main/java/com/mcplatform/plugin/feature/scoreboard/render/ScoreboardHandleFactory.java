package com.mcplatform.plugin.feature.scoreboard.render;

import org.bukkit.entity.Player;

/** Creates a per-player {@link ScoreboardHandle}. Faked in tests; Bukkit-backed in production. */
public interface ScoreboardHandleFactory {

    ScoreboardHandle create(Player player);
}
