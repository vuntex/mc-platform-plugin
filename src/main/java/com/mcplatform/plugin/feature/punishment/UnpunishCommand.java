package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.RevokeRequest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /unpunish <id> [grund]} — revoke a punishment before its natural expiry. The path takes the
 * punishment id directly, so no player lookup is needed. Optimistic Bukkit-permission gate; the backend
 * is authoritative ({@code 403} shown cleanly). Stable {@code transactionId} → {@code callIdempotent}.
 */
public final class UnpunishCommand implements CommandExecutor {

    static final String PERMISSION = "mcplatform.punish.revoke";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;

    public UnpunishCommand(BackendClient backend, PlatformScheduler scheduler) {
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
            sender.sendMessage("§7Usage: /unpunish <id> [grund]");
            return true;
        }

        UUID id;
        try {
            id = UUID.fromString(args[0]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cUngültige Strafen-Id: §f" + args[0]);
            return true;
        }

        String reason = PunishmentCommandSupport.joinReason(args, 1);
        UUID revokedBy = PunishmentCommandSupport.issuedBy(sender);

        scheduler.runAsync(() -> {
            RevokeRequest request = new RevokeRequest(revokedBy, reason, UUID.randomUUID(), "plugin");
            backend.callIdempotent(PunishmentEndpoints.REVOKE, request, id.toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            sender.sendMessage(PunishmentFormat.backendError(error));
                            return;
                        }
                        sender.sendMessage("§aStrafe §f" + id + " §aaufgehoben.");
                    }));
        });
        return true;
    }
}
