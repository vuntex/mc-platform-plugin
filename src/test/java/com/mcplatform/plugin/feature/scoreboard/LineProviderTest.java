package com.mcplatform.plugin.feature.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcplatform.plugin.feature.economy.EconomyReadPort;
import com.mcplatform.plugin.feature.permission.PermissionCache;
import com.mcplatform.plugin.feature.permission.PermissionReadPort;
import com.mcplatform.plugin.feature.permission.PlayerPermissionsView;
import com.mcplatform.plugin.feature.scoreboard.provider.EconomyLineProvider;
import com.mcplatform.plugin.feature.scoreboard.provider.PermissionLineProvider;
import com.mcplatform.plugin.feature.scoreboard.render.PlayerContext;
import com.mcplatform.plugin.feature.scoreboard.support.FakeBackendClient;
import com.mcplatform.plugin.transport.FeatureCache;
import com.mcplatform.protocol.permission.RoleDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Real providers read current values from the ports; rank is plain (FR-003a); empty → placeholder. */
class LineProviderTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private PlayerContext ctx() {
        return new PlayerContext(PLAYER, Optional.empty());
    }

    @Test
    void coinsFromCache() {
        FeatureCache<UUID, Long> cache = new FeatureCache<>();
        cache.put(PLAYER, 250L, 1L);
        EconomyLineProvider provider = new EconomyLineProvider(
                new EconomyReadPort(new FakeBackendClient(), cache, "COINS"));

        assertEquals(Component.text("250", NamedTextColor.YELLOW), provider.resolve(ctx()));
    }

    @Test
    void coinsPlaceholderWhenCold() {
        EconomyLineProvider provider = new EconomyLineProvider(
                new EconomyReadPort(new FakeBackendClient(), new FeatureCache<>(), "COINS"));

        assertEquals(Component.text("…", NamedTextColor.YELLOW), provider.resolve(ctx()));
    }

    @Test
    void rankIsPlainDisplayName() {
        PermissionCache cache = new PermissionCache();
        cache.apply(PLAYER, new PlayerPermissionsView(Set.of(),
                new RoleDisplay("Admin", "RED", "[A] ", "", "RED", null, null)), 1L);
        PermissionLineProvider provider = new PermissionLineProvider(new PermissionReadPort(cache));

        // Plain name (not the role's own RED/[A] prefix, FR-003a) styled with the scoreboard's own color.
        assertEquals(Component.text("Admin", NamedTextColor.WHITE), provider.resolve(ctx()));
    }

    @Test
    void rankFallbackWhenCold() {
        PermissionLineProvider provider = new PermissionLineProvider(new PermissionReadPort(new PermissionCache()));
        assertEquals(Component.text("—", NamedTextColor.WHITE), provider.resolve(ctx()));
    }
}
