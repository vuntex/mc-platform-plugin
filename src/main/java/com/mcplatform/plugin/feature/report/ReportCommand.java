package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.text.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /report <spieler>} — open to all (no permission gate). Resolves an <em>online</em> target,
 * then opens the category-selection menu; choosing a category continues into the chat-input reason flow.
 * Offline targets are treated as "unknown" (this slice is online-only). Self-reports are bounced early
 * for UX, but the backend remains authoritative (422).
 */
public final class ReportCommand implements CommandExecutor {

    private final MenuManager menus;
    private final ReportReasonPrompt prompt;
    private final ReportCooldown cooldown;

    public ReportCommand(MenuManager menus, ReportReasonPrompt prompt, ReportCooldown cooldown) {
        this.menus = menus;
        this.prompt = prompt;
        this.cooldown = cooldown;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (args.length != 1) {
            reporter.sendMessage(Messages.usage("/report <spieler>"));
            return true;
        }

        // Cooldown gate: refuse here, before the menu opens, instead of failing at CREATE.
        if (cooldown.isCoolingDown(reporter.getUniqueId())) {
            reporter.sendMessage(Component.text(ReportFormat.cooldownText(), NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            reporter.sendMessage(Messages.playerNotOnline(args[0]));
            return true;
        }
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(Component.text("Du kannst dich nicht selbst melden.", NamedTextColor.RED));
            return true;
        }

        ReportCategoryMenu menu = new ReportCategoryMenu(
                reporter.getUniqueId(), target.getUniqueId(), target.getName(), prompt);
        menus.open(reporter, menu.menu());
        return true;
    }
}
