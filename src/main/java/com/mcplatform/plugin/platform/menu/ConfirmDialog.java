package com.mcplatform.plugin.platform.menu;

import java.util.List;
import java.util.Objects;

/**
 * Builds the §2.5 confirm dialog — the most important fixed blueprint: a 27er menu with the object of the
 * action on slot 4, confirm (green) on 11, cancel (red) on 15, back on 18 and close on 22. Critical,
 * irreversible actions ({@link #critical()}) require a <em>double-click</em> on confirm and say so in the
 * lore; ordinary confirmations are a single click. Pure, so the slot layout and the click-kind gating
 * are unit-testable.
 */
public final class ConfirmDialog {

    private final MenuText title;
    private final IconSpec object;
    private MenuText confirmName = MenuText.name("Bestätigen", Token.POSITIVE);
    private List<LoreLine> confirmLore;
    private ClickHandler onConfirm = ctx -> {
    };
    private ClickHandler onCancel = ctx -> ctx.view().close();
    private ClickHandler onBack;
    private boolean critical;

    private ConfirmDialog(MenuText title, IconSpec object) {
        this.title = Objects.requireNonNull(title, "title");
        this.object = Objects.requireNonNull(object, "object");
    }

    /** Start a confirm dialog titled {@code title} confirming the action on {@code object} (slot 4). */
    public static ConfirmDialog of(MenuText title, IconSpec object) {
        return new ConfirmDialog(title, object);
    }

    /** Custom confirm-button name (default "Bestätigen", green). */
    public ConfirmDialog confirmName(MenuText name) {
        this.confirmName = Objects.requireNonNull(name, "name");
        return this;
    }

    /** Lore describing the consequence on the confirm button (the action hint is added automatically). */
    public ConfirmDialog confirmDescription(List<LoreLine> lore) {
        this.confirmLore = lore;
        return this;
    }

    public ConfirmDialog onConfirm(ClickHandler handler) {
        this.onConfirm = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** Override cancel behaviour (default: close the dialog). */
    public ConfirmDialog onCancel(ClickHandler handler) {
        this.onCancel = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** Add a back button on slot 18 pointing at the parent menu. */
    public ConfirmDialog onBack(ClickHandler handler) {
        this.onBack = handler;
        return this;
    }

    /** Mark the action irreversible → confirm requires a double-click (§2.5). */
    public ConfirmDialog critical() {
        this.critical = true;
        return this;
    }

    public Menu build() {
        MenuBuilder builder = MenuBuilder.dialog(title);
        builder.item(MenuLayout.DIALOG_HEADER, MenuItem.display(object));

        Lore lore = Lore.builder();
        if (confirmLore != null) {
            for (LoreLine line : confirmLore) {
                lore.line(line);
            }
        }
        // The confirm action hint: double-click for critical, single click otherwise.
        if (critical) {
            lore.doubleClickDanger("bestätigen");
        } else {
            lore.hint("Klicke", ", zum ", "bestätigen", true);
        }
        IconSpec confirmIcon = IconSpec.of(Icon.CONFIRM, confirmName, lore.build());
        ClickAction confirmTrigger = critical ? ClickAction.DOUBLE_CLICK : ClickAction.LEFT;
        builder.item(MenuLayout.DIALOG_CONFIRM, MenuItem.button(confirmIcon, confirmTrigger, ctx -> {
            ctx.view().feedback(Feedback.SUCCESS);
            onConfirm.onClick(ctx);
        }));

        IconSpec cancelIcon = IconSpec.of(Icon.CANCEL, MenuText.name("Abbrechen", Token.NEGATIVE),
                Lore.builder().describe("Bricht die Aktion ab.").hint("Klicke", ", zum ", "abbrechen", false).build());
        builder.item(MenuLayout.DIALOG_CANCEL, MenuItem.button(cancelIcon, ctx -> {
            ctx.view().feedback(Feedback.NAVIGATE);
            onCancel.onClick(ctx);
        }));

        if (onBack != null) {
            builder.item(MenuLayout.DIALOG_BACK, MenuItem.button(
                    IconSpec.of(Icon.BACK, MenuText.name("Zurück"),
                            Lore.builder().clickToOpen("zurückgehen").build()),
                    ctx -> {
                        ctx.view().feedback(Feedback.NAVIGATE);
                        onBack.onClick(ctx);
                    }));
        }
        builder.item(MenuLayout.DIALOG_CLOSE, MenuBuilder.closeButton());
        return builder.build();
    }
}
