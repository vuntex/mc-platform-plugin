package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.*;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.permission.ActiveGrant;
import com.mcplatform.protocol.permission.GrantRoleRequest;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RoleResponse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * STATIC Permission/Rank submenu of the Control Panel ({@code /cp} → "Ränge & Permissions"): the player's
 * <b>ranks shown directly</b> (grant via a picker, revoke via the §2.5 critical confirm) plus a link to
 * the separate {@link PlayerPermissionsMenu} for direct permissions. Reads via {@code EFFECTIVE} (active
 * grants + the name→id map needed to revoke a rank) and {@code LIST_ROLES} (the grant picker, which omits
 * ranks the player already holds). Writes via the grant/revoke endpoints; re-renders from the returned
 * {@link PlayerPermissionsResponse}.
 */
public final class PlayerRanksMenu {

    private static final int SLOT_GRANT = 10;
    private static final int SLOT_PERMISSIONS = 16;
    private static final int[] ROLE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final UUID target;
    private final String targetName;
    private final UUID actor;
    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final Function<String, ItemStack> iconItem;
    private final PermissionInput input;
    private final Runnable back;
    private final Menu menu;

    private final AtomicLong requestSeq = new AtomicLong();
    private List<RoleResponse> allRoles = new ArrayList<>();
    private final Map<String, Long> roleIdByName = new HashMap<>();
    private PlayerPermissionsResponse state;

    public PlayerRanksMenu(UUID target, String targetName, UUID actor, MenuManager menus,
                           BackendClient backend, PlatformScheduler scheduler, PermissionGate gate,
                           Function<String, ItemStack> iconItem, PermissionInput input, Runnable back) {
        this.target = target;
        this.targetName = targetName;
        this.actor = actor;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.iconItem = iconItem;
        this.input = input;
        this.back = back;
        this.menu = MenuBuilder.panel(MenuText.name("Ränge: " + targetName, Token.SPECIAL))
                .back(ctx -> back.run())
                .close()
                .build();
        applyHeader();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Load the player's grants + the role catalogue, then lay out the ranks. */
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
        this.allRoles = new ArrayList<>(Arrays.asList(roles));
        roleIdByName.clear();
        for (RoleResponse role : allRoles) {
            roleIdByName.put(role.name(), role.id());
        }
    }

    private void layout() {
        applyHeader();
        clearContent();

        menu.setItem(SLOT_GRANT, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Rang vergeben", Token.POSITIVE),
                        Lore.builder().describe("Einen Rang hinzufügen.").clickToOpen("auswählen").build()),
                this::onGrant));
        menu.setItem(SLOT_PERMISSIONS, MenuItem.button(
                IconSpec.of(Icon.LOCKED, MenuText.name("Permissions", Token.SPECIAL),
                        Lore.builder().describe("Einzel-Permissions dieses Spielers.").clickToOpen("öffnen").build()),
                this::openPermissions));

        List<ActiveGrant> roles = activeRoles();
        for (int i = 0; i < roles.size() && i < ROLE_SLOTS.length; i++) {
            menu.setItem(ROLE_SLOTS[i], roleGrantItem(roles.get(i)));
        }
    }

    private List<ActiveGrant> activeRoles() {
        return state == null || state.roles() == null ? List.of() : state.roles();
    }

    private MenuItem roleGrantItem(ActiveGrant grant) {
        Lore lore = Lore.builder()
                .describe("Aktiver Rang.")
                .value("Läuft ab:", PermissionFormat.expiry(grant.expiresAtEpochMilli()))
                .doubleClickDanger("entziehen");
        IconSpec icon = IconSpec.of(Icon.INFO, MenuText.name(grant.label(), Token.ENTITY), lore.build());
        return MenuItem.button(icon, com.mcplatform.plugin.platform.menu.ClickAction.DOUBLE_CLICK,
                ctx -> onRevoke(ctx, grant));
    }

    // ── grant (picker omits already-held ranks) ────────────────────────────────────────────────────

    private void onGrant(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        ctx.view().feedback(Feedback.NAVIGATE);
        ctx.view().open(buildRolePicker());
    }

    Menu buildRolePicker() {
        Set<String> held = new HashSet<>();
        for (ActiveGrant grant : activeRoles()) {
            held.add(grant.label());
        }
        Menu picker = MenuBuilder.list(MenuText.name("Rang auswählen", Token.SPECIAL))
                .back(ctx -> ctx.view().open(menu))
                .close()
                .build();
        List<MenuItem> items = new ArrayList<>();
        for (RoleResponse role : allRoles) {
            if (held.contains(role.name())) {
                continue; // don't offer a rank the player already has
            }
            IconSpec icon = IconSpec.ofItem(iconItem.apply(role.displayIcon()),
                    PermissionFormat.roleName(role),
                    Lore.builder().value("Gewicht:", PermissionFormat.weight(role)).clickToOpen("vergeben").build());
            items.add(MenuItem.button(icon, ctx -> grantRole(ctx, role)));
        }
        MenuBuilder.renderPage(picker, items, 0, (ctx, ignored) -> { });
        return picker;
    }

    private void grantRole(ClickContext ctx, RoleResponse role) {
        Player viewer = Bukkit.getPlayer(ctx.playerId());
        if (viewer == null) {
            return;
        }
        // Pick the expiry via the duration menu (Permanent button + presets + custom), then grant.
        DurationPicker picker = new DurationPicker(menus, input, "Rang: " + role.name(), "Rang " + role.name(),
                seconds -> doGrantRole(viewer, role, seconds), () -> reopen(viewer));
        ctx.view().open(picker.menu());
    }

    private void doGrantRole(Player viewer, RoleResponse role, Long expiresInSeconds) {
        backend.call(PermissionEndpoints.GRANT_ROLE,
                        new GrantRoleRequest(role.id(), expiresInSeconds, null, actor), target.toString())
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        viewer.sendMessage(Component.text(
                                PermissionFormat.error(error).text().text(), NamedTextColor.RED));
                    }
                    reopen(viewer);
                }));
    }

    // ── revoke ───────────────────────────────────────────────────────────────────────────────────

    private void onRevoke(ClickContext ctx, ActiveGrant grant) {
        if (denied(ctx)) {
            return;
        }
        IconSpec object = IconSpec.of(Icon.INFO, MenuText.name(grant.label(), Token.ENTITY));
        Menu confirm = ConfirmDialog.of(MenuText.name("Rang entziehen?", Token.DANGER), object)
                .confirmName(MenuText.name("Entziehen", Token.DANGER))
                .critical()
                .onConfirm(c -> revokeRole(c, grant))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    private void revokeRole(ClickContext ctx, ActiveGrant grant) {
        Long roleId = roleIdByName.get(grant.label());
        if (roleId == null) {
            ctx.view().feedback(Feedback.DENY);
            ctx.view().send(PermissionFormat.error(404));
            return;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("actor", actor.toString());
        backend.call(PermissionEndpoints.REVOKE_ROLE, null, query, target.toString(), String.valueOf(roleId))
                .whenComplete((updated, error) -> scheduler.runSync(() -> applied(ctx, updated, error)));
    }

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

    // ── permissions submenu ────────────────────────────────────────────────────────────────────────

    private void openPermissions(ClickContext ctx) {
        ctx.view().feedback(Feedback.NAVIGATE);
        Player viewer = Bukkit.getPlayer(ctx.playerId());
        if (viewer == null) {
            return;
        }
        PlayerPermissionsMenu perms = new PlayerPermissionsMenu(target, targetName, actor, menus, backend,
                scheduler, gate, input, () -> reopen(viewer));
        MenuView view = menus.open(viewer, perms.menu());
        perms.load(view);
    }

    /** Reopen this ranks menu (used as the permissions submenu's "back"). */
    void reopen(Player viewer) {
        PlayerRanksMenu fresh = new PlayerRanksMenu(target, targetName, actor, menus, backend, scheduler,
                gate, iconItem, input, back);
        MenuView view = menus.open(viewer, fresh.menu());
        fresh.load(view);
    }

    // ── header / states ────────────────────────────────────────────────────────────────────────────

    private void applyHeader() {
        Lore lore = Lore.builder().describe("Ränge von " + targetName + ".");
        if (state != null) {
            lore.value("Ränge:", Integer.toString(activeRoles().size()));
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
}
