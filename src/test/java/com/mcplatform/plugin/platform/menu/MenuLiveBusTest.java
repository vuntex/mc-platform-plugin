package com.mcplatform.plugin.platform.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves the LIVE observer fan-out and — most importantly — that closing a subscription cleans up, so no
 * observer is left hanging after a menu closes (MENU_DESIGN §6: "kein Leak, keine hängende Beobachtung",
 * "bei 200 Spielern dürfen sich keine Beobachter ansammeln").
 */
class MenuLiveBusTest {

    @Test
    void notifyRunsOnlyObserversForThatTopic() {
        MenuLiveBus bus = new MenuLiveBus();
        int[] a = {0};
        int[] b = {0};
        bus.observe("alice", () -> a[0]++);
        bus.observe("bob", () -> b[0]++);

        bus.notifyChange("alice");
        assertEquals(1, a[0]);
        assertEquals(0, b[0]);
    }

    @Test
    void closingAHandleRemovesItsObserver() {
        MenuLiveBus bus = new MenuLiveBus();
        int[] hits = {0};
        LiveHandle handle = bus.observe("alice", () -> hits[0]++);

        bus.notifyChange("alice");
        assertEquals(1, hits[0]);

        handle.close();
        assertEquals(0, bus.observerCount("alice"), "topic pruned after last observer closes");
        bus.notifyChange("alice");
        assertEquals(1, hits[0], "closed observer never fires again");
    }

    @Test
    void closeIsIdempotent() {
        MenuLiveBus bus = new MenuLiveBus();
        LiveHandle h1 = bus.observe("alice", () -> {
        });
        bus.observe("alice", () -> {
        });
        h1.close();
        h1.close(); // double close must not remove the other observer
        assertEquals(1, bus.observerCount("alice"));
    }

    @Test
    void noObserversAccumulateAcross200OpenAndClose() {
        MenuLiveBus bus = new MenuLiveBus();
        for (int i = 0; i < 200; i++) {
            UUID player = UUID.randomUUID();
            LiveHandle handle = bus.observe(player, () -> {
            });
            // ... menu open ... then closed:
            handle.close();
        }
        assertEquals(0, bus.totalObservers(), "no hanging observers after 200 open/close cycles");
    }
}
