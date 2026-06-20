package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.transport.BackendException;

import org.junit.jupiter.api.Test;

/** Duration parsing/rendering, player-facing screens, and clean 403 mapping. */
class PunishmentFormatTest {

    @Test
    void parsesSingleAndCombinedDurations() {
        assertEquals(7_200_000L, PunishmentFormat.parseDuration("2h"));
        assertEquals(86_400_000L + 43_200_000L, PunishmentFormat.parseDuration("1d12h"));
        assertEquals(604_800_000L, PunishmentFormat.parseDuration("1w"));
    }

    @Test
    void rejectsGarbageDurations() {
        assertThrows(IllegalArgumentException.class, () -> PunishmentFormat.parseDuration(""));
        assertThrows(IllegalArgumentException.class, () -> PunishmentFormat.parseDuration("abc"));
        assertThrows(IllegalArgumentException.class, () -> PunishmentFormat.parseDuration("2x"));
        assertThrows(IllegalArgumentException.class, () -> PunishmentFormat.parseDuration("2h junk"));
    }

    @Test
    void formatsDurationDroppingZeroParts() {
        assertEquals("1d 1h 1m 1s", PunishmentFormat.formatDuration(90_061_000L));
        assertEquals("0s", PunishmentFormat.formatDuration(0L));
    }

    @Test
    void banScreenShowsRemainingForTempbanAndPermanentOtherwise() {
        long now = 2_000_000_000_000L;
        String temp = PunishmentFormat.banScreen(PunishmentType.TEMPBAN, "grief", now + 3_600_000L, now);
        assertTrue(temp.contains("grief"));
        assertTrue(temp.contains("1h"));

        String perma = PunishmentFormat.banScreen(PunishmentType.PERMABAN, "cheat", 0L, now);
        assertTrue(perma.contains("permanent"));
    }

    @Test
    void backendErrorMapsForbiddenCleanly() {
        BackendException forbidden = BackendException.fromStatus(403, "nope");
        assertTrue(PunishmentFormat.backendError(forbidden).contains("403"));
        assertTrue(PunishmentFormat.backendError(forbidden).toLowerCase().contains("berechtigung"));
    }
}
