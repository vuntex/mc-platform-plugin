package com.mcplatform.plugin.feature.punishment;

import java.util.UUID;

/**
 * One active punishment held in the per-player {@link PunishmentSnapshot} cached locally. Immutable;
 * {@code expiresAtEpochMilli == 0} encodes "permanent / not time-bound" (WARN, PERMABAN). {@code version}
 * is the originating {@code sequence_no} so the generic version-aware cache can keep the newest state.
 */
record ActivePunishment(UUID id, String type, String reason, long expiresAtEpochMilli, long version) {

    /** Still in effect at {@code nowEpochMilli}? Permanent (expiresAt 0) is always active. */
    boolean isActiveAt(long nowEpochMilli) {
        return expiresAtEpochMilli == 0L || expiresAtEpochMilli > nowEpochMilli;
    }
}
