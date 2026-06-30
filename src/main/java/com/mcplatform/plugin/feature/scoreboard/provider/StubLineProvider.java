package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * A fixed placeholder for a feature not yet migrated (Stats/Liga/Season). Structurally a full line —
 * replacing it with a real provider later changes only this binding, not the renderer (AC-4).
 */
public final class StubLineProvider implements LineProvider {

    private final Component placeholder;

    public StubLineProvider(Component placeholder) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    @Override
    public Component resolve(PlayerContext ctx) {
        return placeholder;
    }
}
