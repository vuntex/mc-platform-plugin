package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcplatform.plugin.feature.scoreboard.condition.ConditionRule;
import com.mcplatform.plugin.feature.scoreboard.condition.ProfileResolver;
import com.mcplatform.plugin.feature.scoreboard.condition.RegionCondition;
import com.mcplatform.plugin.feature.scoreboard.condition.RegionId;
import com.mcplatform.plugin.feature.scoreboard.profile.ProfileCatalog;
import com.mcplatform.plugin.feature.scoreboard.profile.Profiles;
import com.mcplatform.plugin.feature.scoreboard.provider.LineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Resolver: no rule → default (AC-1); region rule → matching profile (AC-2); first match wins. */
class ProfileResolverTest {

    private static final LineProvider DUMMY = c -> Component.text("x");
    private static final RegionId TEST = RegionId.of("test_event");

    private ProfileCatalog catalog() {
        return Profiles.catalog(DUMMY, DUMMY);
    }

    private PlayerContext ctx(Optional<RegionId> region) {
        return new PlayerContext(UUID.randomUUID(), region);
    }

    @Test
    void noRuleFallsBackToDefault() {
        ProfileResolver resolver = new ProfileResolver(List.of(), catalog());
        assertEquals(Profiles.DEFAULT_ID, resolver.resolve(ctx(Optional.empty())).id());
    }

    @Test
    void regionRuleSelectsProfile() {
        ProfileResolver resolver = new ProfileResolver(
                List.of(new ConditionRule(new RegionCondition(TEST), Profiles.TEST_EVENT_ID)), catalog());

        assertEquals(Profiles.DEFAULT_ID, resolver.resolve(ctx(Optional.empty())).id());
        assertEquals(Profiles.TEST_EVENT_ID, resolver.resolve(ctx(Optional.of(TEST))).id());
    }

    @Test
    void firstMatchingRuleWins() {
        ProfileResolver resolver = new ProfileResolver(List.of(
                new ConditionRule(c -> true, Profiles.TEST_EVENT_ID),
                new ConditionRule(c -> true, Profiles.DEFAULT_ID)), catalog());
        assertEquals(Profiles.TEST_EVENT_ID, resolver.resolve(ctx(Optional.empty())).id());
    }
}
