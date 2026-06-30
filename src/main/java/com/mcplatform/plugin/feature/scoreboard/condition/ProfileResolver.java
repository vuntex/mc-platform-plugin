package com.mcplatform.plugin.feature.scoreboard.condition;

import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;
import com.mcplatform.plugin.feature.scoreboard.profile.ProfileCatalog;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import java.util.List;
import java.util.Objects;

/**
 * Selection axis (spec FR-004): an ordered list of {@link ConditionRule}s. {@code resolve} returns the
 * profile of the first matching rule, otherwise the default profile. Rule order = priority. Region is
 * the first rule; further predicates append without changing this class (FR-005).
 */
public final class ProfileResolver {

    private final List<ConditionRule> rules;
    private final ProfileCatalog catalog;

    public ProfileResolver(List<ConditionRule> rules, ProfileCatalog catalog) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public ScoreboardProfile resolve(PlayerContext ctx) {
        for (ConditionRule rule : rules) {
            if (rule.condition().matches(ctx)) {
                return catalog.byId(rule.profileId());
            }
        }
        return catalog.defaultProfile();
    }
}
