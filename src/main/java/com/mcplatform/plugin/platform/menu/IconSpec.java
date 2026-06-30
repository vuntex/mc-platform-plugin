package com.mcplatform.plugin.platform.menu;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A fully described, not-yet-rendered icon: which semantic {@link Icon} (→ material), its name line, its
 * lore lines, and — for {@link Icon#PLAYER} heads — whose skin to show. This is the pure hand-off to the
 * render layer, which builds the actual {@code ItemStack} with Adventure components, italic off and
 * attributes hidden where the icon asks for it.
 *
 * <p>Holding lore as {@link LoreLine} (not Components) keeps icon construction unit-testable: a test can
 * assert that, say, a balance item's lore carries the new value without a server.
 *
 * <p>The optional {@code baseItem} is an additive escape hatch: a feature that needs an icon the closed
 * {@link Icon} enum cannot express (an arbitrary material, or a textured custom head built via the Paper
 * {@code PlayerProfile} API) resolves its own {@link ItemStack} and hands it in via {@link #ofItem}; the
 * render layer then clones it and lays this spec's name/lore on top. When {@code baseItem} is
 * {@code null} (the common case) nothing changes and icon construction stays Bukkit-free.
 *
 * @param icon      semantic icon (resolves to a material; ignored when {@code baseItem} is set)
 * @param name      the item name line (may be {@code null} for the filler's empty name)
 * @param lore      ordered lore lines per §3.3 (description, blank, values, blank, action hint)
 * @param skinOwner for {@link Icon#PLAYER}: whose head skin to render, else {@code null}
 * @param glow      add an enchant glint (used sparingly for emphasis)
 * @param baseItem  a pre-built item to clone instead of resolving {@code icon}'s material, or {@code null}
 */
public record IconSpec(Icon icon, MenuText name, List<LoreLine> lore, UUID skinOwner, boolean glow,
                       ItemStack baseItem) {

    public IconSpec {
        Objects.requireNonNull(icon, "icon");
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    /** A display icon with a name and lore (no skin, no glow). */
    public static IconSpec of(Icon icon, MenuText name, List<LoreLine> lore) {
        return new IconSpec(icon, name, lore, null, false, null);
    }

    /** A bare icon with just a name. */
    public static IconSpec of(Icon icon, MenuText name) {
        return new IconSpec(icon, name, List.of(), null, false, null);
    }

    /** The neutral border filler: an empty name so it reads as a frame, not a broken slot (§2.2). */
    public static IconSpec filler() {
        return new IconSpec(Icon.FILLER, null, List.of(), null, false, null);
    }

    /** A player head showing {@code owner}'s skin. */
    public static IconSpec head(UUID owner, MenuText name, List<LoreLine> lore) {
        return new IconSpec(Icon.PLAYER, name, lore, owner, false, null);
    }

    /**
     * A pre-built item icon with a name and lore: the render layer clones {@code base} and applies the
     * name/lore (italic off, attributes hidden). Used for icons the {@link Icon} enum cannot express,
     * e.g. a role's resolved {@code display_icon}.
     */
    public static IconSpec ofItem(ItemStack base, MenuText name, List<LoreLine> lore) {
        return new IconSpec(Icon.PLAYER, name, lore, null, false, base);
    }
}
