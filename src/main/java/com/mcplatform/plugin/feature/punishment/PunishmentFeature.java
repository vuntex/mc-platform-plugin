package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.punishment.PunishmentChangedEventCodec;
import com.mcplatform.protocol.punishment.PunishmentChannels;

import java.util.UUID;

/**
 * Punishments as the second real {@link PluginFeature} — built entirely on the SAME generic transport,
 * cache, scheduler and registry as economy, with NO change to any of them. The local state is a plain
 * {@link FeatureCache}{@code <UUID, PunishmentSnapshot>} using the identical version-aware {@code put}.
 *
 * <p>{@link #onEnable} is the single place punishments touch the platform:
 * <ul>
 *   <li>Subscribe {@code mc:punishment:changed} → version-checked cache apply + kick-on-new-ban
 *       (Live-Revoke / live-kick), delivered on the main thread.</li>
 *   <li>Async pre-login gate (fetch active, disallow bans, warm cache).</li>
 *   <li>Chat mute from the cached snapshot.</li>
 *   <li>Quit eviction (analog economy).</li>
 *   <li>Team commands: /punish, /unpunish, /history, /warn, /tempban, /chatban, /permaban.</li>
 * </ul>
 */
public final class PunishmentFeature implements PluginFeature {

    private final FeatureCache<UUID, PunishmentSnapshot> active = new FeatureCache<>();
    private final MenuManager menus;

    /** The shared menu manager is injected by the composition root — no generic class is touched. */
    public PunishmentFeature(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public String id() {
        return "punishment";
    }

    @Override
    public void onEnable(FeatureContext context) {
        // Live updates: issue/revoke from mc:punishment:changed (version-checked apply + live kick).
        context.eventBus().subscribe(PunishmentChannels.CHANGED, PunishmentChangedEventCodec.INSTANCE,
                new PunishmentLiveUpdater(active));

        // Enforcement listeners.
        context.registerListener(new PunishmentLoginListener(context.backend(), active, context.logger()));
        context.registerListener(new PunishmentChatListener(active));
        context.registerListener(new PunishmentQuitListener(active));

        // Team commands (optimistic Bukkit-permission UI-gate; backend is authoritative via 403).
        BackendClient backend = context.backend();
        PlatformScheduler scheduler = context.scheduler();
        context.registerCommand("punish", new PunishCommand(backend, scheduler));
        context.registerCommand("unpunish", new UnpunishCommand(backend, scheduler));
        context.registerCommand("history", new HistoryCommand(backend, scheduler));
        context.registerCommand("punishments", new PunishmentMenuCommand(backend, scheduler, menus));
        context.registerCommand("punishmenu", new PunishMenuCommand(backend, scheduler, menus));
        context.registerCommand("warn",
                new IssuePunishmentCommand(backend, scheduler, PunishmentType.WARN, false, "mcplatform.punish.warn"));
        context.registerCommand("tempban",
                new IssuePunishmentCommand(backend, scheduler, PunishmentType.TEMPBAN, true, "mcplatform.punish.tempban"));
        context.registerCommand("chatban",
                new IssuePunishmentCommand(backend, scheduler, PunishmentType.CHATBAN, true, "mcplatform.punish.chatban"));
        context.registerCommand("permaban",
                new IssuePunishmentCommand(backend, scheduler, PunishmentType.PERMABAN, false, "mcplatform.punish.permaban"));
    }
}
