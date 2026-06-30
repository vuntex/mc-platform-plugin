package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.support.FakeBackendClient;
import com.mcplatform.plugin.feature.scoreboard.support.ImmediateScheduler;
import com.mcplatform.protocol.permission.PlayerPermissionsResponse;
import com.mcplatform.protocol.permission.RoleDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * The additive live-notify: after a successful reload writes the cache, {@code onApplied} fires (so the
 * scoreboard rank line re-renders AFTER the cache is fresh); on a REST error it does NOT fire.
 */
class PermissionLoaderNotifyTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static PlayerPermissionsResponse response() {
        return new PlayerPermissionsResponse(PLAYER, List.of(), List.of(), List.of("perm.a"), List.of(),
                new RoleDisplay("Admin", "W", "", "", "W", null, null));
    }

    @Test
    void notifiesAfterApply() {
        List<UUID> applied = new ArrayList<>();
        PermissionCache cache = new PermissionCache();
        PermissionLoader loader = new PermissionLoader(
                new FakeBackendClient().result(response()), new ImmediateScheduler(), cache,
                Logger.getLogger("test"), applied::add);

        loader.load(PLAYER, 5L);

        assertEquals(List.of(PLAYER), applied);
        assertTrue(cache.get(PLAYER).isPresent());
    }

    @Test
    void doesNotNotifyOnError() {
        List<UUID> applied = new ArrayList<>();
        PermissionCache cache = new PermissionCache();
        PermissionLoader loader = new PermissionLoader(
                new FakeBackendClient().error(new RuntimeException("boom")), new ImmediateScheduler(), cache,
                Logger.getLogger("test"), applied::add);

        loader.load(PLAYER, 5L);

        assertTrue(applied.isEmpty());
        assertTrue(cache.get(PLAYER).isEmpty());
    }
}
