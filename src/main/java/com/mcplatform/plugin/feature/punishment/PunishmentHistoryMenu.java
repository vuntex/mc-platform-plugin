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
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.PunishmentResponse;
import com.mcplatform.protocol.punishment.RevokeRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Demo 2 — the punishment history menu: a paginated list with a two-step confirm (MENU_DESIGN §4.4,
 * §2.5). STATIC: it shows what was loaded when opened. Clicking an active punishment opens a critical
 * (double-click) confirm dialog that calls the existing {@code REVOKE} endpoint; a backend {@code 403} is
 * shown cleanly to the team member — the menu is openable optimistically, the backend stays authoritative.
 *
 * <p>The layout (pages, entry items, confirm) is pure; only the revoke network call touches transport.
 * Page state lives here and the page arrows re-lay-out through the click's live view, so pagination is
 * unit-testable end to end.
 */
public final class PunishmentHistoryMenu {

    private final UUID target;
    private final String targetName;
    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final Menu menu;

    private List<PunishmentResponse> entries = new ArrayList<>();
    private boolean loaded;
    private int page;

    public PunishmentHistoryMenu(UUID target, String targetName,
                                 BackendClient backend, PlatformScheduler scheduler) {
        this.target = target;
        this.targetName = targetName;
        this.backend = backend;
        this.scheduler = scheduler;
        this.menu = MenuBuilder.list(MenuText.name("Historie: " + targetName, Token.NEGATIVE))
                .header(IconSpec.head(target, MenuText.name(targetName, Token.ENTITY),
                        Lore.builder().describe("Strafhistorie dieses Spielers.").build()))
                .close()
                .build();
        showLoading();
    }

    public Menu menu() {
        return menu;
    }

    /** Feed the loaded active punishments and reset to page 1 (call before {@link #layout}). */
    public void setEntries(List<PunishmentResponse> loadedEntries) {
        this.entries = new ArrayList<>(loadedEntries);
        this.loaded = true;
        this.page = 0;
    }

    /** Lay out the current page into the menu model (pure — no view, no refresh). */
    public void layout() {
        if (!loaded) {
            showLoading();
            return;
        }
        List<MenuItem> items = new ArrayList<>(entries.size());
        for (PunishmentResponse entry : entries) {
            items.add(entryItem(entry));
        }
        MenuBuilder.renderPage(menu, items, page, (ctx, target) -> {
            this.page = target;
            layout();
            ctx.view().refresh();
        });
    }

    /** Lay out the current page and push it to the open view. */
    public void render(MenuView view) {
        layout();
        view.refresh();
    }

    private void showLoading() {
        menu.setItem(MenuBuilder.EMPTY_MARKER_SLOT, MenuItem.display(IconSpec.of(Icon.LOADING,
                MenuText.name("Lade…", Token.BODY),
                Lore.builder().describe("Strafhistorie wird geladen.").build())));
    }

    // ── entry + confirm ──────────────────────────────────────────────────────────────────────────

    private MenuItem entryItem(PunishmentResponse r) {
        long now = System.currentTimeMillis();
        Lore lore = Lore.builder()
                .describe("Grund: " + nullToDash(r.reason()))
                .value("Typ:", r.type())
                .value("Status:", r.active() ? "aktiv" : "abgelaufen/aufgehoben")
                .value("Dauer:", r.expiresAtEpochMilli() == 0
                        ? "permanent"
                        : PunishmentFormat.formatDuration(r.expiresAtEpochMilli() - now) + " verbleibend");

        if (r.active()) {
            lore.clickToOpen("aufheben");
            IconSpec icon = IconSpec.of(iconFor(r.type()), MenuText.name(r.type(), Token.NEGATIVE), lore.build());
            return MenuItem.button(icon, ctx -> openRevokeConfirm(ctx, r));
        }
        IconSpec icon = IconSpec.of(Icon.HISTORY, MenuText.name(r.type(), Token.MUTED), lore.build());
        return MenuItem.display(icon);
    }

    void openRevokeConfirm(ClickContext ctx, PunishmentResponse r) {
        IconSpec object = IconSpec.of(iconFor(r.type()), MenuText.name(r.type(), Token.NEGATIVE),
                Lore.builder().describe("Grund: " + nullToDash(r.reason())).value("Spieler:", targetName).build());
        Menu confirm = ConfirmDialog.of(MenuText.name("Strafe aufheben?", Token.DANGER), object)
                .critical() // irreversible team action → double-click (§2.5)
                .confirmName(MenuText.name("Strafe aufheben", Token.POSITIVE))
                .onConfirm(c -> revoke(c, r))
                .onBack(c -> c.view().open(menu))
                .build();
        ctx.view().open(confirm);
    }

    void revoke(ClickContext ctx, PunishmentResponse r) {
        MenuView view = ctx.view();
        UUID actor = ctx.playerId();
        scheduler.runAsync(() -> {
            RevokeRequest request = new RevokeRequest(actor, "Aufgehoben über das Historie-Menü",
                    UUID.randomUUID(), "plugin");
            backend.callIdempotent(PunishmentEndpoints.REVOKE, request, r.id().toString())
                    .whenComplete((response, error) -> scheduler.runSync(() -> {
                        if (error != null || response == null) {
                            // Backend is authoritative: a 403 is shown cleanly, then back to the list.
                            view.feedback(Feedback.DENY);
                            view.send(PunishmentMenuText.revokeError(error));
                            view.open(menu);
                            return;
                        }
                        view.feedback(Feedback.SUCCESS);
                        view.send(PunishmentMenuText.revokeSuccess());
                        entries.removeIf(e -> e.id().equals(r.id()));
                        layout();
                        view.open(menu);
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
