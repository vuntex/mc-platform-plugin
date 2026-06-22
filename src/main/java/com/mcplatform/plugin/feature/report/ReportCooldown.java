package com.mcplatform.plugin.feature.report;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side report cooldown gate (UX only — the backend stays authoritative via 429). Tracks, per
 * reporter, when they last successfully filed a report, so {@code /report} can refuse <em>before</em>
 * opening the menu instead of letting the player run the whole flow only to be rejected at CREATE.
 *
 * <p>Mirrors the backend's window ({@link #WINDOW_MILLIS}). RAM-only: a restart clears it, in which case
 * the backend 429 is the safety net (and re-arms this gate via {@link #markReported}). Thread-safe — the
 * command checks on the main thread, the chat-input listener marks from the async chat thread.
 */
public final class ReportCooldown {

    /** Mirrors the backend report cooldown (5 minutes). */
    static final long WINDOW_MILLIS = 5L * 60L * 1000L;

    private final Map<UUID, Long> lastReportAt = new ConcurrentHashMap<>();

    /** Record that {@code reporter} just filed a report (or was told they are on cooldown). */
    public void markReported(UUID reporter) {
        lastReportAt.put(reporter, System.currentTimeMillis());
    }

    /** True if {@code reporter} is still inside the cooldown window. */
    public boolean isCoolingDown(UUID reporter) {
        Long last = lastReportAt.get(reporter);
        return last != null && System.currentTimeMillis() - last < WINDOW_MILLIS;
    }
}
