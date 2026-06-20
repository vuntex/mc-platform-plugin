package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.IssueRequest;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The convenience direct-issue commands — one configurable executor reused for {@code /warn},
 * {@code /tempban}, {@code /chatban}, {@code /permaban}. Each instance is bound to a punishment type,
 * whether a duration is required, and the optimistic Bukkit permission for the UI-gate.
 *
 * <p>UI-gate is optimistic (a missing Bukkit permission rejects locally before any call); the REAL
 * authority is the backend, whose {@code 403} is surfaced cleanly. The write carries a fresh, stable
 * {@code transactionId} so {@link BackendClient#callIdempotent} can retry it safely.
 */
public final class IssuePunishmentCommand implements CommandExecutor {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String type;
    private final boolean needsDuration;
    private final String permission;

    public IssuePunishmentCommand(BackendClient backend, PlatformScheduler scheduler,
                                  String type, boolean needsDuration, String permission) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.type = type;
        this.needsDuration = needsDuration;
        this.permission = permission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        int minArgs = needsDuration ? 2 : 1;
        if (args.length < minArgs) {
            sender.sendMessage("§7Usage: /" + label
                    + (needsDuration ? " <player> <duration> [grund]" : " <player> [grund]"));
            return true;
        }

        String targetName = args[0];
        Long durationMillis = null;
        int reasonStart = 1;
        if (needsDuration) {
            try {
                durationMillis = PunishmentFormat.parseDuration(args[1]);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("§cUngültige Dauer: §f" + args[1] + " §7(z.B. 2h, 7d, 1d12h)");
                return true;
            }
            reasonStart = 2;
        }

        String reason = PunishmentCommandSupport.joinReason(args, reasonStart);
        if (reason == null) {
            reason = "Kein Grund angegeben";
        }
        UUID issuedBy = PunishmentCommandSupport.issuedBy(sender);
        UUID online = PunishmentCommandSupport.resolveOnlineUuid(targetName); // main thread

        Long duration = durationMillis;
        String finalReason = reason;
        scheduler.runAsync(() -> {
            UUID target = PunishmentCommandSupport.resolveUuid(targetName, online);
            IssueRequest request =
                    new IssueRequest(type, finalReason, duration, issuedBy, UUID.randomUUID(), "plugin");
            backend.callIdempotent(PunishmentEndpoints.ISSUE, request, target.toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            sender.sendMessage(PunishmentFormat.backendError(error));
                            return;
                        }
                        sender.sendMessage("§a" + type + " gesetzt für §f" + targetName
                                + " §7(id " + response.id() + ")");
                    }));
        });
        return true;
    }
}
