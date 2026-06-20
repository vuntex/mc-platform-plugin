package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * On leave: drop the player's local cache entry (analog the economy quit listener). The backend owns
 * the source of truth; the plugin keeps no per-player state past the session.
 */
public final class PunishmentQuitListener implements Listener {

    private final FeatureCache<UUID, PunishmentSnapshot> cache;

    public PunishmentQuitListener(FeatureCache<UUID, PunishmentSnapshot> cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
