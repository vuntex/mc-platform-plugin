package com.mcplatform.plugin.feature.economyalert;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.feature.economy.EconomyFeature;
import com.mcplatform.plugin.feature.permission.PermissionFeature;
import com.mcplatform.plugin.feature.permission.PermissionGate;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.protocol.economy.EconomyAlertEvent;
import com.mcplatform.protocol.economy.EconomyAlertEventCodec;
import com.mcplatform.protocol.economy.EconomyChannels;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Subscribes to {@code mc:economy:alert} and surfaces suspicious economy movements: a styled broadcast to
 * online admins (permission node {@code mcplatform.economy.alerts}, read from the warm permission cache)
 * plus a console WARN. The backend does the detection and carries both parties (Von/An) for transfers;
 * this feature only presents them. {@link #onEnable} is the single place it touches the platform.
 */
public final class EconomyAlertFeature implements PluginFeature {

    /** Players with this node receive the in-game economy alert broadcasts. */
    public static final String ALERT_NODE = "mcplatform.economy.alerts";

    private final PermissionFeature permission;

    public EconomyAlertFeature(PermissionFeature permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @Override
    public String id() {
        return "economy-alert";
    }

    @Override
    public void onEnable(FeatureContext context) {
        PermissionGate gate = permission.gate();
        Logger log = context.logger();
        context.eventBus().subscribe(EconomyChannels.ALERT, EconomyAlertEventCodec.INSTANCE,
                event -> handle(event, gate, log));
    }

    private void handle(EconomyAlertEvent event, PermissionGate gate, Logger log) {
        log.warning("[economy-alert] " + event.eventType() + " " + event.amount() + " " + event.currencyCode()
                + " von=" + event.playerUuid()
                + (event.targetUuid() == null ? "" : " an=" + event.targetUuid())
                + " (" + event.reason() + ")");

        Component message = render(event);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gate != null && gate.has(player.getUniqueId(), ALERT_NODE)) {
                player.sendMessage(message);
            }
        }
    }

    private Component render(EconomyAlertEvent e) {
        // CHAT_DESIGN.md: bold red "Economy »" prefix, value yellow, names gold, separators/detail dark-gray.
        Component msg = ChatDesign.prefix("ECONOMY", ChatDesign.ERROR)
                .append(ChatDesign.error("⚠ "))
                .append(ChatDesign.value(ChatDesign.number(e.amount()) + " "
                        + EconomyFeature.currencyDisplay(e.currencyCode())))
                .append(ChatDesign.text(" von "))
                .append(ChatDesign.name(name(e.playerUuid())));
        if (e.targetUuid() != null) {
            msg = msg.append(ChatDesign.muted(" → "))
                    .append(ChatDesign.name(name(e.targetUuid())));
        }
        return msg.append(ChatDesign.muted(" · " + e.reason()));
    }

    /** Best-effort name from the server's cache; falls back to a short UUID. */
    private static String name(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String n = p.getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}
