package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentChangedEvent;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import org.junit.jupiter.api.Test;

/**
 * Proves the punishment↔cache mapping against the real {@code plugin-protocol} DTOs, no Bukkit: active
 * list → snapshot, login ban detection, live ISSUED/REVOKED apply, offline-ignore (no cache leak), and
 * version-awareness via the SAME generic {@link FeatureCache} the economy slice uses.
 */
class PunishmentsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID P_CHAT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID P_TEMP = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID P_PERMA = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static PunishmentResponse resp(UUID id, String type, long expiresAt, boolean active, long version) {
        return new PunishmentResponse(id, PLAYER, type, "reason-" + type, ACTOR,
                1_700_000_000_000L, expiresAt, null, 0L, active, version);
    }

    private static PunishmentChangedEvent changed(UUID id, String type, String action, long expiresAt, long version) {
        return new PunishmentChangedEvent(id, PLAYER, type, action, "reason-" + type, ACTOR,
                expiresAt, version, 1_700_000_000_000L);
    }

    @Test
    void snapshotKeepsOnlyBackendActiveEntriesAndMaxVersion() {
        PunishmentResponse[] active = {
                resp(P_CHAT, PunishmentType.CHATBAN, 0L, true, 4L),
                resp(P_TEMP, PunishmentType.TEMPBAN, 0L, true, 7L),
                resp(P_PERMA, PunishmentType.PERMABAN, 0L, false, 9L), // not active → dropped
        };

        PunishmentSnapshot snapshot = Punishments.snapshot(active);
        assertEquals(2, snapshot.size());
        assertEquals(9L, Punishments.maxVersion(active)); // max regardless of active flag
    }

    @Test
    void firstActiveBanFindsTempOrPermaButNotChatOrInactive() {
        PunishmentResponse[] active = {
                resp(P_CHAT, PunishmentType.CHATBAN, 0L, true, 1L),     // not a login ban
                resp(P_TEMP, PunishmentType.TEMPBAN, 0L, true, 2L),     // <- this one
                resp(P_PERMA, PunishmentType.PERMABAN, 0L, false, 3L),  // inactive
        };
        assertEquals(PunishmentType.TEMPBAN, Punishments.firstActiveBan(active).orElseThrow().type());

        PunishmentResponse[] noBan = { resp(P_CHAT, PunishmentType.CHATBAN, 0L, true, 1L) };
        assertTrue(Punishments.firstActiveBan(noBan).isEmpty());
    }

    @Test
    void warmThenLiveIssueAndRevokeKeepCacheCorrect() {
        FeatureCache<UUID, PunishmentSnapshot> cache = new FeatureCache<>();
        long now = 2_000_000_000_000L;

        // Join warmup: one active chatban (future expiry).
        Punishments.warm(cache, PLAYER, new PunishmentResponse[]{
                resp(P_CHAT, PunishmentType.CHATBAN, now + 60_000L, true, 5L)});
        assertTrue(cache.get(PLAYER).orElseThrow().activeChatban(now).isPresent());

        // Live: a tempban is issued → cached + a login ban now present.
        Punishments.apply(cache, changed(P_TEMP, PunishmentType.TEMPBAN, PunishmentType.ACTION_ISSUED, 0L, 6L));
        assertTrue(cache.get(PLAYER).orElseThrow().activeBan(now).isPresent());

        // Live-revoke: chatban lifted → chat mute ends immediately.
        Punishments.apply(cache, changed(P_CHAT, PunishmentType.CHATBAN, PunishmentType.ACTION_REVOKED, 0L, 7L));
        assertTrue(cache.get(PLAYER).orElseThrow().activeChatban(now).isEmpty());
        assertEquals(7L, cache.version(PLAYER).orElseThrow());
    }

    @Test
    void applyIgnoresUntrackedOfflinePlayers() {
        FeatureCache<UUID, PunishmentSnapshot> cache = new FeatureCache<>();
        Punishments.apply(cache, changed(P_TEMP, PunishmentType.TEMPBAN, PunishmentType.ACTION_ISSUED, 0L, 6L));
        assertTrue(cache.get(PLAYER).isEmpty()); // no entry created → no leak
    }

    @Test
    void applyHonorsVersionStaleness() {
        FeatureCache<UUID, PunishmentSnapshot> cache = new FeatureCache<>();
        Punishments.warm(cache, PLAYER, new PunishmentResponse[]{
                resp(P_CHAT, PunishmentType.CHATBAN, 0L, true, 7L)});

        // A stale ISSUED (version 4 < 7) must not move state backwards.
        Punishments.apply(cache, changed(P_TEMP, PunishmentType.TEMPBAN, PunishmentType.ACTION_ISSUED, 0L, 4L));

        assertEquals(7L, cache.version(PLAYER).orElseThrow());
        assertFalse(cache.get(PLAYER).orElseThrow().activeBan(2_000_000_000_000L).isPresent());
    }
}
