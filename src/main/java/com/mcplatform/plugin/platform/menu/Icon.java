package com.mcplatform.plugin.platform.menu;

/**
 * Meaning → material mapping from MENU_DESIGN §3.1. Icons are chosen by *meaning*, not looks: a feature
 * names the semantic {@code Icon}, and the render layer resolves the 1.21 material. Carrying the
 * material as a plain {@code String} (the 1.21 enum name) — not a Bukkit {@code Material} — keeps the
 * model layer Bukkit-free and unit-testable; {@code MenuStyle} calls {@code Material.valueOf(...)} once,
 * at render time.
 *
 * <p>{@link #hideAttributes()} flags icons whose vanilla tooltip would leak attributes (the danger
 * sword), per the design note "Attribute ausblenden".
 */
public enum Icon {

    /** Border / filler with an empty name. */
    FILLER("GRAY_STAINED_GLASS_PANE"),
    /** Optional accent filler for the top border (mood). */
    FILLER_ACCENT("CYAN_STAINED_GLASS_PANE"),
    CLOSE("BARRIER"),
    /** Back to the parent menu — a door, distinct from the page arrows (§2.3 note). */
    BACK("OAK_DOOR"),
    PAGE_PREV("ARROW"),
    PAGE_NEXT("ARROW"),
    CONFIRM("LIME_DYE"),
    CANCEL("RED_DYE"),
    LOCKED("IRON_BARS"),
    PLAYER("PLAYER_HEAD"),
    INFO("BOOK"),
    MANAGE("WRITABLE_BOOK"),
    HISTORY("BOOK"),
    VALUE("OAK_SIGN"),
    /** Danger / punishment — render with attributes hidden. */
    DANGER("IRON_SWORD", true),
    ADD("ANVIL"),
    GLOBAL("COMMAND_BLOCK"),
    /** Neutral placeholder while async data loads. */
    LOADING("CLOCK"),
    /** "Nothing here" marker for empty lists. */
    EMPTY("LIGHT_GRAY_STAINED_GLASS_PANE");

    private final String material;
    private final boolean hideAttributes;

    Icon(String material) {
        this(material, false);
    }

    Icon(String material, boolean hideAttributes) {
        this.material = material;
        this.hideAttributes = hideAttributes;
    }

    /** 1.21 material enum name, resolved by the render layer via {@code Material.valueOf}. */
    public String material() {
        return material;
    }

    public boolean hideAttributes() {
        return hideAttributes;
    }
}
