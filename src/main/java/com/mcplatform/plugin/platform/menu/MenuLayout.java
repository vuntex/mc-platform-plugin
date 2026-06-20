package com.mcplatform.plugin.platform.menu;

/**
 * The fixed slot conventions of MENU_DESIGN §2.3–§2.5, as pure data. Everything here is reserved-slot
 * arithmetic — no Bukkit — so the conventions ("Header auf 4, Zurück auf 48, Schließen auf 49, Footer
 * bleibt Rahmen") are encoded once and unit-tested, instead of every feature counting slots by hand.
 */
public final class MenuLayout {

    private MenuLayout() {
    }

    // ── 54er standard menu (6 rows) ──────────────────────────────────────────────────────────────
    /** Header / context item — always slot 4 (§2.3). */
    public static final int HEADER = 4;
    /** Back to parent — slot 48 (only when a parent exists). */
    public static final int BACK = 48;
    /** Close — slot 49 (always). */
    public static final int CLOSE = 49;
    /** Page-back arrow — slot 45 (only when a previous page exists). */
    public static final int PAGE_PREV = 45;
    /** Page-forward arrow — slot 53 (only when a next page exists). */
    public static final int PAGE_NEXT = 53;
    /** Footer zone reserved for future global buttons — features keep it as border (§2.3). */
    public static final int[] FOOTER_ZONE = {46, 47, 50, 51, 52};

    // ── 27er confirm dialog (3 rows, §2.5) ───────────────────────────────────────────────────────
    /** Object of the confirmation — slot 4. */
    public static final int DIALOG_HEADER = 4;
    /** Confirm (green) — slot 11. */
    public static final int DIALOG_CONFIRM = 11;
    /** Cancel (red) — slot 15. */
    public static final int DIALOG_CANCEL = 15;
    /** Back to parent — slot 18. */
    public static final int DIALOG_BACK = 18;
    /** Close — slot 22. */
    public static final int DIALOG_CLOSE = 22;

    private static final int ROW = 9;

    /** Top row (always border on a 6-row menu): slots 0–8. */
    public static int[] topRow() {
        return range(0, 8);
    }

    /**
     * The full surrounding border of a {@code size}-slot menu: the whole top and bottom rows plus the
     * left/right edge of every interior row. Used by list menus (§2.2 "Listen: voller Seitenrahmen").
     */
    public static boolean isBorder(int size, int slot) {
        int rows = size / ROW;
        int col = slot % ROW;
        int row = slot / ROW;
        boolean topOrBottom = row == 0 || row == rows - 1;
        boolean leftOrRight = col == 0 || col == ROW - 1;
        return topOrBottom || leftOrRight;
    }

    /** Slots forming the full border of a {@code size}-slot menu. */
    public static int[] borderSlots(int size) {
        int count = 0;
        for (int s = 0; s < size; s++) {
            if (isBorder(size, s)) {
                count++;
            }
        }
        int[] out = new int[count];
        int i = 0;
        for (int s = 0; s < size; s++) {
            if (isBorder(size, s)) {
                out[i++] = s;
            }
        }
        return out;
    }

    /**
     * Preferred slots for a small number of centred action items in a non-paginated 54er menu (§2.4):
     * centre 22 first, then mirrored 20/24, then 21/23, working outward — never pinned to the top edge.
     */
    public static final int[] CENTERED_ACTION_SLOTS = {22, 20, 24, 21, 23, 31, 29, 33, 30, 32};

    private static int[] range(int from, int to) {
        int[] out = new int[to - from + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }
}
