package com.mcplatform.plugin.platform.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Bukkit-free {@link MenuView} test double: it records slot writes, refreshes, navigations, feedback
 * and messages, and applies {@code setSlot}/{@code open} to an in-memory model so tests can drive
 * handlers and live-update callbacks exactly as the server would, without a running server.
 */
public final class RecordingMenuView implements MenuView {

    private final UUID playerId;
    private Menu menu;

    public final Map<Integer, MenuItem> slotWrites = new HashMap<>();
    public final List<Feedback> feedback = new ArrayList<>();
    public final List<MenuMessage> messages = new ArrayList<>();
    public final List<Menu> opened = new ArrayList<>();
    public int refreshes;
    public boolean closed;

    public RecordingMenuView(UUID playerId, Menu menu) {
        this.playerId = playerId;
        this.menu = menu;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public Menu menu() {
        return menu;
    }

    @Override
    public void setSlot(int slot, MenuItem item) {
        slotWrites.put(slot, item);
        menu.setItem(slot, item);
    }

    @Override
    public void refresh() {
        refreshes++;
    }

    @Override
    public void open(Menu target) {
        opened.add(target);
        this.menu = target; // navigate the model so subsequent clicks route against the new menu
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void feedback(Feedback kind) {
        feedback.add(kind);
    }

    @Override
    public void send(MenuMessage message) {
        messages.add(message);
    }
}
