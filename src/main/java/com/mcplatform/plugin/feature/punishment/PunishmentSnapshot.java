package com.mcplatform.plugin.feature.punishment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable view of a player's currently-known active punishments (keyed by punishment id), held as
 * the value of the feature's generic {@code FeatureCache<UUID, PunishmentSnapshot>}. Time-based expiry
 * is evaluated locally ({@link ActivePunishment#isActiveAt}) so a CHATBAN/TEMPBAN can lapse while the
 * player is online WITHOUT any Pub/Sub event — matching "Tempban läuft ab → ohne dass ein Event
 * geschrieben wurde". Mutations return new snapshots so the cache only ever swaps whole values.
 */
final class PunishmentSnapshot {

    static final PunishmentSnapshot EMPTY = new PunishmentSnapshot(Map.of());

    private final Map<UUID, ActivePunishment> byId;

    PunishmentSnapshot(Map<UUID, ActivePunishment> byId) {
        this.byId = Map.copyOf(byId);
    }

    /** A copy with {@code punishment} added/replaced (ISSUED). */
    PunishmentSnapshot withIssued(ActivePunishment punishment) {
        Map<UUID, ActivePunishment> next = new HashMap<>(byId);
        next.put(punishment.id(), punishment);
        return new PunishmentSnapshot(next);
    }

    /** A copy with the punishment {@code id} removed (REVOKED). Returns {@code this} if absent. */
    PunishmentSnapshot withRevoked(UUID id) {
        if (!byId.containsKey(id)) {
            return this;
        }
        Map<UUID, ActivePunishment> next = new HashMap<>(byId);
        next.remove(id);
        return new PunishmentSnapshot(next);
    }

    /** An active CHATBAN in effect at {@code now}, if any (drives the chat mute). */
    Optional<ActivePunishment> activeChatban(long now) {
        return firstActive(now, PunishmentType::deniesChat);
    }

    /** An active TEMPBAN/PERMABAN in effect at {@code now}, if any. */
    Optional<ActivePunishment> activeBan(long now) {
        return firstActive(now, PunishmentType::deniesLogin);
    }

    int size() {
        return byId.size();
    }

    private Optional<ActivePunishment> firstActive(long now, java.util.function.Predicate<String> typeMatches) {
        for (ActivePunishment p : byId.values()) {
            if (typeMatches.test(p.type()) && p.isActiveAt(now)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
