package com.mcplatform.plugin.platform.menu;

/**
 * Semantic click feedback from MENU_DESIGN §4.5 — the *meaning* of the feedback, not a sound id. The
 * render layer maps each value to an actual sound (ascending for ADD, descending for REMOVE, a low
 * error tone for DENY, …). Keeping this a Bukkit-free enum lets handlers request feedback in code that
 * unit-tests can drive.
 */
public enum Feedback {
    /** Action succeeded (bright confirmation tone). */
    SUCCESS,
    /** Not allowed / limit reached / error (low tone + action bar). */
    DENY,
    /** Add / activate (ascending tone). */
    ADD,
    /** Remove / deactivate (descending tone). */
    REMOVE,
    /** Neutral navigation click (quiet click). */
    NAVIGATE,
    /** Click on a non-interactive display item (subtle tone). */
    INERT,
    /** No sound at all. */
    NONE
}
