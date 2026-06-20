package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEventCodec;
import com.mcplatform.protocol.economy.EconomyChannels;

import java.util.UUID;

/**
 * Economy as the first real {@link PluginFeature}, built entirely on the generic transport + registry
 * (Prompt 3–5): a join listener (REST session join + cache warmup), the {@code /balance} command
 * (cache-first, REST fallback), a live subscription on {@code mc:economy:balance} (version-checked
 * cache update), and a quit listener (cache eviction). The balance cache is a plain
 * {@link FeatureCache} instance — nothing economy-specific — which proves the generic pattern carries.
 *
 * <p>{@link #onEnable} is the single place economy touches the platform.
 */
public final class EconomyFeature implements PluginFeature {

    /** Single currency for this slice (matches the backend's seeded COINS, 100 start bonus). */
    static final String CURRENCY = "COINS";

    private final FeatureCache<UUID, Long> balances = new FeatureCache<>();
    private final MenuManager menus;

    /** The shared menu manager is injected by the composition root — no generic class is touched. */
    public EconomyFeature(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void onEnable(FeatureContext context) {
        // Live updates: version-checked cache refresh from mc:economy:balance, then nudge any open LIVE
        // balance menu for that player to re-render its value slot (same stream, new consumer).
        context.eventBus().subscribe(EconomyChannels.BALANCE, BalanceChangedEventCodec.INSTANCE,
                event -> {
                    EconomyBalances.apply(balances, event, CURRENCY);
                    menus.liveBus().notifyChange(event.playerUuid());
                });

        // /balance: opens the LIVE balance menu (cache-first, REST fallback for a cold cache).
        context.registerCommand("balance",
                new BalanceCommand(context.backend(), context.scheduler(), balances, CURRENCY, menus));

        // Join → backend session + cache warmup; quit → cache eviction.
        context.registerListener(new PlayerJoinListener(
                context.backend(), context.scheduler(), balances, CURRENCY, context.logger()));
        context.registerListener(new PlayerQuitListener(balances));
    }
}
