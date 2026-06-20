package com.mcplatform.plugin.platform.menu;

/**
 * What a single {@link MenuItem} does for one {@link ClickAction}. Each button carries its own
 * behaviour — there is no global click {@code switch} anywhere in the framework (MENU_DESIGN principle:
 * "jeder Button kennt sein Verhalten"). Bukkit-free: a handler receives a {@link ClickContext} and acts
 * through the pure {@link MenuView}, so routing and even simple handlers are unit-testable.
 */
@FunctionalInterface
public interface ClickHandler {
    void onClick(ClickContext context);
}
