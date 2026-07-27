package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * เทสต์ cache + reverse index ของ {@link NetworkRegistry}
 * เน้นความถูกต้องของการ invalidate (รวมเคสบล็อกใช้ร่วมหลายท่อ) โดยไม่ต้องรัน server
 */
class NetworkRegistryTest {

    private static Location loc(int x, int y, int z) {
        return new Location(null, x, y, z);
    }

    /** สร้างท่อจำลอง: input, source, กระจกหลายก้อน, output piston -> dest (เกาะกระจกก้อนสุดท้าย) */
    private static PipeNetwork net(Location input, Location source,
                                   Location piston, Location dest, Location... glass) {
        Set<Location> pipeBlocks = new HashSet<>(List.of(glass));
        Location lastGlass = glass[glass.length - 1];
        PipeOutput out = new PipeOutput(lastGlass, piston, dest, Material.CHEST);
        List<PipeOutput> outputs = List.of(out);
        Map<Location, List<PipeOutput>> byGlass = new LinkedHashMap<>();
        byGlass.put(lastGlass, new ArrayList<>(List.of(out)));
        return new PipeNetwork(input, source, Material.GLASS, pipeBlocks, outputs, byGlass);
    }

    @Test
    void getReturnsPutNetwork() {
        NetworkRegistry reg = new NetworkRegistry();
        PipeNetwork n = net(loc(0, 0, 0), loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5),
                loc(0, 0, 1), loc(0, 0, 2), loc(0, 0, 3));
        reg.put(n);
        assertSame(n, reg.get(loc(0, 0, 0)));
    }

    @Test
    void everyPartOfPipeIsTracked() {
        NetworkRegistry reg = new NetworkRegistry();
        reg.put(net(loc(0, 0, 0), loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5),
                loc(0, 0, 1), loc(0, 0, 2), loc(0, 0, 3)));

        assertTrue(reg.isTracked(loc(0, 0, 0)), "input piston");
        assertTrue(reg.isTracked(loc(0, 0, -1)), "source");
        assertTrue(reg.isTracked(loc(0, 0, 1)), "glass");
        assertTrue(reg.isTracked(loc(0, 0, 3)), "glass ก้อนสุดท้าย");
        assertTrue(reg.isTracked(loc(0, 0, 4)), "output piston");
        assertTrue(reg.isTracked(loc(0, 0, 5)), "dest");
        assertFalse(reg.isTracked(loc(9, 9, 9)), "บล็อกที่ไม่เกี่ยว");
    }

    @Test
    void invalidateByAnyPipeBlockRemovesTheNetwork() {
        NetworkRegistry reg = new NetworkRegistry();
        Location input = loc(0, 0, 0);
        reg.put(net(input, loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5),
                loc(0, 0, 1), loc(0, 0, 2), loc(0, 0, 3)));

        reg.invalidateByBlock(loc(0, 0, 2)); // ทุบกระจกกลางท่อ

        assertNull(reg.get(input));
        assertFalse(reg.isTracked(loc(0, 0, 1)), "reverse index ต้องถูกล้างหมด");
        assertFalse(reg.isTracked(input));
        assertFalse(reg.isTracked(loc(0, 0, 5)));
    }

    @Test
    void removeClearsReverseIndexCompletely() {
        NetworkRegistry reg = new NetworkRegistry();
        Location input = loc(0, 0, 0);
        reg.put(net(input, loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5), loc(0, 0, 1)));

        reg.remove(input);

        assertNull(reg.get(input));
        for (Location l : List.of(loc(0, 0, 0), loc(0, 0, -1), loc(0, 0, 1), loc(0, 0, 4), loc(0, 0, 5))) {
            assertFalse(reg.isTracked(l), "ต้องไม่เหลือ reverse index ที่ " + l);
        }
    }

    @Test
    void rePuttingSameInputDoesNotLeaveStaleReverseIndex() {
        NetworkRegistry reg = new NetworkRegistry();
        Location input = loc(0, 0, 0);
        // ท่อเวอร์ชันแรกผ่านกระจก z=1..3, output เกาะ z=3 -> dest z=5
        reg.put(net(input, loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5),
                loc(0, 0, 1), loc(0, 0, 2), loc(0, 0, 3)));
        // ท่อเวอร์ชันใหม่ของ input เดิม สั้นลง: กระจก z=1..2, output เกาะ z=2 แยกไปทาง x (ไม่แตะ z=3/z=5 เดิม)
        reg.put(net(input, loc(0, 0, -1), loc(1, 0, 2), loc(2, 0, 2),
                loc(0, 0, 1), loc(0, 0, 2)));

        assertFalse(reg.isTracked(loc(0, 0, 3)), "กระจกเก่าที่หลุดจากท่อต้องไม่ถูก track ค้าง");
        assertFalse(reg.isTracked(loc(0, 0, 5)), "dest เก่าต้องไม่ถูก track ค้าง");
        assertTrue(reg.isTracked(loc(0, 0, 2)));
        assertTrue(reg.isTracked(loc(1, 0, 2)), "output piston ใหม่ต้องถูก track");
    }

    @Test
    void sharedBlockInvalidatesEveryNetworkUsingIt() {
        NetworkRegistry reg = new NetworkRegistry();
        Location inputA = loc(0, 0, 0);
        Location inputB = loc(10, 0, 0);
        Location shared = loc(5, 0, 0); // กระจกก้อนที่สองท่อใช้ร่วมกัน

        reg.put(net(inputA, loc(0, 0, -1), loc(1, 0, 0), loc(2, 0, 0), loc(1, 0, 0), shared));
        reg.put(net(inputB, loc(10, 0, -1), loc(9, 0, 0), loc(8, 0, 0), loc(9, 0, 0), shared));

        // ทุบกระจกก้อนที่ใช้ร่วม -> ต้อง invalidate ทั้งสองท่อ (ไม่ใช่แค่ตัวหลัง)
        reg.invalidateByBlock(shared);

        assertNull(reg.get(inputA), "ท่อ A ต้องถูก invalidate");
        assertNull(reg.get(inputB), "ท่อ B ต้องถูก invalidate");
        assertFalse(reg.isTracked(shared));
    }

    @Test
    void invalidatingOneNetworkLeavesUnrelatedNetworkIntact() {
        NetworkRegistry reg = new NetworkRegistry();
        Location inputA = loc(0, 0, 0);
        Location inputB = loc(100, 0, 0);

        reg.put(net(inputA, loc(0, 0, -1), loc(1, 0, 0), loc(2, 0, 0), loc(1, 0, 0)));
        PipeNetwork b = net(inputB, loc(100, 0, -1), loc(101, 0, 0), loc(102, 0, 0), loc(101, 0, 0));
        reg.put(b);

        reg.invalidateByBlock(loc(1, 0, 0)); // บล็อกของ A เท่านั้น

        assertNull(reg.get(inputA));
        assertSame(b, reg.get(inputB), "ท่อที่ไม่เกี่ยวต้องไม่ถูกแตะ");
    }

    @Test
    void sharedBlockRemainsTrackedAfterRemovingOnlyOneOwner() {
        NetworkRegistry reg = new NetworkRegistry();
        Location inputA = loc(0, 0, 0);
        Location inputB = loc(10, 0, 0);
        Location shared = loc(5, 0, 0);

        reg.put(net(inputA, loc(0, 0, -1), loc(1, 0, 0), loc(2, 0, 0), loc(1, 0, 0), shared));
        reg.put(net(inputB, loc(10, 0, -1), loc(9, 0, 0), loc(8, 0, 0), loc(9, 0, 0), shared));

        reg.remove(inputA);

        assertNull(reg.get(inputA));
        assertTrue(reg.isTracked(shared), "บล็อกร่วมต้องยัง track อยู่เพราะ B ยังใช้");
        assertSame(reg.get(inputB).inputPiston(), inputB);
    }

    @Test
    void clearWipesEverything() {
        NetworkRegistry reg = new NetworkRegistry();
        reg.put(net(loc(0, 0, 0), loc(0, 0, -1), loc(0, 0, 4), loc(0, 0, 5), loc(0, 0, 1)));
        reg.clear();
        assertNull(reg.get(loc(0, 0, 0)));
        assertFalse(reg.isTracked(loc(0, 0, 1)));
    }
}
