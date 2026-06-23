package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ConfirmDialog;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.RoleRequest;
import com.mcplatform.protocol.permission.RoleResponse;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * STATIC detail/edit panel for one role: rename the display name, manage its permissions
 * ({@link RolePermissionsMenu}), or delete it via the §2.5 critical confirm (double-click). Writes go
 * through REST ({@code actor} carried as the staff UUID; delete carries it as a query param). After a
 * write the flow returns to the role list so the change is visible.
 */
public final class RoleDetailMenu {

    private static final int SLOT_RENAME = 20;
    private static final int SLOT_PERMISSIONS = 22;
    private static final int SLOT_DELETE = 24;

    private final UUID viewer;
    private final com.mcplatform.plugin.platform.menu.MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final Function<String, ItemStack> iconItem;
    private final PermissionInput input;
    private final RoleResponse role;
    private final Runnable backToList;
    private final Menu menu;

    public RoleDetailMenu(UUID viewer, com.mcplatform.plugin.platform.menu.MenuManager menus,
                          BackendClient backend, PlatformScheduler scheduler, PermissionGate gate,
                          Function<String, ItemStack> iconItem, PermissionInput input,
                          RoleResponse role, Runnable backToList) {
        this.viewer = viewer;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.iconItem = iconItem;
        this.input = input;
        this.role = role;
        this.backToList = backToList;
        this.menu = build();
    }

    public Menu menu() {
        return menu;
    }

    private Menu build() {
        Lore headerLore = Lore.builder()
                .value("Art:", PermissionFormat.rankKind(role))
                .value("Gewicht:", PermissionFormat.weight(role))
                .value("Permissions:", Integer.toString(role.permissions() == null ? 0 : role.permissions().size()));
        IconSpec header = IconSpec.ofItem(iconItem.apply(role.displayIcon()),
                PermissionFormat.roleName(role), headerLore.build());

        IconSpec rename = IconSpec.of(Icon.VALUE, MenuText.name("Anzeigename ändern", Token.ENTITY),
                Lore.builder().describe("Aktuell: " + displayName()).clickToOpen("ändern").build());
        IconSpec perms = IconSpec.of(Icon.MANAGE, MenuText.name("Permissions verwalten"),
                Lore.builder().describe("Permissions dieser Rolle.").clickToOpen("öffnen").build());
        IconSpec delete = IconSpec.of(Icon.DANGER, MenuText.name("Rolle löschen", Token.DANGER),
                Lore.builder().describe("Löscht diese Rolle endgültig.").doubleClickDanger("löschen").build());

        return MenuBuilder.panel(MenuText.name("Rolle: " + displayName(), Token.SPECIAL))
                .header(header)
                .item(SLOT_RENAME, MenuItem.button(rename, this::onRename))
                .item(SLOT_PERMISSIONS, MenuItem.button(perms, this::onPermissions))
                .item(SLOT_DELETE, MenuItem.button(delete, this::onDelete))
                .back(ctx -> backToList.run())
                .close()
                .build();
    }

    private String displayName() {
        return role.displayName() == null || role.displayName().isBlank() ? role.name() : role.displayName();
    }

    private void onRename(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        Player player = Bukkit.getPlayer(ctx.playerId());
        if (player == null || input == null) {
            return;
        }
        ctx.view().close();
        input.prompt(player, "Neuer Anzeigename für " + role.name() + ":", newName -> rename(player, newName));
    }

    private void rename(Player player, String newDisplayName) {
        RoleRequest request = new RoleRequest(role.name(), newDisplayName, role.color(), role.prefix(),
                role.suffix(), role.tabListColor(), role.tabListIcon(), role.displayIcon(),
                role.weight(), role.teamRank(), role.active(), player.getUniqueId());
        backend.call(PermissionEndpoints.UPDATE_ROLE, request, String.valueOf(role.id()))
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null) {
                        sendError(player, error);
                        return;
                    }
                    backToList.run();
                }));
    }

    private void onPermissions(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        ctx.view().feedback(Feedback.NAVIGATE);
        RolePermissionsMenu perms = new RolePermissionsMenu(viewer, menus, backend, scheduler, gate, input,
                role, () -> reopenDetail(ctx.playerId()));
        ctx.view().open(perms.menu());
    }

    private void onDelete(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        IconSpec object = IconSpec.ofItem(iconItem.apply(role.displayIcon()),
                PermissionFormat.roleName(role), java.util.List.of());
        Menu confirm = ConfirmDialog.of(MenuText.name("Rolle löschen?", Token.DANGER), object)
                .confirmName(MenuText.name("Löschen", Token.DANGER))
                .critical()
                .onConfirm(c -> deleteRole(c))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    private void deleteRole(ClickContext ctx) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("actor", viewer.toString());
        backend.call(PermissionEndpoints.DELETE_ROLE, null, query, String.valueOf(role.id()))
                .whenComplete((ignored, error) -> scheduler.runSync(() -> {
                    if (error != null) {
                        ctx.view().feedback(Feedback.DENY);
                        ctx.view().send(PermissionFormat.error(error));
                        return;
                    }
                    backToList.run();
                }));
    }

    private void reopenDetail(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        // Re-fetch happens via the list; simplest correct path is to return to the list.
        backToList.run();
    }

    private boolean denied(ClickContext ctx) {
        if (gate.has(ctx.playerId(), PermissionNodes.ROLES_MANAGE)) {
            return false;
        }
        ctx.view().feedback(Feedback.DENY);
        ctx.view().send(PermissionFormat.error(403));
        return true;
    }

    private void sendError(Player player, Throwable error) {
        player.sendMessage(net.kyori.adventure.text.Component.text(
                PermissionFormat.error(error).text().text(),
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }
}
