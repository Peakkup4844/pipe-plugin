package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์ตัวจำแนกกระจกท่อและการอ่านทิศ (pure/mock — ไม่ต้องรัน server)
 */
class NetworkDiscoveryTest {

    @Test
    void plainAndTintedGlassArePipeGlass() {
        assertTrue(NetworkDiscovery.isPipeGlass(Material.GLASS));
        assertTrue(NetworkDiscovery.isPipeGlass(Material.TINTED_GLASS));
    }

    @Test
    void allSixteenStainedGlassBlocksArePipeGlass() {
        int count = 0;
        for (Material m : Material.values()) {
            String name = m.name();
            if (name.startsWith("LEGACY_")) {
                continue; // ข้าม material เก่า (data-value) ที่ไม่มีวันปรากฏบนบล็อกจริงในเซิร์ฟเวอร์ 1.13+
            }
            if (name.endsWith("STAINED_GLASS") && !name.endsWith("PANE")) {
                assertTrue(NetworkDiscovery.isPipeGlass(m), m + " ควรเป็นกระจกท่อ");
                count++;
            }
        }
        assertEquals(16, count, "ต้องมี stained glass ครบ 16 สี");
    }

    @Test
    void glassPanesAreNotPipeGlass() {
        assertFalse(NetworkDiscovery.isPipeGlass(Material.GLASS_PANE));
        for (Material m : Material.values()) {
            if (m.name().endsWith("STAINED_GLASS_PANE")) {
                assertFalse(NetworkDiscovery.isPipeGlass(m), m + " (pane) ต้องไม่ใช่กระจกท่อ");
            }
        }
    }

    @Test
    void nonGlassMaterialsAreNotPipeGlass() {
        assertFalse(NetworkDiscovery.isPipeGlass(Material.STONE));
        assertFalse(NetworkDiscovery.isPipeGlass(Material.GLASS_BOTTLE));
        assertFalse(NetworkDiscovery.isPipeGlass(Material.CHEST));
        assertFalse(NetworkDiscovery.isPipeGlass(Material.PISTON));
        assertFalse(NetworkDiscovery.isPipeGlass(Material.AIR));
    }

    @Test
    void facingOfReadsDirectionalBlockData() {
        Directional dir = mock(Directional.class);
        when(dir.getFacing()).thenReturn(BlockFace.EAST);
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(dir);

        assertEquals(BlockFace.EAST, NetworkDiscovery.facingOf(block));
    }

    @Test
    void facingOfReturnsNullForNonDirectional() {
        BlockData plain = mock(BlockData.class);
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(plain);

        assertNull(NetworkDiscovery.facingOf(block));
    }
}
