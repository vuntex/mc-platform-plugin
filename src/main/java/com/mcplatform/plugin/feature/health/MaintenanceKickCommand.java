package com.mcplatform.plugin.feature.health;

import com.mcplatform.plugin.feature.permission.PermissionGate;
import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ConfirmDialog;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.plugin.platform.text.Messages;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /maintenancekick} — opens a critical confirm dialog and, on confirm, kicks every non-bypass
 * player. Meant to be invoked from the clickable staff alert during a backend outage (clean "close the
 * server" so no one keeps playing against a dead source of truth).
 *
 * <p>Only usable WHILE maintenance is active: the lock state is checked both on command execution and
 * again on the confirm click (the backend may have recovered in between → the kick is aborted). Gated by
 * the warm permission cache ({@link PermissionGate}), so it works even while the backend is down; the
 * same {@code bypass} node both authorizes the command and spares its holders from the kick.
 */
public final class MaintenanceKickCommand implements CommandExecutor {

    private final MenuManager menus;
    private final BackendHealthMonitor monitor;
    private final PermissionGate gate;
    private final String bypassNode;

    public MaintenanceKickCommand(MenuManager menus, BackendHealthMonitor monitor,
                                  PermissionGate gate, String bypassNode) {
        this.menus = menus;
        this.monitor = monitor;
        this.gate = gate;
        this.bypassNode = bypassNode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.playersOnly());
            return true;
        }
        if (gate == null || !gate.has(player.getUniqueId(), bypassNode)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }
        if (!monitor.isLocked()) {
            player.sendMessage(ChatDesign.error(
                    "Kein Wartungsmodus aktiv — der Befehl ist nur bei einem Backend-Ausfall verfügbar."));
            return true;
        }

        IconSpec object = IconSpec.of(Icon.DANGER,
                MenuText.name("Alle Spieler kicken", Token.DANGER),
                Lore.builder()
                        .describe("Trennt sofort alle Spieler ohne Bypass-Recht.")
                        .describe("Nur während eines Backend-Ausfalls.")
                        .build());
        Menu confirm = ConfirmDialog.of(MenuText.name("Alle Spieler kicken?", Token.DANGER), object)
                .confirmName(MenuText.name("Alle kicken", Token.DANGER))
                .confirmDescription(Lore.builder()
                        .describe("Alle Spieler ohne Bypass-Recht werden gekickt.")
                        .build())
                .critical() // irreversible → double-click
                .onConfirm(this::kickAll)
                .build();
        menus.open(player, confirm);
        return true;
    }

    private void kickAll(ClickContext ctx) {
        Player admin = Bukkit.getPlayer(ctx.playerId());
        // Re-check on confirm: the backend may have recovered between opening the dialog and clicking.
        if (!monitor.isLocked()) {
            ctx.view().close();
            if (admin != null) {
                admin.sendMessage(Component.text(
                        "Backend ist wieder erreichbar — Kick abgebrochen.", NamedTextColor.GREEN));
            }
            return;
        }

        Component reason = Component.text(
                "Server-Wartung — bitte in Kürze erneut verbinden.", NamedTextColor.YELLOW);
        int kicked = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (gate != null && gate.has(target.getUniqueId(), bypassNode)) {
                continue; // keep staff online
            }
            target.kick(reason);
            kicked++;
        }
        ctx.view().close();
        if (admin != null) {
            // Compose colored segments instead of § codes: parent is green, the count overrides to gray (§7).
            admin.sendMessage(Component.text()
                    .color(NamedTextColor.GREEN)
                    .append(Component.text("Es wurden "))
                    .append(Component.text(kicked, NamedTextColor.GRAY))
                    .append(Component.text(" Spieler gekickt."))
                    .build());
        }
    }
}
