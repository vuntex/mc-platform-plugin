package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendException;

import java.util.concurrent.CompletionException;

/**
 * Pure text mapping for the economy menus — Bukkit-free so the backend-failure rules are unit-testable.
 * The transfer endpoint's normalised errors are shown cleanly in the menu: {@code 422} insufficient
 * funds and {@code 400} (e.g. self-transfer / invalid amount) get their own messages, everything else a
 * generic retry hint.
 */
final class EconomyMenuText {

    private EconomyMenuText() {
    }

    static MenuMessage transferError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException be) {
            return switch (be.statusCode()) {
                case 422 -> MenuMessage.actionBar("Nicht genug Coins für diesen Transfer.", Token.NEGATIVE);
                case 400 -> MenuMessage.actionBar("Ungültiger Transfer (z. B. an dich selbst).", Token.NEGATIVE);
                case 404 -> MenuMessage.actionBar("Zielspieler nicht gefunden.", Token.NEGATIVE);
                default -> MenuMessage.actionBar("Transfer fehlgeschlagen – bitte erneut versuchen.", Token.NEGATIVE);
            };
        }
        return MenuMessage.actionBar("Transfer fehlgeschlagen – bitte erneut versuchen.", Token.NEGATIVE);
    }

    static MenuMessage transferSuccess(long amount, String currency, String toName) {
        return MenuMessage.chat(amount + " " + currency + " an " + toName + " gesendet.", Token.POSITIVE);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
