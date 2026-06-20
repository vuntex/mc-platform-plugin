package com.mcplatform.plugin.platform.menu;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The {@link InventoryHolder} that marks an inventory as one of ours and ties it to its {@link Menu},
 * viewer and {@link MenuView}. The central listener identifies framework menus purely by
 * {@code inventory.getHolder() instanceof MenuHolder} — robust against title spoofing and never confused
 * with vanilla containers.
 */
final class MenuHolder implements InventoryHolder {

    private final UUID playerId;
    private final Menu menu;
    private Inventory inventory;
    private BukkitMenuView view;

    MenuHolder(UUID playerId, Menu menu) {
        this.playerId = playerId;
        this.menu = menu;
    }

    UUID playerId() {
        return playerId;
    }

    Menu menu() {
        return menu;
    }

    BukkitMenuView view() {
        return view;
    }

    void bind(Inventory inventory, BukkitMenuView view) {
        this.inventory = inventory;
        this.view = view;
    }

    /** True if {@code who} still has exactly this inventory open on top (stale-write guard). */
    boolean isOpenFor(HumanEntity who) {
        return inventory != null && who.getOpenInventory().getTopInventory().equals(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
