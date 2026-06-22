package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.transport.BackendException;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the report label mapping and the per-status-code error messages. */
class ReportFormatTest {

    @Test
    void mapsCategoryAndStatusLabelsToGerman() {
        assertEquals("Cheating", ReportFormat.categoryLabel("CHEATING"));
        assertEquals("Spam / Werbung", ReportFormat.categoryLabel("SPAM_WERBUNG"));
        assertEquals("In Bearbeitung", ReportFormat.statusLabel("IN_PROGRESS"));
        assertEquals("Abgelehnt", ReportFormat.statusLabel("REJECTED"));
        // Unknown wire values pass through unchanged (forward-compatible).
        assertEquals("FUTURE", ReportFormat.categoryLabel("FUTURE"));
    }

    @Test
    void eachBackendStatusGetsItsOwnMessage() {
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(422, "self")).contains("selbst"));
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(429, null)).contains("warte"));
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(403, null)).contains("Berechtigung"));
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(404, null)).contains("nicht gefunden"));
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(409, null)).contains("Statuswechsel"));
        // 5xx and transport failures fall through to the generic retry hint.
        assertTrue(ReportFormat.errorText(BackendException.fromStatus(500, null)).contains("später"));
        assertTrue(ReportFormat.errorText(BackendException.transportFailure("timeout", null)).contains("später"));
    }

    @Test
    void cooldownIsDetectedAndShownGenerically() {
        assertTrue(ReportFormat.isCooldown(BackendException.fromStatus(429, null)));
        assertFalse(ReportFormat.isCooldown(BackendException.fromStatus(422, null)));
        // The 429 message is the generic cooldown text (no countdown).
        assertEquals(ReportFormat.cooldownText(), ReportFormat.errorText(BackendException.fromStatus(429, null)));
    }

    @Test
    void unwrapsCompletionExceptionToReachTheBackendStatus() {
        Throwable wrapped = new CompletionException(BackendException.fromStatus(429, null));
        assertTrue(ReportFormat.errorText(wrapped).contains("warte"));
    }

    @Test
    void confirmationNamesTargetAndCategory() {
        String text = ReportFormat.confirmationText("CHEATING", "Steve");
        assertTrue(text.contains("Steve"));
        assertTrue(text.contains("Cheating"));
    }
}
