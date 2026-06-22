package com.mcplatform.plugin.feature.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcplatform.protocol.report.ReportChangedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Bukkit-free proof of the live-event routing: CREATED pings the team; every change refreshes inboxes. */
class ReportLiveUpdaterTest {

    private ReportChangedEvent event(String changeType) {
        return new ReportChangedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CHEATING", "OPEN", changeType, 1_000L);
    }

    @Test
    void createdPingsTeamAndRefreshesInbox() {
        List<ReportChangedEvent> pinged = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();
        ReportLiveUpdater updater = new ReportLiveUpdater(pinged::add, refreshes::incrementAndGet);

        updater.accept(event("CREATED"));

        assertEquals(1, pinged.size(), "CREATED pings the team");
        assertEquals(1, refreshes.get(), "CREATED refreshes the inbox");
    }

    @Test
    void statusChangedRefreshesInboxButDoesNotPing() {
        List<ReportChangedEvent> pinged = new ArrayList<>();
        AtomicInteger refreshes = new AtomicInteger();
        ReportLiveUpdater updater = new ReportLiveUpdater(pinged::add, refreshes::incrementAndGet);

        updater.accept(event("STATUS_CHANGED"));

        assertEquals(0, pinged.size(), "STATUS_CHANGED does not ping");
        assertEquals(1, refreshes.get(), "STATUS_CHANGED still refreshes the inbox");
    }
}
