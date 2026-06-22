package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentChangedEvent;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles live {@link PunishmentChangedEvent}s from {@code mc:punishment:changed}. The
 * {@code EventDispatcher} delivers these on the MAIN thread, in receive order, so the cache
 * read-modify-write is single-threaded here and Bukkit API (the kick) is safe to call directly.
 *
 * <ul>
 *   <li>Every event is fed to the version-aware cache ({@link Punishments#apply}) — this is the
 *       Live-Revoke: when the team lifts a CHATBAN, the snapshot loses it and the chat mute ends
 *       immediately for the online player.</li>
 *   <li>A newly ISSUED TEMPBAN/PERMABAN for an online player kicks them right away.</li>
 *   <li>A newly ISSUED non-disconnecting punishment (WARN/CHATBAN/…) for an online player shows a
 *       graphical chat notice (with remaining time for a time-bound chat-ban) plus an alert sound.</li>
 * </ul>
 */
public final class PunishmentLiveUpdater implements Consumer<PunishmentChangedEvent> {

    private final FeatureCache<UUID, PunishmentSnapshot> cache;

    public PunishmentLiveUpdater(FeatureCache<UUID, PunishmentSnapshot> cache) {
        this.cache = cache;
    }

    @Override
    public void accept(PunishmentChangedEvent event) {
        // Version-checked cache update (Live-Revoke ends chat mute; new chatban starts muting).
        Punishments.apply(cache, event);

        if (!PunishmentType.ACTION_ISSUED.equals(event.action())) {
            return;
        }

        long now = System.currentTimeMillis();

        // Staff broadcast: tell online team members (with the type's permission) that this happened.
        broadcastToStaff(event, now);

        Player online = Bukkit.getPlayer(event.playerUuid());
        if (online == null || !online.isOnline()) {
            return;
        }

        if (PunishmentType.deniesLogin(event.type())) {
            // New ban while online → kick immediately.
            online.kickPlayer(PunishmentFormat.banScreen(
                    event.type(), event.reason(), event.expiresAtEpochMilli(), now));
        } else {
            // Non-disconnecting punishment (warn/chatban/…) → graphical chat notice + alert sound.
            // The issuing staff is intentionally NOT shown to the punished player.
            online.sendMessage(PunishmentNotice.issued(
                    event.type(), event.reason(), event.expiresAtEpochMilli(), now));
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }
    }

    /** Broadcast an issued punishment to online team members holding the type's notify permission. */
    private static void broadcastToStaff(PunishmentChangedEvent event, long now) {
        String permission = PunishmentType.notifyPermission(event.type());
        var message = PunishmentNotice.broadcast(
                event.type(), targetName(event.playerUuid()), event.expiresAtEpochMilli(), now);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(permission)) {
                staff.sendMessage(message);
            }
        }
    }

    /** Display name of the punished player (no blocking lookup): online, cached, else a short UUID. */
    private static String targetName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String cached = Bukkit.getOfflinePlayer(uuid).getName();
        return cached != null ? cached : uuid.toString().substring(0, 8);
    }
}
