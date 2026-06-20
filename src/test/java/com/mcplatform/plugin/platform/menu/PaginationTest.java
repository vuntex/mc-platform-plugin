package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Proves the 7×4 pagination arithmetic (MENU_DESIGN §4.4): 28 per page, page counting, partial last
 * page, and arrow visibility — "50 Einträge → 2 Seiten, korrekte Vor/Zurück-Sichtbarkeit".
 */
class PaginationTest {

    @Test
    void gridIs28Slots() {
        assertEquals(28, Pagination.PER_PAGE);
        assertEquals(28, Pagination.CONTENT_SLOTS.length);
        // First and last content slots of the centred grid.
        assertEquals(10, Pagination.CONTENT_SLOTS[0]);
        assertEquals(43, Pagination.CONTENT_SLOTS[27]);
    }

    @Test
    void fiftyEntriesAreTwoPages() {
        Pagination p = new Pagination(50);
        assertEquals(2, p.pageCount());
        assertEquals(1, p.lastPage());
        assertEquals(28, p.countOn(0));
        assertEquals(22, p.countOn(1)); // 50 - 28
    }

    @Test
    void arrowVisibilityAcrossPages() {
        Pagination p = new Pagination(50);
        assertFalse(p.hasPrev(0), "first page has no back arrow");
        assertTrue(p.hasNext(0), "first page has a forward arrow");
        assertTrue(p.hasPrev(1), "last page has a back arrow");
        assertFalse(p.hasNext(1), "last page has no forward arrow");
    }

    @Test
    void exactlyOneFullPageHasNoArrows() {
        Pagination p = new Pagination(28);
        assertEquals(1, p.pageCount());
        assertFalse(p.hasPrev(0));
        assertFalse(p.hasNext(0));
    }

    @Test
    void twentyNineEntriesSpillToASecondPage() {
        Pagination p = new Pagination(29);
        assertEquals(2, p.pageCount());
        assertEquals(1, p.countOn(1));
        assertTrue(p.hasNext(0));
    }

    @Test
    void emptyListIsStillOnePageWithNoArrows() {
        Pagination p = new Pagination(0);
        assertTrue(p.isEmpty());
        assertEquals(1, p.pageCount());
        assertFalse(p.hasPrev(0));
        assertFalse(p.hasNext(0));
        assertEquals(0, p.countOn(0));
    }

    @Test
    void requestedPageIsClampedIntoRange() {
        Pagination p = new Pagination(50);
        assertEquals(0, p.clamp(-5));
        assertEquals(1, p.clamp(99));
        assertEquals(28, p.firstIndexOf(1));
    }
}
