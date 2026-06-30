package com.mcplatform.plugin.feature.scoreboard.support;

import com.mcplatform.plugin.platform.PlatformScheduler;

/** Test scheduler that runs everything inline on the calling thread. */
public final class ImmediateScheduler implements PlatformScheduler {

    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }
}
