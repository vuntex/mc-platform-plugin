package com.mcplatform.plugin.platform.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * A reusable, paginated player picker (MENU_DESIGN §4.4 list): one player head per entry, click to pick.
 * STATIC by design — an online-player list is a snapshot the user acts on, not a value that must tick
 * live — with the header doubling as a "refresh" control (§2.3 lets the header be interactive). Both the
 * economy transfer recipient choice and the punishment target choice reuse it, so the list/pagination
 * convention is implemented once.
 *
 * <p>Pure: the caller gathers the (Bukkit) online players into {@link Entry} records and hands them in;
 * this class only lays out and routes. Page state lives here; arrows re-lay-out through the live view.
 */
public final class PlayerPickerMenu {

    /** A pickable player as plain data (no Bukkit). */
    public record Entry(UUID uuid, String name) {
    }

    private final Menu menu;
    private final List<Entry> entries;
    private final BiConsumer<ClickContext, Entry> onPick;
    private int page;

    /**
     * @param title    menu title (section-coloured by the caller)
     * @param entries  candidates to show (already gathered by the caller)
     * @param onPick   invoked with the click + chosen entry
     * @param onRefresh optional header "refresh" action (re-gather + reopen); {@code null} → inert header
     */
    public PlayerPickerMenu(MenuText title, List<Entry> entries,
                            BiConsumer<ClickContext, Entry> onPick, ClickHandler onRefresh) {
        this.entries = new ArrayList<>(entries);
        this.onPick = onPick;
        IconSpec headerIcon = IconSpec.of(Icon.INFO, MenuText.name("Spieler wählen", Token.INFO),
                onRefresh == null
                        ? Lore.builder().describe("Wähle einen Spieler aus der Liste.").build()
                        : Lore.builder().describe("Wähle einen Spieler aus der Liste.").clickToOpen("aktualisieren").build());
        MenuBuilder builder = MenuBuilder.list(title);
        builder = onRefresh == null
                ? builder.header(headerIcon)
                : builder.item(MenuLayout.HEADER, MenuItem.button(headerIcon, ctx -> {
                    ctx.view().feedback(Feedback.NAVIGATE);
                    onRefresh.onClick(ctx);
                }));
        this.menu = builder.close().build();
        layout();
    }

    public Menu menu() {
        return menu;
    }

    /** Lay out the current page (pure — no view, no refresh). */
    public void layout() {
        List<MenuItem> items = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            items.add(entryItem(entry));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, target) -> {
            this.page = target;
            layout();
            ctx.view().refresh();
        });
    }

    private MenuItem entryItem(Entry entry) {
        IconSpec icon = IconSpec.head(entry.uuid(), MenuText.name(entry.name(), Token.ENTITY),
                Lore.builder().describe("Spieler " + entry.name() + ".").clickToOpen("auswählen").build());
        return MenuItem.button(icon, ctx -> {
            ctx.view().feedback(Feedback.NAVIGATE);
            onPick.accept(ctx, entry);
        });
    }
}
