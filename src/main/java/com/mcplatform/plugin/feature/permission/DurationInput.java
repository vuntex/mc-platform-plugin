package com.mcplatform.plugin.feature.permission;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a staff-typed grant duration into {@code expiresInSeconds} for {@code GRANT_ROLE} /
 * {@code GRANT_PERMISSION}. Syntax mirrors the punishment durations ({@code 30d}, {@code 12h},
 * {@code 1d12h}; units s/m/h/d/w). Blank or {@code permanent}/{@code perm}/{@code -} → {@code null}
 * (permanent). Invalid input throws. Pure and unit-testable.
 */
public final class DurationInput {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])");

    private DurationInput() {
    }

    /** Seconds for the grant, or {@code null} for permanent. Throws {@link IllegalArgumentException} on garbage. */
    public static Long parseSeconds(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("permanent") || s.equals("perm") || s.equals("-") || s.equals("-1")) {
            return null;
        }
        Matcher matcher = TOKEN.matcher(s);
        long total = 0L;
        int end = 0;
        boolean any = false;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("invalid duration: " + input);
            }
            total += Long.parseLong(matcher.group(1)) * unitSeconds(matcher.group(2));
            end = matcher.end();
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

    private static long unitSeconds(String unit) {
        return switch (unit) {
            case "s" -> 1L;
            case "m" -> 60L;
            case "h" -> 3600L;
            case "d" -> 86_400L;
            case "w" -> 604_800L;
            default -> throw new IllegalArgumentException("unknown unit: " + unit);
        };
    }
}
