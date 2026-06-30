package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mcplatform.plugin.platform.menu.MenuMessage;
import com.mcplatform.plugin.transport.BackendException;
import com.mcplatform.protocol.permission.RoleResponse;

import java.util.List;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

class PermissionFormatTest {

    private static RoleResponse role(String name, String displayName, int weight, boolean team) {
        return new RoleResponse(1L, name, displayName, null, null, null, null, null, null,
                weight, team, true, false, List.of(), List.of());
    }

    @Test
    void roleNameFallsBackToTechnicalNameWhenDisplayBlank() {
        assertEquals("admin", PermissionFormat.roleName(role("admin", "", 10, true)).text());
        assertEquals("Admin", PermissionFormat.roleName(role("admin", "Admin", 10, true)).text());
    }

    @Test
    void rankKindAndExpiry() {
        assertEquals("Team-Rang", PermissionFormat.rankKind(role("a", "A", 1, true)));
        assertEquals("Spieler-Rang", PermissionFormat.rankKind(role("b", "B", 1, false)));
        assertEquals("permanent", PermissionFormat.expiry(null));
        assertNotEquals("permanent", PermissionFormat.expiry(1_700_000_000_000L));
    }

    @Test
    void eachStatusMapsToADistinctMessage() {
        String m403 = PermissionFormat.error(403).text().text();
        String m404 = PermissionFormat.error(404).text().text();
        String m409 = PermissionFormat.error(409).text().text();
        String m422 = PermissionFormat.error(422).text().text();
        String m429 = PermissionFormat.error(429).text().text();
        String m500 = PermissionFormat.error(500).text().text();

        assertEquals(6, List.of(m403, m404, m409, m422, m429, m500).stream().distinct().count());
        assertEquals(MenuMessage.Channel.ACTION_BAR, PermissionFormat.error(403).channel());
    }

    @Test
    void statusIsUnwrappedFromCompletionExceptionWrappedBackendError() {
        BackendException be = BackendException.fromStatus(403, null);
        assertEquals(403, PermissionFormat.statusOf(new CompletionException(be)));
        assertEquals("Dazu fehlt dir die Berechtigung.",
                PermissionFormat.error(new CompletionException(be)).text().text());
    }
}
