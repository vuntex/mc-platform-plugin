package com.mcplatform.plugin.feature.scoreboard.condition;

import java.util.Optional;
import java.util.UUID;

/**
 * Port: the player's current region, or empty. The real region system will replace ONLY the
 * implementation (spec FR-005) — the resolver and conditions stay unchanged.
 */
public interface RegionProvider {

    Optional<RegionId> currentRegion(UUID player);
}
