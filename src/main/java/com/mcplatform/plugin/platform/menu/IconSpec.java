package com.mcplatform.plugin.platform.menu;

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
 * @param icon      semantic icon (resolves to a material)
 * @param name      the item name line (may be {@code null} for the filler's empty name)
 * @param lore      ordered lore lines per §3.3 (description, blank, values, blank, action hint)
 * @param skinOwner for {@link Icon#PLAYER}: whose head skin to render, else {@code null}
 * @param glow      add an enchant glint (used sparingly for emphasis)
 */
public record IconSpec(Icon icon, MenuText name, List<LoreLine> lore, UUID skinOwner, boolean glow) {

    public IconSpec {
        Objects.requireNonNull(icon, "icon");
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    /** A display icon with a name and lore (no skin, no glow). */
    public static IconSpec of(Icon icon, MenuText name, List<LoreLine> lore) {
        return new IconSpec(icon, name, lore, null, false);
    }

    /** A bare icon with just a name. */
    public static IconSpec of(Icon icon, MenuText name) {
        return new IconSpec(icon, name, List.of(), null, false);
    }

    /** The neutral border filler: an empty name so it reads as a frame, not a broken slot (§2.2). */
    public static IconSpec filler() {
        return new IconSpec(Icon.FILLER, null, List.of(), null, false);
    }

    /** A player head showing {@code owner}'s skin. */
    public static IconSpec head(UUID owner, MenuText name, List<LoreLine> lore) {
        return new IconSpec(Icon.PLAYER, name, lore, owner, false);
    }
}
