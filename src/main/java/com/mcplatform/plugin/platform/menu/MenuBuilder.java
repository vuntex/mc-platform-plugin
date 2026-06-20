package com.mcplatform.plugin.platform.menu;

import java.util.List;

/**
 * Encodes the MENU_DESIGN conventions so a feature builds a conformant menu in a few lines without ever
 * counting slots: border filling (§2.2), the fixed header/back/close/page slots (§2.3), the 7×4
 * pagination grid (§4.4), the empty-list marker, and the 27er confirm dialog (§2.5). Pure — it produces
 * a {@link Menu} model that the render layer later draws — so every convention here is unit-testable.
 *
 * <p>Standard buttons carry the design's icons and action hints; the two distinct "back" vs "page back"
 * icons (§2.3 note) live here, on different slots, so features can never conflate them.
 */
public final class MenuBuilder {

    private final Menu menu;

    private MenuBuilder(int size, MenuText title) {
        this.menu = new Menu(size, title);
    }

    /** A 54er list menu: full surrounding border (§2.2 "Listen: voller Seitenrahmen"). */
    public static MenuBuilder list(MenuText title) {
        MenuBuilder b = new MenuBuilder(Menu.SIZE_CHEST, title);
        b.fillBorder();
        return b;
    }

    /** A 54er simple panel: top + bottom rows framed, interior open (§2.2 "einfache Menüs"). */
    public static MenuBuilder panel(MenuText title) {
        MenuBuilder b = new MenuBuilder(Menu.SIZE_CHEST, title);
        b.fillRow(0);
        b.fillRow(5);
        return b;
    }

    /** A 27er confirm dialog: full border, only the inner row carries content (§2.5). */
    public static MenuBuilder dialog(MenuText title) {
        MenuBuilder b = new MenuBuilder(Menu.SIZE_DIALOG, title);
        b.fillBorder();
        return b;
    }

    /** Place the always-present, non-interactive header/context item on slot 4. */
    public MenuBuilder header(IconSpec icon) {
        menu.setItem(MenuLayout.HEADER, MenuItem.display(icon));
        return this;
    }

    /** Place the close button on slot 49 (54er) with the standard barrier icon + hint. */
    public MenuBuilder close() {
        menu.setItem(MenuLayout.CLOSE, closeButton());
        return this;
    }

    /** Place the "back to parent" button on slot 48 (door icon, distinct from the page arrows). */
    public MenuBuilder back(ClickHandler onBack) {
        menu.setItem(MenuLayout.BACK, MenuItem.button(
                IconSpec.of(Icon.BACK, MenuText.name("Zurück"),
                        Lore.builder().describe("Zum vorherigen Menü.").clickToOpen("zurückgehen").build()),
                ctx -> {
                    ctx.view().feedback(Feedback.NAVIGATE);
                    onBack.onClick(ctx);
                }));
        return this;
    }

    /** Mark the built menu LIVE with {@code binding} (STATIC menus skip this). */
    public MenuBuilder live(LiveBinding binding) {
        menu.setLive(binding);
        return this;
    }

    /** Put any item at an explicit slot (escape hatch for centred single actions, value editors, …). */
    public MenuBuilder item(int slot, MenuItem item) {
        menu.setItem(slot, item);
        return this;
    }

    public Menu build() {
        return menu;
    }

    // ── shared standard buttons ──────────────────────────────────────────────────────────────────

    /** The standard close button (barrier, slot-agnostic) — also used by the dialog at slot 22. */
    public static MenuItem closeButton() {
        return MenuItem.button(
                IconSpec.of(Icon.CLOSE, MenuText.name("Schließen", Token.NEGATIVE),
                        Lore.builder().describe("Schließt das Menü.").clickToOpen("schließen").build()),
                ctx -> {
                    ctx.view().feedback(Feedback.NAVIGATE);
                    ctx.view().close();
                });
    }

    // ── pagination (§4.4) ────────────────────────────────────────────────────────────────────────

    /** Centre slot of the 7×4 grid — where the empty-list marker sits (§4.4). */
    public static final int EMPTY_MARKER_SLOT = 22;

    /**
     * Render one page of {@code entries} into the 7×4 grid of {@code menu}: clears the content slots,
     * lays out the page, shows the page arrows on 45/53 only when their page exists, and drops a centred
     * "Keine Einträge vorhanden." marker for an empty list. {@code onPage} is invoked with the click
     * context and the target page index when an arrow is clicked, so the feature re-lays-out and refreshes
     * through the live view (the feature owns page state and re-calls this).
     *
     * @return the {@link Pagination} used, so the caller can read pageCount / clamp the current page
     */
    public static Pagination renderPage(Menu menu, List<MenuItem> entries, int page,
                                        java.util.function.ObjIntConsumer<ClickContext> onPage) {
        Pagination pagination = new Pagination(entries.size());
        int current = pagination.clamp(page);

        // Clear the content grid first (so a shorter page leaves no stale items behind).
        for (int slot : Pagination.CONTENT_SLOTS) {
            menu.setItem(slot, null);
        }

        if (pagination.isEmpty()) {
            menu.setItem(EMPTY_MARKER_SLOT, MenuItem.display(
                    IconSpec.of(Icon.EMPTY, MenuText.name("Keine Einträge vorhanden", Token.MUTED))));
        } else {
            int start = pagination.firstIndexOf(current);
            int count = pagination.countOn(current);
            for (int i = 0; i < count; i++) {
                menu.setItem(Pagination.CONTENT_SLOTS[i], entries.get(start + i));
            }
        }

        // Arrows only when the page exists — no dead/greyed arrows (§4.4).
        menu.setItem(MenuLayout.PAGE_PREV, pagination.hasPrev(current)
                ? pageArrow(Icon.PAGE_PREV, "Vorherige Seite", current - 1, onPage) : null);
        menu.setItem(MenuLayout.PAGE_NEXT, pagination.hasNext(current)
                ? pageArrow(Icon.PAGE_NEXT, "Nächste Seite", current + 1, onPage) : null);
        return pagination;
    }

    private static MenuItem pageArrow(Icon icon, String name, int targetPage,
                                      java.util.function.ObjIntConsumer<ClickContext> onPage) {
        return MenuItem.button(
                IconSpec.of(icon, MenuText.name(name),
                        Lore.builder().clickToOpen("blättern").build()),
                ctx -> {
                    ctx.view().feedback(Feedback.NAVIGATE);
                    onPage.accept(ctx, targetPage);
                });
    }

    // ── internal border filling ──────────────────────────────────────────────────────────────────

    private void fillBorder() {
        for (int slot : MenuLayout.borderSlots(menu.size())) {
            menu.setItem(slot, MenuItem.display(IconSpec.filler()));
        }
    }

    private void fillRow(int row) {
        int base = row * 9;
        for (int i = 0; i < 9; i++) {
            menu.setItem(base + i, MenuItem.display(IconSpec.filler()));
        }
    }
}
