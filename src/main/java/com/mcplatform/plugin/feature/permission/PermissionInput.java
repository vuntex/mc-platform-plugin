package com.mcplatform.plugin.feature.permission;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Short text input via chat (MENU_DESIGN §4.6 fallback — the menu framework has no anvil helper, so the
 * established chat-prompt pattern from the report feature is reused). A menu button closes the GUI and
 * calls {@link #prompt}; the next chat line from that player is captured (not broadcast) and delivered
 * back on the main thread. The cancel word aborts. Used for role name, permission string and duration.
 */
public final class PermissionInput implements Listener {

    /** Word a player types to abort an input flow. */
    public static final String CANCEL_WORD = "abbrechen";

    private final com.mcplatform.plugin.platform.PlatformScheduler scheduler;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public PermissionInput(com.mcplatform.plugin.platform.PlatformScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Ask {@code player} for one line of input. Tell them what to type (and how to cancel), then capture
     * their next chat message. {@code onInput} runs on the main thread with the trimmed text.
     */
    public void prompt(Player player, String request, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), onInput);
        player.sendMessage(Component.text(request, NamedTextColor.AQUA));
        player.sendMessage(Component.text("Tippe deine Eingabe in den Chat — oder \""
                + CANCEL_WORD + "\" zum Abbrechen.", NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> waiting = pending.remove(player.getUniqueId());
        if (waiting == null) {
            return;
        }
        event.setCancelled(true); // consumed as input, never shown in public chat
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.equalsIgnoreCase(CANCEL_WORD) || text.isEmpty()) {
            scheduler.runSync(() -> player.sendMessage(Component.text("Abgebrochen.", NamedTextColor.GRAY)));
            return;
        }
        scheduler.runSync(() -> waiting.accept(text));
    }

    /** Drop a pending prompt (e.g. on quit) — no leak across sessions. */
    public void cancel(UUID player) {
        pending.remove(player);
    }
}
