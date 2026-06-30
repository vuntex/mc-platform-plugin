package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.text.ChatDesign;
import com.mcplatform.plugin.platform.text.Messages;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /pay <Spieler> <Betrag>} — chat-only coin transfer (no menu). Amounts up to
 * {@code confirmThreshold} send immediately; larger ones are held and confirmed by clicking a chat
 * message ({@code /pay confirm}). Backend stays authoritative on funds (422) and validity (400/404).
 * All feedback uses the central chat design ({@link ChatDesign}/{@link Messages}); prefix "Coins".
 */
public final class PayCommand implements CommandExecutor {

    private static final String FEATURE = "COINS";

    private record Pending(UUID target, String targetName, long amount, long expiresAtMillis) {
    }

    private static final long CONFIRM_TIMEOUT_MILLIS = 60_000L;

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final EconomyReadPort readPort;
    private final String currency;
    private final long confirmThreshold;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PayCommand(BackendClient backend, PlatformScheduler scheduler, EconomyReadPort readPort,
                      String currency, long confirmThreshold) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.readPort = readPort;
        this.currency = currency;
        this.confirmThreshold = confirmThreshold;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.playersOnly());
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            confirmPending(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (pending.remove(player.getUniqueId()) != null) {
                player.sendMessage(prefixed(ChatDesign.text("Zahlung abgebrochen.")));
            } else {
                player.sendMessage(prefixed(ChatDesign.text("Es gibt keine ausstehende Zahlung.")));
            }
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(Messages.usage("/pay <Spieler> <Betrag>"));
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
            ActionBars.error(player, ChatDesign.error("Ungültiger Betrag — bitte eine ganze Zahl angeben."));
            return;
        }
        if (amount <= 0) {
            ActionBars.error(player, ChatDesign.error("Der Betrag muss größer als 0 sein."));
            return;
        }
        OfflinePlayer resolved = resolve(targetArg);
        if (resolved == null || resolved.getUniqueId() == null) {
            ActionBars.error(player, Messages.playerNotFound(targetArg));
            return;
        }
        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            ActionBars.error(player, ChatDesign.error("Du kannst dir nicht selbst Coins senden."));
            return;
        }
        String targetName = resolved.getName() != null ? resolved.getName() : targetArg;
        UUID target = resolved.getUniqueId();

        // Funds check BEFORE confirming/transferring — never ask to confirm a payment that can't go through.
        // Cache-first; if the balance is unknown (cold cache + REST miss) we proceed and let the backend stay
        // authoritative (422). Either way the backend is the final guard.
        final long finalAmount = amount;
        readPort.load(player.getUniqueId()).whenComplete((balance, error) -> scheduler.runSync(() -> {
            if (error == null && balance != null && balance.isPresent() && finalAmount > balance.getAsLong()) {
                ActionBars.error(player, ChatDesign.error("Du hast nicht genug "
                        + EconomyFeature.currencyDisplay(currency) + " — dein Guthaben: "
                        + ChatDesign.number(balance.getAsLong()) + "."));
                return;
            }
            if (finalAmount > confirmThreshold) {
                pending.put(player.getUniqueId(),
                        new Pending(target, targetName, finalAmount, now() + CONFIRM_TIMEOUT_MILLIS));
                sendConfirmPrompt(player, targetName, finalAmount);
                return;
            }
            transfer(player, target, targetName, finalAmount);
        }));
    }

    private void confirmPending(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(prefixed(ChatDesign.text("Es gibt keine ausstehende Zahlung zum Bestätigen.")));
            return;
        }
        if (now() > p.expiresAtMillis()) {
            player.sendMessage(prefixed(ChatDesign.error("Die Bestätigung ist abgelaufen — bitte erneut senden.")));
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
                        ActionBars.error(player, transferError(error)); // transient toast + sound
                        return;
                    }
                    player.sendMessage(prefixed(ChatDesign.success("Du hast ")
                            .append(amountComponent(amount))
                            .append(ChatDesign.success(" an "))
                            .append(ChatDesign.name(targetName))
                            .append(ChatDesign.success(" gesendet."))));
                    Player recipient = Bukkit.getPlayer(target);
                    if (recipient != null) {
                        recipient.sendMessage(prefixed(ChatDesign.success("Du hast ")
                                .append(amountComponent(amount))
                                .append(ChatDesign.success(" von "))
                                .append(ChatDesign.name(player.getName()))
                                .append(ChatDesign.success(" erhalten."))));
                    }
                }));
    }

    // ── messages ──────────────────────────────────────────────────────────────────────────────────

    private void sendConfirmPrompt(Player player, String targetName, long amount) {
        player.sendMessage(Component.empty());
        player.sendMessage(prefixed(Component.text("Großer Betrag — bitte bestätigen", NamedTextColor.GOLD,
                TextDecoration.BOLD)));
        player.sendMessage(prefixed(ChatDesign.text("Du sendest ")
                .append(amountComponent(amount))
                .append(ChatDesign.text(" an "))
                .append(ChatDesign.name(targetName))
                .append(ChatDesign.text("."))));
        Component confirm = Component.text(" [ ✔ Bestätigen ] ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pay confirm"))
                .hoverEvent(HoverEvent.showText(ChatDesign.muted("Klicken, um zu senden")));
        Component cancel = Component.text(" [ ✘ Abbrechen ] ", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pay cancel"))
                .hoverEvent(HoverEvent.showText(ChatDesign.muted("Klicken, um abzubrechen")));
        player.sendMessage(confirm.append(cancel));
        player.sendMessage(Component.empty());
    }

    private Component transferError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException be) {
            return switch (be.statusCode()) {
                case 422 -> ChatDesign.error("Du hast nicht genug " + EconomyFeature.currencyDisplay(currency) + ".");
                case 404 -> ChatDesign.error("Konto wurde nicht gefunden.");
                case 400 -> ChatDesign.error("Ungültige Anfrage.");
                default -> Messages.backendError();
            };
        }
        return Messages.backendError();
    }

    /** Amount segment: number + currency, both highlighted (value yellow). */
    private Component amountComponent(long amount) {
        return ChatDesign.value(ChatDesign.number(amount) + " " + EconomyFeature.currencyDisplay(currency));
    }

    /** Prepend the "Coins »" feature prefix to a message body. */
    private static Component prefixed(Component body) {
        return ChatDesign.prefix(FEATURE, NamedTextColor.GOLD).append(body);
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
