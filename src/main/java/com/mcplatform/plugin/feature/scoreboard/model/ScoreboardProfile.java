package com.mcplatform.plugin.feature.scoreboard.model;

import java.util.List;
import java.util.Objects;

/**
 * A named, ordered list of line contributions (spec FR-001). The order is the visual order (position →
 * Bukkit score); ids stay stable across reordering. {@link LineId}s within a profile must be unique so
 * live updates address exactly one line.
 */
public record ScoreboardProfile(String id, List<ScoreboardLine> lines) {

    public ScoreboardProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        long distinctIds = lines.stream().map(ScoreboardLine::id).distinct().count();
        if (distinctIds != lines.size()) {
            throw new IllegalArgumentException("Duplicate LineId in profile " + id);
        }
    }

    public static ScoreboardProfile profile(String id, ScoreboardLine... lines) {
        return new ScoreboardProfile(id, List.of(lines));
    }
}
