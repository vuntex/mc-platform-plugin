package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.permission.PermissionReadPort;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;

/**
 * Rang VALUE line — reads the plain rank display name from the existing permission cache via
 * {@link PermissionReadPort} (the "Rank" label is a separate static line). The name is still the plain
 * display name (FR-003a: not the role's own color/prefix); the scoreboard applies its own style color.
 * Dynamic (changes live). Falls back to a neutral marker on a cold cache.
 */
public final class PermissionLineProvider implements LineProvider {

    private final PermissionReadPort port;

    public PermissionLineProvider(PermissionReadPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    @Override
    public Component resolve(PlayerContext ctx) {
        String rank = port.currentRankName(ctx.player()).orElse("—");
        return Component.text(rank, NamedTextColor.WHITE);
    }

    @Override
    public boolean dynamic() {
        return true;
    }
}
