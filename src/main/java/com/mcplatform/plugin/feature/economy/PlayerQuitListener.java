package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.transport.FeatureCache;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * On leave: drop the player's local cache entry. The backend owns the final sync/TTL on its hot
 * cache — the plugin performs NO Redis write (Prinzip 1).
 */
public final class PlayerQuitListener implements Listener {

    private final FeatureCache<UUID, Long> cache;

    public PlayerQuitListener(FeatureCache<UUID, Long> cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
