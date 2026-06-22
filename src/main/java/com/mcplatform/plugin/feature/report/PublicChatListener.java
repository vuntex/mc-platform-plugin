package com.mcplatform.plugin.feature.report;

import com.mcplatform.protocol.report.ChatMessage;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Feeds the global {@link ChatRingBuffer} from public chat. Uses Paper's Adventure-native
 * {@link AsyncChatEvent} (not the deprecated {@code AsyncPlayerChatEvent}). Runs at
 * {@link EventPriority#MONITOR} with {@code ignoreCancelled = true}, so a message cancelled by another
 * listener — a punishment chat-mute, or the report reason-input listener consuming the reporter's next
 * line — is NOT captured. Private messages are their own commands (not a chat event), so they are
 * naturally excluded (FR-008).
 *
 * <p>The event is already async and the ring is thread-safe, so no main-thread hop is needed.
 */
public final class PublicChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ChatRingBuffer ring;

    public PublicChatListener(ChatRingBuffer ring) {
        this.ring = ring;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ring.add(new ChatMessage(
                event.getPlayer().getUniqueId(),
                PLAIN.serialize(event.message()),
                System.currentTimeMillis()));
    }
}
