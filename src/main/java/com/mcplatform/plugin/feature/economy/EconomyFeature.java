package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceChangedEventCodec;
import com.mcplatform.protocol.economy.EconomyChannels;

import java.util.UUID;

/**
 * Economy as the first real {@link PluginFeature}, built entirely on the generic transport + registry:
 * the {@code /balance} + {@code /pay} menus (cache-first, REST fallback), a live subscription on
 * {@code mc:economy:balance} (version-checked cache update), and a quit listener (cache eviction). The
 * balance cache is filled lazily (on demand / by live events), not at join — establishing the backend
 * session is the platform {@code SessionFeature}'s job, not economy's. The balance cache is a plain
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

        // /balance [Spieler]: chat-only balance read (cache-first, REST fallback), incl. offline players.
        context.registerCommand("balance",
                new BalanceCommand(context.backend(), context.scheduler(), balances, CURRENCY));

        // /pay: player-side transfer flow (recipient picker → amount editor → confirm → TRANSFER).
        context.registerCommand("pay",
                new PayCommand(context.backend(), context.scheduler(), CURRENCY, menus));

        // /transactions [Spieler]: paginated, filterable audit trail of coin movements (GET_HISTORY).
        context.registerCommand("transactions",
                new TransactionHistoryCommand(context.backend(), context.scheduler(), CURRENCY, menus));

        // No join hook: the backend session is established+gated by the platform SessionFeature. Economy
        // fills its cache lazily — cache-first /balance with a REST fallback, plus the live subscription
        // above — so it stays independent of the session gate. Quit → cache eviction.
        context.registerListener(new PlayerQuitListener(balances));
    }
}
