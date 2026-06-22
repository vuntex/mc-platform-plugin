package com.mcplatform.plugin.feature.economy;

import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.protocol.economy.EconomyEventEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure presentation rules for one economy history entry — Bukkit-free so direction/colour/icon mapping
 * and amount/timestamp formatting are unit-testable. {@code amount} is always positive on the wire; the
 * direction (and therefore sign + colour) is derived from the {@code eventType}
 * (CREDITED|DEBITED|SET|TRANSFER_OUT|TRANSFER_IN).
 */
final class EconomyHistoryFormat {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private EconomyHistoryFormat() {
    }

    static boolean isIncoming(String type) {
        return "CREDITED".equals(type) || "TRANSFER_IN".equals(type);
    }

    static boolean isOutgoing(String type) {
        return "DEBITED".equals(type) || "TRANSFER_OUT".equals(type);
    }

    /** A grouped integer, e.g. {@code 1.250} (German grouping). */
    static String grouped(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    /** Signed, grouped amount: {@code +1.250} / {@code -300}; SET shows the absolute value (no sign). */
    static String amount(EconomyEventEntry entry) {
        String n = grouped(entry.amount());
        if (isIncoming(entry.eventType())) {
            return "+" + n;
        }
        if (isOutgoing(entry.eventType())) {
            return "-" + n;
        }
        return n; // SET — absolute set value
    }

    /** Colour for the amount line: green inbound, red outbound, gold for an admin SET. */
    static Token amountToken(String type) {
        if (isIncoming(type)) {
            return Token.POSITIVE;
        }
        if (isOutgoing(type)) {
            return Token.NEGATIVE;
        }
        return Token.ENTITY;
    }

    /** Semantic icon by direction (lime dye in, red dye out, sign for SET). */
    static Icon icon(String type) {
        if (isIncoming(type)) {
            return Icon.CONFIRM;
        }
        if (isOutgoing(type)) {
            return Icon.CANCEL;
        }
        return Icon.VALUE;
    }

    static String typeLabel(String type) {
        return switch (type) {
            case "CREDITED" -> "Gutschrift";
            case "DEBITED" -> "Abbuchung";
            case "TRANSFER_IN" -> "Erhaltener Transfer";
            case "TRANSFER_OUT" -> "Gesendeter Transfer";
            case "SET" -> "Admin-Korrektur";
            default -> type;
        };
    }

    static String time(long epochMilli) {
        return TIME.format(Instant.ofEpochMilli(epochMilli));
    }

    /** First 8 chars of a UUID for a compact, copy-recognisable reference; {@code —} when absent. */
    static String shortId(UUID id) {
        return id == null ? "—" : id.toString().substring(0, 8);
    }
}
