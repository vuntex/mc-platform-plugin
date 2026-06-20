package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.ConfirmDialog;
import com.mcplatform.plugin.platform.menu.Feedback;
import com.mcplatform.plugin.platform.menu.Icon;
import com.mcplatform.plugin.platform.menu.IconSpec;
import com.mcplatform.plugin.platform.menu.Lore;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuBuilder;
import com.mcplatform.plugin.platform.menu.MenuItem;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.IssueFromTemplateRequest;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.TemplateResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Team-side punish menu (MENU_DESIGN demo): the player is the context; templates are shown as a list
 * gated by the backend's {@code canApply} flag — applicable templates are highlighted and interactive,
 * the rest are visible-but-locked ({@link Icon#LOCKED}, §3.1) so the team member sees what exists without
 * being able to apply it. Picking a template opens a §2.5 confirm that calls {@code from-template}; a
 * {@code 403} (not permitted) or {@code 409} (already active) from the authoritative backend is shown
 * cleanly. STATIC: a template list is a snapshot acted on, not a live value.
 *
 * <p>Pure except the issue call: the {@code canApply} gating and layout are unit-testable.
 */
public final class PunishMenu {

    private final UUID target;
    private final String targetName;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Menu menu;

    private List<TemplateResponse> templates = new ArrayList<>();
    private boolean loaded;
    private int page;

    public PunishMenu(UUID target, String targetName, BackendClient backend, PlatformScheduler scheduler) {
        this.target = target;
        this.targetName = targetName;
        this.backend = backend;
        this.scheduler = scheduler;
        this.menu = MenuBuilder.list(MenuText.name("Strafe wählen: " + targetName, Token.NEGATIVE))
                .header(IconSpec.head(target, MenuText.name(targetName, Token.ENTITY),
                        Lore.builder().describe("Wähle eine Vorlage, um sie zu verhängen.").build()))
                .close()
                .build();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Feed the loaded templates (only active ones are shown) and reset to page 1. */
    public void setTemplates(List<TemplateResponse> loadedTemplates) {
        this.templates = new ArrayList<>();
        for (TemplateResponse t : loadedTemplates) {
            if (t.active()) {
                this.templates.add(t);
            }
        }
        this.loaded = true;
        this.page = 0;
    }

    public void layout() {
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(templates.size());
        for (TemplateResponse t : templates) {
            items.add(templateItem(t));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, target) -> {
            this.page = target;
            layout();
            ctx.view().refresh();
        });
    }

    public void render(MenuView view) {
        layout();
        view.refresh();
    }

    private void showLoading() {
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.LOADING,
                MenuText.name("Lade…", Token.BODY),
                Lore.builder().describe("Vorlagen werden geladen.").build())));
    }

    // ── template item: applicable (interactive) vs locked (display) ──────────────────────────────

    MenuItem templateItem(TemplateResponse template) {
        if (template.canApply()) {
            IconSpec icon = IconSpec.of(iconFor(template.type()),
                    MenuText.name(template.key(), Token.POSITIVE),
                    Lore.builder()
                            .describe(nullToDash(template.defaultReason()))
                            .value("Typ:", template.type())
                            .value("Dauer:", template.durationMillis() == 0
                                    ? "permanent"
                                    : PunishmentFormat.formatDuration(template.durationMillis()))
                            .clickToOpen("verhängen")
                            .build());
            return MenuItem.button(icon, ctx -> openConfirm(ctx, template));
        }
        // Visible but locked — no handler, IRON_BARS, muted (§3.1).
        IconSpec icon = IconSpec.of(Icon.LOCKED, MenuText.name(template.key(), Token.MUTED),
                Lore.builder()
                        .describe(nullToDash(template.defaultReason()))
                        .value("Typ:", template.type())
                        .value("Status:", "gesperrt – keine Berechtigung")
                        .build());
        return MenuItem.display(icon);
    }

    void openConfirm(ClickContext ctx, TemplateResponse template) {
        IconSpec object = IconSpec.of(iconFor(template.type()),
                MenuText.name(template.key(), Token.NEGATIVE),
                Lore.builder().describe(nullToDash(template.defaultReason())).value("Spieler:", targetName).build());
        Menu confirm = ConfirmDialog.of(MenuText.name("Strafe verhängen?", Token.DANGER), object)
                .confirmName(MenuText.name("Verhängen", Token.POSITIVE))
                .onConfirm(c -> issue(c, template))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    void issue(ClickContext ctx, TemplateResponse template) {
        MenuView view = ctx.view();
        UUID actor = ctx.playerId();
        scheduler.runAsync(() -> {
            IssueFromTemplateRequest request = new IssueFromTemplateRequest(
                    template.key(), null /* use template default reason */, actor, UUID.randomUUID(), "plugin");
            backend.callIdempotent(PunishmentEndpoints.ISSUE_FROM_TEMPLATE, request, target.toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            view.feedback(Feedback.DENY);
                            view.send(PunishmentMenuText.issueError(error));
                            view.open(menu); // back to the template list
                            return;
                        }
                        view.feedback(Feedback.SUCCESS);
                        view.send(PunishmentMenuText.issueSuccess(template.key(), targetName));
                        view.close();
                    }));
        });
    }

    private static Icon iconFor(String type) {
        if (PunishmentType.deniesChat(type)) {
            return Icon.LOCKED;
        }
        if (PunishmentType.deniesLogin(type)) {
            return Icon.DANGER;
        }
        return Icon.INFO;
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
