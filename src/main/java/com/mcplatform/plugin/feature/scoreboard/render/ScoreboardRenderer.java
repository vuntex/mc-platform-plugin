package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a profile against its providers into ordered {@link RenderedLine}s. Pure: it only knows
 * "profile → lines → components", never the selection/condition logic (spec FR-010, separation of
 * Selektion vs. Komposition). No Bukkit, no I/O — providers read already-known values.
 */
public final class ScoreboardRenderer {

    public List<RenderedLine> render(ScoreboardProfile profile, PlayerContext ctx) {
        List<RenderedLine> out = new ArrayList<>(profile.lines().size());
        for (ScoreboardLine line : profile.lines()) {
            out.add(new RenderedLine(line.id(), line.provider().resolve(ctx)));
        }
        return List.copyOf(out);
    }
}
