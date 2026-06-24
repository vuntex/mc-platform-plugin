package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DurationInputTest {

    @Test
    void blankAndPermanentMeanPermanent() {
        assertNull(DurationInput.parseSeconds(null));
        assertNull(DurationInput.parseSeconds(""));
        assertNull(DurationInput.parseSeconds("   "));
        assertNull(DurationInput.parseSeconds("permanent"));
        assertNull(DurationInput.parseSeconds("perm"));
        assertNull(DurationInput.parseSeconds("-"));
        assertNull(DurationInput.parseSeconds("-1"));
    }

    @Test
    void parsesSingleAndCombinedUnits() {
        assertEquals(30L * 86_400L, DurationInput.parseSeconds("30d"));
        assertEquals(12L * 3600L, DurationInput.parseSeconds("12h"));
        assertEquals(86_400L + 12L * 3600L, DurationInput.parseSeconds("1d12h"));
        assertEquals(604_800L, DurationInput.parseSeconds("1w"));
        assertEquals(90L, DurationInput.parseSeconds("1m30s"));
    }

    @Test
    void rejectsGarbageAndNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> DurationInput.parseSeconds("abc"));
        assertThrows(IllegalArgumentException.class, () -> DurationInput.parseSeconds("30x"));
        assertThrows(IllegalArgumentException.class, () -> DurationInput.parseSeconds("12h foo"));
        assertThrows(IllegalArgumentException.class, () -> DurationInput.parseSeconds("0s"));
    }
}
