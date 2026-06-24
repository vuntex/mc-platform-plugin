package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickAction;
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
import com.mcplatform.protocol.permission.ActiveGrant;
import com.mcplatform.protocol.permission.GrantPermissionRequest;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RevokePermissionRequest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * STATIC, paginated list of a player's <b>direct</b> permission grants — the "extra permissions" submenu
 * reached from {@link PlayerRanksMenu}. The interactive header (§2.3) grants a permission via a chat
 * prompt ({@code GRANT_PERMISSION}); clicking a grant revokes it through the §2.5 critical confirm
 * ({@code REVOKE_PERMISSION}). Reads via {@code EFFECTIVE}; re-renders from the backend's returned state.
 */
public final class PlayerPermissionsMenu {

    private final UUID target;
    private final String targetName;
    private final UUID actor;
    private final MenuManager menus;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final PermissionGate gate;
    private final PermissionInput input;
    private final Runnable back;
    private final Menu menu;

    private final AtomicLong requestSeq = new AtomicLong();
    private List<ActiveGrant> permissions = new ArrayList<>();
    private boolean loaded;
    private int page;

    public PlayerPermissionsMenu(UUID target, String targetName, UUID actor, MenuManager menus,
                                 BackendClient backend, PlatformScheduler scheduler, PermissionGate gate,
                                 PermissionInput input, Runnable back) {
        this.target = target;
        this.targetName = targetName;
        this.actor = actor;
        this.menus = menus;
        this.backend = backend;
        this.scheduler = scheduler;
        this.gate = gate;
        this.input = input;
        this.back = back;
        this.menu = MenuBuilder.list(MenuText.name("Permissions: " + targetName, Token.SPECIAL))
                .back(ctx -> back.run())
                .close()
                .build();
        applyHeader();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Load the player's direct permission grants and lay them out. */
    public void load(MenuView view) {
        long seq = requestSeq.incrementAndGet();
        backend.call(PermissionEndpoints.EFFECTIVE, null, target.toString())
                .whenComplete((effective, error) -> scheduler.runSync(() -> {
                    if (seq != requestSeq.get()) {
                        return;
                    }
                    if (error != null || effective == null) {
                        showError(view);
                        return;
                    }
                    setState(effective);
                    layout();
                    view.refresh();
                }));
    }

    private void setState(PlayerPermissionsResponse effective) {
        this.permissions = new ArrayList<>(effective.permissions() == null ? List.of() : effective.permissions());
        this.loaded = true;
        this.page = 0;
    }

    private void layout() {
        applyHeader();
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(permissions.size());
        for (ActiveGrant grant : permissions) {
            items.add(grantItem(grant));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, targetPage) -> {
            this.page = targetPage;
            layout();
            ctx.view().refresh();
        });
    }

    private MenuItem grantItem(ActiveGrant grant) {
        Lore lore = Lore.builder()
                .describe("Direkte Permission.")
                .value("Läuft ab:", PermissionFormat.expiry(grant.expiresAtEpochMilli()))
                .doubleClickDanger("entziehen");
        IconSpec icon = IconSpec.of(Icon.LOCKED, MenuText.name(grant.label(), Token.BODY), lore.build());
        return MenuItem.button(icon, ClickAction.DOUBLE_CLICK, ctx -> onRevoke(ctx, grant));
    }

    private void applyHeader() {
        Lore lore = Lore.builder().describe("Einzel-Permissions von " + targetName + ".");
        if (loaded) {
            lore.value("Anzahl:", Integer.toString(permissions.size()));
        }
        lore.clickToOpen("hinzufügen");
        menu.setItem(MenuLayout.HEADER, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Permission hinzufügen", Token.SPECIAL), lore.build()),
                this::onGrant));
    }

    // ── grant / revoke ──────────────────────────────────────────────────────────────────────────────

    private void onGrant(ClickContext ctx) {
        if (denied(ctx)) {
            return;
        }
        Player viewer = Bukkit.getPlayer(ctx.playerId());
        if (viewer == null || input == null) {
            return;
        }
        ctx.view().close();
        // Ask for the node via chat, then choose the expiry via the duration menu.
        input.prompt(viewer, "Permission-Node für " + targetName + ":", node -> openDurationPicker(viewer, node));
    }

    private void openDurationPicker(Player viewer, String node) {
        DurationPicker picker = new DurationPicker(menus, input, "Permission", "Permission " + node,
                seconds -> grant(viewer, node, seconds), () -> reopen(viewer));
        menus.open(viewer, picker.menu());
    }

    private void grant(Player viewer, String node, Long expiresInSeconds) {
        backend.call(PermissionEndpoints.GRANT_PERMISSION,
                        new GrantPermissionRequest(node, expiresInSeconds, null, actor), target.toString())
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        viewer.sendMessage(net.kyori.adventure.text.Component.text(
                                PermissionFormat.error(error).text().text(),
                                net.kyori.adventure.text.format.NamedTextColor.RED));
                    }
                    reopen(viewer);
                }));
    }

    private void onRevoke(ClickContext ctx, ActiveGrant grant) {
        if (denied(ctx)) {
            return;
        }
        IconSpec object = IconSpec.of(Icon.LOCKED, MenuText.name(grant.label(), Token.BODY));
        Menu confirm = ConfirmDialog.of(MenuText.name("Permission entziehen?", Token.DANGER), object)
                .confirmName(MenuText.name("Entziehen", Token.DANGER))
                .critical()
                .onConfirm(c -> revoke(c, grant))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    private void revoke(ClickContext ctx, ActiveGrant grant) {
        backend.call(PermissionEndpoints.REVOKE_PERMISSION,
                        new RevokePermissionRequest(grant.label(), null, actor), target.toString())
                .whenComplete((updated, error) -> scheduler.runSync(() -> {
                    if (error != null || updated == null) {
                        ctx.view().feedback(Feedback.DENY);
                        ctx.view().send(PermissionFormat.error(error));
                        return;
                    }
                    setState(updated);
                    layout();
                    ctx.view().feedback(Feedback.REMOVE);
                    ctx.view().open(menu);
                }));
    }

    private void reopen(Player viewer) {
        PlayerPermissionsMenu fresh = new PlayerPermissionsMenu(target, targetName, actor, menus, backend,
                scheduler, gate, input, back);
        MenuView view = menus.open(viewer, fresh.menu());
        fresh.load(view);
    }

    private void showLoading() {
        clearContent();
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
        for (int slot : Pagination.CONTENT_SLOTS) {
            menu.setItem(slot, null);
        }
        menu.setItem(MenuLayout.PAGE_PREV, null);
        menu.setItem(MenuLayout.PAGE_NEXT, null);
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
