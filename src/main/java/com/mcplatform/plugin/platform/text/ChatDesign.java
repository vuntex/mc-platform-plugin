package com.mcplatform.plugin.platform.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

/**
 * Chat-message styling helpers (see {@code CHAT_DESIGN.md}). A message is a feature {@link #prefix}
 * (bold coloured name + a dark-gray
 * {@code »}) followed by gray body text with semantic colours: values yellow, names gold, success green,
 * errors red. Keeps every feature's chat consistent and §-code-free.
 */
public final class ChatDesign {

    /** Body / neutral text. */
    public static final TextColor TEXT = NamedTextColor.GRAY;
    /** Amounts, numbers, highlighted values. */
    public static final TextColor VALUE = NamedTextColor.YELLOW;
    /** Player names. */
    public static final TextColor NAME = NamedTextColor.GOLD;
    /** Success / positive action. */
    public static final TextColor SUCCESS = NamedTextColor.GREEN;
    /** Errors / warnings. */
    public static final TextColor ERROR = NamedTextColor.RED;
    /** Separators, brackets, secondary detail. */
    public static final TextColor MUTED = NamedTextColor.DARK_GRAY;
    /** Accents: links, clickable hints. */
    public static final TextColor ACCENT = NamedTextColor.AQUA;

    private ChatDesign() {
    }

    /**
     * Feature prefix: the name plus a {@code >}, ALL bold and in ONE colour, then a plain space, e.g.
     * "COINS> ". The root component carries NO style, so anything appended afterwards does not inherit the
     * bold/colour — only the explicitly-set colours apply.
     */
    public static Component prefix(String feature, TextColor color) {
        return Component.empty()
                .append(Component.text(feature + ">", color).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" "));
    }

    public static Component text(String s) {
        return Component.text(s, TEXT);
    }

    public static Component value(String s) {
        return Component.text(s, VALUE);
    }

    public static Component name(String s) {
        return Component.text(s, NAME);
    }

    public static Component muted(String s) {
        return Component.text(s, MUTED);
    }

    public static Component success(String s) {
        return Component.text(s, SUCCESS);
    }

    public static Component error(String s) {
        return Component.text(s, ERROR);
    }

    /** German thousand-separated number: {@code 50000 → "50.000"}. */
    public static String number(long amount) {
        return String.format(Locale.GERMANY, "%,d", amount);
    }
}
