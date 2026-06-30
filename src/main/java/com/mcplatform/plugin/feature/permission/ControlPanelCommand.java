package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /cp <Spieler>} — opens the control panel for a player, online or offline. The name is resolved
 * to a UUID on the main thread for online players; otherwise the possibly-blocking offline lookup runs
 * off the main thread (mirrors the punishment pattern). An unresolvable name yields a message and no
 * menu. Gated optimistically on {@code grants.manage} (cold cache → neutral); the backend is the truth.
 */
public final class ControlPanelCommand implements CommandExecutor {

    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final IconResolver iconResolver;
    private final PermissionInput input;

    public ControlPanelCommand(MenuManager menus, BackendClient backend, PlatformScheduler scheduler,
                               PermissionGate gate, IconResolver iconResolver, PermissionInput input) {
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.iconResolver = iconResolver;
        this.input = input;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Nutzung: /cp <Spieler>", NamedTextColor.GRAY));
            return true;
        }
        if (!gate.has(player.getUniqueId(), PermissionNodes.GRANTS_MANAGE)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }

        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            openPanel(player, online.getUniqueId(), online.getName());
            return true;
        }
        // Offline: the lookup may hit the user cache / Mojang → run off the main thread.
        scheduler.runAsync(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            boolean known = offline.hasPlayedBefore() || offline.isOnline();
            UUID uuid = offline.getUniqueId();
            String resolvedName = offline.getName() != null ? offline.getName() : name;
            scheduler.runSync(() -> {
                if (!known || uuid == null) {
                    player.sendMessage(Messages.playerNotFound(name));
                    return;
                }
                openPanel(player, uuid, resolvedName);
            });
        });
        return true;
    }

    private void openPanel(Player viewer, UUID target, String targetName) {
        ControlPanelMenu panel = new ControlPanelMenu(target, targetName, viewer.getUniqueId(), menus,
                backend, scheduler, gate, iconResolver::resolve, input);
        menus.open(viewer, panel.menu());
    }
}
