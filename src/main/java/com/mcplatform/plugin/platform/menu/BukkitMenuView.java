package com.mcplatform.plugin.platform.menu;

import com.mcplatform.plugin.platform.PlatformScheduler;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The Bukkit-backed {@link MenuView} handed to handlers and live-update callbacks. Every operation runs
 * on the main thread (handlers and the manager already call in on main) and no-ops once the player has
 * closed or navigated away — checked via {@link MenuHolder#isOpenFor} — so a late async result can never
 * write into a stale inventory. This is the only place {@code MenuView} touches Bukkit.
 */
final class BukkitMenuView implements MenuView {

    private final Player player;
    private final MenuHolder holder;
    private final MenuRenderer renderer;
    private final MenuManager manager;

    BukkitMenuView(Player player, MenuHolder holder, MenuRenderer renderer, MenuManager manager) {
        this.player = player;
        this.holder = holder;
        this.renderer = renderer;
        this.manager = manager;
    }

    @Override
    public UUID playerId() {
        return player.getUniqueId();
    }

    @Override
    public Menu menu() {
        return holder.menu();
    }

    @Override
    public void setSlot(int slot, MenuItem item) {
        if (!holder.isOpenFor(player)) {
            return;
        }
        holder.menu().setItem(slot, item);
        renderer.renderSlot(holder.getInventory(), slot, item);
    }

    @Override
    public void refresh() {
        if (!holder.isOpenFor(player)) {
            return;
        }
        renderer.renderAll(holder.getInventory(), holder.menu());
    }

    @Override
    public void open(Menu target) {
        manager.open(player, target);
    }

    @Override
    public void close() {
        player.closeInventory();
    }

    @Override
    public void feedback(Feedback feedback) {
        MenuStyle.play(player, feedback);
    }

    @Override
    public void send(MenuMessage message) {
        MenuStyle.send(player, message);
    }
}
