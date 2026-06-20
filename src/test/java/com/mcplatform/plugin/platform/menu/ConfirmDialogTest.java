package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves the confirm-dialog blueprint (MENU_DESIGN §2.5): the 27er layout (object on 4, confirm on 11,
 * cancel on 15, back on 18, close on 22) and the two-step safety — a critical action fires only on a
 * double-click, a standard one on a single click.
 */
class ConfirmDialogTest {

    private final UUID player = UUID.randomUUID();

    private ClickContext click(Menu menu, ClickAction action, int slot) {
        return new ClickContext(player, action, slot, new RecordingMenuView(player, menu));
    }

    private static IconSpec object() {
        return IconSpec.of(Icon.DANGER, MenuText.name("Ban"));
    }

    @Test
    void layoutPlacesEveryFixedSlot() {
        Menu menu = ConfirmDialog.of(MenuText.name("Sicher?"), object())
                .onBack(ctx -> {
                })
                .build();

        assertEquals(27, menu.size());
        assertNotNull(menu.getItem(MenuLayout.DIALOG_HEADER));
        assertTrue(menu.getItem(MenuLayout.DIALOG_CONFIRM).isInteractive());
        assertTrue(menu.getItem(MenuLayout.DIALOG_CANCEL).isInteractive());
        assertTrue(menu.getItem(MenuLayout.DIALOG_BACK).isInteractive());
        assertTrue(menu.getItem(MenuLayout.DIALOG_CLOSE).isInteractive());
    }

    @Test
    void standardConfirmFiresOnASingleClick() {
        int[] confirmed = {0};
        Menu menu = ConfirmDialog.of(MenuText.name("Sicher?"), object())
                .onConfirm(ctx -> confirmed[0]++)
                .build();

        menu.route(click(menu, ClickAction.LEFT, MenuLayout.DIALOG_CONFIRM));
        assertEquals(1, confirmed[0]);
    }

    @Test
    void criticalConfirmRequiresADoubleClick() {
        int[] confirmed = {0};
        Menu menu = ConfirmDialog.of(MenuText.name("Löschen?"), object())
                .critical()
                .onConfirm(ctx -> confirmed[0]++)
                .build();

        menu.route(click(menu, ClickAction.LEFT, MenuLayout.DIALOG_CONFIRM));
        assertEquals(0, confirmed[0], "single click must not trigger an irreversible action");

        menu.route(click(menu, ClickAction.DOUBLE_CLICK, MenuLayout.DIALOG_CONFIRM));
        assertEquals(1, confirmed[0], "double click confirms");
    }

    @Test
    void cancelRunsTheCancelHandler() {
        int[] cancelled = {0};
        Menu menu = ConfirmDialog.of(MenuText.name("Sicher?"), object())
                .onCancel(ctx -> cancelled[0]++)
                .build();

        menu.route(click(menu, ClickAction.LEFT, MenuLayout.DIALOG_CANCEL));
        assertEquals(1, cancelled[0]);
    }

    @Test
    void backIsAbsentWhenNoParentIsGiven() {
        Menu menu = ConfirmDialog.of(MenuText.name("Sicher?"), object()).build();
        // Slot 18 stays part of the framed bottom row (a non-interactive filler), not a back button.
        assertFalse(menu.getItem(MenuLayout.DIALOG_BACK).isInteractive());
    }
}
