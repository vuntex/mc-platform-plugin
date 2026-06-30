package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.permission.ActiveGrant;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/** Proves the extra-permissions submenu: interactive add header, one item per direct grant, empty marker. */
class PlayerPermissionsMenuTest {

    private final UUID target = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private static final class InlineScheduler implements PlatformScheduler {
        @Override public void runSync(Runnable task) { task.run(); }
        @Override public void runAsync(Runnable task) { task.run(); }
    }

    private static final class Backend implements BackendClient {
        private final PlayerPermissionsResponse effective;
        Backend(PlayerPermissionsResponse effective) { this.effective = effective; }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) CompletableFuture.completedFuture(effective);
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException();
        }
    }

    private PlayerPermissionsMenu load(PlayerPermissionsResponse effective) {
        PlayerPermissionsMenu perms = new PlayerPermissionsMenu(target, "Tester", actor, null,
                new Backend(effective), new InlineScheduler(),
                new PermissionGate(new PermissionCache()), null, () -> { });
        perms.load(new RecordingMenuView(target, perms.menu()));
        return perms;
    }

    @Test
    void showsGrantHeaderAndOneItemPerDirectPermission() {
        PlayerPermissionsResponse effective = new PlayerPermissionsResponse(target, List.of(),
                List.of(new ActiveGrant("mcplatform.fly", null, actor, null, null),
                        new ActiveGrant("mcplatform.god", 1_700_000_000_000L, actor, null, "r")),
                List.of(), List.of(), null);
        Menu model = load(effective).menu();

        assertTrue(model.getItem(MenuLayout.HEADER).isInteractive(), "interactive add-permission header");
        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (model.getItem(slot) != null) {
                filled++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, filled);
    }

    @Test
    void emptyPermissionsShowsMarker() {
        PlayerPermissionsResponse effective = new PlayerPermissionsResponse(target, List.of(), List.of(),
                List.of(), List.of(), null);
        Menu model = load(effective).menu();
        assertNotNull(model.getItem(MenuBuilder.EMPTY_MARKER_SLOT), "empty marker present");
    }
}
