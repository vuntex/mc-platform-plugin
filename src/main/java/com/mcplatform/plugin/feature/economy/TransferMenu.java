package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ConfirmDialog;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.PlayerPickerMenu;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.economy.EconomyEndpoints;
import com.mcplatform.protocol.economy.TransferRequest;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The amount step of the player-side transfer flow (pick recipient → choose amount → confirm). It uses
 * the MENU_DESIGN §4.6 <em>value editor</em> convention — a centred value flanked by "−" (left) and "+"
 * (right), with shift for a larger step — rather than inventing a new input; the lower bound triggers
 * error feedback. STATIC: the amount changes only through the player's own clicks, so the slot is
 * re-rendered on each change, not subscribed. Confirm opens the §2.5 dialog and calls the existing
 * {@code TRANSFER} endpoint; a 422 (insufficient) / 400 (self-transfer) is shown cleanly in the menu.
 *
 * <p>Pure except for the network call: the value-editor maths and the error handling are unit-testable.
 */
public final class TransferMenu {

    static final int SLOT_MINUS = 21;
    static final int SLOT_VALUE = 22;
    static final int SLOT_PLUS = 23;
    static final int SLOT_CONFIRM = 31;

    private static final long MIN = 1L;
    private static final long STEP = 1L;
    private static final long STEP_SHIFT = 10L;

    private final PlayerPickerMenu.Entry target;
    private final String currency;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Consumer<ClickContext> onBack;
    private final Menu menu;

    private long amount = MIN;

    public TransferMenu(PlayerPickerMenu.Entry target, String currency,
                        BackendClient backend, PlatformScheduler scheduler, Consumer<ClickContext> onBack) {
        this.target = target;
        this.currency = currency;
        this.backend = backend;
        this.scheduler = scheduler;
        this.onBack = onBack;

        MenuBuilder builder = MenuBuilder.panel(MenuText.name("Coins senden", Token.ENTITY))
                .header(IconSpec.head(target.uuid(), MenuText.name(target.name(), Token.ENTITY),
                        Lore.builder().describe("Empfänger des Transfers.").build()))
                .item(SLOT_MINUS, stepItem(false))
                .item(SLOT_VALUE, valueItem())
                .item(SLOT_PLUS, stepItem(true))
                .item(SLOT_CONFIRM, confirmItem())
                .back(onBack::accept)
                .close();
        this.menu = builder.build();
    }

    public Menu menu() {
        return menu;
    }

    long amount() {
        return amount;
    }

    // ── value editor ─────────────────────────────────────────────────────────────────────────────

    private MenuItem valueItem() {
        return MenuItem.display(IconSpec.of(Icon.VALUE,
                MenuText.name(amount + " " + currency, Token.ENTITY),
                Lore.builder().describe("Zu sendender Betrag.").value("Betrag:", amount + " " + currency).build()));
    }

    private MenuItem stepItem(boolean plus) {
        String verb = plus ? "erhöhen" : "verringern";
        IconSpec icon = IconSpec.of(plus ? Icon.ADD : Icon.LOCKED,
                MenuText.name((plus ? "+" : "−") + " Betrag", plus ? Token.POSITIVE : Token.NEGATIVE),
                Lore.builder()
                        .describe((plus ? "Erhöht" : "Verringert") + " den Betrag.")
                        .hint("Klicke", ", zum ", verb + " (±1)", plus)
                        .hint("Shift-Klick", ", zum ", verb + " (±10)", plus)
                        .build());
        long sign = plus ? 1 : -1;
        return MenuItem.button(icon, ClickAction.LEFT, ctx -> change(ctx, sign * STEP))
                .on(ClickAction.SHIFT_LEFT, ctx -> change(ctx, sign * STEP_SHIFT));
    }

    private void change(ClickContext ctx, long delta) {
        long next = amount + delta;
        if (next < MIN) {
            ctx.view().feedback(Feedback.DENY);
            ctx.view().send(MenuMessage.actionBar("Der Betrag kann nicht unter " + MIN + " sein.", Token.NEGATIVE));
            return;
        }
        amount = next;
        ctx.view().feedback(delta > 0 ? Feedback.ADD : Feedback.REMOVE);
        ctx.view().setSlot(SLOT_VALUE, valueItem()); // only the value slot re-renders (no flicker)
    }

    private MenuItem confirmItem() {
        return MenuItem.button(IconSpec.of(Icon.CONFIRM, MenuText.name("Senden", Token.POSITIVE),
                        Lore.builder().describe("Transfer prüfen und bestätigen.").clickToOpen("fortfahren").build()),
                this::openConfirm);
    }

    // ── confirm + transfer ───────────────────────────────────────────────────────────────────────

    void openConfirm(ClickContext ctx) {
        long pending = amount;
        IconSpec object = IconSpec.head(target.uuid(),
                MenuText.name(pending + " " + currency + " → " + target.name(), Token.ENTITY),
                Lore.builder().describe("An " + target.name() + ".").value("Betrag:", pending + " " + currency).build());
        Menu confirm = ConfirmDialog.of(MenuText.name("Transfer bestätigen?", Token.ENTITY), object)
                .confirmName(MenuText.name("Senden", Token.POSITIVE))
                .onConfirm(c -> transfer(c, pending))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    void transfer(ClickContext ctx, long sendAmount) {
        MenuView view = ctx.view();
        UUID from = ctx.playerId();
        scheduler.runAsync(() -> {
            TransferRequest request = new TransferRequest(target.uuid(), sendAmount, UUID.randomUUID(), "plugin");
            backend.callIdempotent(EconomyEndpoints.TRANSFER, request, from.toString(), currency)
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            view.feedback(Feedback.DENY);
                            view.send(EconomyMenuText.transferError(error));
                            view.open(menu); // back to the amount editor to retry
                            return;
                        }
                        view.feedback(Feedback.SUCCESS);
                        view.send(EconomyMenuText.transferSuccess(sendAmount, currency, target.name()));
                        view.close();
                    }));
        });
    }

    /** Slot of the back button (for tests/diagnostics). */
    static int backSlot() {
        return MenuLayout.BACK;
    }
}
