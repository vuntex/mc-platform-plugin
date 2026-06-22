package com.mcplatform.plugin.feature.report;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drops a reporter's pending reason input if they disconnect mid-prompt, so a half-finished report never
 * lingers (spec edge case). The global chat ring is server-wide, not per-player, so nothing else needs
 * quit-eviction here.
 */
public final class ReportSession implements Listener {

    private final ReportReasonPrompt prompt;

    public ReportSession(ReportReasonPrompt prompt) {
        this.prompt = prompt;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompt.cancel(event.getPlayer().getUniqueId());
    }
}
