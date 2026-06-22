package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.report.ReportChangedEventCodec;
import com.mcplatform.protocol.report.ReportChannels;

/**
 * Reports as the third real {@link PluginFeature} — built entirely on the SAME generic transport,
 * scheduler, registry and menu framework as economy/punishment, with NO change to any of them. Local
 * state is feature-local only: a global {@link ChatRingBuffer} (RAM) and per-inbox read models; the
 * backend is the source of truth.
 *
 * <p>{@link #onEnable} is the single place reports touch the platform:
 * <ul>
 *   <li>Create flow: a public-chat ring listener, a chat-input reason listener, a quit cleanup, and
 *       the open-to-all {@code /report} command (category menu → CREATE).</li>
 *   <li>Inbox: the team {@code /reports} command (LIVE, paginated, status transitions via CHANGE_STATUS).</li>
 *   <li>Live: subscribe {@code mc:report:changed} → ping online team on CREATED + nudge open inboxes.</li>
 * </ul>
 */
public final class ReportFeature implements PluginFeature {

    private final MenuManager menus;

    /** The shared menu manager is injected by the composition root — no generic class is touched. */
    public ReportFeature(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public String id() {
        return "report";
    }

    @Override
    public void onEnable(FeatureContext context) {
        BackendClient backend = context.backend();
        PlatformScheduler scheduler = context.scheduler();

        // ── Create flow (open to all) ──────────────────────────────────────────────────────────────
        ChatRingBuffer ring = new ChatRingBuffer();
        ReportReasonPrompt prompt = new ReportReasonPrompt();
        ReportCooldown cooldown = new ReportCooldown();
        context.registerListener(new PublicChatListener(ring));
        context.registerListener(new ReportChatInputListener(prompt, ring, cooldown, backend, scheduler));
        context.registerListener(new ReportSession(prompt));
        context.registerCommand("report", new ReportCommand(menus, prompt, cooldown));

        // ── Team inbox (UI-gated; backend authoritative) ────────────────────────────────────────────
        context.registerCommand("reports", new ReportInboxCommand(menus, backend, scheduler));

        // ── Live notification + inbox refresh ───────────────────────────────────────────────────────
        ReportNotifier notifier = new ReportNotifier(scheduler);
        ReportLiveUpdater updater = new ReportLiveUpdater(
                notifier::ping,
                () -> menus.liveBus().notifyChange(ReportInboxMenu.INBOX_TOPIC));
        context.eventBus().subscribe(ReportChannels.CHANGED, ReportChangedEventCodec.INSTANCE, updater);
    }
}
