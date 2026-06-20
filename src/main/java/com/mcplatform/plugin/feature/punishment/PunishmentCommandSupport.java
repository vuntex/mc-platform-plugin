package com.mcplatform.plugin.feature.punishment;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/**
 * Small shared helpers for the punishment commands: who is issuing, target UUID resolution split
 * across threads (online lookup must run on the main thread, the possibly-blocking offline lookup must
 * not), and reason joining. Keeps the command classes free of duplicated Bukkit plumbing.
 */
final class PunishmentCommandSupport {

    /** Actor id used when the command comes from the console (no player UUID). */
    static final UUID CONSOLE = new UUID(0L, 0L);

    private PunishmentCommandSupport() {
    }

    /** Issuing actor: the team member's UUID, or {@link #CONSOLE} for console/command blocks. */
    static UUID issuedBy(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    /** UUID of an online player by exact name, or {@code null}. MUST be called on the main thread. */
    static UUID resolveOnlineUuid(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return player == null ? null : player.getUniqueId();
    }

    /**
     * Resolve a target UUID: prefer the already-known online UUID, otherwise fall back to the offline
     * lookup (which may hit the user cache / Mojang and therefore must run off the main thread).
     */
    @SuppressWarnings("deprecation") // getOfflinePlayer(String): intentional, run off-main by the caller
    static UUID resolveUuid(String name, UUID knownOnline) {
        return knownOnline != null ? knownOnline : Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    /** Join {@code args[start..]} into a reason, or {@code null} when none was supplied. */
    static String joinReason(String[] args, int start) {
        if (start >= args.length) {
            return null;
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }
}
