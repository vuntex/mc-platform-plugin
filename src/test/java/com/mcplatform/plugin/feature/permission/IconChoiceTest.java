package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.mcplatform.plugin.feature.permission.DisplayIconFormat.HeadTexture;
import com.mcplatform.plugin.feature.permission.DisplayIconFormat.Material;

import org.junit.jupiter.api.Test;

/**
 * TEST FOCUS: the {@code /rank} tool builds the correct string for vanilla item / custom head /
 * textureless head, and the result round-trips through {@link DisplayIconFormat#parse} (T025/T026 at the
 * pure decision layer — real ItemStack rendering is manually verified, no MockBukkit in the project).
 */
class IconChoiceTest {

    @Test
    void vanillaItemBecomesMaterialString() {
        assertEquals("material:DIAMOND_SWORD", IconExtractor.choose("DIAMOND_SWORD", null));
        assertEquals("material:DIAMOND_SWORD", IconExtractor.choose("DIAMOND_SWORD", "  "));
    }

    @Test
    void texturedHeadBecomesHeadTextureString() {
        assertEquals("head-texture:eyJ0ZXh0dXJl", IconExtractor.choose("PLAYER_HEAD", "eyJ0ZXh0dXJl"));
    }

    @Test
    void texturelessPlayerHeadBecomesMaterialPlayerHead() {
        assertEquals("material:PLAYER_HEAD", IconExtractor.choose("PLAYER_HEAD", null));
    }

    @Test
    void chosenStringRoundTripsThroughParse() {
        assertInstanceOf(Material.class, DisplayIconFormat.parse(IconExtractor.choose("STONE", null)));
        assertInstanceOf(Material.class, DisplayIconFormat.parse(IconExtractor.choose("PLAYER_HEAD", null)));
        assertInstanceOf(HeadTexture.class, DisplayIconFormat.parse(IconExtractor.choose("PLAYER_HEAD", "abc")));
    }
}
