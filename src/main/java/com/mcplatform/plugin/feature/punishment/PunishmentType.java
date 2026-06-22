package com.mcplatform.plugin.feature.punishment;

/**
 * Punishment type and action string constants, mirroring the backend domain enums (the
 * {@code plugin-protocol} carries them as {@link String}s to stay framework-free). Kept feature-local
 * on purpose: no generic transport/registry class ever learns punishment semantics — the meaning of a
 * type lives only inside this feature.
 */
final class PunishmentType {

    static final String WARN = "WARN";
    static final String CHATBAN = "CHATBAN";
    static final String TEMPBAN = "TEMPBAN";
    static final String PERMABAN = "PERMABAN";

    static final String ACTION_ISSUED = "ISSUED";
    static final String ACTION_REVOKED = "REVOKED";

    private PunishmentType() {
    }

    /** Types that deny login (and kick an online player): {@link #TEMPBAN}, {@link #PERMABAN}. */
    static boolean deniesLogin(String type) {
        return TEMPBAN.equals(type) || PERMABAN.equals(type);
    }

    /** Types that mute chat: {@link #CHATBAN}. */
    static boolean deniesChat(String type) {
        return CHATBAN.equals(type);
    }

    /** Whether a punishment of this type is time-bound (needs a duration): TEMPBAN, CHATBAN. */
    static boolean isTimeBound(String type) {
        return TEMPBAN.equals(type) || CHATBAN.equals(type);
    }

    /**
     * Bukkit permission node that gates the staff broadcast for a punishment of this type. Reuses the
     * per-type issue permissions, so team members who may apply a type also see those actions happen.
     */
    static String notifyPermission(String type) {
        return switch (type) {
            case WARN -> "mcplatform.punish.warn";
            case CHATBAN -> "mcplatform.punish.chatban";
            case TEMPBAN -> "mcplatform.punish.tempban";
            case PERMABAN -> "mcplatform.punish.permaban";
            default -> "mcplatform.punish";
        };
    }
}
