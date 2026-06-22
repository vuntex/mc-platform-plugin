package com.mcplatform.plugin.feature.punishment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The graphical, player-facing chat notice shown when a <em>non-disconnecting</em> punishment hits an
 * online player (warning, chat-ban, …) — bans/tempbans use the kick screen instead. A bracketed box
 * ({@code ▛▀▜} / {@code ▙▄▟}) with an indented body and a clickable "report a wrong punishment" link.
 * Pure Adventure {@link Component} building (no Bukkit, no § codes), so the layout is unit-testable.
 * The issuing staff is intentionally never shown to the punished player.
 */
final class PunishmentNotice {

    /** Where the "Klicke hier" appeal link points. Adjust to the server's appeal/Discord URL. */
    private static final String APPEAL_URL = "https://example.com/appeal";

    private static final TextColor LABEL = TextColor.color(0xAAAAAA);
    private static final TextColor VALUE = TextColor.color(0xFFFFFF);
    private static final TextColor GOLD = TextColor.color(0xFFAA00);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor BLUE = TextColor.color(0x5555FF);
    private static final int BORDER_WIDTH = 24;

    private PunishmentNotice() {
    }

    /**
     * Action-bar line shown each time a muted player tries to talk: "Du bist noch für 2h gemutet."
     * (gold remaining time) or "Du bist permanent gemutet.". Non-spammy — the action bar overlays.
     */
    static Component muteActionBar(long expiresAtEpochMilli, long now) {
        TextColor red = accentOf(PunishmentType.CHATBAN);
        if (expiresAtEpochMilli > 0) {
            return Component.text("Du bist noch für ", red)
                    .append(Component.text(PunishmentFormat.formatDurationCoarse(expiresAtEpochMilli - now), GOLD))
                    .append(Component.text(" gemutet.", red));
        }
        return Component.text("Du bist permanent gemutet.", red);
    }

    /** Standalone clickable appeal line for the mute notice (cooldown-gated by the caller). */
    static Component muteAppeal() {
        return appeal("", accentOf(PunishmentType.CHATBAN));
    }

    /** Shown when a chat mute ends (expiry or revoke) while the player is online. */
    static Component muteExpired() {
        return Component.text("Mute> ", BLUE).decorate(TextDecoration.BOLD)
                .append(Component.text("Du kannst nun wieder im globalen Chat schreiben.", LABEL)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Staff broadcast for an issued punishment: "Der Spieler X wurde vom Chat ausgeschlossen (2h)." —
     * yellow with the name/duration in grey. The duration shows only for time-bound types.
     */
    static Component broadcast(String type, String playerName, long expiresAtEpochMilli, long now) {
        boolean timed = expiresAtEpochMilli > 0;
        String action = switch (type) {
            case PunishmentType.WARN -> "verwarnt";
            case PunishmentType.CHATBAN -> timed ? "vom Chat ausgeschlossen" : "permanent vom Chat ausgeschlossen";
            case PunishmentType.TEMPBAN -> "gebannt";
            case PunishmentType.PERMABAN -> "permanent gebannt";
            default -> "bestraft";
        };
        boolean showDuration = timed && (PunishmentType.CHATBAN.equals(type) || PunishmentType.TEMPBAN.equals(type));
        Component message = Component.text("Der Spieler ", YELLOW)
                .append(Component.text(playerName, LABEL))
                .append(Component.text(" wurde " + action, YELLOW));
        if (showDuration) {
            message = message.append(Component.text(
                    " (" + PunishmentFormat.formatDurationCoarse(expiresAtEpochMilli - now) + ")", LABEL));
        }
        return message.append(Component.text(".", YELLOW));
    }

    /** Build the notice for an ISSUED punishment of {@code type}. {@code now} drives the remaining time. */
    static Component issued(String type, String reason, long expiresAtEpochMilli, long now) {
        TextColor accent = accentOf(type);
        Component nl = Component.newline();

        return Component.text()
                .append(nl)
                .append(header(accent))
                .append(nl)
                .append(Component.text("  " + iconOf(type) + " " + titleOf(type), accent)
                        .decorate(TextDecoration.BOLD))
                .append(nl).append(nl)
                .append(description(type, accent, expiresAtEpochMilli, now))
                .append(nl).append(nl)
                .append(Component.text("  Grund: ", accent))
                .append(Component.text(reason, VALUE))
                .append(nl).append(nl)
                .append(appealLink(accent))
                .append(nl)
                .append(footer(accent))
                .append(nl)
                .build();
    }

    /** Prose description (may span two lines), with the duration woven in for a chat-ban. */
    private static Component description(String type, TextColor accent, long expiresAtEpochMilli, long now) {
        if (PunishmentType.CHATBAN.equals(type)) {
            if (expiresAtEpochMilli > 0) {
                String duration = PunishmentFormat.formatDurationCoarse(expiresAtEpochMilli - now);
                return Component.text("  Aufgrund deiner Nachrichten wurde dein", LABEL)
                        .append(Component.newline())
                        .append(Component.text("  Chat für ", LABEL))
                        .append(Component.text(duration, accent))
                        .append(Component.text(" gesperrt.", LABEL));
            }
            return Component.text("  Du kannst ", LABEL)
                    .append(Component.text("permanent", accent))
                    .append(Component.text(" im globalen Chat", LABEL))
                    .append(Component.newline())
                    .append(Component.text("  nicht mehr schreiben.", LABEL));
        }
        if (PunishmentType.WARN.equals(type)) {
            return Component.text("  Du hast eine ", LABEL)
                    .append(Component.text("Verwarnung", accent))
                    .append(Component.text(" erhalten.", LABEL));
        }
        return Component.text("  Dich betrifft eine Strafe.", LABEL);
    }

    /** Clickable "wrong punishment? click here" line with a hover hint (box variant, indented). */
    private static Component appealLink(TextColor accent) {
        return appeal("  ", accent);
    }

    /** Shared clickable appeal builder: {@code prefix} indents it for the box; "" for a standalone line. */
    private static Component appeal(String prefix, TextColor accent) {
        return Component.text(prefix + "Klicke ", LABEL)
                .append(Component.text("hier", accent).decorate(TextDecoration.UNDERLINED))
                .append(Component.text(", falls das ein Fehler war.", LABEL))
                .clickEvent(ClickEvent.openUrl(APPEAL_URL))
                .hoverEvent(HoverEvent.showText(Component.text("Klicke, um Einspruch einzulegen.", accent)));
    }

    private static Component header(TextColor accent) {
        return Component.text("▛" + "▀".repeat(BORDER_WIDTH) + "▜", accent);
    }

    private static Component footer(TextColor accent) {
        return Component.text("▙" + "▄".repeat(BORDER_WIDTH) + "▟", accent);
    }

    private static TextColor accentOf(String type) {
        return switch (type) {
            case PunishmentType.WARN -> TextColor.color(0xFFB02E);     // amber
            case PunishmentType.CHATBAN -> TextColor.color(0xFF5555);  // red
            default -> TextColor.color(0xFF7E29);                      // orange
        };
    }

    private static String iconOf(String type) {
        return switch (type) {
            case PunishmentType.WARN -> "⚠";
            case PunishmentType.CHATBAN -> "✖";
            default -> "●";
        };
    }

    private static String titleOf(String type) {
        return switch (type) {
            case PunishmentType.WARN -> "Verwarnung";
            case PunishmentType.CHATBAN -> "Chat Mute";
            default -> type;
        };
    }
}
