package com.mcplatform.plugin.feature.scoreboard.model;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Renderer output for one line: the stable {@link LineId} plus its resolved Adventure component. */
public record RenderedLine(LineId id, Component component) {

    public RenderedLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(component, "component");
    }
}
