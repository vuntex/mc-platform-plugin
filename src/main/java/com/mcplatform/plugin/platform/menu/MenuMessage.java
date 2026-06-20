package com.mcplatform.plugin.platform.menu;

import java.util.Objects;

/**
 * A transient or permanent message a handler asks the view to deliver (MENU_DESIGN §4.5): transient
 * feedback goes to the {@link Channel#ACTION_BAR}, lasting confirmations to {@link Channel#CHAT}. Pure
 * data ({@link MenuText} + channel) so the message-building logic — e.g. mapping a backend 403 to "no
 * permission" — is unit-testable without Bukkit.
 */
public record MenuMessage(MenuText text, Channel channel) {

    public enum Channel {
        /** Transient — overlays the hotbar, never spams chat. */
        ACTION_BAR,
        /** Lasting — written to chat for important confirmations. */
        CHAT
    }

    public MenuMessage {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(channel, "channel");
    }

    public static MenuMessage actionBar(String text, Token token) {
        return new MenuMessage(MenuText.line(text, token), Channel.ACTION_BAR);
    }

    public static MenuMessage chat(String text, Token token) {
        return new MenuMessage(MenuText.line(text, token), Channel.CHAT);
    }
}
