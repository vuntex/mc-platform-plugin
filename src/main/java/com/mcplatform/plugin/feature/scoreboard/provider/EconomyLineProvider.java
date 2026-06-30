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
 *
 * <p>The number rendering is centralized in {@link #coinComponent(long)} so the coin count-up animation
 * ({@code CoinLineAnimator}) renders intermediate values exactly like the settled one.
 */
public final class EconomyLineProvider implements LineProvider {

    /** Shown until the (lazy) balance is loaded. */
    public static final Component PLACEHOLDER = Component.text("…", NamedTextColor.YELLOW);

    private final EconomyReadPort port;

    public EconomyLineProvider(EconomyReadPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    /** The canonical rendering of a coin amount — used for the settled value AND each animation step. */
    public static Component coinComponent(long coins) {
        return Component.text(coins, NamedTextColor.YELLOW);
    }

    @Override
    public Component resolve(PlayerContext ctx) {
        OptionalLong coins = port.current(ctx.player());
        return coins.isPresent() ? coinComponent(coins.getAsLong()) : PLACEHOLDER;
    }

    @Override
    public boolean dynamic() {
        return true;
    }
}
