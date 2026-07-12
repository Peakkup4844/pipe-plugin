package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์โหมดเทียบไอเทมของ filter
 */
class MatchModeTest {

    @Test
    void typeMatchesByMaterialOnly() {
        ItemStack filter = mock(ItemStack.class);
        ItemStack item = mock(ItemStack.class);
        when(filter.getType()).thenReturn(Material.DIAMOND);
        when(item.getType()).thenReturn(Material.DIAMOND);

        assertTrue(MatchMode.TYPE.matches(filter, item));
    }

    @Test
    void typeRejectsDifferentMaterial() {
        ItemStack filter = mock(ItemStack.class);
        ItemStack item = mock(ItemStack.class);
        when(filter.getType()).thenReturn(Material.DIAMOND);
        when(item.getType()).thenReturn(Material.IRON_INGOT);

        assertFalse(MatchMode.TYPE.matches(filter, item));
    }

    @Test
    void similarDelegatesToIsSimilar() {
        ItemStack filter = mock(ItemStack.class);
        ItemStack item = mock(ItemStack.class);
        when(filter.isSimilar(item)).thenReturn(true);

        assertTrue(MatchMode.SIMILAR.matches(filter, item));

        when(filter.isSimilar(item)).thenReturn(false);
        assertFalse(MatchMode.SIMILAR.matches(filter, item));
    }

    @Test
    void nullsNeverMatch() {
        ItemStack item = mock(ItemStack.class);
        lenient().when(item.getType()).thenReturn(Material.DIAMOND);

        assertFalse(MatchMode.TYPE.matches(null, item));
        assertFalse(MatchMode.TYPE.matches(item, null));
        assertFalse(MatchMode.SIMILAR.matches(null, item));
        assertFalse(MatchMode.SIMILAR.matches(item, null));
    }

    @Test
    void fromConfigDefaultsToSimilar() {
        assertEquals(MatchMode.SIMILAR, MatchMode.fromConfig(null));
        assertEquals(MatchMode.SIMILAR, MatchMode.fromConfig("similar"));
        assertEquals(MatchMode.SIMILAR, MatchMode.fromConfig("garbage"));
    }

    @Test
    void fromConfigParsesTypeCaseInsensitively() {
        assertEquals(MatchMode.TYPE, MatchMode.fromConfig("TYPE"));
        assertEquals(MatchMode.TYPE, MatchMode.fromConfig("type"));
        assertEquals(MatchMode.TYPE, MatchMode.fromConfig(" TYPE ".trim()));
    }
}
