package com.mcplatform.plugin.platform.menu;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;

/**
 * Turns the pure {@link Menu} model into a Bukkit {@link Inventory}: creates the sized, Adventure-titled
 * inventory and renders each {@link MenuItem}'s {@link IconSpec} to an {@code ItemStack} (name + lore via
 * {@link MenuStyle}, italic off, attributes hidden where asked, skull skin for player heads). It also
 * renders a single slot, which is what LIVE updates and page turns use to avoid rebuilding the whole
 * inventory (no flicker, no cursor reset, §6).
 */
final class MenuRenderer {

    /** Build and fill a fresh inventory for {@code holder}'s menu. */
    Inventory render(MenuHolder holder) {
        Menu menu = holder.menu();
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), MenuStyle.component(menu.title()));
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            inventory.setItem(entry.getKey(), toStack(entry.getValue()));
        }
        return inventory;
    }

    /** Render one slot from the model (used by {@code MenuView.setSlot} for LIVE/page updates). */
    void renderSlot(Inventory inventory, int slot, MenuItem item) {
        inventory.setItem(slot, item == null ? null : toStack(item));
    }

    /** Re-render every slot of {@code menu} into {@code inventory} (used after async data arrives). */
    void renderAll(Inventory inventory, Menu menu) {
        inventory.clear();
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            inventory.setItem(entry.getKey(), toStack(entry.getValue()));
        }
    }

    private ItemStack toStack(MenuItem item) {
        IconSpec icon = item.icon();
        // Escape hatch: a feature-resolved item (e.g. a role's display_icon) is cloned as-is; otherwise
        // resolve the closed Icon enum's material as before.
        ItemStack stack = icon.baseItem() != null
                ? icon.baseItem().clone()
                : new ItemStack(MenuStyle.material(icon.icon()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(icon.name() == null
                ? net.kyori.adventure.text.Component.empty()
                : MenuStyle.component(icon.name()));
        if (!icon.lore().isEmpty()) {
            meta.lore(MenuStyle.lore(icon.lore()));
        }
        if (icon.baseItem() != null || icon.icon().hideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        if (icon.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (icon.skinOwner() != null && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(icon.skinOwner()));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
