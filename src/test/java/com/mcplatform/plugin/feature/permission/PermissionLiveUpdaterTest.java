package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.protocol.permission.PermissionChangedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** TEST FOCUS: Cache-Refresh genau für die betroffene UUID bei mc:permission:changed (T016). */
class PermissionLiveUpdaterTest {

    private record Reload(UUID uuid, long version) {
    }

    @Test
    void onlinePlayerReloadsExactlyThatUuidAtEventTimestamp() {
        UUID target = UUID.randomUUID();
        List<Reload> reloads = new ArrayList<>();
        PermissionLiveUpdater updater = new PermissionLiveUpdater(
                uuid -> uuid.equals(target), (uuid, version) -> reloads.add(new Reload(uuid, version)));

        updater.accept(new PermissionChangedEvent(target, "GRANT_ADDED", 123L));

        assertEquals(1, reloads.size());
        assertEquals(target, reloads.get(0).uuid());
        assertEquals(123L, reloads.get(0).version());
    }

    @Test
    void offlinePlayerIsIgnored() {
        List<Reload> reloads = new ArrayList<>();
        PermissionLiveUpdater updater = new PermissionLiveUpdater(
                uuid -> false, (uuid, version) -> reloads.add(new Reload(uuid, version)));

        updater.accept(new PermissionChangedEvent(UUID.randomUUID(), "GRANT_REVOKED", 1L));

        assertTrue(reloads.isEmpty());
    }

    @Test
    void everyChangeTypeIncludingUnknownTriggersReloadWhenOnline() {
        UUID target = UUID.randomUUID();
        List<Reload> reloads = new ArrayList<>();
        PermissionLiveUpdater updater = new PermissionLiveUpdater(
                uuid -> true, (uuid, version) -> reloads.add(new Reload(uuid, version)));

        for (String type : List.of("GRANT_ADDED", "GRANT_REVOKED", "GRANT_EXPIRED",
                "ROLE_CONFIG_CHANGED", "SOME_FUTURE_TYPE")) {
            updater.accept(new PermissionChangedEvent(target, type, 1L));
        }

        assertEquals(5, reloads.size());
    }
}
