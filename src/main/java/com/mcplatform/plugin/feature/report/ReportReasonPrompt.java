package com.mcplatform.plugin.feature.report;

import com.mcplatform.protocol.report.ChatMessage;
import com.mcplatform.protocol.report.CreateReportRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the in-flight "now type your reason" state per reporter, between picking a category in the menu
 * and sending the reason in chat. Thread-safe (the menu click runs on main, the chat read runs async).
 * Bukkit-free → the state machine and the request-building are unit-testable.
 */
public final class ReportReasonPrompt {

    /** A pending report awaiting its free-text reason. */
    public record Pending(UUID target, String targetName, String category, long createdAtMillis) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** Begin (or replace) a reporter's pending reason input after a category was chosen. */
    public void begin(UUID reporter, UUID target, String targetName, String category) {
        pending.put(reporter, new Pending(target, targetName, category, System.currentTimeMillis()));
    }

    /** Remove and return the reporter's pending input, if any (the chat line consumes it exactly once). */
    public Optional<Pending> take(UUID reporter) {
        return Optional.ofNullable(pending.remove(reporter));
    }

    /** Drop a pending input without consuming it (cancel word / disconnect). */
    public void cancel(UUID reporter) {
        pending.remove(reporter);
    }

    boolean hasPending(UUID reporter) {
        return pending.containsKey(reporter);
    }

    /** Build the CREATE body from a pending entry + the typed reason + the chat snapshot. */
    public static CreateReportRequest request(UUID reporter, Pending pending, String detail,
                                              List<ChatMessage> chatContext) {
        return new CreateReportRequest(reporter, pending.target(), pending.category(), detail, chatContext);
    }
}
