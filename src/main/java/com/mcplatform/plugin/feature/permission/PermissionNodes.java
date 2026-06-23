package com.mcplatform.plugin.feature.permission;

/**
 * The fine-grained UI permission nodes the optimistic gate checks (one per action group, per the
 * clarified decision). These are read from the player's cached effective set; the backend stays the
 * authority. Kept as constants in one place so commands/menus reference them by name.
 */
final class PermissionNodes {

    /** Role management: {@code /ranks} entry + every role write button. */
    static final String ROLES_MANAGE = "mcplatform.permission.roles.manage";

    /** Player grants: {@code /cp} entry + every grant/revoke button. */
    static final String GRANTS_MANAGE = "mcplatform.permission.grants.manage";

    private PermissionNodes() {
    }
}
