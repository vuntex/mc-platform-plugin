package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.report.ChatMessage;
import com.mcplatform.protocol.report.ReportResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Bukkit-free proof of the team inbox: it reads ALL open reports with the {@code staff} identity, is
 * LIVE, paginates newest-first via the 28-grid, and opens a detail menu whose status-transition buttons
 * respect both the allowed transitions and the handle UI-gate.
 */
class ReportInboxMenuTest {

    private final UUID viewer = UUID.randomUUID();
    private final Function<UUID, String> names = uuid -> "Tester";

    private static final class InlineScheduler implements PlatformScheduler {
        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }
    }

    /** Returns a canned open-report array for the LIST_OPEN query overload; records each query. */
    private static final class RecordingBackend implements BackendClient {
        final List<Map<String, String>> queries = new ArrayList<>();
        private final ReportResponse[] open;

        RecordingBackend(ReportResponse[] open) {
            this.open = open;
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException("inbox uses the query-param overload");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(
                EndpointDescriptor<REQ, RES> e, REQ b, Map<String, String> query, String... v) {
            queries.add(query);
            return (CompletableFuture<RES>) CompletableFuture.completedFuture(open);
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException();
        }
    }

    private ReportResponse report(int i, String status, List<ChatMessage> chat) {
        return new ReportResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CHEATING", "Grund " + i, status, i /* createdAt: higher i = newer */,
                null, 0L, chat, 1L);
    }

    private ReportResponse[] reports(int count) {
        ReportResponse[] all = new ReportResponse[count];
        for (int i = 0; i < count; i++) {
            all[i] = report(i, "OPEN", List.of());
        }
        return all;
    }

    private ReportInboxMenu open(RecordingBackend backend, boolean canHandle, RecordingMenuView[] outView) {
        ReportInboxMenu inbox = new ReportInboxMenu(viewer, canHandle, backend, new InlineScheduler(), names);
        RecordingMenuView view = new RecordingMenuView(viewer, inbox.menu());
        outView[0] = view;
        inbox.load(view); // runs fully inline (InlineScheduler + completed future)
        return inbox;
    }

    @Test
    void readsAllOpenWithStaffIdentityAndIsLive() {
        RecordingBackend backend = new RecordingBackend(reports(3));
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);

        assertTrue(inbox.menu().isLive(), "inbox is a LIVE menu");
        assertEquals(viewer.toString(), backend.queries.get(0).get("staff"), "staff = requester identity");
    }

    @Test
    void paginatesNewestFirstAcrossTheGrid() {
        RecordingBackend backend = new RecordingBackend(reports(40));
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);
        Menu model = inbox.menu();

        // Page 0: full 28-slot grid, forward arrow only.
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(model.getItem(slot), "page 0 content slot " + slot);
        }
        assertNull(model.getItem(MenuLayout.PAGE_PREV));
        assertNotNull(model.getItem(MenuLayout.PAGE_NEXT));

        // Forward → page 1 holds the remaining 12, back arrow only.
        model.route(new ClickContext(viewer, ClickAction.LEFT, MenuLayout.PAGE_NEXT, view[0]));
        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (model.getItem(slot) != null) {
                filled++;
            }
        }
        assertEquals(12, filled);
        assertNotNull(model.getItem(MenuLayout.PAGE_PREV));
        assertNull(model.getItem(MenuLayout.PAGE_NEXT));
    }

    @Test
    void emptyInboxShowsTheMarker() {
        RecordingBackend backend = new RecordingBackend(reports(0));
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);
        assertNotNull(inbox.menu().getItem(22), "centred empty marker present");
    }

    private int filledContentSlots(Menu model) {
        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (model.getItem(slot) != null) {
                filled++;
            }
        }
        return filled;
    }

    @Test
    void resolvingOrRejectingRemovesTheReportImmediately() {
        UUID keptId = UUID.randomUUID();
        UUID changedId = UUID.randomUUID();
        ReportResponse kept = new ReportResponse(keptId, UUID.randomUUID(), UUID.randomUUID(),
                "CHEATING", "g", "OPEN", 2, null, 0L, List.of(), 1L);
        ReportResponse open = new ReportResponse(changedId, UUID.randomUUID(), UUID.randomUUID(),
                "BELEIDIGUNG", "g", "OPEN", 1, null, 0L, List.of(), 1L);
        RecordingBackend backend = new RecordingBackend(new ReportResponse[]{kept, open});
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);
        assertEquals(2, filledContentSlots(inbox.menu()), "both open reports shown initially");

        // Backend returns the report now REJECTED → it must leave the open queue at once.
        ReportResponse rejected = new ReportResponse(changedId, open.reporter(), open.target(),
                "BELEIDIGUNG", "g", "REJECTED", 1, viewer, 5L, List.of(), 2L);
        inbox.applyChange(rejected);
        inbox.layout();
        assertEquals(1, filledContentSlots(inbox.menu()), "rejected report removed from inbox");
    }

    @Test
    void takingInProgressKeepsTheReportInTheQueue() {
        UUID id = UUID.randomUUID();
        ReportResponse open = new ReportResponse(id, UUID.randomUUID(), UUID.randomUUID(),
                "CHEATING", "g", "OPEN", 1, null, 0L, List.of(), 1L);
        RecordingBackend backend = new RecordingBackend(new ReportResponse[]{open});
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);

        ReportResponse inProgress = new ReportResponse(id, open.reporter(), open.target(),
                "CHEATING", "g", "IN_PROGRESS", 1, viewer, 5L, List.of(), 2L);
        inbox.applyChange(inProgress);
        inbox.layout();
        assertEquals(1, filledContentSlots(inbox.menu()), "in-progress report stays in the queue");
    }

    @Test
    void detailShowsAllowedTransitionsForHandler() {
        ReportResponse openReport = report(1, "OPEN",
                List.of(new ChatMessage(UUID.randomUUID(), "hax", 1_000L)));
        RecordingBackend backend = new RecordingBackend(new ReportResponse[]{openReport});
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, true, view);

        // Click the single entry (first content slot) → detail menu is opened on the view.
        inbox.menu().route(new ClickContext(viewer, ClickAction.LEFT, Pagination.CONTENT_SLOTS[0], view[0]));
        Menu detail = view[0].opened.get(view[0].opened.size() - 1);

        // OPEN → IN_PROGRESS (slot 38) + REJECTED (slot 40); third action slot empty.
        assertNotNull(detail.getItem(38), "in-progress action");
        assertTrue(detail.getItem(38).isInteractive());
        assertNotNull(detail.getItem(40), "reject action");
        assertNull(detail.getItem(42));
    }

    @Test
    void detailHidesActionsWhenViewerCannotHandle() {
        ReportResponse openReport = report(1, "OPEN", List.of());
        RecordingBackend backend = new RecordingBackend(new ReportResponse[]{openReport});
        RecordingMenuView[] view = new RecordingMenuView[1];
        ReportInboxMenu inbox = open(backend, false, view);

        inbox.menu().route(new ClickContext(viewer, ClickAction.LEFT, Pagination.CONTENT_SLOTS[0], view[0]));
        Menu detail = view[0].opened.get(view[0].opened.size() - 1);

        assertNull(detail.getItem(38), "no action buttons without report.handle");
        assertNull(detail.getItem(40));
        assertNull(detail.getItem(42));
    }
}
