package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves click routing (MENU_DESIGN): a click goes to exactly the item at that slot and the handler bound
 * to that click action; an empty slot, a display item, or an unbound action triggers nothing; and a
 * framework menu always reports the click as cancelled (no item theft) regardless.
 */
class MenuRoutingTest {

    private final UUID player = UUID.randomUUID();

    private ClickContext click(Menu menu, ClickAction action, int slot) {
        return new ClickContext(player, action, slot, new RecordingMenuView(player, menu));
    }

    @Test
    void clickRoutesToTheHandlerAtThatSlot() {
        Menu menu = new Menu(54, MenuText.name("t"));
        int[] hits = {0};
        menu.setItem(20, MenuItem.button(IconSpec.of(Icon.INFO, MenuText.name("a")), ctx -> hits[0]++));
        menu.setItem(24, MenuItem.button(IconSpec.of(Icon.INFO, MenuText.name("b")), ctx -> hits[0] += 100));

        menu.route(click(menu, ClickAction.LEFT, 20));
        assertEquals(1, hits[0], "only slot 20's handler ran");
    }

    @Test
    void clicksAreAlwaysCancelledEvenOnEmptyOrDisplaySlots() {
        Menu menu = new Menu(54, MenuText.name("t"));
        menu.setItem(13, MenuItem.display(IconSpec.filler()));

        assertTrue(menu.route(click(menu, ClickAction.LEFT, 13)), "display slot click still cancelled");
        assertTrue(menu.route(click(menu, ClickAction.LEFT, 40)), "empty slot click still cancelled");
    }

    @Test
    void unboundActionDoesNothing() {
        Menu menu = new Menu(54, MenuText.name("t"));
        int[] hits = {0};
        menu.setItem(22, MenuItem.button(IconSpec.of(Icon.INFO, MenuText.name("a")),
                ClickAction.LEFT, ctx -> hits[0]++));

        menu.route(click(menu, ClickAction.RIGHT, 22)); // only LEFT is bound
        assertEquals(0, hits[0]);
        menu.route(click(menu, ClickAction.LEFT, 22));
        assertEquals(1, hits[0]);
    }

    @Test
    void perActionHandlersAreIndependent() {
        Menu menu = new Menu(54, MenuText.name("t"));
        int[] left = {0};
        int[] right = {0};
        MenuItem item = MenuItem.button(IconSpec.of(Icon.INFO, MenuText.name("a")), ClickAction.LEFT, ctx -> left[0]++)
                .on(ClickAction.RIGHT, ctx -> right[0]++);
        menu.setItem(31, item);

        menu.route(click(menu, ClickAction.RIGHT, 31));
        menu.route(click(menu, ClickAction.RIGHT, 31));
        menu.route(click(menu, ClickAction.LEFT, 31));

        assertEquals(1, left[0]);
        assertEquals(2, right[0]);
    }
}
