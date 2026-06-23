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
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.RolePermissionRequest;
import com.mcplatform.protocol.permission.RoleResponse;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * STATIC, paginated list of a role's permissions ({@code /ranks} → role → permissions). The interactive
 * header (§2.3) adds a permission via a chat prompt ({@code ADD_ROLE_PERMISSION}); clicking a permission
 * removes it through the §2.5 critical confirm ({@code REMOVE_ROLE_PERMISSION}). After a write the list
 * is re-laid-out from the backend's returned role state.
 */
public final class RolePermissionsMenu {

    private final UUID viewer;
    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final PermissionInput input;
    private final Runnable back;
    private RoleResponse role;
    private final Menu menu;

    private List<String> permissions;
    private int page;

    public RolePermissionsMenu(UUID viewer, MenuManager menus, BackendClient backend,
                               PlatformScheduler scheduler, PermissionGate gate, PermissionInput input,
                               RoleResponse role, Runnable back) {
        this.viewer = viewer;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.input = input;
        this.role = role;
        this.back = back;
        this.permissions = new ArrayList<>(role.permissions() == null ? List.of() : role.permissions());
        this.menu = MenuBuilder.list(MenuText.name("Permissions", Token.SPECIAL))
                .back(ctx -> back.run())
                .close()
                .build();
        applyHeader();
        layout();
    }

    public Menu menu() {
        return menu;
    }

    private void applyHeader() {
        Lore lore = Lore.builder()
                .describe("Permissions der Rolle " + role.name() + ".")
                .value("Anzahl:", Integer.toString(permissions.size()))
                .clickToOpen("hinzufügen");
        menu.setItem(MenuLayout.HEADER, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Permission hinzufügen", Token.SPECIAL), lore.build()),
                this::onAdd));
    }

    /** Lay out the current page of permissions (pure). */
    public void layout() {
        applyHeader();
        List<MenuItem> items = new ArrayList<>(permissions.size());
        for (String permission : permissions) {
            items.add(permissionItem(permission));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, targetPage) -> {
            this.page = targetPage;
            layout();
            ctx.view().refresh();
        });
    }

    private MenuItem permissionItem(String permission) {
        IconSpec icon = IconSpec.of(Icon.LOCKED, MenuText.name(permission, Token.BODY),
                Lore.builder().describe("Permission dieser Rolle.").doubleClickDanger("entfernen").build());
        return MenuItem.button(icon, com.mcplatform.plugin.platform.menu.ClickAction.DOUBLE_CLICK,
                ctx -> onRemove(ctx, permission));
    }

    private void onAdd(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        Player player = Bukkit.getPlayer(ctx.playerId());
        if (player == null || input == null) {
            return;
        }
        ctx.view().close();
        input.prompt(player, "Permission-Node für " + role.name() + ":", node -> add(player, node));
    }

    private void add(Player player, String node) {
        backend.call(PermissionEndpoints.ADD_ROLE_PERMISSION,
                        new RolePermissionRequest(node, player.getUniqueId()), String.valueOf(role.id()))
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        sendError(player, error);
                        return;
                    }
                    reopen(player, updated);
                }));
    }

    private void onRemove(ClickContext ctx, String permission) {
        if (denied(ctx)) {
            return;
        }
        IconSpec object = IconSpec.of(Icon.LOCKED, MenuText.name(permission, Token.BODY));
        Menu confirm = ConfirmDialog.of(MenuText.name("Permission entfernen?", Token.DANGER), object)
                .confirmName(MenuText.name("Entfernen", Token.DANGER))
                .critical()
                .onConfirm(c -> remove(c, permission))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    private void remove(ClickContext ctx, String permission) {
        backend.call(PermissionEndpoints.REMOVE_ROLE_PERMISSION,
                        new RolePermissionRequest(permission, viewer), String.valueOf(role.id()))
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        ctx.view().feedback(Feedback.DENY);
                        ctx.view().send(PermissionFormat.error(error));
                        return;
                    }
                    this.role = updated;
                    this.permissions = new ArrayList<>(updated.permissions() == null ? List.of() : updated.permissions());
                    this.page = 0;
                    layout();
                    ctx.view().feedback(Feedback.REMOVE);
                    ctx.view().open(menu);
                }));
    }

    private void reopen(Player player, RoleResponse updated) {
        RolePermissionsMenu fresh = new RolePermissionsMenu(viewer, menus, backend, scheduler, gate, input,
                updated, back);
        menus.open(player, fresh.menu());
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
