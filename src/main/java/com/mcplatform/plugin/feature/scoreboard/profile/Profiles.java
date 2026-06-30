package com.mcplatform.plugin.feature.scoreboard.profile;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;
import com.mcplatform.plugin.feature.scoreboard.provider.LineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.StaticLineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.StubLineProvider;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The hardcoded profile definitions (spec FR-001). The DEFINITION lives in code; the VALUES come from
 * providers (no string-building in the renderer). Profiles are built at enable-time because the real
 * providers carry injected read-ports.
 *
 * <p>Layout pattern (top → bottom): a coloured, bold LABEL line, then the VALUE line, then a blank
 * separator — e.g. "Rank" / Admin / (blank) / "Münzen" / 500 / (blank) / … The "MC Platform" header is
 * the coloured sidebar TITLE (see {@code BukkitScoreboardHandle}), so the lines start at Rank.
 */
public final class Profiles {

    public static final String DEFAULT_ID = "Default";
    public static final String TEST_EVENT_ID = "TEST_EVENT";

    // Label / value / blank line ids (value ids stay stable for live addressing + provider swap).
    public static final LineId FIRST_BLANK = LineId.of("first-blank");
    public static final LineId RANK_LABEL = LineId.of("rank_label");
    public static final LineId RANK = LineId.of("rank");
    public static final LineId BLANK1 = LineId.of("blank1");
    public static final LineId COINS_LABEL = LineId.of("coins_label");
    public static final LineId COINS = LineId.of("coins");
    public static final LineId BLANK2 = LineId.of("blank2");
    public static final LineId STATS_LABEL = LineId.of("stats_label");
    public static final LineId STATS = LineId.of("stats");
    public static final LineId EVENT_LABEL = LineId.of("event_label");
    public static final LineId EVENT_VALUE = LineId.of("event_value");
    public static final LineId BLANK0 = LineId.of("blank0");

    private Profiles() {
    }

    /** Build the catalog with the real coins/rank providers injected. */
    public static ProfileCatalog catalog(LineProvider coins, LineProvider rank) {
        ScoreboardProfile def = ScoreboardProfile.profile(DEFAULT_ID,
                new ScoreboardLine(FIRST_BLANK, blank()),
                new ScoreboardLine(RANK_LABEL, label("Rank", NamedTextColor.AQUA)),
                new ScoreboardLine(RANK, rank),
                new ScoreboardLine(BLANK1, blank()),
                new ScoreboardLine(COINS_LABEL, label("Münzen", NamedTextColor.GOLD)),
                new ScoreboardLine(COINS, coins),
                new ScoreboardLine(BLANK2, blank()),
                new ScoreboardLine(STATS_LABEL, label("Kills", NamedTextColor.GREEN)),
                new ScoreboardLine(STATS, stub(Component.text("0", NamedTextColor.WHITE))));

        ScoreboardProfile testEvent = ScoreboardProfile.profile(TEST_EVENT_ID,
                new ScoreboardLine(EVENT_LABEL, label("Event", NamedTextColor.LIGHT_PURPLE)),
                new ScoreboardLine(EVENT_VALUE, new StaticLineProvider(
                        Component.text("läuft", NamedTextColor.WHITE))),
                new ScoreboardLine(BLANK0, blank()),
                new ScoreboardLine(RANK_LABEL, label("Rank", NamedTextColor.AQUA)),
                new ScoreboardLine(RANK, rank),
                new ScoreboardLine(BLANK1, blank()),
                new ScoreboardLine(COINS_LABEL, label("Münzen", NamedTextColor.GOLD)),
                new ScoreboardLine(COINS, coins));

        return new ProfileCatalog(DEFAULT_ID, def, testEvent);
    }

    private static StaticLineProvider label(String text, TextColor color) {
        return new StaticLineProvider(Component.text(text, color, TextDecoration.BOLD));
    }

    private static StaticLineProvider blank() {
        return new StaticLineProvider(Component.empty());
    }

    private static StubLineProvider stub(Component value) {
        return new StubLineProvider(value);
    }
}
