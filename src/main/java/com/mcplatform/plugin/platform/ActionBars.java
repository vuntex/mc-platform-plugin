package com.mcplatform.plugin.platform;

import net.kyori.adventure.text.Component;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Action-bar feedback: short, transient toasts (the hotbar line) paired with a fitting sound — for
 * immediate "that worked / that didn't" acknowledgements that don't belong in the chat log. One-shot
 * (no refresh; the client fades them after a few seconds) and last-writer-wins, matching the common
 * action-bar toast pattern.
 *
 * <p>Rule of thumb (see {@code CHAT_DESIGN.md}): use the <b>action bar</b> for transient single-line
 * feedback (quick validation errors, small confirmations); use <b>chat</b> for records, multi-line or
 * clickable messages (transfers, confirmations, alerts). Styling comes from {@code ChatDesign}; this
 * helper only decides delivery + sound.
 */
public final class ActionBars {

    private ActionBars() {
    }

    /** Positive acknowledgement: action bar + a light pickup "ding". */
    public static void success(Player player, Component message) {
        player.sendActionBar(message);
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    /** Negative feedback: action bar + a low "no" tone. */
    public static void error(Player player, Component message) {
        player.sendActionBar(message);
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /** Neutral info: action bar + a soft click. */
    public static void info(Player player, Component message) {
        player.sendActionBar(message);
        play(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
    }

    /**
     * Terminal denial feedback for a command (e.g. "no permission"): the action bar (+ error sound) for a
     * player, a plain chat line for the console — which has no action bar. Keeps denials out of player chat.
     */
    public static void deny(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            error(player, message);
        } else {
            sender.sendMessage(message);
        }
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
