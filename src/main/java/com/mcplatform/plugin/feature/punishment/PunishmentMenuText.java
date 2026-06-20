package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendException;

import java.util.concurrent.CompletionException;

/**
 * Pure text mapping for the punishment menu — kept Bukkit-free so the important rule is unit-testable:
 * a backend {@code 403} (the team member is allowed to <em>open</em> the menu optimistically, but the
 * backend is authoritative on the revoke) is shown cleanly as "no permission", not as a raw error.
 * Mirrors how {@code PunishmentFormat} maps backend failures, but produces a {@link MenuMessage} for the
 * GUI flow.
 */
final class PunishmentMenuText {

    private PunishmentMenuText() {
    }

    /** Map a revoke failure to a user-facing message. {@code 403 → "keine Berechtigung"}. */
    static MenuMessage revokeError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException be && be.statusCode() == 403) {
            return MenuMessage.chat("Das Backend hat die Aktion abgelehnt: keine Berechtigung.", Token.NEGATIVE);
        }
        if (cause instanceof BackendException be && be.statusCode() == 404) {
            return MenuMessage.chat("Strafe nicht gefunden – evtl. bereits aufgehoben.", Token.NEGATIVE);
        }
        return MenuMessage.chat("Aktion fehlgeschlagen – bitte erneut versuchen.", Token.NEGATIVE);
    }

    /** Success confirmation written to chat (lasting, §4.5). */
    static MenuMessage revokeSuccess() {
        return MenuMessage.chat("Strafe aufgehoben.", Token.POSITIVE);
    }

    /** Map an issue-from-template failure: {@code 403 → no permission}, {@code 409 → already active}. */
    static MenuMessage issueError(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException be) {
            return switch (be.statusCode()) {
                case 403 -> MenuMessage.chat("Das Backend hat die Strafe abgelehnt: keine Berechtigung.", Token.NEGATIVE);
                case 409 -> MenuMessage.chat("Es existiert bereits eine aktive Strafe dieser Art.", Token.NEGATIVE);
                default -> MenuMessage.chat("Strafe konnte nicht gesetzt werden – bitte erneut versuchen.", Token.NEGATIVE);
            };
        }
        return MenuMessage.chat("Strafe konnte nicht gesetzt werden – bitte erneut versuchen.", Token.NEGATIVE);
    }

    /** Success confirmation after issuing a punishment. */
    static MenuMessage issueSuccess(String templateKey, String targetName) {
        return MenuMessage.chat("Strafe '" + templateKey + "' für " + targetName + " gesetzt.", Token.POSITIVE);
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
