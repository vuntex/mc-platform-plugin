package com.mcplatform.plugin.feature.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.health.BackendHealthMonitor.Transition;

import org.junit.jupiter.api.Test;

/** Lock-after-threshold / unlock-on-recovery state machine, plus blip tolerance. */
class BackendHealthMonitorTest {

    @Test
    void locksOnlyAfterThresholdConsecutiveFailures() {
        BackendHealthMonitor m = new BackendHealthMonitor(2);

        assertEquals(Transition.NONE, m.recordFailure()); // 1 failure → still tolerant
        assertFalse(m.isLocked());
        assertEquals(Transition.LOCKED, m.recordFailure()); // 2nd consecutive → lock
        assertTrue(m.isLocked());
    }

    @Test
    void furtherFailuresWhileLockedAreNoTransition() {
        BackendHealthMonitor m = new BackendHealthMonitor(2);
        m.recordFailure();
        m.recordFailure(); // locked
        assertEquals(Transition.NONE, m.recordFailure()); // already locked
        assertTrue(m.isLocked());
    }

    @Test
    void aSuccessBeforeThresholdResetsTheRun() {
        BackendHealthMonitor m = new BackendHealthMonitor(2);
        m.recordFailure();                                  // 1
        assertEquals(Transition.NONE, m.recordSuccess());   // blip recovered, never locked
        assertEquals(0, m.consecutiveFailures());
        assertEquals(Transition.NONE, m.recordFailure());   // count restarts → still tolerant
        assertFalse(m.isLocked());
    }

    @Test
    void unlocksOnFirstSuccessAfterLock() {
        BackendHealthMonitor m = new BackendHealthMonitor(2);
        m.recordFailure();
        m.recordFailure(); // locked
        assertEquals(Transition.UNLOCKED, m.recordSuccess());
        assertFalse(m.isLocked());
        assertEquals(Transition.NONE, m.recordSuccess()); // steady healthy
    }

    @Test
    void thresholdOneLocksImmediately() {
        BackendHealthMonitor m = new BackendHealthMonitor(1);
        assertEquals(Transition.LOCKED, m.recordFailure());
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new BackendHealthMonitor(0));
    }
}
