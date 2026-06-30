package com.mcplatform.plugin.feature.chat;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.feature.permission.PermissionFeature;

import java.util.Objects;

/**
 * Chat presentation: formats player chat with the rank prefix/colour ({@link ChatFormatListener}) and
 * replaces the vanilla unknown-command reply ({@link UnknownCommandListener}). Reads the warm permission
 * cache via {@link PermissionFeature#readPort()}; registered after permission so the port exists.
 * {@link #onEnable} is the single place it touches the platform.
 */
public final class ChatFeature implements PluginFeature {

    private final PermissionFeature permission;

    public ChatFeature(PermissionFeature permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void onEnable(FeatureContext context) {
        context.registerListener(new ChatFormatListener(permission.readPort()));
        context.registerListener(new UnknownCommandListener());
    }
}
