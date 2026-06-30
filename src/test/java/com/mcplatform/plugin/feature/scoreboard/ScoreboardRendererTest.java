package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardRenderer;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Renderer maps profile → ordered RenderedLines via providers (fake providers, no Bukkit). */
class ScoreboardRendererTest {

    @Test
    void rendersEachLineInOrder() {
        PlayerContext ctx = new PlayerContext(UUID.randomUUID(), Optional.empty());
        ScoreboardProfile profile = ScoreboardProfile.profile("Default",
                new ScoreboardLine(LineId.of("a"), c -> Component.text("A")),
                new ScoreboardLine(LineId.of("b"), c -> Component.text("B")));

        List<RenderedLine> rendered = new ScoreboardRenderer().render(profile, ctx);

        assertEquals(List.of(
                        new RenderedLine(LineId.of("a"), Component.text("A")),
                        new RenderedLine(LineId.of("b"), Component.text("B"))),
                rendered);
    }
}
