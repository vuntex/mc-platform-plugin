package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.protocol.report.ChatMessage;
import com.mcplatform.protocol.report.CreateReportRequest;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the reason-prompt state machine and CREATE-request building. */
class ReportReasonPromptTest {

    private final UUID reporter = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void takeConsumesThePendingExactlyOnce() {
        ReportReasonPrompt prompt = new ReportReasonPrompt();
        prompt.begin(reporter, target, "Steve", "CHEATING");
        assertTrue(prompt.hasPending(reporter));

        assertTrue(prompt.take(reporter).isPresent());
        assertFalse(prompt.hasPending(reporter));
        assertTrue(prompt.take(reporter).isEmpty(), "second take returns nothing");
    }

    @Test
    void cancelDropsThePending() {
        ReportReasonPrompt prompt = new ReportReasonPrompt();
        prompt.begin(reporter, target, "Steve", "CHEATING");
        prompt.cancel(reporter);
        assertTrue(prompt.take(reporter).isEmpty());
    }

    @Test
    void buildsCreateRequestFromPendingPlusReasonPlusChatSnapshot() {
        ReportReasonPrompt prompt = new ReportReasonPrompt();
        prompt.begin(reporter, target, "Steve", "CHEATING");
        ReportReasonPrompt.Pending pending = prompt.take(reporter).orElseThrow();

        List<ChatMessage> chat = List.of(new ChatMessage(target, "gg ez hax", 1_000L));
        CreateReportRequest request = ReportReasonPrompt.request(reporter, pending, "er cheatet", chat);

        assertEquals(reporter, request.reporter());
        assertEquals(target, request.target());
        assertEquals("CHEATING", request.category());
        assertEquals("er cheatet", request.detail());
        assertEquals(1, request.chatContext().size());
        assertEquals("gg ez hax", request.chatContext().get(0).text());
    }
}
