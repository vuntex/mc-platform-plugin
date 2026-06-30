package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.PunishmentResponse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /history <player>} — show a player's punishments. The current {@code plugin-protocol} only
 * exposes {@code LIST_ACTIVE} (no full-history endpoint), so this lists the currently ACTIVE entries;
 * that is enough to confirm a just-issued ban shows up ("/history zeigt den Eintrag"). Read goes
 * cache-agnostic straight to the backend (the team member may query offline players too).
 */
public final class HistoryCommand implements CommandExecutor {

    static final String PERMISSION = "mcplatform.punish.history";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;

    public HistoryCommand(BackendClient backend, PlatformScheduler scheduler) {
        this.backend = backend;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            ActionBars.deny(sender, Messages.noPermission());
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§7Usage: /history <player>");
            return true;
        }

        String targetName = args[0];
        UUID online = PunishmentCommandSupport.resolveOnlineUuid(targetName); // main thread

        scheduler.runAsync(() -> {
            UUID target = PunishmentCommandSupport.resolveUuid(targetName, online);
            backend.call(PunishmentEndpoints.LIST_ACTIVE, null, target.toString())
                    .whenComplete((list, error) -> scheduler.runSync(() -> reply(sender, targetName, list, error)));
        });
        return true;
    }

    private void reply(CommandSender sender, String targetName, PunishmentResponse[] list, Throwable error) {
        if (error != null) {
            sender.sendMessage(PunishmentFormat.backendError(error));
            return;
        }
        PunishmentResponse[] entries = list == null ? new PunishmentResponse[0] : list;
        sender.sendMessage("§7Aktive Strafen für §f" + targetName + " §7(" + entries.length + "):");
        long now = System.currentTimeMillis();
        for (PunishmentResponse r : entries) {
            String when = r.expiresAtEpochMilli() == 0
                    ? "permanent"
                    : PunishmentFormat.formatDuration(r.expiresAtEpochMilli() - now) + " verbleibend";
            sender.sendMessage("§e" + r.id() + " §7" + r.type() + " - " + r.reason() + " §8(" + when + ")");
        }
        if (entries.length == 0) {
            sender.sendMessage("§7(keine)");
        }
    }
}
