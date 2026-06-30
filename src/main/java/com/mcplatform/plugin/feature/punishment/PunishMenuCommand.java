package com.mcplatform.plugin.feature.punishment;

import com.mcplatform.plugin.platform.ActionBars;
import com.mcplatform.plugin.platform.PlatformScheduler;
import com.mcplatform.plugin.platform.menu.MenuManager;
import com.mcplatform.plugin.platform.menu.MenuText;
import com.mcplatform.plugin.platform.menu.MenuView;
import com.mcplatform.plugin.platform.menu.PlayerPickerMenu;
import com.mcplatform.plugin.platform.menu.Token;
import com.mcplatform.plugin.platform.text.Messages;
import com.mcplatform.plugin.transport.BackendClient;
import com.mcplatform.protocol.punishment.PunishmentEndpoints;
import com.mcplatform.protocol.punishment.TemplateResponse;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /punishmenu [player]} — team-side entry to the punish flow. With a name it opens the
 * {@link PunishMenu} for that player; without one it opens the shared {@link PlayerPickerMenu} to choose
 * a target first. Optimistic UI gate (the {@code mcplatform.punish} permission to open); the backend is
 * authoritative on each issue (403/409 shown in the menu). The menu opens immediately in "Lade…" and
 * fills once {@code LIST_TEMPLATES} returns — the main thread is never blocked.
 */
public final class PunishMenuCommand implements CommandExecutor {

    static final String PERMISSION = "mcplatform.punish";

    private final BackendClient backend;
    private final PlatformScheduler scheduler;
    private final MenuManager menus;

    public PunishMenuCommand(BackendClient backend, PlatformScheduler scheduler, MenuManager menus) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.playersOnly());
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            ActionBars.error(player, Messages.noPermission());
            return true;
        }

        if (args.length >= 1) {
            String targetName = args[0];
            UUID online = PunishmentCommandSupport.resolveOnlineUuid(targetName);
            scheduler.runAsync(() -> {
                UUID target = PunishmentCommandSupport.resolveUuid(targetName, online);
                scheduler.runSync(() -> openPunish(player, target, targetName));
            });
        } else {
            openPicker(player);
        }
        return true;
    }

    private void openPicker(Player viewer) {
        List<PlayerPickerMenu.Entry> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            candidates.add(new PlayerPickerMenu.Entry(online.getUniqueId(), online.getName()));
        }
        PlayerPickerMenu picker = new PlayerPickerMenu(
                MenuText.name("Spieler bestrafen", Token.NEGATIVE),
                candidates,
                (ctx, entry) -> {
                    Player viewerNow = Bukkit.getPlayer(ctx.playerId());
                    if (viewerNow != null) {
                        openPunish(viewerNow, entry.uuid(), entry.name());
                    }
                },
                ctx -> openPicker(viewer));
        menus.open(viewer, picker.menu());
    }

    /** Open the punish menu for a target and fill its templates asynchronously. Main thread. */
    private void openPunish(Player viewer, UUID target, String targetName) {
        PunishMenu punishMenu = new PunishMenu(target, targetName, backend, scheduler);
        MenuView view = menus.open(viewer, punishMenu.menu());
        Map<String, String> query = Map.of("staff", viewer.getUniqueId().toString());
        backend.call(PunishmentEndpoints.LIST_TEMPLATES, null, query)
                .whenComplete((templates, error) -> scheduler.runSync(() -> {
                    TemplateResponse[] list = templates == null ? new TemplateResponse[0] : templates;
                    punishMenu.setTemplates(Arrays.asList(list));
                    punishMenu.render(view);
                }));
    }
}
