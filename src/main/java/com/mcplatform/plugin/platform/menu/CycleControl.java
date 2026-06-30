package com.mcplatform.plugin.platform.menu;

import java.util.List;
import java.util.Objects;

/**
 * A reusable click-to-cycle selector rendered as a single menu button: an {@link Icon} plus a lore list
 * of all options where the active one is highlighted green ({@link Token#POSITIVE}) and the rest grey
 * ({@link Token#BODY}). <b>Left-click</b> advances to the next option, <b>right-click</b> steps back to
 * the previous one (both wrap around) — so a skipped option is one right-click away.
 *
 * <p>The selected index lives here, so any menu can drop the control in as a field, read
 * {@link #current()} for the chosen value, and re-render the button with {@link #button(ClickHandler)} on
 * every layout. Pure and Bukkit-free → unit-testable. Generic over the value each option carries (e.g. a
 * filter enum), so it is shared across menus rather than re-implemented per menu.
 *
 * @param <T> the value carried by each option
 */
public final class CycleControl<T> {

    /** One selectable option: a display label and the value it stands for. */
    public record Option<T>(String label, T value) {
        public Option {
            Objects.requireNonNull(label, "label");
        }
    }

    private final Icon icon;
    private final String title;
    private final List<Option<T>> options;
    private int index;

    public CycleControl(Icon icon, String title, List<Option<T>> options) {
        this.icon = Objects.requireNonNull(icon, "icon");
        this.title = Objects.requireNonNull(title, "title");
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("options must be non-empty");
        }
        this.options = List.copyOf(options);
    }

    /** The currently selected value. */
    public T current() {
        return options.get(index).value();
    }

    /** The currently selected option (label + value). */
    public Option<T> currentOption() {
        return options.get(index);
    }

    /** Advance forward (left-click); wraps past the last option to the first. */
    public void next() {
        index = (index + 1) % options.size();
    }

    /** Step backward (right-click); wraps before the first option to the last. */
    public void previous() {
        index = (index - 1 + options.size()) % options.size();
    }

    /**
     * The menu button reflecting the current selection. Left-click cycles forward, right-click backward,
     * and {@code onChange} runs after the move (e.g. to re-load the menu). Re-call on every layout so the
     * rendered highlight tracks the selection.
     */
    public MenuItem button(ClickHandler onChange) {
        Objects.requireNonNull(onChange, "onChange");
        return MenuItem.button(render(), ClickAction.LEFT, ctx -> {
                    next();
                    onChange.onClick(ctx);
                })
                .on(ClickAction.RIGHT, ctx -> {
                    previous();
                    onChange.onClick(ctx);
                });
    }

    private IconSpec render() {
        Lore lore = Lore.builder();
        for (int i = 0; i < options.size(); i++) {
            boolean active = i == index;
            lore.line(active
                    ? LoreLine.of(MenuText.line("» " + options.get(i).label(), Token.POSITIVE))
                    : LoreLine.of(MenuText.line("   " + options.get(i).label(), Token.BODY)));
        }
        lore.hint("Linksklick", ", zum ", "Weiter", true);
        lore.hint("Rechtsklick", ", zum ", "Zurück", true);
        return IconSpec.of(icon, MenuText.name(title, Token.INFO), lore.build());
    }
}
