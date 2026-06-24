package com.mcplatform.plugin.feature.web;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.webauth.TokenResponse;
import com.mcplatform.protocol.webauth.WebAuthEndpoints;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * {@code /web} — the ingame web-account commands, a pure backend client over the web-auth bridge:
 *
 * <ul>
 *   <li>{@code /web link} → {@link WebAuthEndpoints#REQUEST_LINK} (create a web account)</li>
 *   <li>{@code /web resetPassword} → {@link WebAuthEndpoints#REQUEST_RESET} (reset the password)</li>
 *   <li>anything else → a short help text</li>
 * </ul>
 *
 * The in-game session proves the identity (UUID = account ownership), so both calls are player-scoped
 * and carry only the sender's UUID — no account/token logic lives here, the backend is authoritative.
 * The returned {@link TokenResponse} token is woven into the configured frontend URL and handed back as
 * a clickable {@code open_url} link. The REST call runs off the main thread; the chat reply hops back on.
 */
public final class WebCommand implements CommandExecutor {

    /** Which token to request — drives both the endpoint and the URL template. */
    enum Action { LINK, RESET }

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final String linkUrlTemplate;
    private final String resetUrlTemplate;

    public WebCommand(BackendClient backend, PlatformScheduler scheduler,
                      String linkUrlTemplate, String resetUrlTemplate) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.linkUrlTemplate = linkUrlTemplate;
        this.resetUrlTemplate = resetUrlTemplate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(WebMessages.consoleOnly());
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(WebMessages.help());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link" -> request(Action.LINK, player.getUniqueId(), player::sendMessage);
            case "resetpassword" -> request(Action.RESET, player.getUniqueId(), player::sendMessage);
            default -> player.sendMessage(WebMessages.help());
        }
        return true;
    }

    /**
     * Bukkit-free core: request a token for {@code uuid} and deliver the resulting chat message to
     * {@code sink} on the main thread. Package-visible so the flow is unit-testable without a Player.
     */
    void request(Action action, UUID uuid, Consumer<Component> sink) {
        EndpointDescriptor<Void, TokenResponse> endpoint =
                action == Action.LINK ? WebAuthEndpoints.REQUEST_LINK : WebAuthEndpoints.REQUEST_RESET;
        backend.call(endpoint, null, uuid.toString())
                .whenComplete((token, error) ->
                        scheduler.runSync(() -> sink.accept(message(action, token, error))));
    }

    private Component message(Action action, TokenResponse token, Throwable error) {
        if (error != null || token == null) {
            return errorMessage(action, error);
        }
        String url = WebMessages.buildUrl(
                action == Action.LINK ? linkUrlTemplate : resetUrlTemplate, token.token());
        return action == Action.LINK ? WebMessages.linkSuccess(url) : WebMessages.resetSuccess(url);
    }

    /**
     * Translate a backend failure into a clear chat line. A 409 is unambiguous per command (the backend
     * maps both "exists" and "missing" to 409, but each only happens on its own endpoint); 429 is the
     * cooldown; everything else (5xx, network) is a generic, non-leaking message.
     */
    private static Component errorMessage(Action action, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException.Conflict) {
            return action == Action.LINK ? WebMessages.alreadyExists() : WebMessages.noAccount();
        }
        if (cause instanceof BackendException backend && backend.statusCode() == 429) {
            return WebMessages.cooldown();
        }
        return WebMessages.genericError();
    }

    /** Futures complete exceptionally with the raw cause, but compositions wrap it in CompletionException. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
