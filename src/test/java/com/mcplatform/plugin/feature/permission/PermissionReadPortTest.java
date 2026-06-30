package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.protocol.permission.RoleDisplay;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** PermissionReadPort: warm cache → plain display name; cold/blank → empty. */
class PermissionReadPortTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static RoleDisplay display(String name) {
        return new RoleDisplay(name, "WHITE", "", "", "WHITE", null, null);
    }

    @Test
    void warmCacheReturnsPlainName() {
        PermissionCache cache = new PermissionCache();
        cache.apply(PLAYER, new PlayerPermissionsView(Set.of(), display("Admin")), 1L);

        assertEquals(Optional.of("Admin"), new PermissionReadPort(cache).currentRankName(PLAYER));
    }

    @Test
    void coldCacheReturnsEmpty() {
        assertTrue(new PermissionReadPort(new PermissionCache()).currentRankName(PLAYER).isEmpty());
    }

    @Test
    void blankNameReturnsEmpty() {
        PermissionCache cache = new PermissionCache();
        cache.apply(PLAYER, new PlayerPermissionsView(Set.of(), display("  ")), 1L);

        assertTrue(new PermissionReadPort(cache).currentRankName(PLAYER).isEmpty());
    }
}
