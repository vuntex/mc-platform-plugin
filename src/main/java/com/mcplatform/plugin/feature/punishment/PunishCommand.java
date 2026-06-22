package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.IssueFromTemplateRequest;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.TemplateResponse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /punish <player> [template] [grund]}.
 *
 * <ul>
 *   <li>Without a template it LISTS the templates — the UI-gate hides any whose
 *       {@code requiredPermission} the team member lacks (optimistic, Bukkit-permission based).</li>
 *   <li>With a template it issues from that template (the {@code grund} optionally overrides the
 *       template's default reason).</li>
 * </ul>
 *
 * The optimistic gate is convenience only; the backend is the real authority and a {@code 403} from it
 * is shown cleanly. The write carries a stable {@code transactionId} → {@code callIdempotent}.
 */
public final class PunishCommand implements CommandExecutor {

    static final String PERMISSION = "mcplatform.punish";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;

    public PunishCommand(BackendClient backend, PlatformScheduler scheduler) {
        this.backend = backend;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7Usage: /punish <player> [template] [grund]");
            return true;
        }

        String targetName = args[0];
        if (args.length == 1) {
            listTemplates(sender, targetName);
            return true;
        }

        String templateKey = args[1];
        String reason = PunishmentCommandSupport.joinReason(args, 2); // null → template default
        UUID issuedBy = PunishmentCommandSupport.issuedBy(sender);
        UUID online = PunishmentCommandSupport.resolveOnlineUuid(targetName); // main thread

        scheduler.runAsync(() -> {
            UUID target = PunishmentCommandSupport.resolveUuid(targetName, online);
            IssueFromTemplateRequest request =
                    new IssueFromTemplateRequest(templateKey, reason, issuedBy, UUID.randomUUID(), "plugin");
            backend.callIdempotent(PunishmentEndpoints.ISSUE_FROM_TEMPLATE, request, target.toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            sender.sendMessage(PunishmentFormat.backendError(error));
                            return;
                        }
                        sender.sendMessage("§aPunishment '" + templateKey + "' gesetzt für §f"
                                + targetName + " §7(id " + response.id() + ")");
                    }));
        });
        return true;
    }

    /** UI-gate: list only templates the member may apply (per the template's requiredPermission). */
    private void listTemplates(CommandSender sender, String targetName) {
        // The backend requires the acting staff UUID as a query param (LIST_TEMPLATES is a bodyless GET,
        // so it cannot ride in a request body like the issue/revoke writes do).
        Map<String, String> query = Map.of("staff", PunishmentCommandSupport.issuedBy(sender).toString());
        backend.call(PunishmentEndpoints.LIST_TEMPLATES, null, query)
                .whenComplete((templates, error) -> scheduler.runSync(() -> {
                    if (error != null || templates == null) {
                        sender.sendMessage(PunishmentFormat.backendError(error));
                        return;
                    }
                    sender.sendMessage("§7Templates für §f" + targetName + "§7:");
                    boolean anyShown = false;
                    for (TemplateResponse template : templates) {
                        if (!template.active() || !mayApply(sender, template)) {
                            continue;
                        }
                        anyShown = true;
                        sender.sendMessage("§e" + template.key() + " §7- " + template.type()
                                + " - " + template.defaultReason());
                    }
                    if (!anyShown) {
                        sender.sendMessage("§7(keine anwendbaren Templates)");
                    }
                }));
    }

    private static boolean mayApply(CommandSender sender, TemplateResponse template) {
        String required = template.requiredPermission();
        return required == null || required.isBlank() || sender.hasPermission(required);
    }
}
