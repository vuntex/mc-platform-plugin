package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Per-player render sink. Abstracts the Bukkit scoreboard mechanics behind a tiny contract so the
 * orchestration logic ({@code ScoreboardService}) is unit-testable against a recording fake, and the
 * Bukkit specifics (Team-entry slots, Flicker-Strategie P2) live only in {@code BukkitScoreboardHandle}.
 *
 * <p>{@link #update} addresses exactly the slot of the given {@link LineId} (changes only its text) —
 * no full rebuild, no flicker (AC-3). All methods are called on the main thread.
 */
public interface ScoreboardHandle {

    /** Build the sidebar with the initial lines (slot per position; id ↔ slot mapping fixed). */
    void install(List<RenderedLine> lines);

    /** Change the text of the slot bound to {@code id}. No-op if the id is unknown. */
    void update(LineId id, Component value);

    /** Detach and release the player's scoreboard. */
    void teardown();
}
