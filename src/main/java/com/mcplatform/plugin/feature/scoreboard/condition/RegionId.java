package com.mcplatform.plugin.feature.scoreboard.condition;

import java.util.Objects;

/** Opaque identifier of a region. Slice 1 produces these only via the stub provider. */
public record RegionId(String value) {

    public RegionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RegionId value must not be blank");
        }
    }

    public static RegionId of(String value) {
        return new RegionId(value);
    }
}
