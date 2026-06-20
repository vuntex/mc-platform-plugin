package com.mcplatform.plugin.platform.menu;

/**
 * A handle to one LIVE subscription. Closing it removes the observer from the {@link MenuLiveBus}.
 * {@link #close()} is idempotent — the manager closes it on menu close, and a double close (e.g. close
 * then disable) must never throw or double-remove.
 */
public interface LiveHandle extends AutoCloseable {

    /** No live subscription (returned for STATIC menus); closing it does nothing. */
    LiveHandle NONE = () -> {
    };

    @Override
    void close();
}
