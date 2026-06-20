package com.mcplatform.plugin.platform.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Bukkit-free model of one menu: its size, title, slot→{@link MenuItem} contents and optional
 * {@link LiveBinding}. This is the single source the render layer draws from and the live-update path
 * mutates. Click routing lives here ({@link #route}) so the central listener stays a thin adapter and
 * the "click → right handler, clicks always cancelled" contract is unit-testable.
 *
 * <p>Mutable on purpose: an async fetch fills a loading menu by swapping items in, and a LIVE update or
 * a page turn rewrites only the affected slots — always followed by a render through the {@link MenuView}.
 */
public final class Menu {

    /** Standard content menu — 6 rows (MENU_DESIGN §2.1, "im Zweifel diese Größe"). */
    public static final int SIZE_CHEST = 54;
    /** Slim single-decision dialog — 3 rows (§2.1). */
    public static final int SIZE_DIALOG = 27;

    private final int size;
    private MenuText title;
    private final Map<Integer, MenuItem> items = new HashMap<>();
    private LiveBinding live;

    public Menu(int size, MenuText title) {
        if (size <= 0 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Menu size must be a positive multiple of 9 up to 54: " + size);
        }
        this.size = size;
        this.title = Objects.requireNonNull(title, "title");
    }

    public int size() {
        return size;
    }

    public MenuText title() {
        return title;
    }

    /** Titles may carry a live value (§3.2); changing it re-renders via the inventory title on refresh. */
    public void setTitle(MenuText title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /** Place (or clear, when {@code item} is {@code null}) the item at {@code slot}. */
    public void setItem(int slot, MenuItem item) {
        checkSlot(slot);
        if (item == null) {
            items.remove(slot);
        } else {
            items.put(slot, item);
        }
    }

    public MenuItem getItem(int slot) {
        return items.get(slot);
    }

    /** Immutable snapshot of the current contents — used by the render layer to draw the inventory. */
    public Map<Integer, MenuItem> items() {
        return Collections.unmodifiableMap(items);
    }

    /** Mark this menu LIVE with the given binding (STATIC menus never call this). */
    public void setLive(LiveBinding binding) {
        this.live = binding;
    }

    public Optional<LiveBinding> live() {
        return Optional.ofNullable(live);
    }

    public boolean isLive() {
        return live != null;
    }

    /**
     * Route a click to the item at {@code context.slot()}. Invokes the handler bound to the click's
     * {@link ClickAction}, if any; an unbound action or an empty/display slot does nothing. Returns
     * {@code true} always — a click inside a framework menu is <em>always</em> cancelled (no item theft),
     * independent of whether it triggered a handler.
     */
    public boolean route(ClickContext context) {
        Objects.requireNonNull(context, "context");
        MenuItem item = items.get(context.slot());
        if (item != null) {
            ClickHandler handler = item.handlerFor(context.action());
            if (handler != null) {
                handler.onClick(context);
            }
        }
        return true; // framework menus always cancel the click
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot " + slot + " outside menu of size " + size);
        }
    }
}
