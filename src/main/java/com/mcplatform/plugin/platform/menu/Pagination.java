package com.mcplatform.plugin.platform.menu;

/**
 * The 7×4 pagination grid of MENU_DESIGN §4.4: 28 entries per page in the interior of a 54er menu
 * (slots 10–16, 19–25, 28–34, 37–43), with the frame all around. Pure arithmetic — page counting and
 * arrow visibility — so "50 Einträge → 2 Seiten, korrekte Vor/Zurück-Sichtbarkeit" is unit-tested
 * without a server.
 *
 * <p>Page indices are zero-based here; features that show "Seite n/m" add one for display.
 */
public final class Pagination {

    /** Content slots of one page, in reading order (7 columns × 4 rows = 28). */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Entries per page. */
    public static final int PER_PAGE = CONTENT_SLOTS.length; // 28

    private final int totalEntries;

    public Pagination(int totalEntries) {
        if (totalEntries < 0) {
            throw new IllegalArgumentException("totalEntries < 0");
        }
        this.totalEntries = totalEntries;
    }

    /** Number of pages — always at least 1, so an empty list still renders a framed page (§4.4). */
    public int pageCount() {
        if (totalEntries == 0) {
            return 1;
        }
        return (totalEntries + PER_PAGE - 1) / PER_PAGE;
    }

    /** The last valid zero-based page index. */
    public int lastPage() {
        return pageCount() - 1;
    }

    /** Clamp an arbitrary requested page into {@code [0, lastPage]}. */
    public int clamp(int page) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, lastPage());
    }

    /** Index into the entry list where {@code page} starts. */
    public int firstIndexOf(int page) {
        return clamp(page) * PER_PAGE;
    }

    /** Number of entries actually shown on {@code page} (the last page may be partial). */
    public int countOn(int page) {
        int start = firstIndexOf(page);
        return Math.max(0, Math.min(PER_PAGE, totalEntries - start));
    }

    /** Show the "page back" arrow on slot 45 only if a previous page exists (§4.4 — no dead arrows). */
    public boolean hasPrev(int page) {
        return clamp(page) > 0;
    }

    /** Show the "page forward" arrow on slot 53 only if a following page exists. */
    public boolean hasNext(int page) {
        return clamp(page) < lastPage();
    }

    public boolean isEmpty() {
        return totalEntries == 0;
    }
}
