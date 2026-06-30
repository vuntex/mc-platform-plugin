package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.EconomyEndpoints;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * {@code /balance [Spieler]} — chat-only balance read.
 *
 * <ul>
 *   <li>{@code /balance} prints the sender's own balance (sender must be a player).</li>
 *   <li>{@code /balance <Spieler>} prints another player's balance; the target may be offline, as long
 *       as the server already knows its UUID (online, or seen before → cached by the server). Works for
 *       the console too.</li>
 * </ul>
 *
 * Cache-first: a value warmed on join / kept fresh by Pub/Sub is printed at once; on a cache miss a REST
 * read fills it asynchronously and the result is printed back on the main thread — the main thread is
 * never blocked.
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
        final UUID target;
        final String targetName;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Messages.usage("/balance <Spieler>"));
                return true;
            }
            target = self.getUniqueId();
            targetName = self.getName();
        } else {
            OfflinePlayer resolved = resolve(args[0]);
            if (resolved == null || resolved.getUniqueId() == null) {
                sender.sendActionBar(Messages.playerNotFound(args[0]));
                return true;
            }
            target = resolved.getUniqueId();
            targetName = resolved.getName() != null ? resolved.getName() : args[0];
        }

        boolean self = sender instanceof Player p && p.getUniqueId().equals(target);

        // Cache-first: print immediately if we already hold a fresh value.
        Optional<Long> cached = cache.get(target);
        if (cached.isPresent()) {
            send(sender, targetName, cached.get(), self);
            return true;
        }

        // Cache miss → REST without blocking, then print on the main thread.
        backend.call(EconomyEndpoints.GET_BALANCE, null, target.toString(), currency)
                .whenComplete((balance, error) ->
                        scheduler.runSync(() -> complete(sender, targetName, self, balance, error)));
        return true;
    }

    /** Resolve a name to an offline player without a blocking Mojang lookup: online first, else cached. */
    private @Nullable OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private void complete(CommandSender sender, String name, boolean self,
                          BalanceResponse balance, Throwable error) {
        if (error != null || balance == null) {
            if (unwrap(error) instanceof BackendException.NotFound) {
                sender.sendMessage(self ? "Du hast noch kein Konto." : name + " hat noch kein Konto.");
            } else {
                sender.sendMessage("Kontostand konnte nicht geladen werden. Bitte später erneut versuchen.");
            }
            return;
        }
        cache.put(balance.player(), balance.balance(), balance.version());
        send(sender, name, balance.balance(), self);
    }

    private void send(CommandSender sender, String name, long balance, boolean self) {
        String value = ChatDesign.number(balance) + " " + EconomyFeature.currencyDisplay(currency);
        sender.sendMessage(self
                ? "Dein Kontostand: " + value
                : "Kontostand von " + name + ": " + value);
    }

    /** Futures complete exceptionally with the raw cause, but compositions wrap it in CompletionException. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
