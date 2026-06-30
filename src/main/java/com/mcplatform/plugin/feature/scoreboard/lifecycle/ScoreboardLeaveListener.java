package com.mcplatform.plugin.feature.scoreboard.lifecycle;

import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardService;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * On leave: remove the player's board — closes the live subscription (no observer leak) and tears the
 * scoreboard down (spec FR-009/AC-6).
 */
public final class ScoreboardLeaveListener implements Listener {

    private final ScoreboardService service;

    public ScoreboardLeaveListener(ScoreboardService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.remove(event.getPlayer().getUniqueId());
    }
}
