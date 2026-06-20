package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Snapshot query + immutable-mutation logic, incl. LOCAL time expiry (no event needed). */
class PunishmentSnapshotTest {

    private static final long NOW = 2_000_000_000_000L;
    private static final UUID CHAT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BAN = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @Test
    void chatbanExpiresLocallyWithoutAnEvent() {
        ActivePunishment chatban = new ActivePunishment(CHAT, PunishmentType.CHATBAN, "spam", NOW + 1_000L, 1L);
        PunishmentSnapshot snapshot = PunishmentSnapshot.EMPTY.withIssued(chatban);

        assertTrue(snapshot.activeChatban(NOW).isPresent());          // before expiry → muted
        assertTrue(snapshot.activeChatban(NOW + 2_000L).isEmpty());   // past expiry → free, no event
    }

    @Test
    void permanentBanIsAlwaysActive() {
        PunishmentSnapshot snapshot = PunishmentSnapshot.EMPTY.withIssued(
                new ActivePunishment(BAN, PunishmentType.PERMABAN, "cheat", 0L, 1L));
        assertTrue(snapshot.activeBan(NOW).isPresent());
        assertTrue(snapshot.activeBan(Long.MAX_VALUE).isPresent());
    }

    @Test
    void withRevokedRemovesAndIsImmutable() {
        PunishmentSnapshot withChatban = PunishmentSnapshot.EMPTY.withIssued(
                new ActivePunishment(CHAT, PunishmentType.CHATBAN, "spam", 0L, 1L));
        PunishmentSnapshot revoked = withChatban.withRevoked(CHAT);

        assertEquals(0, revoked.size());
        assertEquals(1, withChatban.size()); // original untouched
        assertFalse(revoked.activeChatban(NOW).isPresent());
    }
}
