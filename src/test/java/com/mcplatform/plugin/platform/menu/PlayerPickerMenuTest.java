package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Proves the shared player picker: it paginates the candidate list, clicking an entry picks exactly that
 * player, and the interactive header fires the refresh action.
 */
class PlayerPickerMenuTest {

    private List<PlayerPickerMenu.Entry> entries(int n) {
        List<PlayerPickerMenu.Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new PlayerPickerMenu.Entry(UUID.randomUUID(), "Player" + i));
        }
        return out;
    }

    @Test
    void paginatesAcrossPages() {
        PlayerPickerMenu picker = new PlayerPickerMenu(MenuText.name("Pick"), entries(30), (c, e) -> {
        }, null);
        Menu menu = picker.menu();
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(menu.getItem(slot), "page 0 slot " + slot);
        }
        assertNotNull(menu.getItem(MenuLayout.PAGE_NEXT));
        assertNull(menu.getItem(MenuLayout.PAGE_PREV));
    }

    @Test
    void clickingAnEntryPicksThatPlayer() {
        List<PlayerPickerMenu.Entry> list = entries(3);
        AtomicReference<PlayerPickerMenu.Entry> picked = new AtomicReference<>();
        PlayerPickerMenu picker = new PlayerPickerMenu(MenuText.name("Pick"), list,
                (c, e) -> picked.set(e), null);
        Menu menu = picker.menu();
        RecordingMenuView view = new RecordingMenuView(UUID.randomUUID(), menu);

        menu.route(new ClickContext(view.playerId(), ClickAction.LEFT, Pagination.CONTENT_SLOTS[1], view));

        assertSame(list.get(1), picked.get(), "second head picks the second player");
    }

    @Test
    void interactiveHeaderRefreshes() {
        int[] refreshed = {0};
        PlayerPickerMenu picker = new PlayerPickerMenu(MenuText.name("Pick"), entries(1),
                (c, e) -> {
                }, ctx -> refreshed[0]++);
        Menu menu = picker.menu();
        RecordingMenuView view = new RecordingMenuView(UUID.randomUUID(), menu);

        assertTrue(menu.getItem(MenuLayout.HEADER).isInteractive(), "header is the refresh control");
        menu.route(new ClickContext(view.playerId(), ClickAction.LEFT, MenuLayout.HEADER, view));
        assertEquals(1, refreshed[0]);
    }
}
