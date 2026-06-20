package com.mcplatform.plugin.platform.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a §3.3-conformant lore block: description lines, an optional blank + value lines, an optional
 * blank + the action hint as the last line. Encoding the order here means a feature never reconstructs
 * the convention (and never forgets the trailing blank/hint spacing). Pure and unit-testable.
 *
 * <p>The action hint follows the §3.3 grammar: {@code <click kind>}(cue) + bind text(neutral) +
 * {@code <verb>}(positive/negative) + ".".
 */
public final class Lore {

    private final List<LoreLine> description = new ArrayList<>();
    private final List<LoreLine> values = new ArrayList<>();
    private final List<LoreLine> hints = new ArrayList<>();

    public static Lore builder() {
        return new Lore();
    }

    /** Add a grey description line (1–3 of these, §3.3 top). */
    public Lore describe(String text) {
        description.add(LoreLine.body(text));
        return this;
    }

    /** Add a pre-built line to the description block (for callers composing their own segments). */
    public Lore line(LoreLine line) {
        description.add(line);
        return this;
    }

    /** Add a "label: value" line (value in gold). */
    public Lore value(String label, String value) {
        values.add(LoreLine.value(label, value));
        return this;
    }

    /**
     * Add one action-hint line (§3.3). {@code clickKind} is the cue (e.g. "Klicke", "Rechtsklick",
     * "Doppelklicke"); {@code bind} the neutral middle (e.g. ", zum "); {@code verb} the coloured verb;
     * {@code positive} selects green (true) vs red (false) for the verb. A trailing "." is added.
     */
    public Lore hint(String clickKind, String bind, String verb, boolean positive) {
        hints.add(LoreLine.inline(
                MenuText.line(clickKind, Token.CUE),
                MenuText.line(bind, Token.BODY),
                MenuText.line(verb, positive ? Token.POSITIVE : Token.NEGATIVE),
                MenuText.line(".", Token.BODY)));
        return this;
    }

    /** Convenience: "Klicke, zum <verb>." with a positive (green) verb. */
    public Lore clickToOpen(String verb) {
        return hint("Klicke", ", zum ", verb, true);
    }

    /** Convenience: a destructive double-click hint ("Doppelklicke, zum <verb>.", red verb). */
    public Lore doubleClickDanger(String verb) {
        return hint("Doppelklicke", ", zum ", verb, false);
    }

    public List<LoreLine> build() {
        List<LoreLine> out = new ArrayList<>(description);
        if (!values.isEmpty()) {
            out.add(LoreLine.blank());
            out.addAll(values);
        }
        if (!hints.isEmpty()) {
            out.add(LoreLine.blank());
            out.addAll(hints);
        }
        return out;
    }
}
