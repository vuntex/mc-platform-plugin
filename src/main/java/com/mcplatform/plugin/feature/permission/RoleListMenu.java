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
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.RoleRequest;
import com.mcplatform.protocol.permission.RoleResponse;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The STATIC role-management list ({@code /ranks}, MENU_DESIGN §4.4): a paginated list of all roles,
 * each rendered with its {@code display_icon} (resolved by the injected {@code iconItem}, never the
 * backend). The interactive header (§2.3) creates a new role via a chat prompt. Clicking a role opens
 * the {@link RoleDetailMenu}. STATIC — re-laid-out after a write, never subscribed to the bus.
 */
public final class RoleListMenu {

    private final UUID viewer;
    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final Function<String, ItemStack> iconItem;
    private final PermissionInput input;
    private final Menu menu;

    private List<RoleResponse> roles = new ArrayList<>();
    private boolean loaded;
    private int page;

    public RoleListMenu(UUID viewer, MenuManager menus, BackendClient backend, PlatformScheduler scheduler,
                        PermissionGate gate, Function<String, ItemStack> iconItem, PermissionInput input) {
        this.viewer = viewer;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.iconItem = iconItem;
        this.input = input;
        this.menu = MenuBuilder.list(MenuText.name("Rollen", Token.SPECIAL))
                .close()
                .build();
        applyHeader();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Load all roles and fill the open view (STATIC snapshot at open / after a write). */
    public void load(MenuView view) {
        this.loaded = false;
        this.page = 0;
        showLoading();
        view.refresh();
        backend.call(PermissionEndpoints.LIST_ROLES, null)
                .whenComplete((result, error) -> scheduler.runSync(() -> {
                    if (error != null || result == null) {
                        showError(view);
                        return;
                    }
                    setRoles(result);
                    layout();
                    view.refresh();
                }));
    }

    void setRoles(RoleResponse[] all) {
        List<RoleResponse> list = new ArrayList<>(Arrays.asList(all));
        // Highest weight first — the strongest role on top.
        list.sort(Comparator.comparingInt(RoleResponse::weight).reversed());
        this.roles = list;
        this.loaded = true;
        this.page = 0;
    }

    /** Lay out the current page (pure — no view, no refresh). */
    public void layout() {
        applyHeader();
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(roles.size());
        for (RoleResponse role : roles) {
            items.add(roleItem(role));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, targetPage) -> {
            this.page = targetPage;
            layout();
            ctx.view().refresh();
        });
    }

    private MenuItem roleItem(RoleResponse role) {
        Lore lore = Lore.builder()
                .value("Art:", PermissionFormat.rankKind(role))
                .value("Gewicht:", PermissionFormat.weight(role))
                .value("Permissions:", Integer.toString(role.permissions() == null ? 0 : role.permissions().size()))
                .clickToOpen("verwalten");
        IconSpec icon = IconSpec.ofItem(iconItem.apply(role.displayIcon()),
                PermissionFormat.roleName(role), lore.build());
        return MenuItem.button(icon, ctx -> openDetail(ctx, role));
    }

    private void openDetail(ClickContext ctx, RoleResponse role) {
        ctx.view().feedback(Feedback.NAVIGATE);
        RoleDetailMenu detail = new RoleDetailMenu(viewer, menus, backend, scheduler, gate, iconItem, input,
                role, () -> reopen(ctx.playerId()));
        ctx.view().open(detail.menu());
    }

    // ── interactive header = create role ─────────────────────────────────────────────────────────────

    private void applyHeader() {
        Lore lore = Lore.builder().describe("Alle Rollen des Servers.");
        if (loaded) {
            lore.value("Anzahl:", Integer.toString(roles.size()));
        }
        lore.clickToOpen("Rolle anlegen");
        IconSpec icon = IconSpec.of(Icon.ADD, MenuText.name("Rollen verwalten", Token.SPECIAL), lore.build());
        menu.setItem(MenuLayout.HEADER, MenuItem.button(icon, this::onCreate));
    }

    private void onCreate(ClickContext ctx) {
        if (!gate.has(ctx.playerId(), PermissionNodes.ROLES_MANAGE)) {
            ctx.view().feedback(Feedback.DENY);
            ctx.view().send(PermissionFormat.error(403));
            return;
        }
        Player player = Bukkit.getPlayer(ctx.playerId());
        if (player == null || input == null) {
            return;
        }
        ctx.view().close();
        input.prompt(player, "Name der neuen Rolle:", name -> createRole(player, name));
    }

    private void createRole(Player player, String name) {
        RoleRequest request = new RoleRequest(name, name, null, null, null, null, null, null,
                0, false, true, player.getUniqueId());
        backend.call(PermissionEndpoints.CREATE_ROLE, request)
                .whenComplete((created, error) -> scheduler.runSync(() -> {
                    if (error != null) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "Rolle konnte nicht erstellt werden.", net.kyori.adventure.text.format.NamedTextColor.RED));
                        return;
                    }
                    reopen(player.getUniqueId());
                }));
    }

    /** Open a fresh role list for the player (used after a write / chat input closed the GUI). */
    private void reopen(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        RoleListMenu fresh = new RoleListMenu(viewer, menus, backend, scheduler, gate, iconItem, input);
        MenuView view = menus.open(player, fresh.menu());
        fresh.load(view);
    }

    private void showLoading() {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.LOADING,
                MenuText.name("Lade…", Token.BODY))));
    }

    private void showError(MenuView view) {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.EMPTY,
                MenuText.name("Konnte nicht geladen werden", Token.NEGATIVE))));
        view.feedback(Feedback.DENY);
        view.refresh();
    }

    private void clearContent() {
        for (int slot : Pagination.CONTENT_SLOTS) {
            menu.setItem(slot, null);
        }
        menu.setItem(MenuLayout.PAGE_PREV, null);
        menu.setItem(MenuLayout.PAGE_NEXT, null);
    }
}
