package com.mcplatform.plugin.feature.health;

import com.mcplatform.plugin.feature.permission.PermissionGate;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Enforces the maintenance lockdown while the backend is unhealthy. For non-bypass players it:
 * <ul>
 *   <li>freezes movement COMPLETELY — not even looking around (any position OR rotation change is
 *       cancelled);</li>
 *   <li>prevents ALL damage (mobs, fall, fire, …) so a frozen player can't die while stuck;</li>
 *   <li>cancels block break/place, interaction, commands and chat;</li>
 *   <li>(re)shows the maintenance title to anyone who joins mid-lockdown.</li>
 * </ul>
 * Staff with the bypass node stay free (read from the warm permission cache, so it works with the
 * backend down). Without this freeze, players could keep acting/dying while the source of truth is gone.
 */
public final class MaintenanceListener implements Listener {

    private final BackendHealthMonitor monitor;
    private final PermissionGate gate;
    private final String bypassNode;
    private final Consumer<Player> onLockedJoin;

    public MaintenanceListener(BackendHealthMonitor monitor, PermissionGate gate, String bypassNode,
                               Consumer<Player> onLockedJoin) {
        this.monitor = monitor;
        this.gate = gate;
        this.bypassNode = bypassNode;
        this.onLockedJoin = onLockedJoin;
    }

    /** Locked AND this player may not bypass. Cheap early-out when not locked. */
    private boolean blocked(Player player) {
        if (!monitor.isLocked()) {
            return false;
        }
        UUID id = player.getUniqueId();
        return gate == null || !gate.has(id, bypassNode);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!monitor.isLocked()) {
            return;
        }
        // Full freeze: cancel ANY change — position AND rotation (Location#equals covers x/y/z/yaw/pitch),
        // so the player can't walk and can't even turn the camera.
        if (event.getFrom().equals(event.getTo())) {
            return;
        }
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true); // no damage of any cause while frozen
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true); // no knockback/push (explosions, pistons, water, …) while frozen
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Wartungsmodus: Befehle sind aktuell deaktiviert.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Wartungsmodus: Der Chat ist aktuell deaktiviert.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (monitor.isLocked()) {
            // Notify any mid-lockdown joiner; HealthFeature routes bypass-staff vs frozen players.
            onLockedJoin.accept(event.getPlayer());
        }
    }
}
