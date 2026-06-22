package com.mcplatform.plugin.feature.session;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;

/**
 * Platform-level session feature: it owns the single rule "you may only play if the backend can
 * establish your session" via the fail-closed {@link BackendSessionGate}. It depends on nothing but the
 * generic transport seam — no economy, no punishment — so the gate is a standalone concern that holds
 * even if those features change or are removed. Registered first so its gate is in place before the
 * other features' login hooks. {@link #onEnable} is the single place it touches the platform.
 */
public final class SessionFeature implements PluginFeature {

    @Override
    public String id() {
        return "session";
    }

    @Override
    public void onEnable(FeatureContext context) {
        context.registerListener(new BackendSessionGate(context.backend(), context.logger()));
    }
}
