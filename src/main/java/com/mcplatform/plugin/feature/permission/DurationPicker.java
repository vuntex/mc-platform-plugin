package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * STATIC duration chooser for a grant's expiry: a clear <b>Permanent</b> button plus common presets, and
 * an "Eigene Dauer…" option that prompts for a custom value via chat (accepting {@code 30d}/{@code 1d12h}
 * or {@code permanent}/{@code -1}). Replaces the old "leave the chat empty = permanent" flow. Reusable by
 * the rank- and permission-grant flows; reports the chosen {@code expiresInSeconds} ({@code null} =
 * permanent) through {@code onChosen}, which then performs the actual grant + navigation.
 */
public final class DurationPicker {

    private static final long DAY = 86_400L;

    private record Preset(String label, Long seconds) {
    }

    private static final List<Preset> PRESETS = List.of(
            new Preset("1 Tag", DAY),
            new Preset("7 Tage", 7 * DAY),
            new Preset("30 Tage", 30 * DAY),
            new Preset("90 Tage", 90 * DAY),
            new Preset("1 Jahr", 365 * DAY),
            new Preset("Permanent", null));

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int SLOT_CUSTOM = 25;

    private final MenuManager menus;
    private final PermissionInput input;
    private final String subject;
    private final Consumer<Long> onChosen;
    private final Runnable back;
    private final Menu menu;

    /**
     * @param subject  what is being granted, for the custom prompt text (e.g. {@code "Rang vip"})
     * @param onChosen invoked with the chosen {@code expiresInSeconds} ({@code null} = permanent)
     * @param back     return to the calling menu (also used when a custom entry is invalid)
     */
    public DurationPicker(MenuManager menus, PermissionInput input, String title, String subject,
                          Consumer<Long> onChosen, Runnable back) {
        this.menus = menus;
        this.input = input;
        this.subject = subject;
        this.onChosen = onChosen;
        this.back = back;
        this.menu = build(title);
    }

    public Menu menu() {
        return menu;
    }

    private Menu build(String title) {
        MenuBuilder builder = MenuBuilder.panel(MenuText.name(title, Token.SPECIAL))
                .header(IconSpec.of(Icon.INFO, MenuText.name("Dauer wählen", Token.INFO),
                        Lore.builder().describe("Wie lange soll die Vergabe gelten?").build()))
                .back(ctx -> back.run())
                .close();

        for (int i = 0; i < PRESETS.size(); i++) {
            Preset preset = PRESETS.get(i);
            boolean permanent = preset.seconds() == null;
            Lore lore = Lore.builder()
                    .describe(permanent ? "Läuft nie ab." : "Gilt für " + preset.label() + ".")
                    .clickToOpen("wählen");
            IconSpec icon = IconSpec.of(permanent ? Icon.CONFIRM : Icon.DURATION,
                    MenuText.name(preset.label(), permanent ? Token.POSITIVE : Token.ENTITY), lore.build());
            builder.item(SLOTS[i], MenuItem.button(icon, ctx -> {
                ctx.view().feedback(Feedback.SUCCESS);
                onChosen.accept(preset.seconds());
            }));
        }

        builder.item(SLOT_CUSTOM, MenuItem.button(
                IconSpec.of(Icon.ADD, MenuText.name("Eigene Dauer…", Token.ENTITY),
                        Lore.builder().describe("Z. B. 14d, 6h, 1d12h — oder 'permanent'.").clickToOpen("eingeben").build()),
                this::onCustom));
        return builder.build();
    }

    private void onCustom(ClickContext ctx) {
        Player viewer = Bukkit.getPlayer(ctx.playerId());
        if (viewer == null || input == null) {
            return;
        }
        ctx.view().close();
        input.prompt(viewer, "Dauer für " + subject + " (z. B. 30d, 12h, 1d12h — oder 'permanent'):", text -> {
            Long seconds;
            try {
                seconds = DurationInput.parseSeconds(text);
            } catch (IllegalArgumentException ex) {
                viewer.sendMessage(Component.text(
                        "Ungültige Dauer — nutze z. B. 30d, 12h, oder 'permanent'.", NamedTextColor.RED));
                back.run();
                return;
            }
            onChosen.accept(seconds);
        });
    }
}
