package com.mcplatform.plugin.platform.text;

import net.kyori.adventure.text.Component;

/**
 * Central catalog of recurring chat messages (no-permission, player-not-found, …) so they read the same
 * everywhere. Built on {@link ChatDesign}, German, Du-form. Features should prefer these over ad-hoc
 * strings for the common cases; feature-specific wording stays in the feature.
 */
public final class Messages {

    private Messages() {
    }

    /** Missing permission for an action/command. */
    public static Component noPermission() {
        return ChatDesign.error("Dir fehlt die Berechtigung dafür.");
    }

    /** A console/command-block tried a player-only command. */
    public static Component playersOnly() {
        return ChatDesign.error("Diesen Befehl können nur Spieler nutzen.");
    }

    /** No player with that name is known to the server. */
    public static Component playerNotFound(String name) {
        return Component.empty()
                .append(ChatDesign.error("Der Spieler "))
                .append(ChatDesign.name(name))
                .append(ChatDesign.error(" wurde nicht gefunden."));
    }

    /** The named player is known but not currently online. */
    public static Component playerNotOnline(String name) {
        return Component.empty()
                .append(ChatDesign.error("Der Spieler "))
                .append(ChatDesign.name(name))
                .append(ChatDesign.error(" ist nicht online."));
    }

    /** Custom message for an unknown command (replaces the vanilla "Unknown command"). */
    public static Component unknownCommand() {
        return Component.empty()
                .append(ChatDesign.error("Dieser Befehl existiert nicht. "))
                .append(ChatDesign.text("Tippe /help für eine Übersicht."));
    }

    /** Generic backend/transport failure shown to the player. */
    public static Component backendError() {
        return ChatDesign.error("Etwas ist schiefgelaufen. Bitte versuche es später erneut.");
    }

    /** Usage hint: "Verwendung: /cmd …". */
    public static Component usage(String usage) {
        return Component.empty()
                .append(ChatDesign.muted("Verwendung: "))
                .append(ChatDesign.text(usage));
    }
}
