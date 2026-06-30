package com.mcplatform.plugin.feature.scoreboard.provider;

import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;

import net.kyori.adventure.text.Component;

/**
 * Source of a single line's content (spec FR-003). Synchronous and side-effect-free: it reads the
 * currently-known value (a read-port/cache or a static value) and returns an Adventure component. No
 * I/O happens here — async loading is orchestrated by the lifecycle. Swapping a stub for a real
 * provider changes only the line's provider binding; the renderer is untouched (AC-4, FR-013).
 */
public interface LineProvider {

    Component resolve(PlayerContext ctx);

    /**
     * Whether this line's value can change while the player is online (coins, rank). Live re-renders
     * touch only dynamic lines; static/stub lines are rendered once. Defaults to {@code false}.
     */
    default boolean dynamic() {
        return false;
    }
}
