package com.mcplatform.plugin.feature.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendException;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

/**
 * Proves the team-side revoke gate is surfaced cleanly: a backend {@code 403} becomes a "no permission"
 * message (the menu opens optimistically, the backend is authoritative), distinct from other failures.
 */
class PunishmentMenuTextTest {

    @Test
    void forbiddenBecomesAPermissionMessage() {
        MenuMessage msg = PunishmentMenuText.revokeError(BackendException.fromStatus(403, "denied"));
        assertEquals(MenuMessage.Channel.CHAT, msg.channel());
        assertEquals(Token.NEGATIVE, msg.text().token());
        assertTrue(msg.text().text().toLowerCase().contains("berechtigung"), "403 → no-permission text");
    }

    @Test
    void forbiddenIsUnwrappedFromCompletionException() {
        Throwable wrapped = new CompletionException(BackendException.fromStatus(403, "denied"));
        MenuMessage msg = PunishmentMenuText.revokeError(wrapped);
        assertTrue(msg.text().text().toLowerCase().contains("berechtigung"));
    }

    @Test
    void notFoundHasItsOwnMessage() {
        MenuMessage msg = PunishmentMenuText.revokeError(BackendException.fromStatus(404, ""));
        assertTrue(msg.text().text().toLowerCase().contains("nicht gefunden"));
    }

    @Test
    void otherErrorsFallBackToAGenericMessage() {
        MenuMessage msg = PunishmentMenuText.revokeError(new RuntimeException("boom"));
        assertTrue(msg.text().text().toLowerCase().contains("fehlgeschlagen"));
    }
}
