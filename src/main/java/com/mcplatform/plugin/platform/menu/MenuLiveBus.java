package com.mcplatform.plugin.platform.menu;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The menu framework's keyed observer fan-out for LIVE menus (MENU_DESIGN §6). It carries no data of its
 * own: a feature, right after it has updated its {@code FeatureCache} from the existing EventBus/Redis
 * stream, calls {@link #notifyChange(Object)} with the affected topic; every open LIVE menu observing
 * that topic re-renders its affected slots. The menu is thus a consumer of the existing pub/sub path —
 * no new data mechanism.
 *
 * <p>Leak-safety is the whole point: {@link #observe} returns a {@link LiveHandle} whose {@code close()}
 * removes exactly that observer (and prunes the empty topic). The manager closes it on menu close, so at
 * 200 players observers can never accumulate. Thread-safe — {@code notifyChange} runs on the main thread
 * (the EventDispatcher delivers there), {@code observe}/{@code close} too, but a concurrent set keeps it
 * safe regardless.
 */
public final class MenuLiveBus {

    private final Map<Object, Set<Runnable>> observers = new ConcurrentHashMap<>();

    /**
     * Register {@code onChange} for {@code topic}. The returned handle removes it on close; closing twice
     * is a no-op. {@code onChange} is invoked by {@link #notifyChange} until the handle is closed.
     */
    public LiveHandle observe(Object topic, Runnable onChange) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(onChange, "onChange");
        observers.computeIfAbsent(topic, t -> ConcurrentHashMap.newKeySet()).add(onChange);

        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return; // idempotent
            }
            observers.computeIfPresent(topic, (t, set) -> {
                set.remove(onChange);
                return set.isEmpty() ? null : set; // prune empty topic so the map can't grow unbounded
            });
        };
    }

    /** Run every observer currently registered for {@code topic} (a re-render of affected slots). */
    public void notifyChange(Object topic) {
        Set<Runnable> set = observers.get(topic);
        if (set == null) {
            return;
        }
        for (Runnable r : set) {
            r.run();
        }
    }

    /** Observers currently registered for {@code topic} (tests assert this is 0 after close). */
    public int observerCount(Object topic) {
        Set<Runnable> set = observers.get(topic);
        return set == null ? 0 : set.size();
    }

    /** Total observers across all topics (tests assert this is 0 after all menus close). */
    public int totalObservers() {
        int total = 0;
        for (Set<Runnable> set : observers.values()) {
            total += set.size();
        }
        return total;
    }
}
