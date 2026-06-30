package com.mcplatform.plugin.feature.scoreboard.model;

import com.mcplatform.plugin.feature.scoreboard.provider.LineProvider;

import java.util.Objects;

/**
 * One line of a profile: a stable {@link LineId} bound to a {@link LineProvider}. The id is the
 * identity over time; the provider supplies the content. Swapping the provider (stub → real) keeps the
 * id and position (FR-013/AC-4).
 */
public record ScoreboardLine(LineId id, LineProvider provider) {

    public ScoreboardLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
    }

    public static ScoreboardLine line(LineId id, LineProvider provider) {
        return new ScoreboardLine(id, provider);
    }
}
