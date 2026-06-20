package com.mcplatform.plugin.platform.menu;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything a {@link ClickHandler} is handed for one click: who clicked, which {@link ClickAction}, the
 * slot, and the {@link MenuView} to act through. Pure data — no Bukkit {@code Player}/{@code ClickType}
 * leaks in — so a routing test can synthesise a context and assert the right handler ran.
 *
 * @param playerId clicking player's id
 * @param action   the framework click action (already mapped from the raw Bukkit click)
 * @param slot     clicked slot
 * @param view     handle onto the open menu
 */
public record ClickContext(UUID playerId, ClickAction action, int slot, MenuView view) {

    public ClickContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(view, "view");
    }
}
