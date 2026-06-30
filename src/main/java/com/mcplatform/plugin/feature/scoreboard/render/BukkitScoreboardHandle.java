package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONLY Bukkit scoreboard mechanics in the slice (Flicker-Strategie P2). Each line position is a
 * stable Team whose single, invisible entry occupies a fixed sidebar slot; the visible text lives in
 * the Team prefix (Adventure, no §-codes — spec FR-008). A line update only rewrites that Team's prefix
 * → no entry add/remove → no flicker, addressed exactly by {@link LineId} (AC-3). All methods run on
 * the main thread.
 */
public final class BukkitScoreboardHandle implements ScoreboardHandle {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Map<LineId, Team> teams = new HashMap<>();

    public BukkitScoreboardHandle(Player player) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("mcp_sb", Criteria.DUMMY,
                Component.text("MC Platform", NamedTextColor.GOLD, TextDecoration.BOLD));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Override
    public void install(List<RenderedLine> lines) {
        int size = lines.size();
        for (int i = 0; i < size; i++) {
            RenderedLine line = lines.get(i);
            // A distinct, invisible entry per slot (a lone color code renders as nothing).
            String entry = ChatColor.values()[i % ChatColor.values().length].toString();
            Team team = scoreboard.registerNewTeam("sb_" + i);
            team.addEntry(entry);
            team.prefix(line.component());
            teams.put(line.id(), team);
            objective.getScore(entry).setScore(size - i); // higher score = higher on screen
        }
        player.setScoreboard(scoreboard);
    }

    @Override
    public void update(LineId id, Component value) {
        Team team = teams.get(id);
        if (team != null) {
            team.prefix(value);
        }
    }

    @Override
    public void teardown() {
        try {
            for (Team team : teams.values()) {
                team.unregister();
            }
            objective.unregister();
            if (player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } catch (IllegalStateException ignored) {
            // Already unregistered (e.g. double teardown) — safe to ignore.
        } finally {
            teams.clear();
        }
    }
}
