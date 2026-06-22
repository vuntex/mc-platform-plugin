package com.mcplatform.plugin.feature.report;

import com.mcplatform.protocol.report.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A feature-local, RAM-only ring of the last {@link #CAPACITY} <em>public</em> chat messages, server-wide
 * (each tagged with its sender UUID). This is the global window snapshotted as a report's
 * {@code chatContext} at creation time — capturing the reported player's lines AND the surrounding
 * conversation. Not persistent; empty after a restart.
 *
 * <p>Thread-safe: {@link #add} is called from the async chat listener while {@link #snapshot} may be read
 * from a command/async path; both synchronise on the same deque. Bukkit-free → unit-testable.
 */
public final class ChatRingBuffer {

    /** ~20 most recent public messages (FR-007). */
    public static final int CAPACITY = 20;

    private final Deque<ChatMessage> messages = new ArrayDeque<>(CAPACITY);

    /** Append a public message, evicting the oldest once at capacity. */
    public void add(ChatMessage message) {
        synchronized (messages) {
            if (messages.size() >= CAPACITY) {
                messages.removeFirst();
            }
            messages.addLast(message);
        }
    }

    /** An immutable oldest→newest copy of the current window (may be empty). */
    public List<ChatMessage> snapshot() {
        synchronized (messages) {
            return List.copyOf(new ArrayList<>(messages));
        }
    }
}
