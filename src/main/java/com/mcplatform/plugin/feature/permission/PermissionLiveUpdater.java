package com.mcplatform.plugin.feature.permission;

import com.mcplatform.protocol.permission.PermissionChangedEvent;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Routes {@code mc:permission:changed} events to a targeted cache reload (spec FR-003/004/005). For an
 * <b>online</b> affected player, reloads ONLY that player's effective view at the event's timestamp
 * (the cache version); for an <b>offline</b> player the event is ignored (no load, no entry). Every
 * {@code changeType} — including an unknown future one — is treated as a full invalidation of that one
 * UUID; {@code ROLE_CONFIG_CHANGED} arrives per holder, so no special fan-out is needed.
 *
 * <p>The online-check and the reload trigger are injected, so the routing logic is unit-testable
 * without Bukkit or transport. The {@code EventDispatcher} delivers on the main thread.
 */
public final class PermissionLiveUpdater implements java.util.function.Consumer<PermissionChangedEvent> {

    private final Predicate<UUID> online;
    private final BiConsumer<UUID, Long> reload;

    public PermissionLiveUpdater(Predicate<UUID> online, BiConsumer<UUID, Long> reload) {
        this.online = online;
        this.reload = reload;
    }

    @Override
    public void accept(PermissionChangedEvent event) {
        UUID player = event.playerUuid();
        if (online.test(player)) {
            reload.accept(player, event.timestampEpochMilli());
        }
    }
}
