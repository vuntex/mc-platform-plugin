package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.Pagination;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.permission.RoleResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Bukkit-free proof of the role list: loads all roles, paginates the 28-grid, renders one icon per role,
 * and shows the empty marker when there are none. Icons are injected as {@code s -> null} so no server is
 * needed (the menu wraps them with {@code IconSpec.ofItem}; real rendering is manually verified).
 */
class RoleListMenuTest {

    private final UUID viewer = UUID.randomUUID();
    private final Function<String, ItemStack> noIcons = s -> null;

    private static final class InlineScheduler implements PlatformScheduler {
        @Override public void runSync(Runnable task) { task.run(); }
        @Override public void runAsync(Runnable task) { task.run(); }
    }

    private static final class RolesBackend implements BackendClient {
        private final RoleResponse[] roles;
        RolesBackend(RoleResponse[] roles) { this.roles = roles; }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) CompletableFuture.completedFuture(roles);
        }

        @Override
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            throw new UnsupportedOperationException();
        }
    }

    private static RoleResponse role(int weight) {
        return new RoleResponse(weight, "role" + weight, "Role " + weight, null, null, null, null, null,
                "material:PAPER", weight, false, true, false, List.of("perm.a", "perm.b"), List.of());
    }

    private static RoleResponse[] roles(int count) {
        RoleResponse[] all = new RoleResponse[count];
        for (int i = 0; i < count; i++) {
            all[i] = role(i);
        }
        return all;
    }

    private RoleListMenu open(RoleResponse[] roles) {
        RoleListMenu list = new RoleListMenu(viewer, null, new RolesBackend(roles), new InlineScheduler(),
                new PermissionGate(new PermissionCache()), noIcons, null);
        RecordingMenuView view = new RecordingMenuView(viewer, list.menu());
        list.load(view);
        return list;
    }

    @Test
    void rendersOneIconPerRoleOnTheFirstPage() {
        RoleListMenu list = open(roles(5));
        Menu model = list.menu();
        int filled = 0;
        for (int slot : Pagination.CONTENT_SLOTS) {
            if (model.getItem(slot) != null) {
                filled++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(5, filled);
        assertNotNull(model.getItem(MenuLayout.HEADER), "interactive create header present");
        assertTrue(model.getItem(MenuLayout.HEADER).isInteractive());
    }

    @Test
    void paginatesAcrossTheGrid() {
        RoleListMenu list = open(roles(40));
        Menu model = list.menu();
        for (int slot : Pagination.CONTENT_SLOTS) {
            assertNotNull(model.getItem(slot), "page 0 content slot " + slot);
        }
        assertNull(model.getItem(MenuLayout.PAGE_PREV));
        assertNotNull(model.getItem(MenuLayout.PAGE_NEXT));
    }

    @Test
    void emptyRoleListShowsTheMarker() {
        RoleListMenu list = open(roles(0));
        assertNotNull(list.menu().getItem(MenuBuilderEmptyMarker.SLOT), "centred empty marker present");
    }

    /** Local alias so the test reads clearly without importing the constant inline. */
    private static final class MenuBuilderEmptyMarker {
        static final int SLOT = com.mcplatform.plugin.platform.menu.MenuBuilder.EMPTY_MARKER_SLOT;
    }
}
