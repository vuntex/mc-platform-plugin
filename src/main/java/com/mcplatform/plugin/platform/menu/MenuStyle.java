package com.mcplatform.plugin.platform.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The single place semantic tokens/icons/feedback meet Adventure and Bukkit (MENU_DESIGN §3.4, §5.1).
 * It turns {@link MenuText}/{@link LoreLine} into Components (italic forced off, §5.1), {@link Icon} into
 * a {@link Material}, and {@link Feedback} into an Adventure sound. Because everything model-side is
 * Bukkit-free, this class is the only one that needs a server — and so it is exercised in-game, not in
 * unit tests.
 */
final class MenuStyle {

    private MenuStyle() {
    }

    /** Render a single styled segment, italic off, bold as requested. */
    static Component component(MenuText text) {
        Component c = Component.text(text.text())
                .color(TextColor.color(text.token().rgb()))
                .decoration(TextDecoration.ITALIC, false);
        return text.bold() ? c.decoration(TextDecoration.BOLD, true) : c;
    }

    /** Render a (possibly multi-segment) lore line as one Component, italic off. */
    static Component component(LoreLine line) {
        Component out = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (MenuText segment : line.segments()) {
            out = out.append(component(segment));
        }
        return out;
    }

    static List<Component> lore(List<LoreLine> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (LoreLine line : lines) {
            out.add(component(line));
        }
        return out;
    }

    /** Resolve an icon to its 1.21 material, falling back to stone if a name is somehow invalid. */
    static Material material(Icon icon) {
        Material material = Material.matchMaterial(icon.material());
        return material != null ? material : Material.STONE;
    }

    /** Play the semantic feedback sound for a player (§4.5). */
    static void play(Player player, Feedback feedback) {
        switch (feedback) {
            case SUCCESS -> sound(player, "minecraft:block.note_block.pling", 0.8f, 1.6f);
            case DENY -> sound(player, "minecraft:block.note_block.bass", 0.8f, 0.6f);
            case ADD -> sound(player, "minecraft:block.note_block.pling", 0.7f, 1.9f);
            case REMOVE -> sound(player, "minecraft:block.note_block.bass", 0.7f, 0.8f);
            case NAVIGATE -> sound(player, "minecraft:ui.button.click", 0.5f, 1.0f);
            case INERT -> sound(player, "minecraft:ui.button.click", 0.3f, 0.7f);
            case NONE -> {
            }
        }
    }

    /** Deliver a transient (action bar) or lasting (chat) message (§4.5). */
    static void send(Player player, MenuMessage message) {
        Component c = component(message.text());
        if (message.channel() == MenuMessage.Channel.ACTION_BAR) {
            player.sendActionBar(c);
        } else {
            player.sendMessage(c);
        }
    }

    private static void sound(Player player, String key, float volume, float pitch) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(
                Key.key(key), net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch));
    }
}
