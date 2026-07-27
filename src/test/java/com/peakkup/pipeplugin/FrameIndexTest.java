package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์ตรรกะ gate ของ {@link FrameIndex#passes} (block ไม่มี frame = ผ่าน, มี = match อันใดอันหนึ่ง)
 */
class FrameIndexTest {

    private static Location loc(int x, int y, int z) {
        return new Location(null, x, y, z);
    }

    private static Block blockAt(Location l) {
        Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(l);
        return b;
    }

    private static ItemStack item(Material type) {
        ItemStack s = mock(ItemStack.class);
        when(s.getType()).thenReturn(type);
        return s;
    }

    @Test
    void blockWithNoGatePassesEverything() {
        FrameIndex idx = FrameIndex.empty();
        assertTrue(idx.passes(blockAt(loc(0, 0, 0)), item(Material.DIRT), MatchMode.TYPE));
    }

    @Test
    void gatedBlockPassesOnlyMatchingItem() {
        Map<Location, List<ItemStack>> gates = new HashMap<>();
        gates.put(loc(1, 0, 0), List.of(item(Material.DIAMOND)));
        FrameIndex idx = FrameIndex.forTesting(gates);

        Block gated = blockAt(loc(1, 0, 0));
        assertTrue(idx.passes(gated, item(Material.DIAMOND), MatchMode.TYPE));
        assertFalse(idx.passes(gated, item(Material.IRON_INGOT), MatchMode.TYPE));
    }

    @Test
    void multipleFramesOnBlockAreUnion() {
        Map<Location, List<ItemStack>> gates = new HashMap<>();
        gates.put(loc(1, 0, 0), List.of(item(Material.DIAMOND), item(Material.IRON_INGOT)));
        FrameIndex idx = FrameIndex.forTesting(gates);

        Block gated = blockAt(loc(1, 0, 0));
        assertTrue(idx.passes(gated, item(Material.DIAMOND), MatchMode.TYPE), "match อันแรก");
        assertTrue(idx.passes(gated, item(Material.IRON_INGOT), MatchMode.TYPE), "match อันที่สอง");
        assertFalse(idx.passes(gated, item(Material.GOLD_INGOT), MatchMode.TYPE), "ไม่ match ทั้งคู่");
    }

    @Test
    void ungatedBlockPassesEvenWhenOtherBlocksAreGated() {
        Map<Location, List<ItemStack>> gates = new HashMap<>();
        gates.put(loc(1, 0, 0), List.of(item(Material.DIAMOND)));
        FrameIndex idx = FrameIndex.forTesting(gates);

        assertTrue(idx.passes(blockAt(loc(2, 0, 0)), item(Material.IRON_INGOT), MatchMode.TYPE));
    }
}
