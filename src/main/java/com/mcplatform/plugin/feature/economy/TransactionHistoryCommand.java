package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.transport.BackendClient;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /transactions [Spieler]} — opens the paginated {@link TransactionHistoryMenu} (the economy
 * audit trail). Without an argument it shows the sender's own history; with a name it shows that
 * player's history (online or offline-cached), gated behind {@link #PERMISSION_OTHERS} — viewing other
 * players' money movements is more sensitive than reading a balance. The menu opens immediately in a
 * "Lade…" state and fills once the backend read returns; the main thread is never blocked.
 */
public final class TransactionHistoryCommand implements CommandExecutor {

    /** Optimistic UI gate for viewing OTHER players' history; own history needs no permission. */
    static final String PERMISSION_OTHERS = "mcplatform.economy.history.others";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String currency;
    private final MenuManager menus;

    public TransactionHistoryCommand(BackendClient backend, PlatformScheduler scheduler,
                                     String currency, MenuManager menus) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.currency = currency;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können das Menü öffnen.");
            return true;
        }

        if (args.length == 0) {
            open(player, player.getUniqueId(), player.getName());
            return true;
        }

        if (!player.hasPermission(PERMISSION_OTHERS)) {
            player.sendMessage("§cKeine Berechtigung, fremde Kontobewegungen zu sehen.");
            return true;
        }
        // Offline-capable resolution without a blocking Mojang lookup: online first, else server cache.
        Player online = Bukkit.getPlayerExact(args[0]);
        OfflinePlayer resolved = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (resolved == null || resolved.getUniqueId() == null) {
            player.sendMessage("§cUnbekannter Spieler: " + args[0]);
            return true;
        }
        String name = resolved.getName() != null ? resolved.getName() : args[0];
        open(player, resolved.getUniqueId(), name);
        return true;
    }

    private void open(Player viewer, UUID target, String name) {
        TransactionHistoryMenu history =
                new TransactionHistoryMenu(target, name, currency, backend, scheduler);
        MenuView view = menus.open(viewer, history.menu());
        history.load(view);
    }
}
