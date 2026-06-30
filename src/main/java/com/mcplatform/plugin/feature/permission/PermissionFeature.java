package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionChangedEventCodec;
import com.mcplatform.protocol.permission.PermissionChannels;

import org.bukkit.Bukkit;

/**
 * Permission/Rank as the fourth real {@link PluginFeature}, built on the SAME generic transport,
 * scheduler, registry and menu framework as economy/punishment/report. Local state is feature-local
 * only: a version-aware {@link PermissionCache} of effective permissions + display per online player;
 * the backend is the source of truth.
 *
 * <p>{@link #onEnable} is the single place this feature touches the platform:
 * <ul>
 *   <li>Cache + live: join → load {@code /effective}, {@code mc:permission:changed} → reload only the
 *       affected online player, quit → evict.</li>
 *   <li>Icon tool: {@code /rank toDisplayIcon} (ItemStack → opaque string, click-to-copy).</li>
 *   <li>Role management: {@code /ranks} (STATIC role list + CRUD + role permissions).</li>
 *   <li>Player grants: {@code /cp <Spieler>} (STATIC grants for online and offline players).</li>
 * </ul>
 */
public final class PermissionFeature implements PluginFeature {

    private final MenuManager menus;
    private final PermissionCache cache = new PermissionCache();

    /** The shared menu manager is injected by the composition root — no generic class is touched. */
    public PermissionFeature(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public String id() {
        return "permission";
    }

    @Override
    public void onEnable(FeatureContext context) {
        BackendClient backend = context.backend();
        PlatformScheduler scheduler = context.scheduler();

        PermissionLoader loader = new PermissionLoader(backend, scheduler, cache, context.logger());

        // ── Cold start (US2): PreLogin warmup, fail-closed ───────────────────────────────────────────
        // Load effective permissions BEFORE the player enters the world and hard-kick on failure, on the
        // same availability-before-security line as the session gate. Guarantees an in-world player never
        // has a cold cache → the gate can strict-deny a cold cache instead of guessing.
        context.registerListener(new PermissionWarmupListener(backend, cache, context.logger()));
        context.registerListener(new PermissionQuitListener(cache)); // free the entry on quit

        // ── Runtime changes (US2): live push — independent of, and complementary to, the warmup ───────
        // Reload ONLY the affected, online player's effective view; offline → ignore. The EventDispatcher
        // delivers on the main thread, so the Bukkit online-check here is safe.
        PermissionLiveUpdater updater = new PermissionLiveUpdater(
                uuid -> Bukkit.getPlayer(uuid) != null, loader::reload);
        context.eventBus().subscribe(
                PermissionChannels.CHANGED, PermissionChangedEventCodec.INSTANCE, updater);

        PermissionGate gate = new PermissionGate(cache, context.logger());
        IconResolver iconResolver = new IconResolver();
        PermissionInput chatInput = new PermissionInput(scheduler);
        context.registerListener(chatInput);

        // ── Icon tool (US5): /rank toDisplayIcon ─────────────────────────────────────────────────────
        context.registerCommand("rank", new RankCommand(gate));

        // ── Role management (US1): /ranks ────────────────────────────────────────────────────────────
        context.registerCommand("ranks",
                new RanksCommand(menus, backend, scheduler, gate, iconResolver, chatInput));

        // ── Player grants (US1): /cp <Spieler> ───────────────────────────────────────────────────────
        context.registerCommand("cp",
                new ControlPanelCommand(menus, backend, scheduler, gate, iconResolver, chatInput));
    }
}
