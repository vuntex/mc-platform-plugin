package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.economy.EconomyEndpoints;
import com.mcplatform.protocol.economy.TransferRequest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /pay <Spieler> <Betrag>} — chat-only coin transfer (no menu). Amounts up to
 * {@code confirmThreshold} are sent immediately; a larger amount is held as a pending payment and must be
 * confirmed by clicking a chat message ({@code /pay confirm}). The backend stays authoritative on funds
 * (422) and validity (400/404). All feedback is rendered as styled Adventure components.
 */
public final class PayCommand implements CommandExecutor {

    private record Pending(UUID target, String targetName, long amount, long expiresAtMillis) {
    }

    private static final long CONFIRM_TIMEOUT_MILLIS = 60_000L;

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String currency;
    private final long confirmThreshold;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PayCommand(BackendClient backend, PlatformScheduler scheduler, String currency, long confirmThreshold) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.currency = currency;
        this.confirmThreshold = confirmThreshold;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können Coins senden.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            confirmPending(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (pending.remove(player.getUniqueId()) != null) {
                player.sendMessage(line("Zahlung abgebrochen.", NamedTextColor.GRAY));
            } else {
                player.sendMessage(line("Es gibt keine ausstehende Zahlung.", NamedTextColor.GRAY));
            }
            return true;
        }
        if (args.length != 2) {
            sendUsage(player);
            return true;
        }
        handlePay(player, args[0], args[1]);
        return true;
    }

    private void handlePay(Player player, String targetArg, String amountArg) {
        long amount;
        try {
            amount = Long.parseLong(amountArg.replace(".", "").replace("_", ""));
        } catch (NumberFormatException ex) {
            player.sendMessage(line("Ungültiger Betrag — bitte eine ganze Zahl angeben.", NamedTextColor.RED));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(line("Der Betrag muss größer als 0 sein.", NamedTextColor.RED));
            return;
        }
        OfflinePlayer resolved = resolve(targetArg);
        if (resolved == null || resolved.getUniqueId() == null) {
            player.sendMessage(line("Spieler ", NamedTextColor.RED)
                    .append(Component.text(targetArg, NamedTextColor.WHITE))
                    .append(Component.text(" wurde nicht gefunden.", NamedTextColor.RED)));
            return;
        }
        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(line("Du kannst dir nicht selbst Coins senden.", NamedTextColor.RED));
            return;
        }
        String targetName = resolved.getName() != null ? resolved.getName() : targetArg;

        if (amount > confirmThreshold) {
            pending.put(player.getUniqueId(),
                    new Pending(resolved.getUniqueId(), targetName, amount, now() + CONFIRM_TIMEOUT_MILLIS));
            sendConfirmPrompt(player, targetName, amount);
            return;
        }
        transfer(player, resolved.getUniqueId(), targetName, amount);
    }

    private void confirmPending(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(line("Es gibt keine ausstehende Zahlung zum Bestätigen.", NamedTextColor.GRAY));
            return;
        }
        if (now() > p.expiresAtMillis()) {
            player.sendMessage(line("Die Bestätigung ist abgelaufen — bitte erneut senden.", NamedTextColor.RED));
            return;
        }
        transfer(player, p.target(), p.targetName(), p.amount());
    }

    private void transfer(Player player, UUID target, String targetName, long amount) {
        UUID from = player.getUniqueId();
        TransferRequest request = new TransferRequest(target, amount, UUID.randomUUID(), "PLUGIN:pay");
        backend.callIdempotent(EconomyEndpoints.TRANSFER, request, from.toString(), currency)
                .whenComplete((response, error) -> scheduler.runSync(() -> {
                    if (error != null || response == null) {
                        player.sendMessage(transferError(error));
                        return;
                    }
                    player.sendMessage(line("Du hast ", NamedTextColor.GREEN)
                            .append(amountComponent(amount))
                            .append(Component.text(" an ", NamedTextColor.GREEN))
                            .append(Component.text(targetName, NamedTextColor.AQUA))
                            .append(Component.text(" gesendet.", NamedTextColor.GREEN)));
                    Player recipient = Bukkit.getPlayer(target);
                    if (recipient != null) {
                        recipient.sendMessage(line("Du hast ", NamedTextColor.GREEN)
                                .append(amountComponent(amount))
                                .append(Component.text(" von ", NamedTextColor.GREEN))
                                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                                .append(Component.text(" erhalten.", NamedTextColor.GREEN)));
                    }
                }));
    }

    // ── messages ──────────────────────────────────────────────────────────────────────────────────

    private void sendUsage(Player player) {
        player.sendMessage(line("Verwendung: ", NamedTextColor.GOLD)
                .append(Component.text("/pay <Spieler> <Betrag>", NamedTextColor.GRAY)));
    }

    private void sendConfirmPrompt(Player player, String targetName, long amount) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Großer Betrag — bitte bestätigen", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Du sendest ", NamedTextColor.GRAY)
                .append(amountComponent(amount))
                .append(Component.text(" an ", NamedTextColor.GRAY))
                .append(Component.text(targetName, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GRAY)));
        Component confirm = Component.text(" [ ✔ Bestätigen ] ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pay confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("Klicken, um zu senden", NamedTextColor.GRAY)));
        Component cancel = Component.text(" [ ✘ Abbrechen ] ", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pay cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Klicken, um abzubrechen", NamedTextColor.GRAY)));
        player.sendMessage(confirm.append(cancel));
        player.sendMessage(Component.empty());
    }

    private Component transferError(Throwable error) {
        Throwable cause = unwrap(error);
        String text;
        if (cause instanceof BackendException be) {
            text = switch (be.statusCode()) {
                case 422 -> "Du hast nicht genug " + currency + ".";
                case 404 -> "Konto wurde nicht gefunden.";
                case 400 -> "Ungültige Anfrage.";
                default -> "Transfer fehlgeschlagen. Bitte später erneut versuchen.";
            };
        } else {
            text = "Transfer fehlgeschlagen. Bitte später erneut versuchen.";
        }
        return line(text, NamedTextColor.RED);
    }

    /** Amount segment: the number in gray, the currency in green — reused across messages. */
    private Component amountComponent(long amount) {
        return Component.text(format(amount), NamedTextColor.GRAY)
                .append(Component.text(" " + currency, NamedTextColor.GREEN));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    private static String format(long amount) {
        return String.format(Locale.GERMANY, "%,d", amount); // 50000 → "50.000"
    }

    /** Resolve a name to an online or server-cached offline player, without a blocking Mojang lookup. */
    private @Nullable OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
