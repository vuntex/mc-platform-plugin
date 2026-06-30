package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.LiveBinding;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.plugin.platform.menu.Token;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Demo 1 — the Economy balance menu: LIVE, single-value (MENU_DESIGN §6). A panel that shows the coins
 * balance and updates the value slot in place whenever the balance changes while the menu is open. It is
 * pure (no Bukkit): the menu model and the live-update item are built here, so both the layout and the
 * "balance changed → value re-rendered" behaviour are unit-testable. The actual data comes from the same
 * {@code FeatureCache}/EventBus stream economy already maintains — the menu just consumes it.
 *
 * @see com.mcplatform.plugin.platform.menu.LiveBinding
 */
public final class BalanceMenu {

    /** Centred value slot (§2.4 — single action/value goes to the centre, slot 22). */
    static final int VALUE_SLOT = 22;

    private BalanceMenu() {
    }

    /**
     * Build the LIVE balance menu for {@code player}. {@code balanceSupplier} reads the current cached
     * balance (empty = still loading); the LIVE binding observes {@code player}'s UUID on the shared bus
     * and re-renders only {@link #VALUE_SLOT} when the feature signals a change.
     */
    public static Menu build(UUID player, String playerName, String currency,
                             Supplier<Optional<Long>> balanceSupplier) {
        Menu menu = MenuBuilder.panel(MenuText.name("Dein Kontostand", Token.ENTITY))
                .header(IconSpec.head(player, MenuText.name(playerName, Token.ENTITY),
                        Lore.builder().describe("Dein aktueller Münzstand.").build()))
                .item(VALUE_SLOT, valueItem(balanceSupplier.get(), currency))
                .close()
                .build();

        // LIVE: on a balance change for this player, re-render only the value slot (no full rebuild).
        menu.setLive(new LiveBinding(player,
                view -> view.setSlot(VALUE_SLOT, valueItem(balanceSupplier.get(), currency))));
        return menu;
    }

    /** The value item — a gold readout, or a neutral "Lade…" placeholder while data is in flight. */
    static MenuItem valueItem(Optional<Long> balance, String currency) {
        if (balance.isEmpty()) {
            return MenuItem.display(IconSpec.of(Icon.LOADING,
                    MenuText.name("Lade…", Token.BODY),
                    Lore.builder().describe("Kontostand wird geladen.").build()));
        }
        String value = ChatDesign.number(balance.get()) + " " + EconomyFeature.currencyDisplay(currency);
        return MenuItem.display(IconSpec.of(Icon.VALUE,
                MenuText.name(value, Token.ENTITY),
                Lore.builder().describe("Dein aktueller Kontostand.").value("Stand:", value).build()));
    }
}
