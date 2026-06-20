package com.mcplatform.plugin.platform.menu;

import java.util.List;

/**
 * One lore line as an ordered list of coloured {@link MenuText} segments. A line is usually a single
 * segment, but the action hint (§3.3 — "die strengste Konvention") needs several colours on one line:
 * the click kind in {@link Token#CUE}, neutral binding text, and the verb in {@link Token#POSITIVE}/
 * {@link Token#NEGATIVE}. The render layer concatenates the segments into one Component (italic off).
 *
 * @param segments inline pieces, rendered left to right on a single line
 */
public record LoreLine(List<MenuText> segments) {

    public LoreLine {
        segments = List.copyOf(segments);
    }

    /** A blank spacer line (§3.3 puts blank lines between description, values and the hint). */
    public static LoreLine blank() {
        return new LoreLine(List.of(MenuText.body("")));
    }

    /** A single-segment line. */
    public static LoreLine of(MenuText segment) {
        return new LoreLine(List.of(segment));
    }

    /** A grey description line. */
    public static LoreLine body(String text) {
        return of(MenuText.body(text));
    }

    /** A "label: value" line with the value in {@link Token#ENTITY} gold (§3.3 current-values block). */
    public static LoreLine value(String label, String value) {
        return new LoreLine(List.of(MenuText.line(label + " ", Token.BODY), MenuText.line(value, Token.ENTITY)));
    }

    /** An inline multi-segment line. */
    public static LoreLine inline(MenuText... segments) {
        return new LoreLine(List.of(segments));
    }
}
