package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.protocol.report.ReportChangedEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Live team ping: when a new report is created, notify every online player holding
 * {@code mcplatform.report.view} with a chat line + alert sound (FR-019). Recipient selection is purely
 * client-side via the UI permission node (no per-player backend call); the backend stays authoritative
 * for the actual inbox access. Runs on the main thread (Bukkit API).
 */
public final class ReportNotifier {

    private static final String VIEW_PERMISSION = "mcplatform.report.view";

    private final PlatformScheduler scheduler;

    public ReportNotifier(PlatformScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Ping online team members about a newly created report. Safe to call from any thread. */
    public void ping(ReportChangedEvent event) {
        scheduler.runSync(() -> {
            Component message = Component.text("[Report] ", NamedTextColor.RED)
                    .append(Component.text(ReportFormat.categoryLabel(event.category()), NamedTextColor.YELLOW))
                    .append(Component.text(" gegen ", NamedTextColor.GRAY))
                    .append(Component.text(ReportNames.of(event.target()), NamedTextColor.YELLOW))
                    .append(Component.text(" – /reports zum Bearbeiten.", NamedTextColor.GRAY));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(VIEW_PERMISSION)) {
                    player.sendMessage(message);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                }
            }
        });
    }
}
