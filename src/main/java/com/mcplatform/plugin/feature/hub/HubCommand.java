package com.mcplatform.plugin.feature.hub;

import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.text.Messages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /menu} — opens the {@link HubMenu}. The entries shown are gated by the player's optimistic
 * Bukkit permission ({@code mcplatform.punish} for the team tools); each entry simply launches the
 * relevant feature command, so the hub stays decoupled from the feature menus it points at. Every real
 * action behind those entries remains backend-checked.
 */
public final class HubCommand implements CommandExecutor {

    static final String PUNISH_PERMISSION = "mcplatform.punish";

    private final MenuManager menus;

    public HubCommand(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.playersOnly());
            return true;
        }
        boolean canPunish = player.hasPermission(PUNISH_PERMISSION);
        menus.open(player, HubMenu.build(canPunish,
                run("balance"),
                run("pay"),
                run("punishmenu")));
        return true;
    }

    /** An entry handler that closes the hub and runs a feature command for the clicking player. */
    private static com.mcplatform.plugin.platform.menu.ClickHandler run(String command) {
        return (ClickContext ctx) -> {
            Player player = Bukkit.getPlayer(ctx.playerId());
            if (player != null) {
                player.performCommand(command);
            }
        };
    }
}
