package com.mcplatform.plugin.feature.web;

import com.mcplatform.plugin.platform.text.ChatDesign;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player-facing chat content for {@code /web}, built as pure Adventure {@link Component}s (no Bukkit,
 * no § codes — FR-025), so the wording and the clickable link are unit-testable. Styled with the central
 * {@link ChatDesign} scheme (see {@code CHAT_DESIGN.md}): a bold {@code WEB>} prefix, gray body, aqua
 * links/commands, red errors — consistent with the rest of the plugin. The link itself is the secret:
 * the one-time token is substituted into the admin-configured frontend URL template and shipped as an
 * {@link ClickEvent#openUrl(String) open_url} component the player never has to type.
 */
final class WebMessages {

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
                "Klicke auf den Button, um deinen Web-Account zu erstellen.",
                "Dort legst du dein Passwort fürs Webinterface fest.",
                "Web-Account erstellen",
                url);
    }

    /** Success notice for {@code /web resetPassword}: reset context, clickable link to set a new password. */
    static Component resetSuccess(String url) {
        return success(
                "Klicke auf den Button, um dein Passwort zurückzusetzen.",
                "Dort vergibst du ein neues Passwort fürs Webinterface.",
                "Passwort zurücksetzen",
                url);
    }

    private static Component success(String intro, String hint, String buttonLabel, String url) {
        Component button = Component.text(" [ " + buttonLabel + " » ] ", ChatDesign.ACCENT)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(ChatDesign.muted("Öffnen: " + url)));
        return Component.empty()
                .append(prefix()).append(ChatDesign.text(intro))
                .append(Component.newline())
                .append(prefix()).append(ChatDesign.muted(hint))
                .append(Component.newline())
                .append(button)
                .append(Component.newline())
                .append(prefix()).append(ChatDesign.muted("Der Link ist nur kurz gültig."));
    }

    /** {@code /web link} but the player already owns a web account (backend 409). */
    static Component alreadyExists() {
        return prefix()
                .append(ChatDesign.error("Du hast bereits einen Web-Account. Nutze "))
                .append(command("/web resetPassword"))
                .append(ChatDesign.error(", um dein Passwort zurückzusetzen."));
    }

    /** {@code /web resetPassword} but the player has no web account yet (backend 409). */
    static Component noAccount() {
        return prefix()
                .append(ChatDesign.error("Du hast noch keinen Web-Account. Nutze "))
                .append(command("/web link"))
                .append(ChatDesign.error(", um einen zu erstellen."));
    }

    /** Backend cooldown (429): a token was requested very recently. */
    static Component cooldown() {
        return prefix().append(ChatDesign.error("Bitte warte einen Moment, bevor du es erneut versuchst."));
    }

    /** Network / 5xx / anything else — never a stacktrace to the player. */
    static Component genericError() {
        return prefix().append(ChatDesign.error("Aktion fehlgeschlagen. Bitte versuche es später erneut."));
    }

    /** Shown for {@code /web} without (or with an unknown) subcommand. */
    static Component help() {
        return Component.empty()
                .append(prefix()).append(ChatDesign.text("Verwalte deinen Web-Account:"))
                .append(Component.newline())
                .append(ChatDesign.muted("  • ")).append(command("/web link"))
                .append(ChatDesign.text(" – erstellt deinen Web-Account."))
                .append(Component.newline())
                .append(ChatDesign.muted("  • ")).append(command("/web resetPassword"))
                .append(ChatDesign.text(" – setzt dein Passwort zurück."));
    }

    /** {@code /web} is player-only (needs a UUID); the console gets a hint instead of a crash. */
    static Component consoleOnly() {
        return prefix().append(ChatDesign.error("Nur Spieler können /web nutzen (es braucht deine Account-Identität)."));
    }

    /** The bold {@code WEB>} feature prefix (aqua) shared by every line. */
    private static Component prefix() {
        return ChatDesign.prefix("WEB", ChatDesign.ACCENT);
    }

    /** A clickable command reference: aqua, suggests the command on click. */
    private static Component command(String cmd) {
        return Component.text(cmd, ChatDesign.ACCENT)
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(ChatDesign.muted("Klicken zum Einfügen")));
    }
}
