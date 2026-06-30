package com.mcplatform.plugin.feature.scoreboard;

import com.mcplatform.plugin.feature.FeatureContext;
import com.mcplatform.plugin.feature.PluginFeature;
import com.mcplatform.plugin.feature.economy.EconomyFeature;
import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.permission.PermissionFeature;
import com.mcplatform.plugin.feature.permission.PermissionReadPort;
import com.mcplatform.plugin.feature.scoreboard.condition.ConditionRule;
import com.mcplatform.plugin.feature.scoreboard.condition.ProfileResolver;
import com.mcplatform.plugin.feature.scoreboard.condition.RegionCondition;
import com.mcplatform.plugin.feature.scoreboard.condition.RegionId;
import com.mcplatform.plugin.feature.scoreboard.condition.StubRegionProvider;
import com.mcplatform.plugin.feature.scoreboard.lifecycle.ScoreboardJoinListener;
import com.mcplatform.plugin.feature.scoreboard.lifecycle.ScoreboardLeaveListener;
import com.mcplatform.plugin.feature.scoreboard.profile.ProfileCatalog;
import com.mcplatform.plugin.feature.scoreboard.profile.Profiles;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.PermissionLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.BukkitScoreboardHandleFactory;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardHandleFactory;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardRenderer;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardService;
import com.mcplatform.plugin.platform.menu.MenuManager;

import java.util.List;
import java.util.Objects;

/**
 * Render-only scoreboard slice (Slice 1). A new {@link PluginFeature} that consumes the EXISTING
 * economy/permission caches via their read-ports (no parallel cache, no own transport subscription),
 * re-renders live through the shared {@link MenuManager#liveBus()}, and shows a context-dependent
 * sidebar. {@link #onEnable} is the single place it touches the platform.
 *
 * <p>Sibling features are pulled by reference and read in {@code onEnable}; registration order in the
 * composition root guarantees economy/permission enable first, so their read-ports exist here.
 */
public final class ScoreboardFeature implements PluginFeature {

    /** The single test region the stub can report to prove condition-based selection (AC-2). */
    public static final RegionId TEST_REGION = RegionId.of("test_event");

    private final MenuManager menus;
    private final EconomyFeature economy;
    private final PermissionFeature permission;

    public ScoreboardFeature(MenuManager menus, EconomyFeature economy, PermissionFeature permission) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public void onEnable(FeatureContext context) {
        EconomyReadPort economyPort = Objects.requireNonNull(economy.readPort(),
                "economy.readPort() — economy must enable before scoreboard");
        PermissionReadPort permissionPort = Objects.requireNonNull(permission.readPort(),
                "permission.readPort() — permission must enable before scoreboard");

        EconomyLineProvider coins = new EconomyLineProvider(economyPort);
        PermissionLineProvider rank = new PermissionLineProvider(permissionPort);

        ProfileCatalog catalog = Profiles.catalog(coins, rank);
        StubRegionProvider regions = new StubRegionProvider();

        List<ConditionRule> rules = List.of(
                new ConditionRule(new RegionCondition(TEST_REGION), Profiles.TEST_EVENT_ID));
        ProfileResolver resolver = new ProfileResolver(rules, catalog);

        ScoreboardService service = new ScoreboardService(
                new ScoreboardRenderer(), resolver, economyPort, menus.liveBus(), context.scheduler());
        ScoreboardHandleFactory handles = new BukkitScoreboardHandleFactory();

        context.registerListener(new ScoreboardJoinListener(service, handles, regions));
        context.registerListener(new ScoreboardLeaveListener(service));
    }
}
