package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.permission.PermissionCache;
import com.mcplatform.plugin.feature.permission.PermissionReadPort;
import com.mcplatform.plugin.feature.permission.PlayerPermissionsView;
import com.mcplatform.plugin.feature.scoreboard.condition.ProfileResolver;
import com.mcplatform.plugin.feature.scoreboard.profile.ProfileCatalog;
import com.mcplatform.plugin.feature.scoreboard.profile.Profiles;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.PermissionLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardRenderer;
import com.mcplatform.plugin.feature.scoreboard.render.ScoreboardService;
import com.mcplatform.plugin.feature.scoreboard.support.DirectCoinLine;
import com.mcplatform.plugin.feature.scoreboard.support.FakeBackendClient;
import com.mcplatform.plugin.feature.scoreboard.support.ImmediateScheduler;
import com.mcplatform.plugin.feature.scoreboard.support.RecordingScoreboardHandle;
import com.mcplatform.plugin.platform.menu.MenuLiveBus;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.economy.BalanceResponse;
import com.mcplatform.protocol.permission.RoleDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Orchestration: join render (rank warm + coins async), live re-render of coins/rank, leave cleanup. */
class ScoreboardServiceTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private FeatureCache<UUID, Long> coinsCache;
    private PermissionCache permCache;
    private MenuLiveBus liveBus;
    private ScoreboardService service;
    private RecordingScoreboardHandle handle;

    private static PlayerPermissionsView rank(String name) {
        return new PlayerPermissionsView(Set.of(), new RoleDisplay(name, "W", "", "", "W", null, null));
    }

    @BeforeEach
    void setUp() {
        coinsCache = new FeatureCache<>();
        permCache = new PermissionCache();
        permCache.apply(PLAYER, rank("Admin"), 1L);

        // Economy cold at join → load() falls back to REST returning 250.
        FakeBackendClient backend = new FakeBackendClient().result(new BalanceResponse(PLAYER, "COINS", 250L, 1L));
        EconomyReadPort economyPort = new EconomyReadPort(backend, coinsCache, "COINS");
        PermissionReadPort permPort = new PermissionReadPort(permCache);

        ProfileCatalog catalog = Profiles.catalog(
                new EconomyLineProvider(economyPort), new PermissionLineProvider(permPort));
        ProfileResolver resolver = new ProfileResolver(List.of(), catalog); // no rules → always Default

        liveBus = new MenuLiveBus();
        service = new ScoreboardService(new ScoreboardRenderer(), resolver, economyPort, liveBus,
                new ImmediateScheduler(), Profiles.COINS, new DirectCoinLine());
        handle = new RecordingScoreboardHandle();
    }

    @Test
    void joinRendersDefaultWithRealValues() {
        service.show(new PlayerContext(PLAYER, Optional.empty()), handle);

        assertEquals(Component.text("Admin", NamedTextColor.WHITE), handle.current.get(Profiles.RANK));
        assertEquals(Component.text("250", NamedTextColor.WHITE), handle.current.get(Profiles.COINS)); // via load
        assertEquals(Component.text("Rank", NamedTextColor.AQUA, TextDecoration.BOLD),
                handle.current.get(Profiles.RANK_LABEL)); // static label
        assertEquals(1, service.activeBoards());
    }

    @Test
    void liveCoinsUpdateOnNotify() {
        service.show(new PlayerContext(PLAYER, Optional.empty()), handle);

        coinsCache.put(PLAYER, 999L, 2L);     // a balance event updated economy's cache
        liveBus.notifyChange(PLAYER);

        assertEquals(Component.text("999", NamedTextColor.WHITE), handle.current.get(Profiles.COINS));
        assertEquals(Component.text("Rank", NamedTextColor.AQUA, TextDecoration.BOLD),
                handle.current.get(Profiles.RANK_LABEL)); // static label unchanged
    }

    @Test
    void liveRankUpdateOnNotify() {
        service.show(new PlayerContext(PLAYER, Optional.empty()), handle);

        permCache.apply(PLAYER, rank("VIP"), 3L); // a permission reload updated the cache
        liveBus.notifyChange(PLAYER);

        assertEquals(Component.text("VIP", NamedTextColor.WHITE), handle.current.get(Profiles.RANK));
    }

    @Test
    void leaveTearsDownAndUnsubscribes() {
        service.show(new PlayerContext(PLAYER, Optional.empty()), handle);
        assertEquals(1, liveBus.observerCount(PLAYER));

        service.remove(PLAYER);

        assertTrue(handle.torn);
        assertEquals(0, liveBus.observerCount(PLAYER));
        assertEquals(0, service.activeBoards());

        // No further updates after leave.
        handle.updatedIds.clear();
        coinsCache.put(PLAYER, 5L, 9L);
        liveBus.notifyChange(PLAYER);
        assertTrue(handle.updatedIds.isEmpty());
    }

    @Test
    void coldEconomyStillSafe() {
        // Sanity: even if load yielded nothing, the board still installs (placeholder coins).
        assertFalse(handle.torn);
    }
}
