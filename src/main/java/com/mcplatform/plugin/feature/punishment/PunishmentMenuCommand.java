package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /punishments <player>} — opens the paginated {@link PunishmentHistoryMenu} (MENU_DESIGN demo 2).
 * Optimistic UI gate (the {@code history} permission to open); the backend stays authoritative on the
 * revoke action inside the menu (a 403 is shown cleanly there). The menu opens immediately in a "Lade…"
 * state and fills once {@code LIST_ACTIVE} returns — the main thread is never blocked.
 */
public final class PunishmentMenuCommand implements CommandExecutor {

    static final String PERMISSION = "mcplatform.punish.history";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final MenuManager menus;

    public PunishmentMenuCommand(BackendClient backend, PlatformScheduler scheduler, MenuManager menus) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können das Menü öffnen.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§7Usage: /punishments <player>");
            return true;
        }

        String targetName = args[0];
        UUID online = PunishmentCommandSupport.resolveOnlineUuid(targetName); // main thread

        scheduler.runAsync(() -> {
            UUID target = PunishmentCommandSupport.resolveUuid(targetName, online);
            scheduler.runSync(() -> {
                // Open the menu now (loading), then fill it once the list arrives.
                PunishmentHistoryMenu historyMenu =
                        new PunishmentHistoryMenu(target, targetName, backend, scheduler);
                MenuView view = menus.open(player, historyMenu.menu());
                loadInto(historyMenu, view, target);
            });
        });
        return true;
    }

    private void loadInto(PunishmentHistoryMenu historyMenu, MenuView view, UUID target) {
        backend.call(PunishmentEndpoints.LIST_ACTIVE, null, target.toString())
                .whenComplete((list, error) -> scheduler.runSync(() -> {
                    PunishmentResponse[] entries = list == null ? new PunishmentResponse[0] : list;
                    historyMenu.setEntries(Arrays.asList(entries));
                    historyMenu.render(view);
                }));
    }
}
