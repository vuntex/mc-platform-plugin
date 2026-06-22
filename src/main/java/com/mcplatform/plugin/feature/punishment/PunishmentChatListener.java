package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.Cooldowns;
import com.mcplatform.plugin.transport.FeatureCache;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Chat mute. Reads the player's locally cached {@link PunishmentSnapshot} (warmed at login, kept fresh
 * by live Pub/Sub revokes) and cancels the chat when an active CHATBAN is in effect — expiry evaluated
 * locally, so a chatban that lapses while online stops muting WITHOUT any event. Uses Paper's
 * Adventure-native {@link AsyncChatEvent}.
 *
 * <p>On each blocked message the player gets an action-bar hint (with remaining time); a clickable
 * appeal line is sent to chat at most once per {@link #APPEAL_COOLDOWN_MILLIS} via the shared
 * {@link Cooldowns}, so the mute never spams chat.
 */
public final class PunishmentChatListener implements Listener {

    private static final long APPEAL_COOLDOWN_MILLIS = 30_000L;
    private static final String APPEAL_TYPE = "mute_appeal";

    private final FeatureCache<UUID, PunishmentSnapshot> cache;
    private final Cooldowns cooldowns;

    public PunishmentChatListener(FeatureCache<UUID, PunishmentSnapshot> cache, Cooldowns cooldowns) {
        this.cache = cache;
        this.cooldowns = cooldowns;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        cache.get(uuid).orElse(PunishmentSnapshot.EMPTY).activeChatban(now).ifPresent(chatban -> {
            event.setCancelled(true);
            player.sendActionBar(PunishmentNotice.muteActionBar(chatban.expiresAtEpochMilli(), now));
            // Clickable appeal link to chat — throttled so repeated attempts don't spam.
            if (!cooldowns.throttle(uuid, APPEAL_TYPE, APPEAL_COOLDOWN_MILLIS)) {
                player.sendMessage(PunishmentNotice.muteAppeal());
            }
        });
    }
}
