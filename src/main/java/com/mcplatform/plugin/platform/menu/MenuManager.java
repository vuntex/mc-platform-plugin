package com.mcplatform.plugin.platform.menu;

import com.mcplatform.plugin.platform.PlatformScheduler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * The ONE central menu listener and entry point (MENU_DESIGN principle: features register no click
 * listener of their own). It opens menus, cancels every click inside a framework menu (no item theft),
 * routes each click to the clicked {@link MenuItem}'s per-action handler, and owns the LIVE lifecycle:
 * on open it subscribes the menu's {@link LiveBinding} on the shared {@link MenuLiveBus}; on close it
 * unsubscribes — so observers never accumulate.
 *
 * <p>Features obtain this from the composition root (constructor injection) and call {@link #open} and
 * {@link #liveBus()} — no generic platform class is touched to make menus work.
 */
public final class MenuManager implements Listener {

    private final JavaPlugin plugin;
    private final PlatformScheduler scheduler;
    private final MenuRenderer renderer = new MenuRenderer();
    private final OpenMenuTracker tracker = new OpenMenuTracker();
    private final MenuLiveBus liveBus = new MenuLiveBus();

    public MenuManager(JavaPlugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /** Register the single listener. Called once by the composition root after construction. */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** The shared LIVE fan-out: features call {@code notifyChange(topic)} after a cache update (§6). */
    public MenuLiveBus liveBus() {
        return liveBus;
    }

    /**
     * Open {@code menu} for {@code player} (also used for in-place navigation). Builds the inventory,
     * opens it on the main thread, and — for a LIVE menu — subscribes its binding so changes re-render
     * only the affected slots while it stays open. Returns the {@link MenuView} the caller can use to
     * fill a loading menu once async data arrives. Call on the main thread.
     */
    public MenuView open(Player player, Menu menu) {
        UUID id = player.getUniqueId();
        MenuHolder holder = new MenuHolder(id, menu);
        Inventory inventory = renderer.render(holder);
        BukkitMenuView view = new BukkitMenuView(player, holder, renderer, this);
        holder.bind(inventory, view);

        // Opening replaces any previous inventory; that fires InventoryCloseEvent for the old menu, which
        // releases its (older) subscription BEFORE we track the new one below — so no handle is leaked.
        player.openInventory(inventory);

        LiveHandle handle = LiveHandle.NONE;
        if (menu.isLive()) {
            LiveBinding binding = menu.live().orElseThrow();
            handle = liveBus.observe(binding.topic(), () -> scheduler.runSync(() -> {
                if (holder.isOpenFor(player)) {
                    binding.onChange().accept(view); // re-render only affected slots, on main
                }
            }));
        }
        tracker.track(id, handle);
        return view;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true); // framework menus always cancel — clicks never move items
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return; // a (cancelled) click in the player's own inventory or outside — nothing to route
        }
        ClickAction action = map(event.getClick());
        MenuItem item = holder.menu().getItem(raw);
        if (item == null) {
            return;
        }
        if (item.isInteractive() && action != ClickAction.OTHER && item.handlerFor(action) != null) {
            holder.menu().route(new ClickContext(player.getUniqueId(), action, raw, holder.view()));
        } else if (!item.isInteractive()) {
            MenuStyle.play(player, Feedback.INERT); // subtle tone on a display item (§4.5)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true); // no dragging items into a framework menu
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            tracker.release(holder.playerId()); // unsubscribe the LIVE binding — the leak guard
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracker.release(event.getPlayer().getUniqueId()); // safety net if no close event fired
    }

    private static ClickAction map(ClickType type) {
        return switch (type) {
            case LEFT -> ClickAction.LEFT;
            case RIGHT -> ClickAction.RIGHT;
            case SHIFT_LEFT -> ClickAction.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickAction.SHIFT_RIGHT;
            case MIDDLE -> ClickAction.MIDDLE;
            case DROP, CONTROL_DROP -> ClickAction.DROP;
            case DOUBLE_CLICK -> ClickAction.DOUBLE_CLICK;
            default -> ClickAction.OTHER;
        };
    }
}
