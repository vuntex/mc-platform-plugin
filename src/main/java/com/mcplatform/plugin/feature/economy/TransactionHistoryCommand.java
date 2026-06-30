package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.feature.permission.PermissionFeature;
import com.mcplatform.plugin.feature.permission.PermissionGate;
import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.text.Messages;
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
 * audit trail). Staff-only: the whole command is gated behind {@link #PERMISSION}. Without an argument it
 * shows the sender's OWN history; with a name it shows that player's (online or offline-cached). The menu
 * opens immediately in a "Lade…" state and fills once the backend read returns; the main thread is never
 * blocked.
 */
public final class TransactionHistoryCommand implements CommandExecutor {

    /** Staff gate: viewing other players' money movements is sensitive — admins only. */
    static final String PERMISSION = "mcplatform.economy.history.others";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String currency;
    private final MenuManager menus;
    private final PermissionFeature permission;

    public TransactionHistoryCommand(BackendClient backend, PlatformScheduler scheduler,
                                     String currency, MenuManager menus, PermissionFeature permission) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.currency = currency;
        this.menus = menus;
        this.permission = permission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.playersOnly());
            return true;
        }

        // Gate against the backend rank system (warm permission cache), like the rest of the plugin —
        // NOT Bukkit's op/permissions.yml. Fail-closed if the cache isn't ready yet.
        PermissionGate gate = permission.gate();
        if (gate == null || !gate.has(player.getUniqueId(), PERMISSION)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }
        if (args.length == 0) {
            open(player, player.getUniqueId(), player.getName()); // own history
            return true;
        }
        // Offline-capable resolution without a blocking Mojang lookup: online first, else server cache.
        Player online = Bukkit.getPlayerExact(args[0]);
        OfflinePlayer resolved = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (resolved == null || resolved.getUniqueId() == null) {
            player.sendMessage(Messages.playerNotFound(args[0]));
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
