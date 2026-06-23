package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
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

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Bukkit-free proof of the control panel: it loads the player's grants + the role catalogue and lays out
 * the grant buttons and one item per active role/permission grant. Slot numbers mirror
 * {@code PlayerGrantsMenu}'s layout (grant-role 10, grant-permission 16, first role 19, first perm 28).
 */
class PlayerGrantsMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private static final class InlineScheduler implements PlatformScheduler {
        @Override public void runSync(Runnable task) { task.run(); }
        @Override public void runAsync(Runnable task) { task.run(); }
    }

    private static final class PanelBackend implements BackendClient {
        private final PlayerPermissionsResponse effective;
        private final RoleResponse[] roles;
        PanelBackend(PlayerPermissionsResponse effective, RoleResponse[] roles) {
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

    @Test
    void laysOutGrantButtonsAndOneItemPerActiveGrant() {
        ActiveGrant roleGrant = new ActiveGrant("vip", null, actor, null);
        ActiveGrant permGrant = new ActiveGrant("mcplatform.fly", 1_700_000_000_000L, actor, "reason");
        PlayerPermissionsResponse effective = new PlayerPermissionsResponse(target,
                List.of(roleGrant), List.of(permGrant), List.of("mcplatform.fly"), null);
        RoleResponse[] roles = {new RoleResponse(7L, "vip", "VIP", null, null, null, null, null,
                "material:PAPER", 5, false, true, false, List.of())};

        PlayerGrantsMenu panel = new PlayerGrantsMenu(target, "Tester", actor, null,
                new PanelBackend(effective, roles), new InlineScheduler(),
                new PermissionGate(new PermissionCache()), (java.util.function.Function<String, ItemStack>) s -> null, null);
        RecordingMenuView view = new RecordingMenuView(target, panel.menu());
        panel.load(view);

        Menu model = panel.menu();
        assertNotNull(model.getItem(MenuLayout.HEADER), "player header present");
        assertTrue(model.getItem(10).isInteractive(), "grant-role button");
        assertTrue(model.getItem(16).isInteractive(), "grant-permission button");
        assertNotNull(model.getItem(19), "first active role grant shown");
        assertTrue(model.getItem(19).isInteractive(), "role grant is revocable");
        assertNotNull(model.getItem(28), "first active permission grant shown");
        assertTrue(model.getItem(28).isInteractive(), "permission grant is revocable");
    }
}
