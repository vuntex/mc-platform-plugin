package com.mcplatform.plugin.feature.permission;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Frees a player's permission cache entry on quit (spec FR-006) — no leak across sessions. The cache is
 * filled earlier, at {@link AsyncPlayerPreLoginEvent} via {@link PermissionWarmupListener}, so there is
 * no join-time load here.
 */
public final class PermissionQuitListener implements Listener {

    private final PermissionCache cache;

    public PermissionQuitListener(PermissionCache cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.evict(event.getPlayer().getUniqueId());
    }
}
