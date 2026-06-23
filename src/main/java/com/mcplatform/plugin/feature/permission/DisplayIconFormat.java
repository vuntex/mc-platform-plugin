package com.mcplatform.plugin.feature.permission;

import java.util.Optional;
import java.util.UUID;

/**
 * The SINGLE source of truth for the opaque {@code display_icon} string format, shared by both
 * directions so they never drift: {@link IconResolver} (String → ItemStack) and {@link IconExtractor}
 * (ItemStack → String). The backend never interprets this string — the plugin does, only here.
 *
 * <p>Format is {@code <prefix>:<payload>}, split on the FIRST colon. Pure and Bukkit-free, so the
 * parsing is fully unit-testable without a server.
 */
public final class DisplayIconFormat {

    public static final String MATERIAL_PREFIX = "material:";
    public static final String HEAD_TEXTURE_PREFIX = "head-texture:";
    public static final String HEAD_PLAYER_PREFIX = "head-player:";

    private DisplayIconFormat() {
    }

    /** The parsed outcome of a {@code display_icon} string. */
    public sealed interface Parsed permits Material, HeadTexture, HeadPlayer, Invalid {
    }

    /** {@code material:<NAME>} — a 1.21 material name (validated at render time). */
    public record Material(String materialName) implements Parsed {
    }

    /** {@code head-texture:<base64>} — a custom head with an embedded texture. */
    public record HeadTexture(String texture) implements Parsed {
    }

    /** {@code head-player:<uuid>} — a player head by UUID. */
    public record HeadPlayer(UUID uuid) implements Parsed {
    }

    /** {@code null}, no colon, unknown prefix, empty payload, or a malformed UUID → render a fallback. */
    public record Invalid() implements Parsed {
    }

    /**
     * Parse an opaque {@code display_icon} string. Never throws: anything unrecognised maps to
     * {@link Invalid} so the caller renders a forward-compatible fallback icon.
     */
    public static Parsed parse(String displayIcon) {
        if (displayIcon == null) {
            return new Invalid();
        }
        int colon = displayIcon.indexOf(':');
        if (colon < 0) {
            return new Invalid();
        }
        String prefix = displayIcon.substring(0, colon + 1);
        String payload = displayIcon.substring(colon + 1);
        if (payload.isEmpty()) {
            return new Invalid();
        }
        return switch (prefix) {
            case MATERIAL_PREFIX -> new Material(payload);
            case HEAD_TEXTURE_PREFIX -> new HeadTexture(payload);
            case HEAD_PLAYER_PREFIX -> tryUuid(payload).<Parsed>map(HeadPlayer::new).orElseGet(Invalid::new);
            default -> new Invalid();
        };
    }

    /** Build a {@code material:<NAME>} string (write side, e.g. the {@code /rank} tool). */
    public static String material(String materialName) {
        return MATERIAL_PREFIX + materialName;
    }

    /** Build a {@code head-texture:<base64>} string (write side, e.g. the {@code /rank} tool). */
    public static String headTexture(String base64) {
        return HEAD_TEXTURE_PREFIX + base64;
    }

    private static Optional<UUID> tryUuid(String s) {
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
