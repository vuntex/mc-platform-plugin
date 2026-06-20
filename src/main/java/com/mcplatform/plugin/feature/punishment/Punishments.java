package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentChangedEvent;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure mapping between {@code plugin-protocol} punishment DTOs/events and the generic
 * {@link FeatureCache}{@code <UUID, PunishmentSnapshot>} the feature keeps (player UUID → active
 * punishments). No Bukkit, no I/O — the warmup and live-update logic stays unit-testable, exactly like
 * {@code EconomyBalances}. Version-awareness is the cache's job: every {@link FeatureCache#put} carries
 * the originating {@code sequence_no} so out-of-order live updates can never move state backwards.
 */
final class Punishments {

    private Punishments() {
    }

    /** Build a snapshot from the backend's active list, keeping only entries the backend marks active. */
    static PunishmentSnapshot snapshot(PunishmentResponse[] active) {
        Map<UUID, ActivePunishment> byId = new HashMap<>();
        for (PunishmentResponse r : active) {
            if (r.active()) {
                byId.put(r.id(), new ActivePunishment(
                        r.id(), r.type(), r.reason(), r.expiresAtEpochMilli(), r.version()));
            }
        }
        return new PunishmentSnapshot(byId);
    }

    /** Newest {@code version} in the active list — the version to put the whole snapshot at (0 if empty). */
    static long maxVersion(PunishmentResponse[] active) {
        long max = 0L;
        for (PunishmentResponse r : active) {
            max = Math.max(max, r.version());
        }
        return max;
    }

    /**
     * First active login-denying punishment (TEMPBAN/PERMABAN), if any. Trusts the backend-computed
     * {@code active} flag — the login check always runs against a FRESH list, so an expired TEMPBAN is
     * already {@code active == false} (no event needed, "Tempban läuft ab → Login wieder möglich").
     */
    static Optional<PunishmentResponse> firstActiveBan(PunishmentResponse[] active) {
        for (PunishmentResponse r : active) {
            if (r.active() && PunishmentType.deniesLogin(r.type())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Warm the cache for a player from a fresh active list (version-aware put). */
    static void warm(FeatureCache<UUID, PunishmentSnapshot> cache, UUID player, PunishmentResponse[] active) {
        cache.put(player, snapshot(active), maxVersion(active));
    }

    /**
     * Apply a live punishment change to the cache — but only for a player we are already tracking
     * (i.e. online; an entry exists). Ignoring untracked players means a stream of events for offline
     * players never creates leaking entries. ISSUED adds/replaces, REVOKED removes; the put carries the
     * event's {@code version} so a re-delivered/older event is dropped by the cache.
     */
    static void apply(FeatureCache<UUID, PunishmentSnapshot> cache, PunishmentChangedEvent event) {
        Optional<PunishmentSnapshot> current = cache.get(event.playerUuid());
        if (current.isEmpty()) {
            return; // offline / untracked — nothing to enforce locally, and we avoid a cache leak
        }
        PunishmentSnapshot updated = PunishmentType.ACTION_REVOKED.equals(event.action())
                ? current.get().withRevoked(event.punishmentId())
                : current.get().withIssued(new ActivePunishment(
                        event.punishmentId(), event.type(), event.reason(),
                        event.expiresAtEpochMilli(), event.version()));
        cache.put(event.playerUuid(), updated, event.version());
    }
}
