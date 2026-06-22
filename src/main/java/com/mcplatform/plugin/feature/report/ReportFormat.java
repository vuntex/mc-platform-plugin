package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.transport.BackendException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Pure presentation + error-mapping rules for the report feature — Bukkit-free so every branch is
 * unit-testable. Maps the backend's normalised failures (400/403/404/409/422/429/5xx) to friendly
 * German messages. 403 and 429 are intentionally <em>not</em> their own {@link BackendException}
 * subclasses (that would mean editing the sealed generic type) — they arrive as a
 * {@link BackendException.BackendError} carrying the real {@link BackendException#statusCode()}, which we
 * inspect here.
 */
final class ReportFormat {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private ReportFormat() {
    }

    static String categoryLabel(String wire) {
        return ReportCategory.labelOf(wire);
    }

    static String statusLabel(String wire) {
        return ReportStatus.labelOf(wire);
    }

    static String time(long epochMilli) {
        return TIME.format(Instant.ofEpochMilli(epochMilli));
    }

    static String confirmationText(String categoryWire, String targetName) {
        return "Deine Meldung gegen " + targetName + " (" + categoryLabel(categoryWire)
                + ") ist eingegangen. Danke!";
    }

    /** Generic cooldown message (no countdown) — shown by the /report gate and on a backend 429. */
    static String cooldownText() {
        return "Du musst noch etwas warten, bevor du wieder einen Spieler melden kannst.";
    }

    /** Whether a failed call is the report cooldown (HTTP 429). */
    static boolean isCooldown(Throwable error) {
        return unwrap(error) instanceof BackendException be && be.statusCode() == 429;
    }

    /** Friendly message for a failed report call, by HTTP status (feature-local, no generic change). */
    static String errorText(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof BackendException be) {
            return switch (be.statusCode()) {
                case 422 -> "Meldung ungültig (z. B. du kannst dich nicht selbst melden, "
                        + "oder der Grund fehlt/ist zu lang).";
                case 429 -> cooldownText();
                case 403 -> "Dir fehlt die Berechtigung dafür.";
                case 404 -> "Meldung nicht gefunden.";
                case 409 -> "Statuswechsel nicht möglich – die Meldung wurde gerade bereits geändert.";
                default -> "Aktuell nicht möglich – bitte später erneut versuchen.";
            };
        }
        return "Aktuell nicht möglich – bitte später erneut versuchen.";
    }

    /** Unwrap the future-composition wrappers to reach the real {@link BackendException}. */
    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
