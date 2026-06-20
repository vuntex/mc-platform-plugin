package com.mcplatform.plugin.platform.menu;

/**
 * Semantic colour tokens from MENU_DESIGN §3.4. Features reference a token by name, never a raw hex
 * value; the Bukkit/Adventure render layer ({@code MenuStyle}) is the single place that maps a token to
 * an actual {@code TextColor}. Keeping the token a plain enum (no Adventure import) is what lets the
 * whole menu *model* compile and unit-test without a running server.
 *
 * <p>The hex value is the 2026 specification carried here only as documentation/data; it is consumed by
 * the render layer.
 */
public enum Token {

    /** Item name (default): white, bold. */
    TITLE(0xFFFFFF),
    /** Description text: grey. */
    BODY(0xAAAAAA),
    /** Click kind in the action hint: aqua. */
    CUE(0x00AAAA),
    /** Confirm / enable / online: green. */
    POSITIVE(0x55FF55),
    /** Cancel / disable / error / offline: red. */
    NEGATIVE(0xFF5555),
    /** Irreversible action (delete, ban): dark red. */
    DANGER(0xAA0000),
    /** Player names, concrete values, "change": gold. */
    ENTITY(0xFFAA00),
    /** Premium / protected: purple. */
    SPECIAL(0xAA00AA),
    /** Info / statistics header: light blue. */
    INFO(0x55FFFF),
    /** Bullet / reference lines: dark grey. */
    MUTED(0x555555);

    private final int rgb;

    Token(int rgb) {
        this.rgb = rgb;
    }

    /** The 2026 hex value (0xRRGGBB), consumed by the render layer's token→colour mapping. */
    public int rgb() {
        return rgb;
    }
}
