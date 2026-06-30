package com.mcplatform.plugin.feature.web;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player-facing chat content for {@code /web}, built as pure Adventure {@link Component}s (no Bukkit,
 * no § codes — FR-025), so the wording and the clickable link are unit-testable. The link itself is the
 * secret: the one-time token is substituted into the admin-configured frontend URL template and shipped
 * as an {@link ClickEvent#openUrl(String) open_url} component the player never has to type.
 */
final class WebMessages {

    private static final TextColor ACCENT = TextColor.color(0x4FC3F7); // web blue
    private static final TextColor LABEL = TextColor.color(0xAAAAAA);
    private static final TextColor LINK = TextColor.color(0x55FFFF);
    private static final TextColor ERROR = TextColor.color(0xFF5555);

    /** Placeholder the configured frontend URL templates must contain. */
    static final String TOKEN_PLACEHOLDER = "{token}";

    private WebMessages() {
    }

    /** Substitute the one-time token into the configured URL template (token is URL-safe by contract). */
    static String buildUrl(String template, String token) {
        return template.replace(TOKEN_PLACEHOLDER, token);
    }

    /** Success notice for {@code /web link}: create-account context, clickable link to set a password. */
    static Component linkSuccess(String url) {
        return success(
                "Klicke hier, um deinen Web-Account zu erstellen",
                "Du legst dort dein Passwort fürs Webinterface fest.",
                url);
    }

    /** Success notice for {@code /web resetPassword}: reset context, clickable link to set a new password. */
    static Component resetSuccess(String url) {
        return success(
                "Klicke hier, um dein Passwort zurückzusetzen",
                "Du vergibst dort ein neues Passwort fürs Webinterface.",
                url);
    }

    private static Component success(String linkText, String hint, String url) {
        Component link = Component.text("» " + linkText, LINK)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, ACCENT)));
        return Component.text("Web-Account", ACCENT).decorate(TextDecoration.BOLD)
                .append(Component.text(" » ", LABEL).decoration(TextDecoration.BOLD, false))
                .append(Component.text(hint, LABEL))
                .append(Component.newline())
                .append(link)
                .append(Component.newline())
                .append(Component.text("Der Link ist nur kurz gültig.", LABEL));
    }

    /** {@code /web link} but the player already owns a web account (backend 409). */
    static Component alreadyExists() {
        return error("Du hast bereits einen Web-Account. Nutze ")
                .append(Component.text("/web resetPassword", LINK))
                .append(Component.text(", um dein Passwort zurückzusetzen.", ERROR));
    }

    /** {@code /web resetPassword} but the player has no web account yet (backend 409). */
    static Component noAccount() {
        return error("Du hast noch keinen Web-Account. Nutze ")
                .append(Component.text("/web link", LINK))
                .append(Component.text(", um einen zu erstellen.", ERROR));
    }

    /** Backend cooldown (429): a token was requested very recently. */
    static Component cooldown() {
        return error("Bitte warte einen Moment, bevor du es erneut versuchst.");
    }

    /** Network / 5xx / anything else — never a stacktrace to the player. */
    static Component genericError() {
        return error("Aktion fehlgeschlagen. Bitte versuche es später erneut.");
    }

    /** Shown for {@code /web} without (or with an unknown) subcommand. */
    static Component help() {
        return Component.text("Web-Account", ACCENT).decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("  /web link", LINK))
                .append(Component.text(" – erstellt deinen Web-Account.", LABEL))
                .append(Component.newline())
                .append(Component.text("  /web resetPassword", LINK))
                .append(Component.text(" – setzt dein Passwort zurück.", LABEL));
    }

    /** {@code /web} is player-only (needs a UUID); the console gets a hint instead of a crash. */
    static Component consoleOnly() {
        return error("Nur Spieler können /web nutzen (es braucht deine Account-Identität).");
    }

    private static Component error(String message) {
        return Component.text(message, ERROR);
    }
}
