package com.mcplatform.plugin.feature.scoreboard.render;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Bukkit implementation of {@link ScoreboardSound}: a soft UI click on each coin count-up step and a
 * richer XP-pickup "ding" when it finishes. Played at the player's own location, low volume.
 */
public final class BukkitScoreboardSound implements ScoreboardSound {

    @Override
    public void coinTick(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 2.0f);
        }
    }

    @Override
    public void coinComplete(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }
}
