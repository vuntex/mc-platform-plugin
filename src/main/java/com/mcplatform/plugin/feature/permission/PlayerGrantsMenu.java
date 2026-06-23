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
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.ActiveGrant;
import com.mcplatform.protocol.permission.GrantPermissionRequest;
import com.mcplatform.protocol.permission.GrantRoleRequest;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RevokePermissionRequest;
import com.mcplatform.protocol.permission.RoleResponse;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * STATIC control panel for one player ({@code /cp <Spieler>}, online or offline): shows the player's
 * active rank grants and direct permission grants, and lets staff grant/revoke each. Reads via
 * {@code EFFECTIVE} (+ {@code LIST_ROLES} for the grant picker and the name→id map needed to revoke a
 * rank); writes via the grant/revoke endpoints. After a write the panel re-renders from the returned
 * {@link PlayerPermissionsResponse}. STATIC — not subscribed to the bus.
 */
public final class PlayerGrantsMenu {

    private static final int SLOT_GRANT_ROLE = 10;
    private static final int SLOT_GRANT_PERMISSION = 16;
    private static final int[] ROLE_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] PERMISSION_SLOTS = {28, 29, 30, 31, 32, 33, 34};

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

    private final AtomicLong requestSeq = new AtomicLong();
    private List<RoleResponse> allRoles = new ArrayList<>();
    private final Map<String, Long> roleIdByName = new HashMap<>();
    private PlayerPermissionsResponse state;

    public PlayerGrantsMenu(UUID target, String targetName, UUID actor, MenuManager menus,
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
        this.menu = MenuBuilder.panel(MenuText.name("Control Panel: " + targetName, Token.SPECIAL))
                .close()
                .build();
        applyHeader();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Load the player's grants and the role catalogue, then lay out the panel. */
    public void load(MenuView view) {
        long seq = requestSeq.incrementAndGet();
        backend.call(PermissionEndpoints.EFFECTIVE, null, target.toString())
                .thenCompose(effective -> backend.call(PermissionEndpoints.LIST_ROLES, null)
                        .thenApply(roles -> new Object[]{effective, roles}))
                .whenComplete((pair, error) -> scheduler.runSync(() -> {
                    if (seq != requestSeq.get()) {
                        return;
                    }
                    if (error != null || pair == null) {
                        showError(view);
                        return;
                    }
                    apply((PlayerPermissionsResponse) pair[0], (RoleResponse[]) pair[1]);
                    layout();
                    view.refresh();
                }));
    }

    private void apply(PlayerPermissionsResponse effective, RoleResponse[] roles) {
        this.state = effective;
        this.allRoles = new ArrayList<>(java.util.Arrays.asList(roles));
        roleIdByName.clear();
        for (RoleResponse role : allRoles) {
            roleIdByName.put(role.name(), role.id());
        }
    }

    private void layout() {
        applyHeader();
        clearContent();

        menu.setItem(SLOT_GRANT_ROLE, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Rang vergeben", Token.POSITIVE),
                        Lore.builder().describe("Einem Spieler einen Rang geben.").clickToOpen("auswählen").build()),
                this::onGrantRole));
        menu.setItem(SLOT_GRANT_PERMISSION, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Permission vergeben", Token.POSITIVE),
                        Lore.builder().describe("Einzel-Permission geben.").clickToOpen("eingeben").build()),
                this::onGrantPermission));

        List<ActiveGrant> roles = state == null || state.roles() == null ? List.of() : state.roles();
        for (int i = 0; i < roles.size() && i < ROLE_SLOTS.length; i++) {
            ActiveGrant grant = roles.get(i);
            menu.setItem(ROLE_SLOTS[i], grantItem(grant, "Rang", true));
        }
        List<ActiveGrant> perms = state == null || state.permissions() == null ? List.of() : state.permissions();
        for (int i = 0; i < perms.size() && i < PERMISSION_SLOTS.length; i++) {
            ActiveGrant grant = perms.get(i);
            menu.setItem(PERMISSION_SLOTS[i], grantItem(grant, "Permission", false));
        }
    }

    private MenuItem grantItem(ActiveGrant grant, String kind, boolean isRole) {
        Lore lore = Lore.builder()
                .describe(kind + "-Vergabe.")
                .value("Läuft ab:", PermissionFormat.expiry(grant.expiresAtEpochMilli()))
                .doubleClickDanger("entziehen");
        IconSpec icon = IconSpec.of(isRole ? Icon.INFO : Icon.LOCKED,
                MenuText.name(grant.label(), isRole ? Token.ENTITY : Token.BODY), lore.build());
        return MenuItem.button(icon, com.mcplatform.plugin.platform.menu.ClickAction.DOUBLE_CLICK,
                ctx -> onRevoke(ctx, grant, isRole));
    }

    // ── grant ────────────────────────────────────────────────────────────────────────────────────

    private void onGrantRole(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        ctx.view().feedback(Feedback.NAVIGATE);
        ctx.view().open(buildRolePicker());
    }

    private Menu buildRolePicker() {
        Menu picker = MenuBuilder.list(MenuText.name("Rang auswählen", Token.SPECIAL))
                .back(ctx -> ctx.view().open(menu))
                .close()
                .build();
        List<MenuItem> items = new ArrayList<>(allRoles.size());
        for (RoleResponse role : allRoles) {
            IconSpec icon = IconSpec.ofItem(iconItem.apply(role.displayIcon()),
                    PermissionFormat.roleName(role),
                    Lore.builder().value("Gewicht:", PermissionFormat.weight(role)).clickToOpen("vergeben").build());
            items.add(MenuItem.button(icon, ctx -> grantRole(ctx, role)));
        }
        MenuBuilder.renderPage(picker, items, 0, (ctx, ignored) -> { });
        return picker;
    }

    private void grantRole(ClickContext ctx, RoleResponse role) {
        backend.call(PermissionEndpoints.GRANT_ROLE,
                        new GrantRoleRequest(role.id(), null, null, actor), target.toString())
                .whenComplete((updated, error) -> scheduler.runSync(() -> applied(ctx, updated, error)));
    }

    private void onGrantPermission(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        Player player = Bukkit.getPlayer(ctx.playerId());
        if (player == null || input == null) {
            return;
        }
        ctx.view().close();
        input.prompt(player, "Permission-Node für " + targetName + ":", node -> grantPermission(player, node));
    }

    private void grantPermission(Player player, String node) {
        backend.call(PermissionEndpoints.GRANT_PERMISSION,
                        new GrantPermissionRequest(node, null, null, actor), target.toString())
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        sendError(player, error);
                        return;
                    }
                    reopen(player, updated);
                }));
    }

    // ── revoke ───────────────────────────────────────────────────────────────────────────────────

    private void onRevoke(ClickContext ctx, ActiveGrant grant, boolean isRole) {
        if (denied(ctx)) {
            return;
        }
        IconSpec object = IconSpec.of(isRole ? Icon.INFO : Icon.LOCKED,
                MenuText.name(grant.label(), isRole ? Token.ENTITY : Token.BODY));
        Menu confirm = ConfirmDialog.of(MenuText.name("Entziehen?", Token.DANGER), object)
                .confirmName(MenuText.name("Entziehen", Token.DANGER))
                .critical()
                .onConfirm(c -> revoke(c, grant, isRole))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    private void revoke(ClickContext ctx, ActiveGrant grant, boolean isRole) {
        CompletableFuture<PlayerPermissionsResponse> future;
        if (isRole) {
            Long roleId = roleIdByName.get(grant.label());
            if (roleId == null) {
                ctx.view().feedback(Feedback.DENY);
                ctx.view().send(PermissionFormat.error(404));
                return;
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("actor", actor.toString());
            future = backend.call(PermissionEndpoints.REVOKE_ROLE, null, query,
                    target.toString(), String.valueOf(roleId));
        } else {
            future = backend.call(PermissionEndpoints.REVOKE_PERMISSION,
                    new RevokePermissionRequest(grant.label(), null, actor), target.toString());
        }
        future.whenComplete((updated, error) -> scheduler.runSync(() -> applied(ctx, updated, error)));
    }

    /** Apply a write result back into the open panel (re-render from the backend truth). */
    private void applied(ClickContext ctx, PlayerPermissionsResponse updated, Throwable error) {
        if (error != null || updated == null) {
            ctx.view().feedback(Feedback.DENY);
            ctx.view().send(PermissionFormat.error(error));
            return;
        }
        this.state = updated;
        layout();
        ctx.view().feedback(Feedback.SUCCESS);
        ctx.view().open(menu);
    }

    private void reopen(Player player, PlayerPermissionsResponse updated) {
        PlayerGrantsMenu fresh = new PlayerGrantsMenu(target, targetName, actor, menus, backend, scheduler,
                gate, iconItem, input);
        MenuView view = menus.open(player, fresh.menu());
        fresh.load(view);
    }

    // ── header / states ────────────────────────────────────────────────────────────────────────────

    private void applyHeader() {
        Lore lore = Lore.builder().describe("Ränge und Permissions von " + targetName + ".");
        if (state != null) {
            lore.value("Ränge:", Integer.toString(state.roles() == null ? 0 : state.roles().size()));
            lore.value("Permissions:", Integer.toString(state.permissions() == null ? 0 : state.permissions().size()));
        }
        menu.setItem(MenuLayout.HEADER, MenuItem.display(
                IconSpec.head(target, MenuText.name(targetName, Token.ENTITY), lore.build())));
    }

    private void showLoading() {
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(
                IconSpec.of(Icon.LOADING, MenuText.name("Lade…", Token.BODY))));
    }

    private void showError(MenuView view) {
        clearContent();
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(
                IconSpec.of(Icon.EMPTY, MenuText.name("Konnte nicht geladen werden", Token.NEGATIVE))));
        view.feedback(Feedback.DENY);
        view.refresh();
    }

    private void clearContent() {
        for (int slot = 9; slot <= 44; slot++) {
            menu.setItem(slot, null);
        }
    }

    private boolean denied(ClickContext ctx) {
        if (gate.has(ctx.playerId(), PermissionNodes.GRANTS_MANAGE)) {
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
