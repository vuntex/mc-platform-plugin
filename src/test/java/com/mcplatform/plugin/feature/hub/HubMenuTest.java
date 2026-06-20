package com.mcplatform.plugin.feature.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ClickHandler;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves the hub's optimistic UI gate: a permitted (team) player sees the punishment entry, an
 * unpermitted player does not — while the economy entries are present for both. Also that an entry
 * launches its handler.
 */
class HubMenuTest {

    private static final ClickHandler NOOP = ctx -> {
    };

    @Test
    void unpermittedPlayerSeesOnlyEconomyEntries() {
        Menu menu = HubMenu.build(false, NOOP, NOOP, NOOP);
        assertNotNull(menu.getItem(HubMenu.SLOT_BALANCE), "balance for everyone");
        assertNotNull(menu.getItem(HubMenu.SLOT_PAY), "pay for everyone");
        assertNull(menu.getItem(HubMenu.SLOT_PUNISH), "no punishment entry without permission");
    }

    @Test
    void permittedTeamPlayerAlsoSeesThePunishmentEntry() {
        Menu menu = HubMenu.build(true, NOOP, NOOP, NOOP);
        assertNotNull(menu.getItem(HubMenu.SLOT_BALANCE));
        assertNotNull(menu.getItem(HubMenu.SLOT_PAY));
        assertNotNull(menu.getItem(HubMenu.SLOT_PUNISH), "team sees the punishment entry");
        assertTrue(menu.getItem(HubMenu.SLOT_PUNISH).isInteractive());
    }

    @Test
    void clickingAnEntryRunsItsHandler() {
        int[] balanceRuns = {0};
        Menu menu = HubMenu.build(true, ctx -> balanceRuns[0]++, NOOP, NOOP);
        RecordingMenuView view = new RecordingMenuView(UUID.randomUUID(), menu);

        menu.route(new ClickContext(view.playerId(), ClickAction.LEFT, HubMenu.SLOT_BALANCE, view));
        assertEquals(1, balanceRuns[0]);
    }
}
