package com.mcplatform.plugin.platform.menu;

/**
 * The framework's own click vocabulary (MENU_DESIGN §4.3). The Bukkit listener maps each raw
 * {@code ClickType} to one of these once, at the event boundary, so the rest of the framework — and
 * every test — speaks this Bukkit-free enum. A {@code MenuItem} binds a handler per action, which is
 * what makes "every click kind has its own behaviour and its own lore line" enforceable.
 */
public enum ClickAction {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    /** Double left-click — used for the two-step confirm of irreversible actions (§2.5). */
    DOUBLE_CLICK,
    /** Any click kind we do not route (number keys, etc.); never triggers a handler. */
    OTHER
}
