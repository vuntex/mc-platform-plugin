package com.mcplatform.plugin.feature.chat;

import com.mcplatform.plugin.feature.permission.PermissionReadPort;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.protocol.permission.RoleDisplay;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders player chat with the rank prefix + name colour from the warm permission cache
 * (see {@code CHAT_DESIGN.md}): {@code <prefix><name in rank colour>: <message>}. Uses Paper's
 * viewer-unaware {@link ChatRenderer} so the line is built once. The role's {@code prefix} may contain
 * legacy {@code &}/§ codes (incl. {@code &#rrggbb}); the {@code color} may be a colour name or hex.
 * Missing values render neutrally (white). Reads the cache only (thread-safe), so it is async-safe.
 */
public final class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final PermissionReadPort permission;

    public ChatFormatListener(PermissionReadPort permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> render(source, message)));
    }

    private Component render(Player source, Component message) {
        Optional<RoleDisplay> display = permission.currentDisplay(source.getUniqueId());
        Component prefix = display.map(d -> prefix(d.prefix())).orElse(Component.empty());
        TextColor nameColor = display.map(d -> color(d.color())).orElse(NamedTextColor.GRAY);
        return Component.empty()
                .append(prefix)
                .append(Component.text(source.getName(), nameColor))
                .append(Component.text(": ", ChatDesign.MUTED))
                .append(message.colorIfAbsent(NamedTextColor.WHITE));
    }

    /** Render a role prefix (legacy &/§ codes supported), with a trailing space, or empty if blank. */
    private static Component prefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        Component rendered = LEGACY.deserialize(prefix.strip().replace('§', '&'));
        return rendered.append(Component.space());
    }

    /** Resolve a role colour: a colour name ("RED"), a hex ("#55FF55"), else white. */
    private static TextColor color(String color) {
        if (color == null || color.isBlank()) {
            return NamedTextColor.WHITE;
        }
        String value = color.strip();
        NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (value.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(value);
            if (hex != null) {
                return hex;
            }
        }
        return NamedTextColor.WHITE;
    }
}
