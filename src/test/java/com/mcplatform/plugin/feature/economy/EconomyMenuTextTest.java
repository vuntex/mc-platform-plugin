package com.mcplatform.plugin.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendException;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

/** Proves the transfer failures are surfaced cleanly per status: 422 insufficient, 400 self/invalid. */
class EconomyMenuTextTest {

    @Test
    void insufficientFundsIsAClearMessage() {
        MenuMessage msg = EconomyMenuText.transferError(BackendException.fromStatus(422, "no funds"));
        assertEquals(Token.NEGATIVE, msg.text().token());
        assertTrue(msg.text().text().toLowerCase().contains("nicht genug"));
    }

    @Test
    void badRequestIsTheSelfTransferMessage() {
        MenuMessage msg = EconomyMenuText.transferError(
                new CompletionException(BackendException.fromStatus(400, "self")));
        assertTrue(msg.text().text().toLowerCase().contains("ungültig"));
    }

    @Test
    void otherErrorsFallBack() {
        MenuMessage msg = EconomyMenuText.transferError(new RuntimeException("x"));
        assertTrue(msg.text().text().toLowerCase().contains("fehlgeschlagen"));
    }
}
