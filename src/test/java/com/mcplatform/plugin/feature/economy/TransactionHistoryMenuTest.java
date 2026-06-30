package com.mcplatform.plugin.feature.economy;

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
import com.mcplatform.protocol.economy.EconomyEventEntry;
import com.mcplatform.protocol.economy.EconomyHistoryResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Bukkit-free proof of the transaction-history menu: it follows the backend keyset cursor across pages,
 * paginates the accumulated entries client-side, shows the empty marker, and re-reads with the right
 * {@code type} query param when the filter is cycled.
 */
class TransactionHistoryMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();

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

    /** Records the query of every history call and replays a queued response per call (last one sticks). */
    private static final class RecordingBackend implements BackendClient {
        final List<Map<String, String>> queries = new ArrayList<>();
        private final Deque<EconomyHistoryResponse> responses = new ArrayDeque<>();
        private EconomyHistoryResponse last;

        RecordingBackend(EconomyHistoryResponse... canned) {
            for (EconomyHistoryResponse r : canned) {
                responses.add(r);
                last = r;
            }
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException("history uses the query-param overload");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(
                EndpointDescriptor<REQ, RES> e, REQ b, Map<String, String> query, String... v) {
            queries.add(query);
            EconomyHistoryResponse r = responses.isEmpty() ? last : responses.poll();
            return (CompletableFuture<RES>) CompletableFuture.completedFuture(r);
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException();
        }
    }

    private EconomyEventEntry entry(long seq, String type, long amount) {
        return new EconomyEventEntry(seq, "COINS", type, amount, 1_000L, UUID.randomUUID(), "PLUGIN:test",
                null, null, 0L);
    }

    private EconomyHistoryResponse page(int count, long startSeq, String type, Long nextCursor) {
        List<EconomyEventEntry> es = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            es.add(entry(startSeq - i, type, 100 + i));
        }
        return new EconomyHistoryResponse(target, es, nextCursor);
    }

    private TransactionHistoryMenu open(RecordingBackend backend, RecordingMenuView view) {
        TransactionHistoryMenu menu = new TransactionHistoryMenu(target, "Steve", "COINS", backend, new InlineScheduler());
        // load() runs fully inline (InlineScheduler + completed futures), filling the view synchronously.
        menu.load(view == null ? new RecordingMenuView(viewer, menu.menu()) : view);
        return menu;
    }

    @Test
    void followsCursorAcrossBackendPagesThenPaginatesClientSide() {
        // Two backend pages: 28 (nextCursor=5) then 12 (end) → 40 accumulated entries.
        RecordingBackend backend = new RecordingBackend(
                page(28, 100, "CREDITED", 5L),
                page(12, 72, "CREDITED", null));
        TransactionHistoryMenu menu = new TransactionHistoryMenu(target, "Steve", "COINS", backend, new InlineScheduler());
        RecordingMenuView view = new RecordingMenuView(viewer, menu.menu());
        menu.load(view);

        // Two backend calls; the second carried the keyset cursor from the first page.
        assertEquals(2, backend.queries.size());
        assertEquals("5", backend.queries.get(1).get("before"));
        assertEquals("COINS", backend.queries.get(0).get("currency"));

        Menu model = menu.menu();
        // Page 0: full 28-slot grid, forward arrow only, controls present.
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(model.getItem(slot), "page 0 content slot " + slot);
        }
        assertNull(model.getItem(MenuLayout.PAGE_PREV));
        assertNotNull(model.getItem(MenuLayout.PAGE_NEXT));
        assertNotNull(model.getItem(MenuLayout.HEADER), "header control");
        assertNotNull(model.getItem(TransactionHistoryMenu.FILTER_SLOT), "filter control");

        // Forward → page 1 holds the remaining 12 entries, back arrow only.
        model.route(new ClickContext(viewer, ClickAction.LEFT, MenuLayout.PAGE_NEXT, view));
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
    void emptyHistoryShowsTheMarker() {
        RecordingBackend backend = new RecordingBackend(page(0, 0, "CREDITED", null));
        TransactionHistoryMenu menu = open(backend, null);
        assertNotNull(menu.menu().getItem(22), "centred empty marker present");
    }

    @Test
    void cyclingTheFilterReReadsWithTheTypeParam() {
        RecordingBackend backend = new RecordingBackend(page(1, 1, "CREDITED", null));
        TransactionHistoryMenu menu = new TransactionHistoryMenu(target, "Steve", "COINS", backend, new InlineScheduler());
        RecordingMenuView view = new RecordingMenuView(viewer, menu.menu());
        menu.load(view);

        // Initial load is the ALL filter → no type param.
        assertNull(backend.queries.get(0).get("type"));

        // Left-click cycles forward (ALL → CREDITED) → a fresh read scoped to that type.
        menu.menu().route(new ClickContext(viewer, ClickAction.LEFT, TransactionHistoryMenu.FILTER_SLOT, view));
        assertEquals("CREDITED", backend.queries.get(backend.queries.size() - 1).get("type"));

        // Right-click cycles backward (CREDITED → ALL again) → no type param.
        menu.menu().route(new ClickContext(viewer, ClickAction.RIGHT, TransactionHistoryMenu.FILTER_SLOT, view));
        assertNull(backend.queries.get(backend.queries.size() - 1).get("type"));
    }
}
