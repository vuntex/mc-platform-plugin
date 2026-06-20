package com.mcplatform.plugin.feature.hub;

import com.mcplatform.plugin.platform.menu.ClickHandler;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;

/**
 * The cross-feature hub (MENU_DESIGN "ein Einstieg"): one entry point that shows only the entries the
 * player is allowed to use, so the whole thing feels like ONE system with consistent navigation. Economy
 * entries are for everyone; the punishment tools appear only for a permitted team member — an optimistic
 * UI gate (every real action stays backend-checked inside the opened menu).
 *
 * <p>Pure: it takes a {@code canPunish} flag and the per-entry handlers (the feature supplies the actual
 * launches), so "permitted vs unpermitted player sees different entries" is unit-testable without Bukkit.
 * STATIC — a launcher menu has nothing to update live.
 */
public final class HubMenu {

    /** Centred entry slots (§2.4): balance left-of-centre, pay centre, punish right-of-centre. */
    public static final int SLOT_BALANCE = 20;
    public static final int SLOT_PAY = 22;
    public static final int SLOT_PUNISH = 24;

    private HubMenu() {
    }

    /**
     * @param canPunish whether to show the team punishment entry (optimistic Bukkit-permission gate)
     */
    public static Menu build(boolean canPunish, ClickHandler onBalance, ClickHandler onPay, ClickHandler onPunish) {
        MenuBuilder builder = MenuBuilder.panel(MenuText.name("Hauptmenü", Token.INFO))
                .header(IconSpec.of(Icon.INFO, MenuText.name("Hauptmenü", Token.INFO),
                        Lore.builder().describe("Wähle einen Bereich.").build()))
                .item(SLOT_BALANCE, MenuItem.button(IconSpec.of(Icon.VALUE,
                                MenuText.name("Kontostand", Token.ENTITY),
                                Lore.builder().describe("Zeigt deinen Münzstand (live).").clickToOpen("öffnen").build()),
                        wrap(onBalance)))
                .item(SLOT_PAY, MenuItem.button(IconSpec.of(Icon.ADD,
                                MenuText.name("Coins senden", Token.ENTITY),
                                Lore.builder().describe("Überweise Coins an einen Spieler.").clickToOpen("öffnen").build()),
                        wrap(onPay)))
                .close();

        if (canPunish) {
            builder.item(SLOT_PUNISH, MenuItem.button(IconSpec.of(Icon.DANGER,
                            MenuText.name("Strafverwaltung", Token.NEGATIVE),
                            Lore.builder().describe("Strafen verhängen und einsehen.")
                                    .value("Zugriff:", "Team").clickToOpen("öffnen").build()),
                    wrap(onPunish)));
        }
        return builder.build();
    }

    /** Wrap a launch handler with the neutral navigation feedback every hub click gives (§4.5). */
    private static ClickHandler wrap(ClickHandler handler) {
        return ctx -> {
            ctx.view().feedback(com.mcplatform.plugin.platform.menu.Feedback.NAVIGATE);
            handler.onClick(ctx);
        };
    }
}
