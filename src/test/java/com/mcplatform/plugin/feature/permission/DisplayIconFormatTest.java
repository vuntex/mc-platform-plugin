package com.mcplatform.plugin.feature.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcplatform.plugin.feature.permission.DisplayIconFormat.HeadPlayer;
import com.mcplatform.plugin.feature.permission.DisplayIconFormat.HeadTexture;
import com.mcplatform.plugin.feature.permission.DisplayIconFormat.Invalid;
import com.mcplatform.plugin.feature.permission.DisplayIconFormat.Material;
import com.mcplatform.plugin.feature.permission.DisplayIconFormat.Parsed;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** TEST FOCUS: Icon-Mapping je Präfix + Default/unbekannt (T004). */
class DisplayIconFormatTest {

    @Test
    void materialPrefixIsParsed() {
        Parsed p = DisplayIconFormat.parse("material:DIAMOND_SWORD");
        assertInstanceOf(Material.class, p);
        assertEquals("DIAMOND_SWORD", ((Material) p).materialName());
    }

    @Test
    void headTexturePrefixKeepsFullPayloadIncludingFurtherColons() {
        Parsed p = DisplayIconFormat.parse("head-texture:eyJ0:ZXh0dXJl");
        assertInstanceOf(HeadTexture.class, p);
        assertEquals("eyJ0:ZXh0dXJl", ((HeadTexture) p).texture());
    }

    @Test
    void headPlayerPrefixParsesUuid() {
        UUID uuid = UUID.randomUUID();
        Parsed p = DisplayIconFormat.parse("head-player:" + uuid);
        assertInstanceOf(HeadPlayer.class, p);
        assertEquals(uuid, ((HeadPlayer) p).uuid());
    }

    @Test
    void malformedUuidIsInvalid() {
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse("head-player:not-a-uuid"));
    }

    @Test
    void nullEmptyNoColonUnknownAndEmptyPayloadAreInvalid() {
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse(null));
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse(""));
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse("abc"));
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse("banner:red"));
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse("material:"));
        assertInstanceOf(Invalid.class, DisplayIconFormat.parse("head-player:"));
    }

    @Test
    void formatHelpersRoundTripThroughParse() {
        assertEquals("material:STONE", DisplayIconFormat.material("STONE"));
        assertEquals("head-texture:abc", DisplayIconFormat.headTexture("abc"));
        assertInstanceOf(Material.class, DisplayIconFormat.parse(DisplayIconFormat.material("STONE")));
        assertTrue(DisplayIconFormat.parse(DisplayIconFormat.headTexture("abc")) instanceof HeadTexture);
    }
}
