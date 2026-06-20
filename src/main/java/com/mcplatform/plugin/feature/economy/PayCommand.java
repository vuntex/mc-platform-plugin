package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.PlayerPickerMenu;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /pay} — player-side transfer flow, entirely menu-driven: pick a recipient from the online
 * players (shared {@link PlayerPickerMenu}, STATIC with a refresh header), then choose the amount in the
 * {@link TransferMenu} value editor, confirm, and the existing {@code TRANSFER} endpoint runs. Everyone
 * may use it (no permission); the backend stays authoritative on funds (422) and validity (400).
 */
public final class PayCommand implements CommandExecutor {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String currency;
    private final MenuManager menus;

    public PayCommand(BackendClient backend, PlatformScheduler scheduler, String currency, MenuManager menus) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.currency = currency;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can pay.");
            return true;
        }
        openPicker(player);
        return true;
    }

    private void openPicker(Player player) {
        List<PlayerPickerMenu.Entry> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) { // can't pay yourself
                candidates.add(new PlayerPickerMenu.Entry(online.getUniqueId(), online.getName()));
            }
        }
        PlayerPickerMenu picker = new PlayerPickerMenu(
                MenuText.name("Coins senden an", Token.ENTITY),
                candidates,
                (ctx, entry) -> ctx.view().open(
                        new TransferMenu(entry, currency, backend, scheduler, back -> openPicker(player)).menu()),
                ctx -> openPicker(player)); // refresh re-gathers the online players
        menus.open(player, picker.menu());
    }
}
