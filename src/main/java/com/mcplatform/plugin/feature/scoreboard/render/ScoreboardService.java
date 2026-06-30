package com.mcplatform.plugin.feature.scoreboard.render;

import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.scoreboard.condition.ProfileResolver;
import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.model.RenderedLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardLine;
import com.mcplatform.plugin.feature.scoreboard.model.ScoreboardProfile;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.LiveHandle;
import com.mcplatform.plugin.platform.menu.MenuLiveBus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-free orchestration of per-player scoreboards — the testable heart of the slice.
 *
 * <ul>
 *   <li><b>show</b>: resolve the profile, install initial lines (rank from the warm permission cache;
 *       coins as a placeholder), subscribe the player's UUID on the shared {@link MenuLiveBus}, and
 *       trigger an async coins load that re-renders on completion (economy cache is lazy/cold at join).</li>
 *   <li><b>live</b>: when economy/permission update their cache they call {@code liveBus.notifyChange(uuid)};
 *       the observer re-resolves ONLY the dynamic lines (coins, rank) and writes the affected slots —
 *       last-write-wins, no debounce (FR-007a), flicker-free via the handle (AC-3).</li>
 *   <li><b>remove</b>: close the live handle (no observer leak) and tear the board down (AC-6/FR-009).</li>
 * </ul>
 *
 * The re-render reads the read-ports (existing caches) — no parallel cache, no own transport
 * subscription. {@code notifyChange} runs on the main thread; the observer additionally hops via the
 * scheduler so a future off-main notifier stays safe.
 */
public final class ScoreboardService {

    private record Board(ScoreboardHandle handle, ScoreboardProfile profile, PlayerContext ctx, LiveHandle live) {
    }

    private final ScoreboardRenderer renderer;
    private final ProfileResolver resolver;
    private final EconomyReadPort economyPort;
    private final MenuLiveBus liveBus;
    private final PlatformScheduler scheduler;
    private final LineId coinLineId;
    private final CoinLineRenderer coinLine;
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();

    public ScoreboardService(ScoreboardRenderer renderer, ProfileResolver resolver,
                             EconomyReadPort economyPort, MenuLiveBus liveBus, PlatformScheduler scheduler,
                             LineId coinLineId, CoinLineRenderer coinLine) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.economyPort = Objects.requireNonNull(economyPort, "economyPort");
        this.liveBus = Objects.requireNonNull(liveBus, "liveBus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coinLineId = Objects.requireNonNull(coinLineId, "coinLineId");
        this.coinLine = Objects.requireNonNull(coinLine, "coinLine");
    }

    /** Build and show the player's scoreboard. Call on the main thread. */
    public void show(PlayerContext ctx, ScoreboardHandle handle) {
        UUID player = ctx.player();
        ScoreboardProfile profile = resolver.resolve(ctx);
        List<RenderedLine> initial = renderer.render(profile, ctx);
        handle.install(initial);

        LiveHandle live = liveBus.observe(player, () -> scheduler.runSync(() -> reRender(player)));
        boards.put(player, new Board(handle, profile, ctx, live));

        // Economy fills lazily → warm it, then re-render the coins (and rank) slots when it arrives.
        economyPort.load(player).whenComplete((value, error) -> scheduler.runSync(() -> reRender(player)));
    }

    /** Re-resolve only the dynamic lines (coins, rank) from the read-ports and write their slots. */
    public void reRender(UUID player) {
        Board board = boards.get(player);
        if (board == null) {
            return;
        }
        for (ScoreboardLine line : board.profile().lines()) {
            if (!line.provider().dynamic()) {
                continue;
            }
            if (line.id().equals(coinLineId)) {
                // The coins line is rendered by the animator (count-up + sound on a gain).
                coinLine.update(player, board.handle(), coinLineId, economyPort.current(player));
            } else {
                board.handle().update(line.id(), line.provider().resolve(board.ctx()));
            }
        }
    }

    /** Remove the player's scoreboard: unsubscribe and tear down (no leak). Call on the main thread. */
    public void remove(UUID player) {
        Board board = boards.remove(player);
        if (board == null) {
            return;
        }
        board.live().close();
        board.handle().teardown();
        coinLine.clear(player);
    }

    /** Diagnostics/tests: number of active boards. */
    public int activeBoards() {
        return boards.size();
    }
}
