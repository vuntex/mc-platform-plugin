package com.mcplatform.plugin.platform.menu;

import java.util.UUID;

/**
 * The live handle a handler (or an async data callback) uses to act on an *open* menu, without ever
 * touching Bukkit. The real implementation ({@code BukkitMenuView}) performs every inventory operation
 * on the main thread and no-ops once the player has closed or navigated away, so a late async result can
 * never write into a stale inventory.
 *
 * <p>Every method here takes only framework-pure types — that is deliberate: it lets tests pass a
 * recording fake to drive handlers and live-update callbacks with no server present.
 */
public interface MenuView {

    /** The viewing player's id (resolve the Bukkit Player from this only inside the render layer). */
    UUID playerId();

    /** The model currently shown — handlers mutate it via {@link #setSlot} / read it for navigation. */
    Menu menu();

    /** Replace one slot's item and re-render just that slot (LIVE updates touch only affected slots). */
    void setSlot(int slot, MenuItem item);

    /** Re-render every slot from the current model (used after async data fills a loading menu). */
    void refresh();

    /** Navigate to another menu in place (parent, child, confirm dialog, refreshed page). */
    void open(Menu menu);

    /** Close the menu for this player. */
    void close();

    /** Play semantic click feedback (§4.5). */
    void feedback(Feedback feedback);

    /** Deliver a transient (action bar) or lasting (chat) message (§4.5). */
    void send(MenuMessage message);
}
