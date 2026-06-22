package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.economy.EconomyEndpoints;
import com.mcplatform.protocol.economy.EconomyEventEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The economy transaction-history menu (MENU_DESIGN §4.4 list): a paginated, newest-first audit trail of
 * a player's coin movements, with a cycling type filter and a header that doubles as a reload control
 * (§2.3). STATIC — it shows what was loaded; a fresh read happens on open, on filter change, and on
 * header click.
 *
 * <p>The backend paginates by keyset cursor ({@code before}=sequenceNo → {@code nextCursor}); this menu
 * follows the cursor off-main and accumulates up to {@link #MAX_ENTRIES} entries, then paginates them
 * client-side through {@link MenuBuilder#renderPage}. Hitting the cap is surfaced honestly in the header
 * ("gekürzt"), never silently dropped. The layout/filter/pagination logic is pure (no Bukkit); only the
 * REST read touches transport — so it is unit-testable end to end.
 */
public final class TransactionHistoryMenu {

    /** A type filter mapped 1:1 to the backend's single {@code type} query param ({@code null} = all). */
    enum Filter {
        ALL(null, "Alle Buchungen", Icon.HISTORY),
        CREDITED("CREDITED", "Gutschriften", Icon.CONFIRM),
        DEBITED("DEBITED", "Abbuchungen", Icon.CANCEL),
        TRANSFER_IN("TRANSFER_IN", "Erhaltene Transfers", Icon.CONFIRM),
        TRANSFER_OUT("TRANSFER_OUT", "Gesendete Transfers", Icon.CANCEL),
        SET("SET", "Admin-Korrekturen", Icon.VALUE);

        private final String type;
        private final String label;
        private final Icon icon;

        Filter(String type, String label, Icon icon) {
            this.type = type;
            this.label = label;
            this.icon = icon;
        }

        String type() {
            return type;
        }

        String label() {
            return label;
        }

        Icon icon() {
            return icon;
        }

        Filter next() {
            Filter[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** Top-border control slot for the filter toggle (the header sits on slot 4). */
    static final int FILTER_SLOT = 2;
    /** Entries fetched per backend request (the endpoint's documented max). */
    private static final int FETCH_LIMIT = 200;
    /** Hard cap on accumulated entries — bounds memory; surfaced as "gekürzt" when hit. */
    static final int MAX_ENTRIES = 500;

    private final UUID target;
    private final String targetName;
    private final String currency;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Menu menu;

    private List<EconomyEventEntry> entries = new ArrayList<>();
    private boolean loaded;
    private boolean truncated;
    private int page;
    private Filter filter = Filter.ALL;

    public TransactionHistoryMenu(UUID target, String targetName, String currency,
                                  BackendClient backend, PlatformScheduler scheduler) {
        this.target = target;
        this.targetName = targetName;
        this.currency = currency;
        this.backend = backend;
        this.scheduler = scheduler;
        this.menu = MenuBuilder.list(MenuText.name("Kontobewegungen", Token.ENTITY))
                .close()
                .build();
        applyControls();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** (Re)load from the backend, newest-first, for the current filter; fills the open {@code view}. */
    public void load(MenuView view) {
        this.loaded = false;
        this.truncated = false;
        this.page = 0;
        showLoading();
        applyControls();
        view.refresh();
        fetchFrom(view, new ArrayList<>(), null);
    }

    /** Follow the keyset cursor off-main, accumulating pages until the cap or the end is reached. */
    private void fetchFrom(MenuView view, List<EconomyEventEntry> accumulated, Long before) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("currency", currency);
        if (filter.type() != null) {
            query.put("type", filter.type());
        }
        if (before != null) {
            query.put("before", Long.toString(before));
        }
        query.put("limit", Integer.toString(FETCH_LIMIT));

        backend.call(EconomyEndpoints.GET_HISTORY, null, query, target.toString())
                .whenComplete((response, error) -> {
                    if (error != null || response == null) {
                        scheduler.runSync(() -> showError(view, error));
                        return;
                    }
                    accumulated.addAll(response.entries());
                    Long next = response.nextCursor();
                    if (next != null && accumulated.size() < MAX_ENTRIES) {
                        fetchFrom(view, accumulated, next); // keep following the cursor (still off-main)
                    } else {
                        boolean more = next != null; // hit the cap with older entries still available
                        scheduler.runSync(() -> {
                            setEntries(accumulated, more);
                            render(view);
                        });
                    }
                });
    }

    /** Feed loaded entries (newest-first) and reset to page 1. {@code more} = older entries were capped. */
    void setEntries(List<EconomyEventEntry> loadedEntries, boolean more) {
        this.entries = new ArrayList<>(loadedEntries);
        this.loaded = true;
        this.truncated = more;
        this.page = 0;
    }

    /** Lay out the current page into the model (pure — no view, no refresh). */
    public void layout() {
        applyControls();
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(entries.size());
        for (EconomyEventEntry entry : entries) {
            items.add(entryItem(entry));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, targetPage) -> {
            this.page = targetPage;
            layout();
            ctx.view().refresh();
        });
    }

    /** Lay out the current page and push it to the open view. */
    public void render(MenuView view) {
        layout();
        view.refresh();
    }

    // ── controls: header (reload) + filter toggle ───────────────────────────────────────────────────

    private void applyControls() {
        menu.setItem(MenuLayout.HEADER, MenuItem.button(headerIcon(), ctx -> {
            ctx.view().feedback(Feedback.NAVIGATE);
            load(ctx.view());
        }));
        menu.setItem(FILTER_SLOT, filterButton());
    }

    private IconSpec headerIcon() {
        Lore lore = Lore.builder().describe("Kontobewegungen von " + targetName + ".");
        if (loaded) {
            lore.value("Einträge:", entries.size() + (truncated ? "+ (gekürzt)" : ""));
            lore.value("Filter:", filter.label());
        }
        return IconSpec.head(target, MenuText.name(targetName, Token.ENTITY),
                lore.clickToOpen("aktualisieren").build());
    }

    private MenuItem filterButton() {
        return MenuItem.button(
                IconSpec.of(filter.icon(), MenuText.name("Filter: " + filter.label(), Token.INFO),
                        Lore.builder()
                                .describe("Aktuell: " + filter.label() + ".")
                                .clickToOpen("Filter wechseln")
                                .build()),
                this::cycleFilter);
    }

    void cycleFilter(ClickContext ctx) {
        ctx.view().feedback(Feedback.NAVIGATE);
        this.filter = filter.next();
        load(ctx.view()); // a different filter means a different result set → re-read from the backend
    }

    // ── states ──────────────────────────────────────────────────────────────────────────────────────

    private void showLoading() {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.LOADING,
                MenuText.name("Lade…", Token.BODY),
                Lore.builder().describe("Transaktionen werden geladen.").build())));
    }

    private void showError(MenuView view, Throwable error) {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.EMPTY,
                MenuText.name("Konnte nicht geladen werden", Token.NEGATIVE),
                Lore.builder().describe("Bitte später erneut versuchen.").build())));
        view.feedback(Feedback.DENY);
        view.send(EconomyMenuText.historyError(error));
        view.refresh();
    }

    private void clearContent() {
        for (int slot : Pagination.CONTENT_SLOTS) {
            menu.setItem(slot, null);
        }
        menu.setItem(MenuLayout.PAGE_PREV, null);
        menu.setItem(MenuLayout.PAGE_NEXT, null);
    }

    // ── one history entry (display-only) ─────────────────────────────────────────────────────────────

    private MenuItem entryItem(EconomyEventEntry entry) {
        Lore lore = Lore.builder()
                .describe(EconomyHistoryFormat.typeLabel(entry.eventType()))
                .value("Betrag:", EconomyHistoryFormat.amount(entry) + " " + currency)
                .value("Stand danach:", EconomyHistoryFormat.grouped(entry.balanceAfter()) + " " + currency)
                .value("Quelle:", entry.source())
                .value("Zeit:", EconomyHistoryFormat.time(entry.timestampEpochMilli()));
        if (entry.correlationId() != null) {
            lore.value("Transfer:", EconomyHistoryFormat.shortId(entry.correlationId()));
        }
        IconSpec icon = IconSpec.of(EconomyHistoryFormat.icon(entry.eventType()),
                MenuText.name(EconomyHistoryFormat.amount(entry) + " " + currency,
                        EconomyHistoryFormat.amountToken(entry.eventType())),
                lore.build());
        return MenuItem.display(icon);
    }
}
