package com.mcplatform.plugin.feature.chat;

import com.mcplatform.plugin.platform.text.Messages;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;

/** Replaces the vanilla "Unknown command" reply with the styled {@link Messages#unknownCommand()}. */
public final class UnknownCommandListener implements Listener {

    @EventHandler
    public void onUnknownCommand(UnknownCommandEvent event) {
        event.message(Messages.unknownCommand());
    }
}
