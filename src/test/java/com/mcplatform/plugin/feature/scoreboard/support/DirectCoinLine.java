package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.CoinLineRenderer;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandle;

import java.util.OptionalLong;
import java.util.UUID;

/** Non-animating coin line for service tests: sets the value instantly (animation is tested separately). */
public final class DirectCoinLine implements CoinLineRenderer {

    @Override
    public void update(UUID player, ScoreboardHandle handle, LineId line, OptionalLong coins) {
        handle.update(line, coins.isPresent()
                ? EconomyLineProvider.coinComponent(coins.getAsLong())
                : EconomyLineProvider.PLACEHOLDER);
    }
}
