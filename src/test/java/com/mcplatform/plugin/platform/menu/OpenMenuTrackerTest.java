package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves the per-player subscription bookkeeping that backs the central listener's leak guarantee:
 * releasing on close closes the handle; opening a new menu for the same player closes the previous
 * handle (menu→menu navigation); and at 200 players, releasing all leaves nothing tracked.
 */
class OpenMenuTrackerTest {

    /** A handle that records whether it was closed (and how often). */
    private static final class CountingHandle implements LiveHandle {
        int closes;

        @Override
        public void close() {
            closes++;
        }
    }

    @Test
    void releaseClosesAndForgetsTheHandle() {
        OpenMenuTracker tracker = new OpenMenuTracker();
        UUID player = UUID.randomUUID();
        CountingHandle handle = new CountingHandle();

        tracker.track(player, handle);
        assertEquals(1, tracker.activeCount());

        tracker.release(player);
        assertTrue(handle.closes >= 1, "handle closed on release");
        assertEquals(0, tracker.activeCount());
    }

    @Test
    void trackingANewMenuClosesThePreviousSubscription() {
        OpenMenuTracker tracker = new OpenMenuTracker();
        UUID player = UUID.randomUUID();
        CountingHandle first = new CountingHandle();
        CountingHandle second = new CountingHandle();

        tracker.track(player, first);
        tracker.track(player, second); // navigated to another menu without an explicit release first

        assertEquals(1, first.closes, "previous subscription closed on re-track");
        assertEquals(0, second.closes, "new subscription stays open");
        assertEquals(1, tracker.activeCount());
    }

    @Test
    void releaseOfUnknownPlayerIsHarmless() {
        OpenMenuTracker tracker = new OpenMenuTracker();
        tracker.release(UUID.randomUUID()); // no throw
        assertEquals(0, tracker.activeCount());
    }

    @Test
    void noSubscriptionsRemainAfter200PlayersClose() {
        OpenMenuTracker tracker = new OpenMenuTracker();
        CountingHandle[] handles = new CountingHandle[200];
        UUID[] players = new UUID[200];
        for (int i = 0; i < 200; i++) {
            players[i] = UUID.randomUUID();
            handles[i] = new CountingHandle();
            tracker.track(players[i], handles[i]);
        }
        assertEquals(200, tracker.activeCount());

        for (int i = 0; i < 200; i++) {
            tracker.release(players[i]);
        }
        assertEquals(0, tracker.activeCount());
        for (CountingHandle handle : handles) {
            assertFalse(handle.closes == 0, "every handle was closed");
        }
    }
}
