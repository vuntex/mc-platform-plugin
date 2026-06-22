package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.Token;

import java.util.List;

/**
 * Report status with its German label and — for the actionable ones — the icon/colour/verb of the button
 * that transitions <em>into</em> it. The allowed transitions are enforced client-side only as a UI
 * convenience (which buttons to show); the backend is authoritative and rejects an illegal or concurrent
 * transition with 409.
 *
 * <p>Transitions (mirror the backend): {@code OPEN→IN_PROGRESS}, {@code OPEN→REJECTED},
 * {@code IN_PROGRESS→RESOLVED}, {@code IN_PROGRESS→REJECTED}. Terminal states offer none.
 */
public enum ReportStatus {

    OPEN("OPEN", "Offen", Icon.GLOBAL, Token.INFO, "Offen"),
    IN_PROGRESS("IN_PROGRESS", "In Bearbeitung", Icon.MANAGE, Token.INFO, "In Bearbeitung nehmen"),
    RESOLVED("RESOLVED", "Erledigt", Icon.CONFIRM, Token.POSITIVE, "Als erledigt markieren"),
    REJECTED("REJECTED", "Abgelehnt", Icon.CANCEL, Token.NEGATIVE, "Ablehnen");

    private final String wire;
    private final String label;
    private final Icon icon;
    private final Token token;
    private final String actionLabel;

    ReportStatus(String wire, String label, Icon icon, Token token, String actionLabel) {
        this.wire = wire;
        this.label = label;
        this.icon = icon;
        this.token = token;
        this.actionLabel = actionLabel;
    }

    public String wire() {
        return wire;
    }

    public String label() {
        return label;
    }

    public Icon icon() {
        return icon;
    }

    public Token token() {
        return token;
    }

    /** Label for the button that transitions a report into this status. */
    public String actionLabel() {
        return actionLabel;
    }

    /** German label for a wire value, or the raw value if it is unknown (forward-compatible). */
    public static String labelOf(String wire) {
        for (ReportStatus s : values()) {
            if (s.wire.equals(wire)) {
                return s.label;
            }
        }
        return wire;
    }

    /** Target statuses reachable from {@code fromWire} (UI gate only; backend re-checks). */
    public static List<ReportStatus> transitionsFrom(String fromWire) {
        return switch (fromWire) {
            case "OPEN" -> List.of(IN_PROGRESS, REJECTED);
            case "IN_PROGRESS" -> List.of(RESOLVED, REJECTED);
            default -> List.of();
        };
    }
}
