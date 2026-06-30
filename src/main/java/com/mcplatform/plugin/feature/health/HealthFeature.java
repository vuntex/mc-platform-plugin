package com.mcplatform.plugin.feature.health;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.feature.permission.PermissionFeature;
import com.mcplatform.plugin.feature.permission.PermissionGate;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.health.HealthEndpoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Periodic backend health check + maintenance lockdown. Every {@code intervalSeconds} it polls
 * {@code GET /api/health} (non-blocking); after {@code failureThreshold} consecutive failures (404,
 * transport error, or {@code status != "UP"}) it LOCKS the server — non-bypass players are fully frozen
 * (no move/look, no damage, no build/interact/commands/chat — see {@link MaintenanceListener}) — and
 * unlocks on the first healthy probe. Keeps in-world state from drifting while the source of truth is gone.
 *
 * <p>The maintenance title is kept PERMANENT by re-showing it every probe tick while locked (its stay is
 * a little longer than the interval, with no fade, so there is no gap or flicker), plus immediately on
 * lock and to anyone joining mid-lockdown.
 *
 * <p>{@link #onEnable} is the single place it touches the platform. Staff bypass is read from the warm
 * permission cache via {@link PermissionFeature#gate()} (works with the backend down).
 */
public final class HealthFeature implements PluginFeature {

    /** Players with this node are exempt from the lockdown (can investigate while it's active). */
    public static final String BYPASS_NODE = "mcplatform.maintenance.bypass";

    private static final Component TITLE = Component.text("⚠ Wartung", NamedTextColor.RED);
    private static final Component SUBTITLE = Component.text("Bitte kurz warten …", NamedTextColor.GRAY);
    private static final Component LOCK_MESSAGE =
            Component.text("Verbindung zum Server-Backend verloren — Wartungsmodus aktiv.", NamedTextColor.RED);
    private static final Component UNLOCK_MESSAGE =
            Component.text("Backend wieder erreichbar — du kannst weiterspielen.", NamedTextColor.GREEN);

    // Staff/admins (bypass) are NOT frozen, but MUST be actively told the backend is timing out — and WHAT
    // is down (db/redis) — so they can investigate. Chat alert on the lock/unlock edges + a persistent
    // action bar (with the reason) while it lasts. The reason is dynamic, so these are built per-send.
    private static final Component STAFF_UNLOCK_MESSAGE = Component.text(
            "✔ Backend wieder erreichbar — Wartungsmodus beendet.", NamedTextColor.GREEN);

    private final MenuManager menus;
    private final PermissionFeature permission;
    private final int intervalSeconds;
    private final int failureThreshold;
    private final long recommendKickAfterMillis;
    private final Title lockTitle;

    private BackendHealthMonitor monitor;
    private PermissionGate gate;
    private AutoCloseable timer;
    /** Latest human-readable reason for the outage, for the staff alert (e.g. "Redis nicht erreichbar"). */
    private volatile String lastDownReason = "Backend nicht erreichbar";
    /** When the current lockdown started, to delay the kick recommendation; 0 when not locked. */
    private long lockedSinceMillis = 0L;
    /** Whether the (delayed) kick recommendation was already sent for the current lockdown. */
    private boolean recommendationSent = false;

    public HealthFeature(MenuManager menus, PermissionFeature permission,
                         int intervalSeconds, int failureThreshold, int recommendKickAfterSeconds) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recommendKickAfterMillis = Math.max(0, recommendKickAfterSeconds) * 1000L;
        // No fade, and a stay longer than the refresh interval → re-showing each tick is seamless and the
        // title never disappears while locked.
        Title.Times times = Title.Times.times(
                Duration.ZERO, Duration.ofSeconds(this.intervalSeconds + 2L), Duration.ZERO);
        this.lockTitle = Title.title(TITLE, SUBTITLE, times);
    }

    @Override
    public String id() {
        return "health";
    }

    @Override
    public void onEnable(FeatureContext context) {
        this.monitor = new BackendHealthMonitor(failureThreshold);
        this.gate = permission.gate(); // warm-cache gate; may be null if permission failed to enable

        context.registerListener(new MaintenanceListener(monitor, gate, BYPASS_NODE, this::onLockedJoin));
        context.registerCommand("maintenancekick",
                new MaintenanceKickCommand(menus, monitor, gate, BYPASS_NODE));

        BackendClient backend = context.backend();
        PlatformScheduler scheduler = context.scheduler();
        Logger log = context.logger();

        long periodTicks = intervalSeconds * 20L;
        this.timer = scheduler.runSyncTimer(() -> probe(backend, scheduler, log), periodTicks, periodTicks);
        log.info("Backend health check active: every " + intervalSeconds + "s, lock after "
                + failureThreshold + " consecutive failures.");
    }

    private void probe(BackendClient backend, PlatformScheduler scheduler, Logger log) {
        backend.call(HealthEndpoints.CHECK, null).whenComplete((response, error) -> scheduler.runSync(() -> {
            boolean healthy;
            if (error != null || response == null) {
                healthy = false;
                lastDownReason = "Backend nicht erreichbar"; // process gone / 404 / transport — no detail
            } else {
                healthy = "UP".equalsIgnoreCase(response.status());
                if (!healthy) {
                    lastDownReason = describe(response.components()); // e.g. "Redis nicht erreichbar"
                }
            }
            BackendHealthMonitor.Transition transition =
                    healthy ? monitor.recordSuccess() : monitor.recordFailure();
            switch (transition) {
                case LOCKED -> {
                    log.severe("Backend unhealthy (" + lastDownReason
                            + ") — maintenance lockdown ENGAGED (players frozen).");
                    lockedSinceMillis = System.currentTimeMillis();
                    recommendationSent = false;
                    forAffected(player -> {
                        player.sendMessage(LOCK_MESSAGE);
                        player.showTitle(lockTitle);
                        freeze(player);
                    });
                    forBypass(this::staffAlert); // admins are told actively (with the reason), not frozen
                }
                case UNLOCKED -> {
                    log.info("Backend healthy again — maintenance lockdown LIFTED.");
                    lockedSinceMillis = 0L;
                    recommendationSent = false;
                    forAffected(player -> {
                        player.sendMessage(UNLOCK_MESSAGE);
                        player.clearTitle();
                        unfreeze(player);
                    });
                    forBypass(player -> {
                        player.sendMessage(STAFF_UNLOCK_MESSAGE);
                        player.sendActionBar(Component.empty());
                    });
                }
                case NONE -> {
                    // Steady state while locked: keep the title + freeze for users permanent, and keep the
                    // staff action bar visible (re-sent each tick so it doesn't fade out).
                    if (monitor.isLocked()) {
                        forAffected(player -> {
                            player.showTitle(lockTitle);
                            freeze(player);
                        });
                        forBypass(player -> player.sendActionBar(staffActionBar()));
                        // After a sustained outage, recommend kicking everyone (once, to all staff).
                        if (!recommendationSent
                                && System.currentTimeMillis() - lockedSinceMillis >= recommendKickAfterMillis) {
                            recommendationSent = true;
                            forBypass(this::sendKickRecommendation);
                        }
                    }
                }
            }
        }));
    }

    /** A player joined while locked: route bypass-staff (alert) vs frozen players (lock UI). */
    private void onLockedJoin(Player player) {
        if (gate != null && gate.has(player.getUniqueId(), BYPASS_NODE)) {
            staffAlert(player);
            if (recommendationSent) {
                sendKickRecommendation(player); // outage already sustained → show the recommendation too
            }
        } else {
            showLockUi(player);
        }
    }

    /** Show the maintenance title + message to one player and freeze them (frozen players). */
    private void showLockUi(Player player) {
        player.sendMessage(LOCK_MESSAGE);
        player.showTitle(lockTitle);
        freeze(player);
    }

    /** Actively alert a bypass admin (chat + action bar) with WHAT is down — not frozen, just informed. */
    private void staffAlert(Player player) {
        player.sendMessage(Component.text(
                "⚠ Wartungsmodus aktiv — " + lastDownReason + ". (Du bist ausgenommen.)", NamedTextColor.RED));
        player.sendActionBar(staffActionBar());
    }

    /** The clickable kick recommendation — sent only after a sustained outage (not immediately). */
    private void sendKickRecommendation(Player player) {
        player.sendMessage(Component.text("» Empfehlung: alle Spieler kicken «", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/maintenancekick"))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Klicken — öffnet eine Bestätigung", NamedTextColor.GRAY))));
    }

    private Component staffActionBar() {
        return Component.text("⚠ " + lastDownReason, NamedTextColor.RED);
    }

    /** Human-readable outage reason from the per-component map, e.g. "Redis nicht erreichbar". */
    private static String describe(java.util.Map<String, String> components) {
        if (components == null || components.isEmpty()) {
            return "Backend nicht erreichbar";
        }
        java.util.List<String> down = new java.util.ArrayList<>();
        components.forEach((key, value) -> {
            if (!"UP".equalsIgnoreCase(value)) {
                down.add(label(key));
            }
        });
        return down.isEmpty() ? "Backend nicht erreichbar" : String.join(", ", down) + " nicht erreichbar";
    }

    private static String label(String componentKey) {
        return switch (componentKey.toLowerCase(java.util.Locale.ROOT)) {
            case "db", "database" -> "Datenbank";
            case "redis" -> "Redis";
            default -> componentKey;
        };
    }

    /**
     * Hold a frozen player in the air without the server's flight check kicking them
     * ("Flying is not enabled on this server"): allowing flight disables that check, and flying keeps
     * them hovering instead of fighting gravity against the cancelled move events.
     */
    private void freeze(Player player) {
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        if (!player.isFlying()) {
            player.setFlying(true);
        }
    }

    /** Restore normal flight on unlock — but never clobber creative/spectator, which manage their own. */
    private void unfreeze(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Run an action for every online non-bypass player (the ones actually affected by the lockdown). */
    private void forAffected(Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gate != null && gate.has(player.getUniqueId(), BYPASS_NODE)) {
                continue;
            }
            action.accept(player);
        }
    }

    /** Run an action for every online bypass player (staff/admins to be actively informed). */
    private void forBypass(Consumer<Player> action) {
        if (gate == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gate.has(player.getUniqueId(), BYPASS_NODE)) {
                action.accept(player);
            }
        }
    }

    @Override
    public void onDisable() {
        if (timer != null) {
            try {
                timer.close();
            } catch (Exception ignored) {
                // best-effort cancel
            }
            timer = null;
        }
    }
}
