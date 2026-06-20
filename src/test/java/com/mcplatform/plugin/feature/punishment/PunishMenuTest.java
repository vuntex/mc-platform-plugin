package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.punishment.TemplateResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Proves the team-side punish menu: the {@code canApply} flag gates applicable (interactive) vs locked
 * (display) templates, inactive templates are hidden, picking opens the confirm, and issuing honours the
 * backend gate — a 403 is shown cleanly (the required not-permitted path).
 */
class PunishMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

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

    private TemplateResponse template(String key, String type, boolean active, boolean canApply) {
        return new TemplateResponse(key, type, "default reason", 3_600_000L, "mcplatform.punish." + key, active, canApply);
    }

    private PunishMenu menuWith(List<TemplateResponse> templates, BackendClient backend) {
        PunishMenu pm = new PunishMenu(target, "Griefer", backend, new InlineScheduler());
        pm.setTemplates(templates);
        pm.layout();
        return pm;
    }

    @Test
    void canApplyGatesApplicableVsLocked() {
        PunishMenu pm = menuWith(List.of(
                template("warn", "WARN", true, true),
                template("permaban", "PERMABAN", true, false)),
                new FixedBackend(CompletableFuture.completedFuture(null)));

        MenuItem applicable = pm.menu().getItem(Pagination.CONTENT_SLOTS[0]);
        MenuItem locked = pm.menu().getItem(Pagination.CONTENT_SLOTS[1]);
        assertTrue(applicable.isInteractive(), "canApply=true → interactive");
        assertFalse(locked.isInteractive(), "canApply=false → visible but locked");
    }

    @Test
    void inactiveTemplatesAreHidden() {
        PunishMenu pm = menuWith(List.of(
                template("warn", "WARN", true, true),
                template("old", "WARN", false, true)),
                new FixedBackend(CompletableFuture.completedFuture(null)));

        assertTrue(pm.menu().getItem(Pagination.CONTENT_SLOTS[0]).isInteractive());
        // Only one item placed; the second content slot is empty (inactive filtered out).
        assertFalse(pm.menu().getItem(Pagination.CONTENT_SLOTS[1]) != null
                && pm.menu().getItem(Pagination.CONTENT_SLOTS[1]).isInteractive());
    }

    @Test
    void pickingApplicableOpensTheConfirm() {
        PunishMenu pm = menuWith(List.of(template("warn", "WARN", true, true)),
                new FixedBackend(CompletableFuture.completedFuture(null)));
        RecordingMenuView view = new RecordingMenuView(actor, pm.menu());

        pm.menu().route(new ClickContext(actor, ClickAction.LEFT, Pagination.CONTENT_SLOTS[0], view));
        assertEquals(1, view.opened.size());
        assertEquals(27, view.opened.get(0).size());
    }

    @Test
    void issueForbiddenIsShownCleanly() {
        CompletableFuture<?> failed = CompletableFuture.failedFuture(BackendException.fromStatus(403, "no"));
        PunishMenu pm = menuWith(List.of(template("permaban", "PERMABAN", true, true)),
                new FixedBackend(failed));
        TemplateResponse t = template("permaban", "PERMABAN", true, true);
        RecordingMenuView view = new RecordingMenuView(actor, pm.menu());

        pm.issue(new ClickContext(actor, ClickAction.LEFT, 11, view), t);

        assertTrue(view.feedback.contains(Feedback.DENY));
        assertTrue(view.messages.stream().anyMatch(m -> m.text().text().toLowerCase().contains("berechtigung")),
                "403 surfaced as a no-permission message");
        assertTrue(view.opened.contains(pm.menu()), "returns to the template list");
    }

    @Test
    void issueSuccessConfirmsAndCloses() {
        // ISSUE_FROM_TEMPLATE returns a PunishmentResponse — the menu casts the body to that type.
        com.mcplatform.protocol.punishment.PunishmentResponse ok =
                new com.mcplatform.protocol.punishment.PunishmentResponse(UUID.randomUUID(), target, "WARN",
                        "reason", actor, 0L, 0L, null, 0L, true, 1L);
        PunishMenu pm = menuWith(List.of(template("warn", "WARN", true, true)),
                new FixedBackend(CompletableFuture.completedFuture(ok)));
        TemplateResponse t = template("warn", "WARN", true, true);
        RecordingMenuView view = new RecordingMenuView(actor, pm.menu());

        pm.issue(new ClickContext(actor, ClickAction.LEFT, 11, view), t);

        assertTrue(view.feedback.contains(Feedback.SUCCESS));
        assertTrue(view.closed);
    }
}
