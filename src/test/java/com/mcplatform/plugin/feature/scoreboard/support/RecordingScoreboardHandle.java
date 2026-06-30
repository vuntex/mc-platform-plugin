package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandle;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Bukkit-free {@link ScoreboardHandle} double recording installs/updates/teardown for assertions. */
public final class RecordingScoreboardHandle implements ScoreboardHandle {

    public final List<RenderedLine> installed = new ArrayList<>();
    public final Map<LineId, Component> current = new LinkedHashMap<>();
    public final List<LineId> updatedIds = new ArrayList<>();
    public boolean torn = false;

    @Override
    public void install(List<RenderedLine> lines) {
        installed.clear();
        installed.addAll(lines);
        for (RenderedLine line : lines) {
            current.put(line.id(), line.component());
        }
    }

    @Override
    public void update(LineId id, Component value) {
        current.put(id, value);
        updatedIds.add(id);
    }

    @Override
    public void teardown() {
        torn = true;
    }
}
