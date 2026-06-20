package com.mcplatform.plugin.platform.menu;

import java.util.Objects;

/**
 * One styled line of text as pure data: the literal string plus the semantic {@link Token} that colours
 * it, and whether it is bold. The render layer turns this into an Adventure {@code Component} (with
 * italic forced off, §5.1). Modelling text this way — instead of holding a {@code Component} — is what
 * keeps menu titles, item names and lore unit-testable without Adventure on the test classpath.
 *
 * @param text  the literal content (already localised by the feature)
 * @param token semantic colour
 * @param bold  render bold (titles/names are bold by convention, body lines are not)
 */
public record MenuText(String text, Token token, boolean bold) {

    public MenuText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(token, "token");
    }

    /** A bold name in the given token (item names / titles). */
    public static MenuText name(String text, Token token) {
        return new MenuText(text, token, true);
    }

    /** A bold white name (the §3.2 default for interactive item names). */
    public static MenuText name(String text) {
        return new MenuText(text, Token.TITLE, true);
    }

    /** A non-bold body line in the given token. */
    public static MenuText line(String text, Token token) {
        return new MenuText(text, token, false);
    }

    /** A non-bold grey description line (the §3.3 body default). */
    public static MenuText body(String text) {
        return new MenuText(text, Token.BODY, false);
    }
}
