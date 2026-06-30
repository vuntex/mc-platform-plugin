package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.menu.LiveBinding;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * End-to-end (Bukkit-free) proof of demo 1 — the LIVE balance menu: it is marked LIVE on the player's
 * topic, shows the current value, and when the balance changes while open, the live binding re-renders
 * ONLY the value slot with the new amount.
 */
class BalanceMenuTest {

    private static String valueName(Menu menu) {
        MenuItem item = menu.getItem(BalanceMenu.VALUE_SLOT);
        return item.icon().name().text();
    }

    @Test
    void menuIsLiveOnThePlayersTopicAndShowsTheBalance() {
        UUID player = UUID.randomUUID();
        Menu menu = BalanceMenu.build(player, "Steve", "COINS", () -> Optional.of(500L));

        assertTrue(menu.isLive(), "balance menu is LIVE");
        assertSame(player, menu.live().orElseThrow().topic(), "observes this player's topic");
        assertTrue(valueName(menu).contains("500 Coins"), "shows the current balance");
    }

    @Test
    void coldCacheOpensInALoadingState() {
        UUID player = UUID.randomUUID();
        Menu menu = BalanceMenu.build(player, "Steve", "COINS", Optional::empty);
        assertTrue(valueName(menu).contains("Lade"), "shows a loading placeholder until data arrives");
    }

    @Test
    void aLiveBalanceChangeReRendersOnlyTheValueSlot() {
        UUID player = UUID.randomUUID();
        AtomicReference<Long> balance = new AtomicReference<>(500L);
        Menu menu = BalanceMenu.build(player, "Steve", "COINS", () -> Optional.of(balance.get()));

        // A transaction elsewhere raises the balance; the feature notifies → the binding re-renders.
        balance.set(750L);
        RecordingMenuView view = new RecordingMenuView(player, menu);
        LiveBinding binding = menu.live().orElseThrow();
        binding.onChange().accept(view);

        assertEquals(1, view.slotWrites.size(), "only one slot re-rendered");
        MenuItem updated = view.slotWrites.get(BalanceMenu.VALUE_SLOT);
        assertTrue(updated.icon().name().text().contains("750 Coins"), "value slot shows the new balance");
    }
}
