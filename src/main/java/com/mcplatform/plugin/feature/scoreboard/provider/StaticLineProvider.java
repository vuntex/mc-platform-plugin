package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** A fixed line (header, footer, separator). Never changes → not dynamic. */
public final class StaticLineProvider implements LineProvider {

    private final Component value;

    public StaticLineProvider(Component value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public Component resolve(PlayerContext ctx) {
        return value;
    }
}
