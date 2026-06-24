package com.mcplatform.plugin.feature.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.core.EndpointDescriptor;
import com.mcplatform.protocol.webauth.TokenResponse;
import com.mcplatform.protocol.webauth.WebAuthEndpoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Bukkit-free proof of the {@code /web} flow: each subcommand hits the right web-auth endpoint with the
 * sender's UUID, a success builds the configured clickable link, and the 409/429/5xx paths surface a
 * clear message instead of a crash. Drives {@link WebCommand#request} directly with a recording fake
 * backend (same style as the economy/transport feature tests).
 */
class WebCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String LINK_TEMPLATE = "https://web.example.com/set-password?token={token}";
    private static final String RESET_TEMPLATE = "https://web.example.com/reset-password?token={token}";

    private final UUID player = UUID.randomUUID();

    /** Runs both hops inline so the captured message is ready synchronously. */
    private static final class InlineScheduler implements PlatformScheduler {
        @Override public void runSync(Runnable task) {
            task.run();
        }

        @Override public void runAsync(Runnable task) {
            task.run();
        }
    }

    /** Records the endpoint + path vars of the last call and returns a canned future. */
    private static final class RecordingBackend implements BackendClient {
        EndpointDescriptor<?, ?> endpoint;
        String[] pathVars;
        Object body = new Object();
        private final CompletableFuture<?> result;

        RecordingBackend(CompletableFuture<?> result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> call(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            this.endpoint = e;
            this.body = b;
            this.pathVars = v;
            return (CompletableFuture<RES>) result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <REQ, RES> CompletableFuture<RES> callIdempotent(EndpointDescriptor<REQ, RES> e, REQ b, String... v) {
            return (CompletableFuture<RES>) result;
        }
    }

    private WebCommand command(BackendClient backend) {
        return new WebCommand(backend, new InlineScheduler(), LINK_TEMPLATE, RESET_TEMPLATE);
    }

    private Component capture(WebCommand command, WebCommand.Action action) {
        List<Component> sent = new ArrayList<>();
        command.request(action, player, sent::add);
        assertEquals(1, sent.size(), "exactly one chat reply");
        return sent.get(0);
    }

    @Test
    void linkCallsRequestLinkWithTheSenderUuidAndBuildsTheClickableLink() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.completedFuture(new TokenResponse("tok-abc", "LINK", 0L)));

        Component msg = capture(command(backend), WebCommand.Action.LINK);

        assertSame(WebAuthEndpoints.REQUEST_LINK, backend.endpoint, "uses the LINK endpoint");
        assertArrayEquals(new String[] {player.toString()}, backend.pathVars, "UUID is the only path var");
        assertEquals("https://web.example.com/set-password?token=tok-abc", findOpenUrl(msg).value());
    }

    @Test
    void resetPasswordCallsRequestResetWithTheSenderUuid() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.completedFuture(new TokenResponse("tok-xyz", "RESET", 0L)));

        Component msg = capture(command(backend), WebCommand.Action.RESET);

        assertSame(WebAuthEndpoints.REQUEST_RESET, backend.endpoint, "uses the RESET endpoint");
        assertArrayEquals(new String[] {player.toString()}, backend.pathVars);
        assertEquals("https://web.example.com/reset-password?token=tok-xyz", findOpenUrl(msg).value());
    }

    @Test
    void linkOnExistingAccountIsExplainedNotCrashed() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.failedFuture(BackendException.fromStatus(409, "{\"error\":\"web_account_exists\"}")));

        String out = PLAIN.serialize(capture(command(backend), WebCommand.Action.LINK));
        assertTrue(out.contains("bereits einen Web-Account"), out);
        assertTrue(out.contains("/web resetPassword"), out);
    }

    @Test
    void resetWithoutAccountIsExplainedNotCrashed() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.failedFuture(BackendException.fromStatus(409, "{\"error\":\"web_account_missing\"}")));

        String out = PLAIN.serialize(capture(command(backend), WebCommand.Action.RESET));
        assertTrue(out.contains("noch keinen Web-Account"), out);
        assertTrue(out.contains("/web link"), out);
    }

    @Test
    void cooldownIsAFriendlyMessage() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.failedFuture(BackendException.fromStatus(429, "cooldown")));

        String out = PLAIN.serialize(capture(command(backend), WebCommand.Action.LINK)).toLowerCase();
        assertTrue(out.contains("warte"), out);
    }

    @Test
    void serverErrorFallsBackToAGenericMessage() {
        RecordingBackend backend = new RecordingBackend(
                CompletableFuture.failedFuture(BackendException.fromStatus(500, "boom")));

        String out = PLAIN.serialize(capture(command(backend), WebCommand.Action.RESET)).toLowerCase();
        assertTrue(out.contains("fehlgeschlagen"), out);
    }

    private static ClickEvent findOpenUrl(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
            return click;
        }
        for (Component child : component.children()) {
            ClickEvent found = findOpenUrl(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
