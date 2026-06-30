package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /ranks} — opens the STATIC role-management list. Gated optimistically by the cache-based
 * {@link PermissionGate} on {@code roles.manage} (cold cache → neutral allow); the backend stays the
 * authority on every write.
 */
public final class RanksCommand implements CommandExecutor {

    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final IconResolver iconResolver;
    private final PermissionInput input;

    public RanksCommand(MenuManager menus, BackendClient backend, PlatformScheduler scheduler,
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
        if (!gate.has(player.getUniqueId(), PermissionNodes.ROLES_MANAGE)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }
        RoleListMenu list = new RoleListMenu(player.getUniqueId(), menus, backend, scheduler, gate,
                iconResolver::resolve, input);
        MenuView view = menus.open(player, list.menu());
        list.load(view);
        return true;
    }
}
