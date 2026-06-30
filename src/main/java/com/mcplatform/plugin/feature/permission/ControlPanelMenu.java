package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Function;

/**
 * The STATIC top-level Control Panel for a player ({@code /cp <Spieler>}). A shell that holds per-player
 * staff tools; for this slice the only entry is "Ränge & Permissions" → {@link PlayerRanksMenu}. Kept as
 * its own menu so further options (history, economy, …) can slot in later without disturbing the rank UI.
 */
public final class ControlPanelMenu {

    private static final int SLOT_RANKS = 22;

    private final UUID target;
    private final String targetName;
    private final UUID actor;
    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final Function<String, ItemStack> iconItem;
    private final PermissionInput input;
    private final Menu menu;

    public ControlPanelMenu(UUID target, String targetName, UUID actor, MenuManager menus,
                            BackendClient backend, PlatformScheduler scheduler, PermissionGate gate,
                            Function<String, ItemStack> iconItem, PermissionInput input) {
        this.target = target;
        this.targetName = targetName;
        this.actor = actor;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.iconItem = iconItem;
        this.input = input;
        this.menu = build();
    }

    public Menu menu() {
        return menu;
    }

    private Menu build() {
        IconSpec header = IconSpec.head(target, MenuText.name(targetName, Token.ENTITY),
                Lore.builder().describe("Control Panel für " + targetName + ".").build());
        IconSpec ranks = IconSpec.of(Icon.MANAGE, MenuText.name("Ränge & Permissions", Token.SPECIAL),
                Lore.builder().describe("Ränge und Einzel-Permissions verwalten.").clickToOpen("öffnen").build());

        return MenuBuilder.panel(MenuText.name("Control Panel: " + targetName, Token.SPECIAL))
                .header(header)
                .item(SLOT_RANKS, MenuItem.button(ranks, this::openRanks))
                .close()
                .build();
    }

    private void openRanks(ClickContext ctx) {
        ctx.view().feedback(Feedback.NAVIGATE);
        Player viewer = Bukkit.getPlayer(ctx.playerId());
        if (viewer == null) {
            return;
        }
        PlayerRanksMenu ranks = new PlayerRanksMenu(target, targetName, actor, menus, backend, scheduler,
                gate, iconItem, input, () -> reopen(viewer));
        MenuView view = menus.open(viewer, ranks.menu());
        ranks.load(view);
    }

    /** Reopen this CP menu (used as the ranks submenu's "back"). */
    void reopen(Player viewer) {
        ControlPanelMenu fresh = new ControlPanelMenu(target, targetName, actor, menus, backend, scheduler,
                gate, iconItem, input);
        menus.open(viewer, fresh.menu());
    }
}
