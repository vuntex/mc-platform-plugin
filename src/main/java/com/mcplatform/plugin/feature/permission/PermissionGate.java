package com.mcplatform.plugin.feature.permission;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The cache-based UI/command gate (spec FR-008/010/011). UI/command comfort: the backend is the real
 * authority and a {@code 403} still overrides any optimistic display.
 *
 * <ul>
 *   <li>Cache entry present + node granted → allow.</li>
 *   <li>Cache entry present + node not granted → deny.</li>
 *   <li><b>No cache entry (cold cache) → strict deny + a warning log.</b> The {@code PreLogin}
 *       permission warmup ({@link PermissionWarmupListener}) guarantees an in-world player always has a
 *       filled cache, so a cold cache here is a <em>bug indicator</em> (a warmup gap), not a normal
 *       state — it must surface loudly, not hide behind an optimistic allow. This replaces the former
 *       FR-009 "neutral allow on cold cache".</li>
 * </ul>
 *
 * <p>"Granted" understands the usual wildcard conventions, since the backend's flattened effective set
 * carries wildcards verbatim ({@code "*"} for all, {@code "x.y.*"} for a subtree) — it cannot enumerate
 * every concrete node. Interpreting them is the plugin's job, like the opaque {@code display_icon}.
 */
public final class PermissionGate {

    private final PermissionCache cache;
    private final Logger logger;

    public PermissionGate(PermissionCache cache) {
        this(cache, Logger.getLogger(PermissionGate.class.getName()));
    }

    public PermissionGate(PermissionCache cache, Logger logger) {
        this.cache = cache;
        this.logger = logger;
    }

    /**
     * Cache-based check. A cold cache is strict-denied and logged — the warmup guarantees it should never
     * happen for an in-world player.
     */
    public boolean has(UUID player, String node) {
        Optional<PlayerPermissionsView> view = cache.get(player);
        if (view.isEmpty()) {
            logger.warning("permission check on cold cache for " + player + " — warmup gap?");
            return false;
        }
        return grants(view.get().effective(), node);
    }

    /**
     * Whether {@code effective} grants {@code node}: the global {@code "*"}, an exact match, or an
     * ancestor wildcard (e.g. {@code mcplatform.permission.*} or {@code mcplatform.*} grants
     * {@code mcplatform.permission.roles.manage}).
     */
    static boolean grants(Set<String> effective, String node) {
        if (effective.contains("*") || effective.contains(node)) {
            return true;
        }
        String prefix = node;
        int dot;
        while ((dot = prefix.lastIndexOf('.')) >= 0) {
            prefix = prefix.substring(0, dot);
            if (effective.contains(prefix + ".*")) {
                return true;
            }
        }
        return false;
    }
}
