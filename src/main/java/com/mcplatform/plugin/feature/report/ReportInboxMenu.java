package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.LiveBinding;
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
import com.mcplatform.protocol.report.ReportEndpoints;
import com.mcplatform.protocol.report.ReportResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The team inbox (MENU_DESIGN §4.4 list): a LIVE, paginated, newest-first view of ALL open reports (a
 * shared queue — {@code staff} is just the requester's identity, not a filter). The local read-cache is
 * this menu's own {@code entries} list, refilled by a full {@code LIST_OPEN} on open and on every live
 * {@code mc:report:changed} (the {@link #INBOX_TOPIC} binding). Backend stays the source of truth; a
 * "latest-request-wins" sequence guard drops an out-of-order LIST_OPEN response (FR-021). Clicking an
 * entry navigates to the {@link ReportDetailMenu}; {@link MenuManager} unsubscribes the live binding on
 * close. The layout/pagination is pure; only the read touches transport.
 */
public final class ReportInboxMenu {

    /** Shared LIVE topic for every open inbox — the updater nudges this on any report change. */
    public static final String INBOX_TOPIC = "report:inbox";

    private final UUID viewer;
    private final boolean canHandle;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Function<UUID, String> names;
    private final Menu menu;

    private final AtomicLong requestSeq = new AtomicLong();
    private volatile long lastApplied = -1L;

    private List<ReportResponse> entries = new ArrayList<>();
    private boolean loaded;
    private int page;

    public ReportInboxMenu(UUID viewer, boolean canHandle, BackendClient backend,
                           PlatformScheduler scheduler, Function<UUID, String> names) {
        this.viewer = viewer;
        this.canHandle = canHandle;
        this.backend = backend;
        this.scheduler = scheduler;
        this.names = names;
        this.menu = MenuBuilder.list(MenuText.name("Offene Meldungen", Token.INFO))
                .live(new LiveBinding(INBOX_TOPIC, this::onLive))
                .close()
                .build();
        applyHeader();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** (Re)load all open reports from the backend and fill the open {@code view}. */
    public void load(MenuView view) {
        long seq = requestSeq.incrementAndGet();
        this.loaded = false;
        this.page = 0;
        showLoading();
        applyHeader();
        view.refresh();

        Map<String, String> query = new LinkedHashMap<>();
        query.put("staff", viewer.toString());
        backend.call(ReportEndpoints.LIST_OPEN, null, query)
                .whenComplete((open, error) -> scheduler.runSync(() -> {
                    if (seq != requestSeq.get()) {
                        return; // a newer load superseded this one — drop the stale result
                    }
                    if (error != null || open == null) {
                        showError(view);
                        return;
                    }
                    lastApplied = seq;
                    setEntries(open);
                    layout();
                    view.refresh();
                }));
    }

    /** A live change fired: re-read so the open inbox reflects the new truth. */
    private void onLive(MenuView view) {
        load(view);
    }

    void setEntries(ReportResponse[] open) {
        List<ReportResponse> list = new ArrayList<>(Arrays.asList(open));
        list.sort(Comparator.comparingLong(ReportResponse::createdAtEpochMilli).reversed());
        this.entries = list;
        this.loaded = true;
        this.page = 0;
    }

    /** Lay out the current page into the model (pure — no view, no refresh). */
    public void layout() {
        applyHeader();
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(entries.size());
        for (ReportResponse report : entries) {
            items.add(entryItem(report));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, targetPage) -> {
            this.page = targetPage;
            layout();
            ctx.view().refresh();
        });
    }

    private MenuItem entryItem(ReportResponse report) {
        Lore lore = Lore.builder()
                .describe("Kategorie: " + ReportFormat.categoryLabel(report.category()))
                .value("Status:", ReportFormat.statusLabel(report.status()))
                .value("Melder:", names.apply(report.reporter()))
                .value("Zeit:", ReportFormat.time(report.createdAtEpochMilli()))
                .clickToOpen("öffnen");
        IconSpec icon = IconSpec.head(report.target(),
                MenuText.name(names.apply(report.target()), Token.ENTITY), lore.build());
        return MenuItem.button(icon, ctx -> openDetail(ctx, report));
    }

    private void openDetail(ClickContext ctx, ReportResponse report) {
        ctx.view().feedback(Feedback.NAVIGATE);
        ReportDetailMenu detail = new ReportDetailMenu(report, viewer, canHandle, backend, scheduler, names,
                back -> {                                   // plain back button: re-render current list
                    layout();
                    back.view().open(menu);
                },
                (updated, back) -> {                        // after a status change: apply + back
                    applyChange(updated);
                    layout();
                    back.view().open(menu);
                });
        ctx.view().open(detail.menu());
    }

    /**
     * Apply the backend's returned report state to the local read-model: a report that is no longer open
     * (RESOLVED/REJECTED) leaves the queue; an still-open one (e.g. OPEN→IN_PROGRESS) is updated in place.
     * Optimistic — the next {@code LIST_OPEN} refresh reconciles against the backend truth anyway.
     */
    void applyChange(ReportResponse updated) {
        entries.removeIf(r -> r.id().equals(updated.id()));
        if (isOpen(updated.status())) {
            entries.add(updated);
            entries.sort(Comparator.comparingLong(ReportResponse::createdAtEpochMilli).reversed());
        }
    }

    private static boolean isOpen(String status) {
        return "OPEN".equals(status) || "IN_PROGRESS".equals(status);
    }

    // ── header (doubles as reload) + states ─────────────────────────────────────────────────────────

    private void applyHeader() {
        Lore lore = Lore.builder()
                .describe("Geteilte Warteschlange offener Meldungen.")
                .describe("Aktualisiert sich automatisch.");
        if (loaded) {
            lore.value("Offen:", Integer.toString(entries.size()));
        }
        // Display-only: the inbox is LIVE and re-reads on every mc:report:changed, so there is no manual
        // reload button — the header is pure context.
        IconSpec icon = IconSpec.of(Icon.GLOBAL, MenuText.name("Offene Meldungen", Token.INFO), lore.build());
        menu.setItem(MenuLayout.HEADER, MenuItem.display(icon));
    }

    private void showLoading() {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.LOADING,
                MenuText.name("Lade…", Token.BODY),
                Lore.builder().describe("Meldungen werden geladen.").build())));
    }

    private void showError(MenuView view) {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.EMPTY,
                MenuText.name("Konnte nicht geladen werden", Token.NEGATIVE),
                Lore.builder().describe("Bitte später erneut versuchen.").build())));
        view.feedback(Feedback.DENY);
        view.refresh();
    }

    private void clearContent() {
        for (int slot : Pagination.CONTENT_SLOTS) {
            menu.setItem(slot, null);
        }
        menu.setItem(MenuLayout.PAGE_PREV, null);
        menu.setItem(MenuLayout.PAGE_NEXT, null);
    }
}
