package com.mcplatform.plugin.feature.scoreboard.condition;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Slice-1 stub: returns a single configurable region for everyone (or empty). Lets AC-2 be tested
 * without a real region system. The real {@link RegionProvider} replaces only this class.
 */
public final class StubRegionProvider implements RegionProvider {

    private final AtomicReference<RegionId> region = new AtomicReference<>(null);

    /** Set the region every player is reported to be in, or {@code null} for "no region". */
    public void setRegion(RegionId region) {
        this.region.set(region);
    }

    @Override
    public Optional<RegionId> currentRegion(UUID player) {
        return Optional.ofNullable(region.get());
    }
}
