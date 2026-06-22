package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ConfirmDialog;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.report.ChangeStatusRequest;
import com.mcplatform.protocol.report.ChatMessage;
import com.mcplatform.protocol.report.ReportEndpoints;
import com.mcplatform.protocol.report.ReportResponse;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One report in detail: a summary header (reporter, category, status, time, reason), the attached public
 * chat-context snapshot rendered as per-sender heads in the grid, and — for a team member who may handle
 * (UI gate {@code mcplatform.report.handle}) — the allowed status-transition buttons. Each transition
 * goes through a {@link ConfirmDialog} and the {@code CHANGE_STATUS} endpoint with the actor as
 * {@code handledBy}; 403/404/409 are surfaced cleanly. The data comes from the already-loaded
 * {@link ReportResponse} (LIST_OPEN includes chatContext), so opening the detail needs no extra fetch.
 */
public final class ReportDetailMenu {

    /** Chat-context heads occupy the first three interior rows (21 slots); ~20 messages fit. */
    private static final int[] CHAT_SLOTS = java.util.Arrays.copyOfRange(Pagination.CONTENT_SLOTS, 0, 21);
    /** Status-transition buttons sit on the last interior row, centred. */
    private static final int[] ACTION_SLOTS = {38, 40, 42};

    private final ReportResponse report;
    private final UUID actor;
    private final boolean canHandle;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Function<UUID, String> names;
    private final Consumer<ClickContext> onBack;
    private final BiConsumer<ReportResponse, ClickContext> onChanged;
    private final Menu menu;

    public ReportDetailMenu(ReportResponse report, UUID actor, boolean canHandle,
                            BackendClient backend, PlatformScheduler scheduler, Function<UUID, String> names,
                            Consumer<ClickContext> onBack, BiConsumer<ReportResponse, ClickContext> onChanged) {
        this.report = report;
        this.actor = actor;
        this.canHandle = canHandle;
        this.backend = backend;
        this.scheduler = scheduler;
        this.names = names;
        this.onBack = onBack;
        this.onChanged = onChanged;

        MenuBuilder builder = MenuBuilder.list(MenuText.name("Meldung", Token.INFO))
                .header(summaryIcon())
                .back(onBack::accept)
                .close();
        layoutChatContext(builder);
        layoutActions(builder);
        this.menu = builder.build();
    }

    public Menu menu() {
        return menu;
    }

    private IconSpec summaryIcon() {
        Lore lore = Lore.builder()
                .describe("Grund: " + report.detail())
                .value("Kategorie:", ReportFormat.categoryLabel(report.category()))
                .value("Status:", ReportFormat.statusLabel(report.status()))
                .value("Melder:", names.apply(report.reporter()))
                .value("Gemeldet:", ReportFormat.time(report.createdAtEpochMilli()));
        return IconSpec.head(report.target(),
                MenuText.name(names.apply(report.target()), Token.ENTITY), lore.build());
    }

    private void layoutChatContext(MenuBuilder builder) {
        List<ChatMessage> context = report.chatContext();
        if (context == null || context.isEmpty()) {
            return;
        }
        int count = Math.min(context.size(), CHAT_SLOTS.length);
        for (int i = 0; i < count; i++) {
            ChatMessage message = context.get(i);
            IconSpec icon = IconSpec.head(message.sender(),
                    MenuText.name(names.apply(message.sender()), Token.BODY),
                    Lore.builder()
                            .describe(message.text())
                            .value("Zeit:", ReportFormat.time(message.timestampEpochMilli()))
                            .build());
            builder.item(CHAT_SLOTS[i], MenuItem.display(icon));
        }
    }

    private void layoutActions(MenuBuilder builder) {
        if (!canHandle) {
            return; // UI gate only; backend rejects with 403 if the gate is stale
        }
        List<ReportStatus> transitions = ReportStatus.transitionsFrom(report.status());
        for (int i = 0; i < transitions.size() && i < ACTION_SLOTS.length; i++) {
            builder.item(ACTION_SLOTS[i], actionButton(transitions.get(i)));
        }
    }

    private MenuItem actionButton(ReportStatus target) {
        IconSpec icon = IconSpec.of(target.icon(), MenuText.name(target.actionLabel(), target.token()),
                Lore.builder()
                        .describe("Setzt den Status auf \"" + target.label() + "\".")
                        .clickToOpen("bestätigen")
                        .build());
        return MenuItem.button(icon, ctx -> openConfirm(ctx, target));
    }

    void openConfirm(ClickContext ctx, ReportStatus target) {
        IconSpec object = IconSpec.head(report.target(),
                MenuText.name(names.apply(report.target()) + " → " + target.label(), Token.ENTITY),
                Lore.builder().describe("Status der Meldung ändern.").build());
        Menu confirm = ConfirmDialog.of(MenuText.name("Status setzen?", Token.INFO), object)
                .confirmName(MenuText.name(target.actionLabel(), target.token()))
                .onConfirm(c -> changeStatus(c, target))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    void changeStatus(ClickContext ctx, ReportStatus target) {
        scheduler.runAsync(() -> {
            ChangeStatusRequest request = new ChangeStatusRequest(target.wire(), actor);
            backend.call(ReportEndpoints.CHANGE_STATUS, request, report.id().toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            ctx.view().feedback(Feedback.DENY);
                            ctx.view().send(MenuMessage.actionBar(ReportFormat.errorText(error), Token.NEGATIVE));
                            ctx.view().open(menu); // back to the detail to retry
                            return;
                        }
                        ctx.view().feedback(Feedback.SUCCESS);
                        ctx.view().send(MenuMessage.chat(
                                "Status gesetzt: " + target.label() + ".", Token.POSITIVE));
                        // Apply the backend's returned state to the inbox optimistically, then navigate
                        // back — so the change shows immediately, independent of live-event timing.
                        onChanged.accept(response, ctx);
                    }));
        });
    }
}
