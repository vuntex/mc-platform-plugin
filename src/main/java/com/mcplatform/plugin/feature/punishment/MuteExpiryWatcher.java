package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Polls online players once per second and, when a player's chat mute ends — by time expiry (which
 * fires no Pub/Sub event, since expiry is evaluated locally) OR a live revoke — sends a one-time
 * "you can chat again" notice. Tracks who is currently muted, so the message is sent exactly on the
 * muted→unmuted transition (never repeated, never on login). Main-thread only (scheduled via
 * {@code runSyncTimer}), so the cache reads and the tracking set need no synchronisation.
 */
final class MuteExpiryWatcher implements Runnable {

    private final FeatureCache<UUID, PunishmentSnapshot> cache;
    private final Set<UUID> muted = new HashSet<>();

    MuteExpiryWatcher(FeatureCache<UUID, PunishmentSnapshot> cache) {
        this.cache = cache;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            online.add(id);
            boolean nowMuted = cache.get(id).orElse(PunishmentSnapshot.EMPTY).activeChatban(now).isPresent();
            if (nowMuted) {
                muted.add(id);
            } else if (muted.remove(id)) {
                player.sendMessage(PunishmentNotice.muteExpired());
            }
        }
        muted.retainAll(online); // forget players who logged off while still muted
    }
}
