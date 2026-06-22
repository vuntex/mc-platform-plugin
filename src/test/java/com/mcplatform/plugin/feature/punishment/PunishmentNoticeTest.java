package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the player-facing punishment notice content (flattened to plain text). */
class PunishmentNoticeTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final long NOW = 1_000_000L;

    private String text(String type, String reason, long expiresAt) {
        return PLAIN.serialize(PunishmentNotice.issued(type, reason, expiresAt, NOW));
    }

    @Test
    void warnShowsTitleReasonAndAppealLinkButNoIssuer() {
        String out = text(PunishmentType.WARN, "Spam im Chat", 0L);
        assertTrue(out.contains("Verwarnung"), out);
        assertTrue(out.contains("Spam im Chat"));
        assertTrue(out.contains("hier"), "appeal link present");
        assertFalse(out.contains("Von"), "the issuing staff must not be shown to the player");
    }

    @Test
    void timeBoundChatbanShowsCoarseDurationRoundedUp() {
        // 1h 59m 59s remaining must read as "2h" — no noisy seconds.
        String out = text(PunishmentType.CHATBAN, "Toxisch", NOW + 7_199_000L);
        assertTrue(out.contains("Chat Mute"), out);
        assertTrue(out.contains("Chat für 2h gesperrt"), out);
        assertFalse(out.contains("59s"), out);
    }

    @Test
    void permanentChatbanShowsPermanent() {
        String out = text(PunishmentType.CHATBAN, "Werbung", 0L);
        assertTrue(out.contains("Chat Mute"));
        assertTrue(out.contains("permanent"), out);
        assertTrue(out.contains("nicht mehr schreiben"), out);
    }

    @Test
    void muteActionBarShowsRemainingOrPermanent() {
        String timed = PLAIN.serialize(PunishmentNotice.muteActionBar(NOW + 7_199_000L, NOW));
        assertTrue(timed.contains("noch für 2h gemutet"), timed);

        String perma = PLAIN.serialize(PunishmentNotice.muteActionBar(0L, NOW));
        assertTrue(perma.contains("permanent gemutet"), perma);
    }

    @Test
    void muteExpiredTellsPlayerTheyCanChatAgain() {
        String out = PLAIN.serialize(PunishmentNotice.muteExpired());
        assertTrue(out.contains("Mute>"), out);
        assertTrue(out.contains("wieder im globalen Chat schreiben"), out);
    }

    @Test
    void muteAppealIsAClickHint() {
        String out = PLAIN.serialize(PunishmentNotice.muteAppeal());
        assertTrue(out.contains("hier"), out);
        assertTrue(out.contains("Fehler"), out);
    }

    @Test
    void broadcastReadsAsAStaffLogLine() {
        String chatban = PLAIN.serialize(PunishmentNotice.broadcast(
                PunishmentType.CHATBAN, "Steve", NOW + 7_199_000L, NOW));
        assertTrue(chatban.contains("Steve"), chatban);
        assertTrue(chatban.contains("vom Chat ausgeschlossen"), chatban);
        assertTrue(chatban.contains("(2h)"), chatban);

        String permaban = PLAIN.serialize(PunishmentNotice.broadcast(
                PunishmentType.PERMABAN, "Steve", 0L, NOW));
        assertTrue(permaban.contains("permanent gebannt"), permaban);

        String warn = PLAIN.serialize(PunishmentNotice.broadcast(PunishmentType.WARN, "Steve", 0L, NOW));
        assertTrue(warn.contains("verwarnt"), warn);
        assertFalse(warn.contains("("), "a warning carries no duration");
    }
}
