package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.scoreboard.model.LineId;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.CoinLineAnimator;
import com.mcplatform.plugin.feature.scoreboard.support.FakeTimerScheduler;
import com.mcplatform.plugin.feature.scoreboard.support.RecordingScoreboardHandle;
import com.mcplatform.plugin.feature.scoreboard.support.RecordingScoreboardSound;

import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Coin count-up: first value/loss are instant; a gain animates step-by-step with sound and completes. */
class CoinLineAnimatorTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final LineId COINS = LineId.of("coins");

    private FakeTimerScheduler scheduler;
    private RecordingScoreboardSound sound;
    private RecordingScoreboardHandle handle;
    private CoinLineAnimator animator;

    @BeforeEach
    void setUp() {
        scheduler = new FakeTimerScheduler();
        sound = new RecordingScoreboardSound();
        handle = new RecordingScoreboardHandle();
        animator = new CoinLineAnimator(scheduler, sound, 1L);
    }

    private void drainAnimation() {
        for (int i = 0; i < 100 && scheduler.activeTimers() > 0; i++) {
            scheduler.tick();
        }
    }

    @Test
    void firstValueIsInstantNoAnimationNoSound() {
        animator.update(PLAYER, handle, COINS, OptionalLong.of(100));

        assertEquals(EconomyLineProvider.coinComponent(100), handle.current.get(COINS));
        assertEquals(0, scheduler.activeTimers());
        assertEquals(0, sound.ticks + sound.completes);
    }

    @Test
    void emptyShowsPlaceholder() {
        animator.update(PLAYER, handle, COINS, OptionalLong.empty());
        assertEquals(EconomyLineProvider.PLACEHOLDER, handle.current.get(COINS));
    }

    @Test
    void gainAnimatesUpAndCompletes() {
        animator.update(PLAYER, handle, COINS, OptionalLong.of(100)); // first value, instant
        animator.update(PLAYER, handle, COINS, OptionalLong.of(200)); // gain → animates

        assertEquals(1, scheduler.activeTimers(), "animation scheduled");
        drainAnimation();

        assertEquals(EconomyLineProvider.coinComponent(200), handle.current.get(COINS), "settles on target");
        assertEquals(1, sound.completes, "one completion sound");
        assertTrue(sound.ticks > 0, "ticks played during count-up");
        assertEquals(0, scheduler.activeTimers(), "timer stopped at the end");
        // The coins line was updated multiple times (count-up), not in a single jump.
        assertTrue(handle.updatedIds.stream().filter(COINS::equals).count() > 1);
    }

    @Test
    void lossIsInstant() {
        animator.update(PLAYER, handle, COINS, OptionalLong.of(200));
        animator.update(PLAYER, handle, COINS, OptionalLong.of(50));

        assertEquals(EconomyLineProvider.coinComponent(50), handle.current.get(COINS));
        assertEquals(0, scheduler.activeTimers());
    }

    @Test
    void newGainSupersedesRunningAnimation() {
        animator.update(PLAYER, handle, COINS, OptionalLong.of(100));
        animator.update(PLAYER, handle, COINS, OptionalLong.of(200)); // starts
        animator.update(PLAYER, handle, COINS, OptionalLong.of(500)); // supersedes

        assertEquals(1, scheduler.activeTimers(), "only one animation runs");
        drainAnimation();
        assertEquals(EconomyLineProvider.coinComponent(500), handle.current.get(COINS));
        assertEquals(0, scheduler.activeTimers());
    }

    @Test
    void clearCancelsRunningAnimation() {
        animator.update(PLAYER, handle, COINS, OptionalLong.of(100));
        animator.update(PLAYER, handle, COINS, OptionalLong.of(200));
        assertEquals(1, scheduler.activeTimers());

        animator.clear(PLAYER);
        assertEquals(0, scheduler.activeTimers());
    }
}
