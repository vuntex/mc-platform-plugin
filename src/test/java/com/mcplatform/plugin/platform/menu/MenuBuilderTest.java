package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves the {@link MenuBuilder} conventions: border filling for list vs panel menus, header/close on
 * their fixed slots, and the 7×4 pagination render (content placement, arrow visibility, empty marker,
 * and that turning a page re-lays-out + refreshes through the live view).
 */
class MenuBuilderTest {

    private static IconSpec dummy() {
        return IconSpec.of(Icon.INFO, MenuText.name("x"));
    }

    @Test
    void listMenuFillsTheFullBorderAndHeaderAndClose() {
        Menu menu = MenuBuilder.list(MenuText.name("List"))
                .header(dummy())
                .close()
                .build();

        // Every border slot is a filler (or the header/close on their reserved slots).
        for (int slot : MenuLayout.borderSlots(54)) {
            assertNotNull(menu.getItem(slot), "border slot " + slot + " must be filled");
        }
        // Interior is open.
        assertNull(menu.getItem(22));
        // Header slot 4 and close slot 49 carry their items.
        assertNotNull(menu.getItem(MenuLayout.HEADER));
        assertTrue(menu.getItem(MenuLayout.CLOSE).isInteractive());
    }

    @Test
    void panelMenuFramesOnlyTopAndBottomRows() {
        Menu menu = MenuBuilder.panel(MenuText.name("Panel")).build();
        for (int slot = 0; slot <= 8; slot++) {
            assertNotNull(menu.getItem(slot), "top row filled");
        }
        for (int slot = 45; slot <= 53; slot++) {
            assertNotNull(menu.getItem(slot), "bottom row filled");
        }
        // Interior left/right edges are NOT framed in a panel (unlike a list).
        assertNull(menu.getItem(9));
        assertNull(menu.getItem(17));
    }

    @Test
    void renderPagePlacesAFullFirstPageWithOnlyAForwardArrow() {
        Menu menu = MenuBuilder.list(MenuText.name("List")).build();
        List<MenuItem> entries = entries(50);

        Pagination p = MenuBuilder.renderPage(menu, entries, 0, (ctx, page) -> {
        });

        assertEquals(2, p.pageCount());
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(menu.getItem(slot), "page 0 content slot " + slot + " filled");
        }
        assertNull(menu.getItem(MenuLayout.PAGE_PREV), "no back arrow on page 0");
        assertNotNull(menu.getItem(MenuLayout.PAGE_NEXT), "forward arrow on page 0");
    }

    @Test
    void renderPageLastPageIsPartialWithOnlyABackArrow() {
        Menu menu = MenuBuilder.list(MenuText.name("List")).build();
        List<MenuItem> entries = entries(50);

        MenuBuilder.renderPage(menu, entries, 1, (ctx, page) -> {
        });

        // 22 items on page 1; the remaining 6 content slots are cleared.
        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (menu.getItem(slot) != null) {
                filled++;
            }
        }
        assertEquals(22, filled);
        assertNotNull(menu.getItem(MenuLayout.PAGE_PREV), "back arrow on last page");
        assertNull(menu.getItem(MenuLayout.PAGE_NEXT), "no forward arrow on last page");
    }

    @Test
    void emptyListShowsTheCentredMarkerAndNoArrows() {
        Menu menu = MenuBuilder.list(MenuText.name("List")).build();
        MenuBuilder.renderPage(menu, List.of(), 0, (ctx, page) -> {
        });
        assertNotNull(menu.getItem(MenuBuilder.EMPTY_MARKER_SLOT));
        assertNull(menu.getItem(MenuLayout.PAGE_PREV));
        assertNull(menu.getItem(MenuLayout.PAGE_NEXT));
    }

    @Test
    void clickingTheForwardArrowTurnsThePageThroughTheLiveView() {
        Menu menu = MenuBuilder.list(MenuText.name("List")).build();
        List<MenuItem> entries = entries(50);
        int[] turnedTo = {-1};

        MenuBuilder.renderPage(menu, entries, 0, (ctx, page) -> turnedTo[0] = page);

        RecordingMenuView view = new RecordingMenuView(UUID.randomUUID(), menu);
        boolean cancelled = menu.route(new ClickContext(view.playerId(), ClickAction.LEFT,
                MenuLayout.PAGE_NEXT, view));

        assertTrue(cancelled, "clicks are always cancelled");
        assertEquals(1, turnedTo[0], "forward arrow targets page 1");
        assertSame(Feedback.NAVIGATE, view.feedback.get(0));
    }

    private static List<MenuItem> entries(int n) {
        List<MenuItem> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(MenuItem.display(IconSpec.of(Icon.INFO, MenuText.name("entry " + i))));
        }
        return out;
    }
}
