package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;
import com.mcplatform.plugin.feature.scoreboard.support.RecordingScoreboardHandle;

import net.kyori.adventure.text.Component;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Contract a {@link com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandle} must satisfy and
 * that the service relies on: install seeds slots; update changes ONLY the addressed slot (no full
 * rebuild → flicker-free, AC-3); teardown is observable. Exercised via the recording double; the Bukkit
 * implementation is validated by the manual smoke (quickstart).
 */
class ScoreboardHandleContractTest {

    @Test
    void updateTouchesOnlyTheAddressedSlot() {
        RecordingScoreboardHandle handle = new RecordingScoreboardHandle();
        handle.install(List.of(
                new RenderedLine(LineId.of("rank"), Component.text("Rang: A")),
                new RenderedLine(LineId.of("coins"), Component.text("Coins: 1")),
                new RenderedLine(LineId.of("footer"), Component.text("footer"))));

        handle.update(LineId.of("coins"), Component.text("Coins: 2"));

        assertEquals(Component.text("Coins: 2"), handle.current.get(LineId.of("coins")));
        assertEquals(Component.text("Rang: A"), handle.current.get(LineId.of("rank")));      // untouched
        assertEquals(Component.text("footer"), handle.current.get(LineId.of("footer")));     // untouched
        assertEquals(List.of(LineId.of("coins")), handle.updatedIds);                        // no rebuild
    }

    @Test
    void teardownObservable() {
        RecordingScoreboardHandle handle = new RecordingScoreboardHandle();
        assertFalse(handle.torn);
        handle.teardown();
        assertTrue(handle.torn);
    }
}
