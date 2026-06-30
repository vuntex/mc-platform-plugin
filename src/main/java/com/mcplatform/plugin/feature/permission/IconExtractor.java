package com.mcplatform.plugin.feature.permission;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The read side of the {@code display_icon} format (for {@code /rank toDisplayIcon}): turns the item in
 * a player's hand into the opaque, prefixed string. Exactly two outputs (clarified): if a texture base64
 * can be extracted from the head's {@code PlayerProfile} → {@code head-texture:<base64>}; otherwise
 * (a vanilla item, or a textureless {@code PLAYER_HEAD}) → {@code material:<TYPE>}. Never emits
 * {@code head-player:} in this slice. Uses the SAME {@link DisplayIconFormat} as the write side.
 *
 * <p>The choice logic is split into the pure {@link #choose} so it is unit-testable without a server;
 * {@link #toDisplayIcon} is the thin Bukkit extraction around it.
 */
public final class IconExtractor {

    private static final String TEXTURES = "textures";

    /** Build the prefixed string from an in-hand item. */
    public String toDisplayIcon(ItemStack item) {
        return choose(item.getType().name(), textureOf(item));
    }

    /**
     * Pure choice: a non-blank {@code texture} → {@code head-texture:<texture>}; otherwise
     * {@code material:<materialName>}. No Bukkit — fully unit-testable.
     */
    public static String choose(String materialName, String texture) {
        if (texture != null && !texture.isBlank()) {
            return DisplayIconFormat.headTexture(texture);
        }
        return DisplayIconFormat.material(materialName);
    }

    /** The embedded skin texture base64 of a custom head, or {@code null} if there is none. */
    private static String textureOf(ItemStack item) {
        if (!(item.getItemMeta() instanceof SkullMeta skull)) {
            return null;
        }
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return null;
        }
        for (ProfileProperty property : profile.getProperties()) {
            if (TEXTURES.equals(property.getName()) && property.getValue() != null && !property.getValue().isBlank()) {
                return property.getValue();
            }
        }
        return null;
    }
}
