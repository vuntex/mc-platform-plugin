package com.mcplatform.plugin.platform.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player bookkeeping of the LIVE subscription behind the menu a player currently has open. The
 * central listener calls {@link #track} on open and {@link #release} on close; tracking a new menu for a
 * player who already had one closes the previous handle first (e.g. navigating menu→menu fires open
 * before the old close). This is the guarantee that observers never accumulate — and it is Bukkit-free,
 * so the "no leak at 200 players" claim is unit-tested directly.
 *
 * <p>Only the {@link LiveHandle} is held here, never the inventory, so the class stays pure.
 */
public final class OpenMenuTracker {

    private final Map<UUID, LiveHandle> handles = new ConcurrentHashMap<>();

    /** Record {@code handle} as player's current subscription, closing any previous one first. */
    public void track(UUID player, LiveHandle handle) {
        LiveHandle previous = handles.put(player, handle == null ? LiveHandle.NONE : handle);
        if (previous != null) {
            previous.close();
        }
    }

    /** Close and forget the player's current subscription (called on menu close / player quit). */
    public void release(UUID player) {
        LiveHandle handle = handles.remove(player);
        if (handle != null) {
            handle.close();
        }
    }

    /** Players with a menu currently tracked. */
    public int activeCount() {
        return handles.size();
    }
}
