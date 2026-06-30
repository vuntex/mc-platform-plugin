package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;
import com.mcplatform.plugin.feature.scoreboard.provider.StaticLineProvider;

import net.kyori.adventure.text.Component;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Model invariants: LineId identity, ordering, id-stability under reorder (AC-5), unique ids. */
class ScoreboardModelTest {

    private static ScoreboardLine line(String id) {
        return new ScoreboardLine(LineId.of(id), new StaticLineProvider(Component.text(id)));
    }

    @Test
    void lineIdEqualityIsByValue() {
        assertEquals(LineId.of("rank"), new LineId("rank"));
    }

    @Test
    void profilePreservesOrder() {
        ScoreboardProfile p = ScoreboardProfile.profile("Default", line("a"), line("b"), line("c"));
        assertEquals(List.of(LineId.of("a"), LineId.of("b"), LineId.of("c")),
                p.lines().stream().map(ScoreboardLine::id).toList());
    }

    @Test
    void reorderKeepsSameLineIds() {
        ScoreboardProfile original = ScoreboardProfile.profile("Default", line("a"), line("b"), line("c"));
        ScoreboardProfile reordered = ScoreboardProfile.profile("Default", line("c"), line("a"), line("b"));
        // Same id set regardless of position — position drives score, not identity (AC-5).
        assertEquals(original.lines().stream().map(ScoreboardLine::id).collect(java.util.stream.Collectors.toSet()),
                reordered.lines().stream().map(ScoreboardLine::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void duplicateLineIdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ScoreboardProfile.profile("Default", line("a"), line("a")));
    }

    @Test
    void blankLineIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> LineId.of(" "));
    }
}
