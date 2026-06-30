package com.mcplatform.plugin.feature.scoreboard.lifecycle;

import com.mcplatform.plugin.feature.scoreboard.condition.RegionProvider;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandle;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandleFactory;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * On join: build the {@link PlayerContext} (region snapshot via the stub), create a per-player handle,
 * and hand both to the service. Permission is warm by now (PreLogin fail-closed warmup); economy is
 * filled lazily by the service's coins load (spec FR-009). Thin Bukkit glue — logic lives in the
 * service.
 */
public final class ScoreboardJoinListener implements Listener {

    private final ScoreboardService service;
    private final ScoreboardHandleFactory handles;
    private final RegionProvider regions;

    public ScoreboardJoinListener(ScoreboardService service, ScoreboardHandleFactory handles,
                                  RegionProvider regions) {
        this.service = Objects.requireNonNull(service, "service");
        this.handles = Objects.requireNonNull(handles, "handles");
        this.regions = Objects.requireNonNull(regions, "regions");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        PlayerContext ctx = new PlayerContext(id, regions.currentRegion(id));
        ScoreboardHandle handle = handles.create(player);
        service.show(ctx, handle);
    }
}
