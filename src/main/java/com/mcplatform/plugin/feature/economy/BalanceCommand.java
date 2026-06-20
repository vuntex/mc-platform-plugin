package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.EconomyEndpoints;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /balance} — cache-first: shows the locally cached balance (filled on join and kept fresh by
 * live Pub/Sub events) instantly. On a cache miss it falls back to a REST read, fills the cache, then
 * replies. All Bukkit interaction is on the main thread; the REST result is delivered back via the
 * scheduler (Prinzip 5).
 */
public final class BalanceCommand implements CommandExecutor {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final FeatureCache<UUID, Long> cache;
    private final String currency;

    public BalanceCommand(BackendClient backend, PlatformScheduler scheduler,
                          FeatureCache<UUID, Long> cache, String currency) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have a balance.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        var cached = cache.get(uuid);
        if (cached.isPresent()) {
            player.sendMessage("§aBalance: §f" + cached.get() + " §7" + currency);
            return true;
        }

        // Miss — fall back to REST (non-blocking), fill the cache, reply on the main thread.
        backend.call(EconomyEndpoints.GET_BALANCE, null, uuid.toString(), currency)
                .whenComplete((balance, error) -> scheduler.runSync(() -> reply(player, balance, error)));
        return true;
    }

    private void reply(Player player, BalanceResponse balance, Throwable error) {
        if (!player.isOnline()) {
            return;
        }
        if (error != null || balance == null) {
            player.sendMessage("§cBalance gerade nicht verfügbar");
            return;
        }
        cache.put(balance.player(), balance.balance(), balance.version());
        player.sendMessage("§aBalance: §f" + balance.balance() + " §7" + balance.currency());
    }
}
