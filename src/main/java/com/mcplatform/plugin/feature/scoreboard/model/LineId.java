package com.mcplatform.plugin.feature.scoreboard.model;

import java.util.Objects;

/**
 * Stable identity of a scoreboard line, independent of its position. Live updates address a line by
 * its {@code LineId} (not its index), so reordering or swapping a line never breaks live addressing or
 * causes flicker (spec FR-002, AC-5). The on-screen height (Bukkit {@code score}) is derived from the
 * line's position in the profile, never from this id.
 */
public record LineId(String value) {

    public LineId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LineId value must not be blank");
        }
    }

    public static LineId of(String value) {
        return new LineId(value);
    }
}
