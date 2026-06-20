package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Chat mute. Reads the player's locally cached {@link PunishmentSnapshot} (warmed at login, kept fresh
 * by live Pub/Sub revokes) and cancels the chat when an active CHATBAN is in effect — with expiry
 * evaluated locally, so a chatban that lapses while online stops muting WITHOUT any event. The cache is
 * thread-safe and the event is already async, so no main-thread hop is needed for the read.
 */
public final class PunishmentChatListener implements Listener {

    private final FeatureCache<UUID, PunishmentSnapshot> cache;

    public PunishmentChatListener(FeatureCache<UUID, PunishmentSnapshot> cache) {
        this.cache = cache;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PunishmentSnapshot snapshot = cache.get(uuid).orElse(PunishmentSnapshot.EMPTY);
        long now = System.currentTimeMillis();
        snapshot.activeChatban(now).ifPresent(chatban -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    PunishmentFormat.chatbanNotice(chatban.reason(), chatban.expiresAtEpochMilli(), now));
        });
    }
}
