package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.text.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /rank toDisplayIcon} — a read-only tool that turns the item in the player's hand into the opaque
 * {@code display_icon} string and shows it as a click-to-copy chat component, ready to paste into the
 * web interface. No backend call, no write path; the optimistic gate is pure UI comfort here.
 */
public final class RankCommand implements CommandExecutor {

    private final PermissionGate gate;
    private final IconExtractor extractor = new IconExtractor();

    public RankCommand(PermissionGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur für Spieler.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("toDisplayIcon")) {
            player.sendMessage(Component.text("Nutzung: /rank toDisplayIcon", NamedTextColor.GRAY));
            return true;
        }
        if (!gate.has(player.getUniqueId(), PermissionNodes.ROLES_MANAGE)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(Component.text("Nimm das gewünschte Item in die Hand.", NamedTextColor.GRAY));
            return true;
        }

        String displayIcon = extractor.toDisplayIcon(hand);
        Component value = Component.text(displayIcon, NamedTextColor.GOLD)
                .clickEvent(ClickEvent.copyToClipboard(displayIcon))
                .hoverEvent(HoverEvent.showText(Component.text("Klicke, zum Kopieren.", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("display_icon: ", NamedTextColor.GRAY).append(value));
        return true;
    }
}
