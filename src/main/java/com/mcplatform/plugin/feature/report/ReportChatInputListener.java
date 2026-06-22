package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.report.CreateReportRequest;
import com.mcplatform.protocol.report.ReportEndpoints;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * Captures the reporter's next public chat line as the report reason (MENU_DESIGN chat-input fallback,
 * since no Anvil input exists). Uses Paper's Adventure-native {@link AsyncChatEvent} (not the deprecated
 * {@code AsyncPlayerChatEvent}). Runs at {@link EventPriority#LOWEST} and cancels the event, so the
 * reason never appears in public chat and is never captured by the {@link PublicChatListener} (which is
 * {@code ignoreCancelled = true} at MONITOR). The cancel word {@code abbrechen} aborts. On submit, the
 * current {@link ChatRingBuffer} snapshot is attached as {@code chatContext} and CREATE is called
 * off-thread; the reporter is messaged back on the main thread.
 */
public final class ReportChatInputListener implements Listener {

    static final String CANCEL_WORD = "abbrechen";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ReportReasonPrompt prompt;
    private final ChatRingBuffer ring;
    private final ReportCooldown cooldown;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;

    public ReportChatInputListener(ReportReasonPrompt prompt, ChatRingBuffer ring, ReportCooldown cooldown,
                                   BackendClient backend, PlatformScheduler scheduler) {
        this.prompt = prompt;
        this.ring = ring;
        this.cooldown = cooldown;
        this.backend = backend;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID reporter = player.getUniqueId();
        Optional<ReportReasonPrompt.Pending> maybe = prompt.take(reporter);
        if (maybe.isEmpty()) {
            return; // not in a report flow — leave the line to normal chat (and the ring)
        }
        event.setCancelled(true);

        String text = PLAIN.serialize(event.message()).trim();
        if (text.equalsIgnoreCase(CANCEL_WORD)) {
            scheduler.runSync(() -> player.sendMessage(
                    Component.text("Meldung abgebrochen.", NamedTextColor.GRAY)));
            return;
        }

        ReportReasonPrompt.Pending p = maybe.get();
        CreateReportRequest request = ReportReasonPrompt.request(reporter, p, text, ring.snapshot());
        backend.call(ReportEndpoints.CREATE, request)
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null || response == null) {
                        // Re-arm the gate if the backend reports a cooldown (e.g. our RAM state was lost).
                        if (ReportFormat.isCooldown(error)) {
                            cooldown.markReported(reporter);
                        }
                        player.sendMessage(Component.text(ReportFormat.errorText(error), NamedTextColor.RED));
                        return;
                    }
                    cooldown.markReported(reporter); // start the client-side cooldown on a real submission
                    player.sendMessage(Component.text(
                            ReportFormat.confirmationText(p.category(), p.targetName()), NamedTextColor.GREEN));
                }));
    }
}
