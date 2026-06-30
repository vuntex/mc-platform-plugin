package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.profile.ProfileCatalog;
import com.mcplatform.plugin.feature.scoreboard.profile.Profiles;
import com.mcplatform.plugin.feature.scoreboard.provider.LineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.StubLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Catalog wiring: Default present with the blueprint lines incl. a structurally-full stub (AC-4). */
class ProfileCatalogTest {

    private static final LineProvider COINS = c -> Component.text("Coins: x");
    private static final LineProvider RANK = c -> Component.text("Rang: x");

    @Test
    void defaultProfileHasBlueprintLines() {
        ProfileCatalog catalog = Profiles.catalog(COINS, RANK);
        assertEquals(Profiles.DEFAULT_ID, catalog.defaultProfile().id());
        var ids = catalog.defaultProfile().lines().stream().map(ScoreboardLine::id).toList();
        // Label/value/blank pattern: Rank label+value, Münzen label+value, Kills label+value.
        assertTrue(ids.containsAll(java.util.List.of(
                Profiles.RANK_LABEL, Profiles.RANK, Profiles.COINS_LABEL, Profiles.COINS,
                Profiles.STATS_LABEL, Profiles.STATS)));
    }

    @Test
    void stubLineIsStructurallyFull() {
        ProfileCatalog catalog = Profiles.catalog(COINS, RANK);
        ScoreboardLine stats = catalog.defaultProfile().lines().stream()
                .filter(l -> l.id().equals(Profiles.STATS)).findFirst().orElseThrow();
        // A full line with a provider that yields a fixed placeholder value — swappable later (AC-4).
        assertNotNull(stats.provider());
        assertEquals(Component.text("0", NamedTextColor.WHITE),
                stats.provider().resolve(new PlayerContext(UUID.randomUUID(), Optional.empty())));
    }

    @Test
    void providerSwapChangesOnlyTheBinding() {
        // Swapping the stub for a "real" provider is just a different binding at the same line id.
        LineProvider real = c -> Component.text("Kills: 42");
        ScoreboardLine swapped = new ScoreboardLine(Profiles.STATS, real);
        assertEquals(Profiles.STATS, swapped.id()); // id unchanged
        assertEquals(Component.text("Kills: 42"),
                swapped.provider().resolve(new PlayerContext(UUID.randomUUID(), Optional.empty())));
    }

    @Test
    void testEventProfileResolvable() {
        ProfileCatalog catalog = Profiles.catalog(COINS, RANK);
        assertEquals(Profiles.TEST_EVENT_ID, catalog.byId(Profiles.TEST_EVENT_ID).id());
    }

    // Reference kept so the import stays meaningful if the blueprint changes to an explicit stub check.
    @SuppressWarnings("unused")
    private static StubLineProvider unused() {
        return new StubLineProvider(Component.text("x"));
    }
}
