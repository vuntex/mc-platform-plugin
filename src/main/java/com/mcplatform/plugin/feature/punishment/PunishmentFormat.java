package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.transport.BackendException;

import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure formatting + parsing for the punishment feature: human duration parsing ({@code 2h}, {@code 7d},
 * {@code 1d12h}), duration rendering, the ban/chatban player-facing screens, and mapping a failed
 * backend call to a team-member message (so a backend {@code 403} is shown cleanly). No Bukkit, no
 * I/O — unit-testable. Legacy {@code §} colour codes match the rest of the plugin's messages.
 */
final class PunishmentFormat {

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([smhdw])");

    private PunishmentFormat() {
    }

    /** Parse {@code 30s/15m/2h/7d/1w} (combinable, e.g. {@code 1d12h}) to millis. Throws on garbage. */
    static long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("empty duration");
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        Matcher m = DURATION_TOKEN.matcher(s);
        long total = 0L;
        int end = 0;
        boolean any = false;
        while (m.find()) {
            if (m.start() != end) {
                throw new IllegalArgumentException("invalid duration: " + input);
            }
            total += Long.parseLong(m.group(1)) * unitMillis(m.group(2));
            end = m.end();
            any = true;
        }
        if (!any || end != s.length()) {
            throw new IllegalArgumentException("invalid duration: " + input);
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("duration must be positive: " + input);
        }
        return total;
    }

    /** Render a millis duration as {@code 2d 3h 15m} (drops zero parts; floors at {@code 0s}). */
    static String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        long secs = millis / 1000L;
        long d = secs / 86_400L;
        secs %= 86_400L;
        long h = secs / 3_600L;
        secs %= 3_600L;
        long m = secs / 60L;
        secs %= 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (m > 0) {
            sb.append(m).append("m ");
        }
        if (secs > 0 || sb.isEmpty()) {
            sb.append(secs).append("s");
        }
        return sb.toString().trim();
    }

    /**
     * Coarse duration for player-facing notices: rounds UP to the next minute and drops seconds, so a
     * remaining {@code 1h 59m 59s} reads as {@code 2h} instead of a noisy ticking value. Sub-minute
     * remainders show as {@code 1m}.
     */
    static String formatDurationCoarse(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        long minutes = (millis + 59_999L) / 60_000L; // ceil to whole minutes
        long d = minutes / 1_440L;
        minutes %= 1_440L;
        long h = minutes / 60L;
        long m = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (m > 0 || sb.isEmpty()) {
            sb.append(m).append("m");
        }
        return sb.toString().trim();
    }

    /** Player-facing disallow/kick screen for a (temp/perma)ban. Shows remaining time for a TEMPBAN. */
    static String banScreen(String type, String reason, long expiresAtEpochMilli, long nowEpochMilli) {
        StringBuilder sb = new StringBuilder();
        sb.append("§c§lDu bist gebannt.\n\n");
        sb.append("§7Grund: §f").append(reason).append('\n');
        if (PunishmentType.TEMPBAN.equals(type) && expiresAtEpochMilli > 0) {
            sb.append("§7Verbleibend: §f").append(formatDuration(expiresAtEpochMilli - nowEpochMilli));
        } else {
            sb.append("§7Dauer: §fpermanent");
        }
        return sb.toString();
    }

    /**
     * Map a failed backend call to a team-member message. A {@code 403} (the backend is the real
     * authority on who may apply which template/action) is surfaced cleanly; other normed statuses get
     * a precise hint. Unwraps the {@link CompletableFuture} wrapper exceptions first.
     */
    static String backendError(Throwable error) {
        Throwable t = unwrap(error);
        if (t instanceof BackendException be) {
            return switch (be.statusCode()) {
                case 403 -> "§cKeine Berechtigung – vom Backend abgelehnt (403).";
                case 404 -> "§cNicht gefunden (404).";
                case 400 -> "§cUngültige Anfrage (400)." + bodyHint(be);
                case 409 -> "§cKonflikt (409)." + bodyHint(be);
                default -> "§cBackend-Fehler (" + be.statusCode() + ").";
            };
        }
        return "§cAktion fehlgeschlagen: " + (t == null ? "unbekannt" : t.getMessage());
    }

    private static String bodyHint(BackendException be) {
        String body = be.responseBody();
        return (body == null || body.isBlank()) ? "" : " §7" + body;
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static long unitMillis(String unit) {
        return switch (unit) {
            case "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            default -> throw new IllegalArgumentException("unknown unit: " + unit);
        };
    }
}
