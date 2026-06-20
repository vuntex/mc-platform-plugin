package com.mcplatform.plugin.platform.menu;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One slot's content: an {@link IconSpec} plus a handler per {@link ClickAction}. A display-only item
 * (header, value readout, filler) has no handlers and so does nothing on click. Binding behaviour
 * per-action is what enforces §4.3 — "jede belegte Klick-Art hat ihre eigene Lore-Hinweiszeile" — and
 * the two-step confirm (§2.5), where the only bound action is {@link ClickAction#DOUBLE_CLICK}.
 *
 * <p>Immutable and Bukkit-free; build with {@link #display(IconSpec)} / {@link #button}.
 */
public final class MenuItem {

    private final IconSpec icon;
    private final Map<ClickAction, ClickHandler> handlers;

    private MenuItem(IconSpec icon, Map<ClickAction, ClickHandler> handlers) {
        this.icon = icon;
        this.handlers = handlers;
    }

    /** A non-interactive item (no click does anything but produce inert feedback). */
    public static MenuItem display(IconSpec icon) {
        return new MenuItem(Objects.requireNonNull(icon, "icon"), new EnumMap<>(ClickAction.class));
    }

    /** An item that reacts to a single {@code action}. */
    public static MenuItem button(IconSpec icon, ClickAction action, ClickHandler handler) {
        return display(icon).on(action, handler);
    }

    /** Convenience: a left-click button. */
    public static MenuItem button(IconSpec icon, ClickHandler handler) {
        return button(icon, ClickAction.LEFT, handler);
    }

    /** Return a copy of this item that also reacts to {@code action}. */
    public MenuItem on(ClickAction action, ClickHandler handler) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(handler, "handler");
        Map<ClickAction, ClickHandler> next = new EnumMap<>(handlers);
        next.put(action, handler);
        return new MenuItem(icon, next);
    }

    public IconSpec icon() {
        return icon;
    }

    /** The handler bound to {@code action}, or {@code null} if this item ignores that click kind. */
    public ClickHandler handlerFor(ClickAction action) {
        return handlers.get(action);
    }

    /** True if any click action is bound (i.e. the item is interactive). */
    public boolean isInteractive() {
        return !handlers.isEmpty();
    }
}
