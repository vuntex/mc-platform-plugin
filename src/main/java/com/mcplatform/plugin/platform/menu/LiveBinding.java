package com.mcplatform.plugin.platform.menu;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A menu's opt-in LIVE capability (MENU_DESIGN §6). A STATIC menu has none; a LIVE menu carries a
 * binding naming the {@code topic} it cares about and an {@code onChange} callback that re-renders only
 * the affected slots through the {@link MenuView}.
 *
 * <p>The {@code topic} is an opaque key (e.g. a player UUID) published on the shared {@link MenuLiveBus}
 * by the feature right after it updates its {@code FeatureCache} from the existing Redis/EventBus stream
 * — so a LIVE menu is purely a consumer of that existing path, with no new data mechanism. The manager
 * subscribes on open and unsubscribes on close, so observers never accumulate.
 *
 * @param topic    key this menu observes (matched against {@link MenuLiveBus#notifyChange})
 * @param onChange re-render of the affected slots; runs on the main thread via the manager
 */
public record LiveBinding(Object topic, Consumer<MenuView> onChange) {

    public LiveBinding {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(onChange, "onChange");
    }
}
