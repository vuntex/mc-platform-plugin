package com.mcplatform.plugin.feature.health;

/**
 * Bukkit-free state machine for backend health → maintenance lockdown. Locks after
 * {@code failureThreshold} consecutive failed probes (so a single blip doesn't lock the server) and
 * unlocks on the first success. {@code isLocked()} is read by the lockdown listeners (possibly off the
 * main thread, e.g. async chat) → kept on a {@code volatile}; the record methods run on the main thread
 * but are {@code synchronized} for safety.
 */
public final class BackendHealthMonitor {

    /** What a probe result changed, so the feature can broadcast exactly on the edges. */
    public enum Transition { NONE, LOCKED, UNLOCKED }

    private final int failureThreshold;
    private volatile boolean locked = false;
    private int consecutiveFailures = 0;

    public BackendHealthMonitor(int failureThreshold) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
    }

    /** A healthy probe: reset the failure run; unlock if we were locked. */
    public synchronized Transition recordSuccess() {
        consecutiveFailures = 0;
        if (locked) {
            locked = false;
            return Transition.UNLOCKED;
        }
        return Transition.NONE;
    }

    /** A failed probe: count it; lock once the threshold is reached (and we aren't already locked). */
    public synchronized Transition recordFailure() {
        consecutiveFailures++;
        if (!locked && consecutiveFailures >= failureThreshold) {
            locked = true;
            return Transition.LOCKED;
        }
        return Transition.NONE;
    }

    public boolean isLocked() {
        return locked;
    }

    /** Diagnostics/tests. */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
