package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Coins VALUE line — reads the current value from the existing economy cache via {@link EconomyReadPort}
 * and renders just the number (the "Münzen" label is a separate static line). Dynamic (changes live).
 * Renders a neutral placeholder until the value is known (lazy cache cold at join; the lifecycle triggers
 * a load and re-renders on completion).
 */
public final class EconomyLineProvider implements LineProvider {

    private final EconomyReadPort port;

    public EconomyLineProvider(EconomyReadPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    @Override
    public Component resolve(PlayerContext ctx) {
        OptionalLong coins = port.current(ctx.player());
        String value = coins.isPresent() ? Long.toString(coins.getAsLong()) : "…";
        return Component.text(value, NamedTextColor.YELLOW);
    }

    @Override
    public boolean dynamic() {
        return true;
    }
}
