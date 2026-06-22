package com.mcplatform.plugin.feature.report;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * UUID → display-name resolution for menus and notifications. Online player first, then the server's
 * cached {@link OfflinePlayer} name, then a short-UUID fallback. No blocking Mojang lookup — purely
 * local/cached, safe to call on the main thread. Kept apart from {@link ReportFormat} so that the pure
 * formatting rules stay Bukkit-free and unit-testable.
 */
final class ReportNames {

    private ReportNames() {
    }

    static String of(UUID uuid) {
        if (uuid == null) {
            return "—";
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
