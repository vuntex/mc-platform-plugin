package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.permission.ActiveGrant;
import com.mcplatform.protocol.permission.PermissionEndpoints;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RoleResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Proves the ranks submenu: ranks shown directly with a grant + a permissions link, and the role picker
 * omits ranks the player already holds. Slot numbers mirror {@code PlayerRanksMenu} (grant 10,
 * permissions 16, first role 19). Icons injected as {@code s -> null} so no server is needed.
 */
class PlayerRanksMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final Function<String, ItemStack> noIcons = s -> null;

    private static final class InlineScheduler implements PlatformScheduler {
        @Override public void runSync(Runnable task) { task.run(); }
        @Override public void runAsync(Runnable task) { task.run(); }
    }

    private static final class Backend implements BackendClient {
        private final PlayerPermissionsResponse effective;
        private final RoleResponse[] roles;
        Backend(PlayerPermissionsResponse effective, RoleResponse[] roles) {
            this.effective = effective;
            this.roles = roles;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            Object result = e == PermissionEndpoints.EFFECTIVE ? effective : roles;
            return (CompletableFuture<RES>) CompletableFuture.completedFuture(result);
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException();
        }
    }

    private static RoleResponse role(long id, String name) {
        return new RoleResponse(id, name, name.toUpperCase(), null, null, null, null, null, "material:PAPER",
                (int) id, false, true, false, List.of());
    }

    private PlayerRanksMenu load(PlayerPermissionsResponse effective, RoleResponse[] roles) {
        PlayerRanksMenu ranks = new PlayerRanksMenu(target, "Tester", actor, null,
                new Backend(effective, roles), new InlineScheduler(),
                new PermissionGate(new PermissionCache()), noIcons, null, () -> { });
        ranks.load(new RecordingMenuView(target, ranks.menu()));
        return ranks;
    }

    @Test
    void showsRanksDirectlyWithGrantAndPermissionsLink() {
        PlayerPermissionsResponse effective = new PlayerPermissionsResponse(target,
                List.of(new ActiveGrant("vip", null, actor, null)), List.of(), List.of(), null);
        PlayerRanksMenu ranks = load(effective, new RoleResponse[]{role(1, "vip"), role(2, "admin")});
        Menu model = ranks.menu();

        assertNotNull(model.getItem(MenuLayout.HEADER), "player header");
        assertTrue(model.getItem(10).isInteractive(), "grant-rank button");
        assertTrue(model.getItem(16).isInteractive(), "permissions link");
        assertNotNull(model.getItem(19), "active rank shown directly");
        assertTrue(model.getItem(19).isInteractive(), "active rank is revocable");
    }

    @Test
    void rolePickerOmitsRanksThePlayerAlreadyHas() {
        PlayerPermissionsResponse effective = new PlayerPermissionsResponse(target,
                List.of(new ActiveGrant("vip", null, actor, null)), List.of(), List.of(), null);
        PlayerRanksMenu ranks = load(effective, new RoleResponse[]{role(1, "vip"), role(2, "admin"), role(3, "mod")});

        Menu picker = ranks.buildRolePicker();
        int offered = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (picker.getItem(slot) != null) {
                offered++;
            }
        }
        assertEquals(2, offered, "vip is held → only admin + mod offered");
    }
}
