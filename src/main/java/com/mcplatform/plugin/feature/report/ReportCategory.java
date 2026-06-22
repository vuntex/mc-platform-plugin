package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.menu.Icon;

/**
 * The five report categories. Each binds the canonical wire value (sent to the backend) to a German
 * display label and a semantic menu icon, so the category-selection menu never hard-codes either. The
 * wire values mirror the backend contract exactly ({@code CHEATING|BELEIDIGUNG|SPAM_WERBUNG|
 * TEAMING_BUG_ABUSE|SONSTIGES}); the backend rejects anything else with 422.
 */
public enum ReportCategory {

    CHEATING("CHEATING", "Cheating", Icon.DANGER),
    BELEIDIGUNG("BELEIDIGUNG", "Beleidigung", Icon.CANCEL),
    SPAM_WERBUNG("SPAM_WERBUNG", "Spam / Werbung", Icon.VALUE),
    TEAMING_BUG_ABUSE("TEAMING_BUG_ABUSE", "Teaming / Bug-Abuse", Icon.GLOBAL),
    SONSTIGES("SONSTIGES", "Sonstiges", Icon.INFO);

    private final String wire;
    private final String label;
    private final Icon icon;

    ReportCategory(String wire, String label, Icon icon) {
        this.wire = wire;
        this.label = label;
        this.icon = icon;
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

    /** German label for a wire value, or the raw value if it is unknown (forward-compatible). */
    public static String labelOf(String wire) {
        for (ReportCategory c : values()) {
            if (c.wire.equals(wire)) {
                return c.label;
            }
        }
        return wire;
    }
}
