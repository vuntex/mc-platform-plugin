package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.permission.RoleResponse;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionException;

/**
 * Pure German presentation for the permission feature: role/grant labels and the canonical mapping of a
 * backend failure to a user-facing message (the backend is the authority; a 403 is the truth). Adventure
 * is reached only via the {@link MenuText}/{@link MenuMessage} model — no §-codes — so this stays
 * unit-testable without a server.
 */
public final class PermissionFormat {

    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private PermissionFormat() {
    }

    /** The role's display name (gold), falling back to its technical name when blank. */
    public static MenuText roleName(RoleResponse role) {
        String label = role.displayName() == null || role.displayName().isBlank() ? role.name() : role.displayName();
        return MenuText.name(label, Token.ENTITY);
    }

    /** "Gewicht: N" — value line text. */
    public static String weight(RoleResponse role) {
        return String.valueOf(role.weight());
    }

    /** "Team-Rang" / "Spieler-Rang" marker. */
    public static String rankKind(RoleResponse role) {
        return role.teamRank() ? "Team-Rang" : "Spieler-Rang";
    }

    /** Grant expiry: "permanent" when {@code null}, else a formatted local date-time. */
    public static String expiry(Long expiresAtEpochMilli) {
        return expiresAtEpochMilli == null ? "permanent" : EXPIRY.format(Instant.ofEpochMilli(expiresAtEpochMilli));
    }

    /** HTTP status of a (possibly wrapped) backend failure, or {@code -1} if it is not a backend error. */
    public static int statusOf(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof BackendException be ? be.statusCode() : -1;
    }

    /** Map a (possibly wrapped) backend failure to a transient action-bar message. */
    public static MenuMessage error(Throwable error) {
        return error(statusOf(error));
    }

    /** Map an HTTP status to the canonical German action-bar message. */
    public static MenuMessage error(int status) {
        String message = switch (status) {
            case 403 -> "Dazu fehlt dir die Berechtigung.";
            case 404 -> "Nicht gefunden.";
            case 409 -> "Konflikt — bitte erneut öffnen.";
            case 422 -> "Ungültige Eingabe.";
            case 429 -> "Zu schnell — bitte kurz warten.";
            default -> "Backend nicht erreichbar.";
        };
        return MenuMessage.actionBar(message, Token.NEGATIVE);
    }
}
