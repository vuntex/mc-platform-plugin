package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.economy.EconomyEndpoints;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /balance} — opens the LIVE {@link BalanceMenu} instead of printing chat text (MENU_DESIGN demo
 * 1). The menu opens immediately: if the balance is already cached (warmed on join, kept fresh by
 * Pub/Sub) the value shows at once; on a cache miss it opens in a "Lade…" state and fills the value slot
 * once a REST read returns — the main thread is never blocked. While open, the menu re-renders live from
 * the same cache via the menu live bus.
 */
public final class BalanceCommand implements CommandExecutor {

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final FeatureCache<UUID, Long> cache;
    private final String currency;
    private final MenuManager menus;

    public BalanceCommand(BackendClient backend, PlatformScheduler scheduler,
                          FeatureCache<UUID, Long> cache, String currency, MenuManager menus) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.cache = cache;
        this.currency = currency;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have a balance.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        // Menu opens now; the value slot reads the cache live (empty Optional → "Lade…").
        MenuView view = menus.open(player,
                BalanceMenu.build(uuid, player.getName(), currency, () -> cache.get(uuid)));

        // Cache miss → fill via REST without blocking, then update just the value slot.
        if (cache.get(uuid).isEmpty()) {
            backend.call(EconomyEndpoints.GET_BALANCE, null, uuid.toString(), currency)
                    .whenComplete((balance, error) -> scheduler.runSync(() -> fill(view, balance, error)));
        }
        return true;
    }

    private void fill(MenuView view, BalanceResponse balance, Throwable error) {
        if (error != null || balance == null) {
            return; // menu keeps the "Lade…" item; transient failure, nothing to crash on
        }
        cache.put(balance.player(), balance.balance(), balance.version());
        view.setSlot(BalanceMenu.VALUE_SLOT, BalanceMenu.valueItem(Optional.of(balance.balance()), currency));
    }
}
