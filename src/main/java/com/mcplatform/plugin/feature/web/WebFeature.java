package com.mcplatform.plugin.feature.web;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;

/**
 * The ingame web-account bridge as a {@link PluginFeature}: a single {@code /web} command (link /
 * resetPassword) on top of the generic {@code BackendClient}. It is a pure request/response client —
 * the web-auth bridge has no live/Pub-Sub path (R7), so this feature has <b>no</b> cache, <b>no</b>
 * {@code EventBus} subscription and <b>no</b> listener: {@link #onEnable} only wires the command. The
 * two frontend URL templates are injected by the composition root (read from config.yml), so no
 * generic platform class is touched.
 */
public final class WebFeature implements PluginFeature {

    private final String linkUrlTemplate;
    private final String resetUrlTemplate;

    public WebFeature(String linkUrlTemplate, String resetUrlTemplate) {
        this.linkUrlTemplate = linkUrlTemplate;
        this.resetUrlTemplate = resetUrlTemplate;
    }

    @Override
    public String id() {
        return "web";
    }

    @Override
    public void onEnable(FeatureContext context) {
        context.registerCommand("web",
                new WebCommand(context.backend(), context.scheduler(), linkUrlTemplate, resetUrlTemplate));
    }
}
