package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.PlayerPickerMenu;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.TransferResponse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * End-to-end (Bukkit-free) proof of the player-side transfer flow: the value editor raises/lowers the
 * amount with a lower-bound guard, confirm opens the dialog, and the transfer surfaces a backend 422
 * cleanly in the menu (the required insufficient-funds path).
 */
class TransferMenuTest {

    private final UUID sender = UUID.randomUUID();
    private final PlayerPickerMenu.Entry target =
            new PlayerPickerMenu.Entry(UUID.randomUUID(), "Alex");

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

    private TransferMenu newMenu(BackendClient backend) {
        return new TransferMenu(target, "COINS", backend, new InlineScheduler(), ctx -> {
        });
    }

    @Test
    void plusAndShiftRaiseTheAmount() {
        TransferMenu tm = newMenu(null);
        Menu menu = tm.menu();
        RecordingMenuView view = new RecordingMenuView(sender, menu);

        menu.route(new ClickContext(sender, ClickAction.LEFT, TransferMenu.SLOT_PLUS, view));
        assertEquals(2, tm.amount());
        menu.route(new ClickContext(sender, ClickAction.SHIFT_LEFT, TransferMenu.SLOT_PLUS, view));
        assertEquals(12, tm.amount());
        assertTrue(view.slotWrites.containsKey(TransferMenu.SLOT_VALUE), "only the value slot re-rendered");
    }

    @Test
    void cannotGoBelowTheMinimum() {
        TransferMenu tm = newMenu(null);
        Menu menu = tm.menu();
        RecordingMenuView view = new RecordingMenuView(sender, menu);

        menu.route(new ClickContext(sender, ClickAction.LEFT, TransferMenu.SLOT_MINUS, view));
        assertEquals(1, tm.amount(), "amount clamped at the minimum");
        assertTrue(view.feedback.contains(Feedback.DENY), "lower-bound triggers error feedback");
    }

    @Test
    void confirmOpensTheDialog() {
        TransferMenu tm = newMenu(null);
        Menu menu = tm.menu();
        RecordingMenuView view = new RecordingMenuView(sender, menu);

        menu.route(new ClickContext(sender, ClickAction.LEFT, TransferMenu.SLOT_CONFIRM, view));
        assertEquals(1, view.opened.size());
        assertEquals(27, view.opened.get(0).size(), "transfer confirm is a 27er dialog");
    }

    @Test
    void insufficientFundsIsShownInTheMenu() {
        CompletableFuture<?> failed = CompletableFuture.failedFuture(BackendException.fromStatus(422, "no funds"));
        TransferMenu tm = newMenu(new FixedBackend(failed));
        RecordingMenuView view = new RecordingMenuView(sender, tm.menu());

        tm.transfer(new ClickContext(sender, ClickAction.LEFT, TransferMenu.SLOT_CONFIRM, view), 50L);

        assertTrue(view.feedback.contains(Feedback.DENY));
        assertTrue(view.messages.stream().anyMatch(m -> m.text().text().toLowerCase().contains("nicht genug")),
                "422 surfaced as insufficient funds");
        assertTrue(view.opened.contains(tm.menu()), "returns to the amount editor to retry");
    }

    @Test
    void successConfirmsAndCloses() {
        // TRANSFER returns a TransferResponse — the menu's whenComplete casts the body to that type.
        TransferResponse ok = new TransferResponse(
                new BalanceResponse(sender, "COINS", 50L, 2L),
                new BalanceResponse(target.uuid(), "COINS", 50L, 2L));
        TransferMenu tm = newMenu(new FixedBackend(CompletableFuture.completedFuture(ok)));
        RecordingMenuView view = new RecordingMenuView(sender, tm.menu());

        tm.transfer(new ClickContext(sender, ClickAction.LEFT, TransferMenu.SLOT_CONFIRM, view), 50L);

        assertTrue(view.feedback.contains(Feedback.SUCCESS));
        assertTrue(view.closed, "menu closes on a successful transfer");
    }
}
