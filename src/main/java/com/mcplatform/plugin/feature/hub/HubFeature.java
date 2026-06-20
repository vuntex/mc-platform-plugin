package com.mcplatform.plugin.feature.hub;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.platform.menu.MenuManager;

/**
 * A thin cross-feature {@link PluginFeature} that owns the {@code /menu} hub. It depends only on the
 * shared {@link MenuManager} (injected by the composition root) and launches existing feature commands,
 * so it adds the unifying entry point without coupling to economy/punishment internals — and without
 * touching any generic class. {@link #onEnable} is the single place it touches the platform.
 */
public final class HubFeature implements PluginFeature {

    private final MenuManager menus;

    public HubFeature(MenuManager menus) {
        this.menus = menus;
    }

    @Override
    public String id() {
        return "hub";
    }

    @Override
    public void onEnable(FeatureContext context) {
        context.registerCommand("menu", new HubCommand(menus));
    }
}
