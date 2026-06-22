package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.Cooldowns;

import java.util.UUID;

/**
 * Client-side report cooldown gate (UX only — the backend stays authoritative via 429). A thin,
 * domain-named wrapper over the shared {@link Cooldowns} utility: {@code /report} refuses <em>before</em>
 * opening the menu instead of letting the player run the whole flow only to be rejected at CREATE.
 * Mirrors the backend's window ({@link #WINDOW_MILLIS}). RAM-only — a restart clears it, and the backend
 * 429 re-arms it via {@link #markReported}.
 */
public final class ReportCooldown {

    /** Mirrors the backend report cooldown (5 minutes). */
    static final long WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final String TYPE = "report";

    private final Cooldowns cooldowns = new Cooldowns();

    /** Record that {@code reporter} just filed a report (or was told they are on cooldown). */
    public void markReported(UUID reporter) {
        cooldowns.mark(reporter, TYPE);
    }

    /** True if {@code reporter} is still inside the cooldown window. */
    public boolean isCoolingDown(UUID reporter) {
        return cooldowns.isActive(reporter, TYPE, WINDOW_MILLIS);
    }
}
