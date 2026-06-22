package com.mcplatform.plugin.feature.report;

import com.mcplatform.protocol.report.ReportChangedEvent;

import java.util.function.Consumer;

/**
 * Routes a decoded {@code mc:report:changed} event (delivered on the main thread by the EventBus): a
 * {@code CREATED} event pings the online team; <em>every</em> change (CREATED or STATUS_CHANGED) nudges
 * the LIVE inbox to re-read. The event itself carries no chatContext and no version — staleness is
 * handled where the data is read (the inbox's full LIST_OPEN refresh + latest-request-wins guard), not
 * here. Both collaborators are injected as plain seams, so the routing is unit-testable without Bukkit.
 */
public final class ReportLiveUpdater implements Consumer<ReportChangedEvent> {

    private static final String CREATED = "CREATED";

    private final Consumer<ReportChangedEvent> onCreated;
    private final Runnable onAnyChange;

    public ReportLiveUpdater(Consumer<ReportChangedEvent> onCreated, Runnable onAnyChange) {
        this.onCreated = onCreated;
        this.onAnyChange = onAnyChange;
    }

    @Override
    public void accept(ReportChangedEvent event) {
        if (CREATED.equals(event.changeType())) {
            onCreated.accept(event);
        }
        onAnyChange.run();
    }
}
