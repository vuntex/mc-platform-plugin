package com.mcplatform.plugin.feature.scoreboard.profile;

import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Lookup id → profile plus the default-profile reference (spec FR-004 fallback). */
public final class ProfileCatalog {

    private final Map<String, ScoreboardProfile> byId = new LinkedHashMap<>();
    private final String defaultId;

    public ProfileCatalog(String defaultId, ScoreboardProfile... profiles) {
        this.defaultId = Objects.requireNonNull(defaultId, "defaultId");
        for (ScoreboardProfile profile : profiles) {
            byId.put(profile.id(), profile);
        }
        if (!byId.containsKey(defaultId)) {
            throw new IllegalArgumentException("Default profile not in catalog: " + defaultId);
        }
    }

    public ScoreboardProfile byId(String id) {
        ScoreboardProfile profile = byId.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile id: " + id);
        }
        return profile;
    }

    public ScoreboardProfile defaultProfile() {
        return byId.get(defaultId);
    }
}
