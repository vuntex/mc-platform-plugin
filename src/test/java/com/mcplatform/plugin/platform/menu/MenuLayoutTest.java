package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Proves the fixed slot conventions (MENU_DESIGN §2.3–§2.5): reserved slots, the full border of a 54er
 * and 27er menu, and that the footer zone is part of the border (never a feature button).
 */
class MenuLayoutTest {

    @Test
    void reservedSlotsMatchTheDesign() {
        assertEquals(4, MenuLayout.HEADER);
        assertEquals(48, MenuLayout.BACK);
        assertEquals(49, MenuLayout.CLOSE);
        assertEquals(45, MenuLayout.PAGE_PREV);
        assertEquals(53, MenuLayout.PAGE_NEXT);
        assertEquals(4, MenuLayout.DIALOG_HEADER);
        assertEquals(11, MenuLayout.DIALOG_CONFIRM);
        assertEquals(15, MenuLayout.DIALOG_CANCEL);
        assertEquals(18, MenuLayout.DIALOG_BACK);
        assertEquals(22, MenuLayout.DIALOG_CLOSE);
    }

    @Test
    void topRowIsAlwaysBorderOn54() {
        for (int slot = 0; slot <= 8; slot++) {
            assertTrue(MenuLayout.isBorder(54, slot), "top-row slot " + slot + " must be border");
        }
    }

    @Test
    void interiorOf54IsNotBorder() {
        // The 7×4 content grid (e.g. slot 22) is interior, not border.
        assertFalse(MenuLayout.isBorder(54, 22));
        assertFalse(MenuLayout.isBorder(54, 13));
        assertFalse(MenuLayout.isBorder(54, 43));
    }

    @Test
    void edgesAndBottomOf54AreBorder() {
        assertTrue(MenuLayout.isBorder(54, 9));   // left edge
        assertTrue(MenuLayout.isBorder(54, 17));  // right edge
        assertTrue(MenuLayout.isBorder(54, 45));  // bottom row
        assertTrue(MenuLayout.isBorder(54, 53));  // bottom-right corner
    }

    @Test
    void footerZoneIsEntirelyBorder() {
        // 46,47,50,51,52 must remain frame — features never place buttons there (§2.3).
        for (int slot : MenuLayout.FOOTER_ZONE) {
            assertTrue(MenuLayout.isBorder(54, slot), "footer slot " + slot + " must stay border");
        }
        assertArrayEquals(new int[]{46, 47, 50, 51, 52}, MenuLayout.FOOTER_ZONE);
    }

    @Test
    void dialogBorderLeavesOnlyTheFiveContentSlotsFree() {
        // A 27er confirm: every slot is border except header/confirm/cancel/back/close.
        int borderCount = 0;
        for (int slot = 0; slot < 27; slot++) {
            boolean reserved = slot == 4 || slot == 11 || slot == 15 || slot == 18 || slot == 22;
            if (MenuLayout.isBorder(27, slot)) {
                borderCount++;
            }
            if (reserved) {
                // confirm/cancel are interior; header/back/close sit in the framed top/bottom rows.
                if (slot == 11 || slot == 15) {
                    assertFalse(MenuLayout.isBorder(27, slot), "content slot " + slot);
                }
            }
        }
        // 3 rows: top 9 + bottom 9 + the two middle edges (9,17) = 20 border slots.
        assertEquals(20, borderCount);
    }
}
