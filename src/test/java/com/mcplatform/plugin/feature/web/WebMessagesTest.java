package com.mcplatform.plugin.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the {@code /web} chat content: URL building, the clickable link, error wording. */
class WebMessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void buildUrlSubstitutesTheTokenIntoTheTemplate() {
        assertEquals("https://web.example.com/set-password?token=abc123",
                WebMessages.buildUrl("https://web.example.com/set-password?token={token}", "abc123"));
    }

    @Test
    void linkSuccessIsAClickableOpenUrlWithTheBuiltLink() {
        String url = "https://web.example.com/set-password?token=tok-1";
        Component msg = WebMessages.linkSuccess(url);

        ClickEvent click = findOpenUrl(msg);
        assertEquals(ClickEvent.Action.OPEN_URL, click.action());
        assertEquals(url, click.value(), "the player clicks the exact built link — no retyping");
        assertTrue(PLAIN.serialize(msg).contains("erstellen"), "create-account context");
    }

    @Test
    void resetSuccessIsAClickableOpenUrlWithResetContext() {
        String url = "https://web.example.com/reset-password?token=tok-2";
        Component msg = WebMessages.resetSuccess(url);

        assertEquals(url, findOpenUrl(msg).value());
        assertTrue(PLAIN.serialize(msg).toLowerCase().contains("zurückzusetzen"), "reset context");
    }

    @Test
    void alreadyExistsPointsToResetPassword() {
        String out = PLAIN.serialize(WebMessages.alreadyExists());
        assertTrue(out.contains("bereits einen Web-Account"), out);
        assertTrue(out.contains("/web resetPassword"), out);
    }

    @Test
    void noAccountPointsToLink() {
        String out = PLAIN.serialize(WebMessages.noAccount());
        assertTrue(out.contains("noch keinen Web-Account"), out);
        assertTrue(out.contains("/web link"), out);
    }

    @Test
    void cooldownAndGenericNeverLeakInternals() {
        assertTrue(PLAIN.serialize(WebMessages.cooldown()).toLowerCase().contains("warte"));
        assertTrue(PLAIN.serialize(WebMessages.genericError()).toLowerCase().contains("fehlgeschlagen"));
    }

    @Test
    void helpListsBothSubcommands() {
        String out = PLAIN.serialize(WebMessages.help());
        assertTrue(out.contains("/web link"), out);
        assertTrue(out.contains("/web resetPassword"), out);
    }

    /** Walk the component tree and return the first {@code open_url} click event. */
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
