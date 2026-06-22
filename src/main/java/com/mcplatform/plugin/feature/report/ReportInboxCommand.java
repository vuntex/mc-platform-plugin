package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.transport.BackendClient;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /reports} — opens the team inbox. UI-gated by {@code mcplatform.report.view} in plugin.yml
 * (comfort only); the backend re-checks {@code report.view}/{@code report.handle} and rejects with 403.
 * The handle permission is resolved once here and passed to the menu so the detail view shows the
 * status-transition buttons only to those who may handle.
 */
public final class ReportInboxCommand implements CommandExecutor {

    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;

    public ReportInboxCommand(MenuManager menus, BackendClient backend, PlatformScheduler scheduler) {
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        boolean canHandle = viewer.hasPermission("mcplatform.report.handle");
        ReportInboxMenu inbox = new ReportInboxMenu(
                viewer.getUniqueId(), canHandle, backend, scheduler, ReportNames::of);
        MenuView view = menus.open(viewer, inbox.menu());
        inbox.load(view);
        return true;
    }
}
