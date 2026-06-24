package com.mcplatform.plugin.feature.permission;

import com.mcplatform.plugin.platform.menu.ClickAction;
import com.mcplatform.plugin.platform.menu.ClickContext;
import com.mcplatform.plugin.platform.menu.Menu;
import com.mcplatform.plugin.platform.menu.MenuLayout;
import com.mcplatform.plugin.platform.menu.RecordingMenuView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Proves the duration picker reports the right expiry: a Permanent button (→ null) plus day/year presets. */
class DurationPickerTest {

    private final UUID uuid = UUID.randomUUID();

    private DurationPicker picker(List<Long> chosen) {
        return new DurationPicker(null, null, "Test", "Test", chosen::add, () -> { });
    }

    private void click(Menu menu, int slot, RecordingMenuView view) {
        menu.route(new ClickContext(uuid, ClickAction.LEFT, slot, view));
    }

    @Test
    void permanentButtonReportsNull() {
        List<Long> chosen = new ArrayList<>();
        DurationPicker picker = picker(chosen);
        click(picker.menu(), 24, new RecordingMenuView(uuid, picker.menu())); // slot 24 = Permanent

        assertEquals(1, chosen.size());
        assertNull(chosen.get(0), "Permanent → null expiresInSeconds");
    }

    @Test
    void presetButtonsReportTheirSeconds() {
        List<Long> chosen = new ArrayList<>();
        DurationPicker picker = picker(chosen);
        RecordingMenuView view = new RecordingMenuView(uuid, picker.menu());

        click(picker.menu(), 19, view); // 1 Tag
        click(picker.menu(), 23, view); // 1 Jahr

        assertEquals(86_400L, chosen.get(0));
        assertEquals(365L * 86_400L, chosen.get(1));
    }

    @Test
    void hasHeaderAndCustomEntry() {
        DurationPicker picker = picker(new ArrayList<>());
        Menu menu = picker.menu();
        assertNotNull(menu.getItem(MenuLayout.HEADER));
        assertTrue(menu.getItem(25).isInteractive(), "custom-duration entry present");
    }
}
