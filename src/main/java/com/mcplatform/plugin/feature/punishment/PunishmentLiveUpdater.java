package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentChangedEvent;

import org.bukkit.Bukkit;
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

        // New ban while online → kick immediately.
        if (PunishmentType.ACTION_ISSUED.equals(event.action()) && PunishmentType.deniesLogin(event.type())) {
            Player online = Bukkit.getPlayer(event.playerUuid());
            if (online != null && online.isOnline()) {
                online.kickPlayer(PunishmentFormat.banScreen(
                        event.type(), event.reason(), event.expiresAtEpochMilli(), System.currentTimeMillis()));
            }
        }
    }
}
