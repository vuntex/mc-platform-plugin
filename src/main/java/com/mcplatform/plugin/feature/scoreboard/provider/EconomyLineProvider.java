package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;
import com.mcplatform.plugin.platform.text.ChatDesign;

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
 * <p>The number rendering is centralized here: {@link #coinComponent(long)} is the settled value (white),
 * while {@link #coinComponentAnimating(long)} is the green count-up step used by {@code CoinLineAnimator}.
 */
public final class EconomyLineProvider implements LineProvider {

    /** Shown until the (lazy) balance is loaded. */
    public static final Component PLACEHOLDER = Component.text("…", NamedTextColor.WHITE);

    private final EconomyReadPort port;

    public EconomyLineProvider(EconomyReadPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    /** The canonical settled rendering of a coin amount — neutral white, German thousand dots (50000 → 50.000). */
    public static Component coinComponent(long coins) {
        return Component.text(ChatDesign.number(coins), NamedTextColor.WHITE);
    }

    /** Rendering for an in-progress count-up step — green, to highlight the gain effect; same number format. */
    public static Component coinComponentAnimating(long coins) {
        return Component.text(ChatDesign.number(coins), NamedTextColor.GREEN);
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
