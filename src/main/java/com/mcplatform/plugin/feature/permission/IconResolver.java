package com.mcplatform.plugin.feature.permission;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Resolves an opaque {@code display_icon} string to a Bukkit {@link ItemStack} — the SINGLE place the
 * write side of the format lives (spec FR-020/022). Uses the Paper {@code PlayerProfile} API for heads
 * (no NMS). Anything unrecognised — {@code null}, unknown prefix, invalid material, malformed payload,
 * or any runtime error — renders a visible {@link #FALLBACK} icon, never a crash or an empty slot
 * (spec FR-021). The string is parsed via the shared {@link DisplayIconFormat}.
 */
public final class IconResolver {

    /** Visible "no/invalid icon" marker. */
    public static final Material FALLBACK = Material.BARRIER;

    private static final String TEXTURES = "textures";

    /** Resolve an opaque {@code display_icon} string to a bare item (name/lore added by the menu). */
    public ItemStack resolve(String displayIcon) {
        try {
            DisplayIconFormat.Parsed parsed = DisplayIconFormat.parse(displayIcon);
            if (parsed instanceof DisplayIconFormat.Material(String materialName)) {
                Material type = Material.matchMaterial(materialName);
                return type != null ? new ItemStack(type) : fallback();
            }
            if (parsed instanceof DisplayIconFormat.HeadTexture(String texture)) {
                return texturedHead(texture);
            }
            if (parsed instanceof DisplayIconFormat.HeadPlayer(UUID uuid)) {
                return playerHead(uuid);
            }
            return fallback();
        } catch (RuntimeException ex) {
            return fallback();
        }
    }

    private ItemStack fallback() {
        return new ItemStack(FALLBACK);
    }

    private ItemStack playerHead(UUID uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            head.setItemMeta(skull);
        }
        return head;
    }

    private ItemStack texturedHead(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty(TEXTURES, base64));
            skull.setPlayerProfile(profile);
            head.setItemMeta(skull);
        }
        return head;
    }
}
