package com.mcplatform.plugin.feature.report;

import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.Token;

import java.util.UUID;

/**
 * Step 1 of {@code /report}: a STATIC menu to pick the category. Choosing one arms the
 * {@link ReportReasonPrompt} for the reporter, tells them to type the reason in chat (with the cancel
 * word), and closes — the rest of the flow is the {@link ReportChatInputListener}. Bukkit-free: the
 * prompt-arming and the chat instruction go through the {@link com.mcplatform.plugin.platform.menu.MenuView},
 * so the selection logic is testable without a server.
 */
public final class ReportCategoryMenu {

    private final UUID reporter;
    private final UUID target;
    private final String targetName;
    private final ReportReasonPrompt prompt;
    private final Menu menu;

    public ReportCategoryMenu(UUID reporter, UUID target, String targetName, ReportReasonPrompt prompt) {
        this.reporter = reporter;
        this.target = target;
        this.targetName = targetName;
        this.prompt = prompt;

        MenuBuilder builder = MenuBuilder.panel(MenuText.name("Spieler melden", Token.NEGATIVE))
                .header(IconSpec.head(target, MenuText.name(targetName, Token.ENTITY),
                        Lore.builder().describe("Melde " + targetName + ".")
                                .describe("Wähle die passende Kategorie.").build()))
                .close();

        ReportCategory[] categories = ReportCategory.values();
        for (int i = 0; i < categories.length; i++) {
            builder.item(MenuLayout.CENTERED_ACTION_SLOTS[i], categoryButton(categories[i]));
        }
        this.menu = builder.build();
    }

    public Menu menu() {
        return menu;
    }

    private MenuItem categoryButton(ReportCategory category) {
        IconSpec icon = IconSpec.of(category.icon(),
                MenuText.name(category.label(), Token.INFO),
                Lore.builder()
                        .describe("Kategorie: " + category.label() + ".")
                        .clickToOpen("auswählen")
                        .build());
        return MenuItem.button(icon, ctx -> select(ctx, category));
    }

    void select(ClickContext ctx, ReportCategory category) {
        prompt.begin(reporter, target, targetName, category.wire());
        ctx.view().feedback(Feedback.SUCCESS);
        ctx.view().send(MenuMessage.chat(
                "Bitte gib jetzt den Grund für die Meldung gegen " + targetName
                        + " im Chat ein. Zum Abbrechen schreibe: " + ReportChatInputListener.CANCEL_WORD,
                Token.INFO));
        ctx.view().close();
    }
}
