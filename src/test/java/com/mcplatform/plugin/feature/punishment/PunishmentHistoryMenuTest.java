package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * End-to-end (Bukkit-free) proof of demo 2 — the paginated history menu with a two-step confirm: page
 * layout and navigation, opening the critical confirm for an active punishment, and the revoke path
 * honouring the backend gate (a 403 is shown cleanly; success refreshes the list).
 */
class PunishmentHistoryMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    /** Runs both hops inline so async revoke flows complete within the test. */
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

    /** Returns a preset future for every call — enough to drive the revoke path. */
    private static final class FixedBackend implements BackendClient {
        private final CompletableFuture<?> result;

        FixedBackend(CompletableFuture<?> result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) result;
        }
    }

    private PunishmentResponse punishment(String type, boolean active) {
        return new PunishmentResponse(UUID.randomUUID(), target, type, "reason", actor,
                0L, 0L, null, 0L, active, 1L);
    }

    private List<PunishmentResponse> many(int n) {
        List<PunishmentResponse> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(punishment("TEMPBAN", true));
        }
        return out;
    }

    private PunishmentHistoryMenu menuWith(List<PunishmentResponse> entries, BackendClient backend) {
        PunishmentHistoryMenu hm = new PunishmentHistoryMenu(target, "Steve", backend, new InlineScheduler());
        hm.setEntries(entries);
        hm.layout();
        return hm;
    }

    @Test
    void fiftyEntriesPaginateAndNavigate() {
        PunishmentHistoryMenu hm = menuWith(many(50), new FixedBackend(CompletableFuture.completedFuture(null)));
        Menu menu = hm.menu();

        // Page 0: full grid, forward arrow only.
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(menu.getItem(slot), "page 0 slot " + slot);
        }
        assertNull(menu.getItem(MenuLayout.PAGE_PREV));
        assertNotNull(menu.getItem(MenuLayout.PAGE_NEXT));

        // Click forward → page 1: 22 items, back arrow only.
        RecordingMenuView view = new RecordingMenuView(actor, menu);
        menu.route(new ClickContext(actor, ClickAction.LEFT, MenuLayout.PAGE_NEXT, view));

        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (menu.getItem(slot) != null) {
                filled++;
            }
        }
        assertEquals(22, filled);
        assertNotNull(menu.getItem(MenuLayout.PAGE_PREV));
        assertNull(menu.getItem(MenuLayout.PAGE_NEXT));
        assertTrue(view.refreshes >= 1, "page turn refreshed the view");
    }

    @Test
    void emptyHistoryShowsTheMarker() {
        PunishmentHistoryMenu hm = menuWith(List.of(), new FixedBackend(CompletableFuture.completedFuture(null)));
        assertNotNull(hm.menu().getItem(22), "centred empty marker present");
    }

    @Test
    void clickingAnActivePunishmentOpensTheCriticalConfirm() {
        PunishmentHistoryMenu hm = menuWith(many(1), new FixedBackend(CompletableFuture.completedFuture(null)));
        Menu menu = hm.menu();
        RecordingMenuView view = new RecordingMenuView(actor, menu);

        // First content slot holds the single active entry.
        menu.route(new ClickContext(actor, ClickAction.LEFT, Pagination.CONTENT_SLOTS[0], view));

        assertEquals(1, view.opened.size(), "a confirm dialog was opened");
        assertEquals(27, view.opened.get(0).size(), "confirm is a 27er dialog");
    }

    @Test
    void revokeForbiddenIsShownCleanlyAndReturnsToTheList() {
        CompletableFuture<?> failed = CompletableFuture.failedFuture(BackendException.fromStatus(403, "denied"));
        PunishmentHistoryMenu hm = menuWith(many(1), new FixedBackend(failed));
        PunishmentResponse entry = punishment("TEMPBAN", true);
        RecordingMenuView view = new RecordingMenuView(actor, hm.menu());

        hm.revoke(new ClickContext(actor, ClickAction.DOUBLE_CLICK, MenuLayout.DIALOG_CONFIRM, view), entry);

        assertTrue(view.feedback.contains(Feedback.DENY), "denied feedback played");
        assertTrue(view.messages.stream().anyMatch(m -> m.text().text().toLowerCase().contains("berechtigung")),
                "403 surfaced as a no-permission message");
        assertTrue(view.opened.contains(hm.menu()), "returns to the history list");
    }

    @Test
    void revokeSuccessConfirmsAndRefreshesTheList() {
        PunishmentResponse revoked = punishment("TEMPBAN", false);
        PunishmentHistoryMenu hm = menuWith(many(1),
                new FixedBackend(CompletableFuture.completedFuture(revoked)));
        PunishmentResponse entry = punishment("TEMPBAN", true);
        RecordingMenuView view = new RecordingMenuView(actor, hm.menu());

        hm.revoke(new ClickContext(actor, ClickAction.DOUBLE_CLICK, MenuLayout.DIALOG_CONFIRM, view), entry);

        assertTrue(view.feedback.contains(Feedback.SUCCESS), "success feedback played");
        assertTrue(view.messages.stream().anyMatch(m -> m.text().text().toLowerCase().contains("aufgehoben")),
                "success message shown");
        assertTrue(view.opened.contains(hm.menu()), "returns to the refreshed history list");
    }
}
