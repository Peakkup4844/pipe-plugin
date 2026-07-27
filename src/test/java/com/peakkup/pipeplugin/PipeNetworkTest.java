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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * เทสต์การเทียบโทโพโลยี + การรับช่วงล็อก ซึ่งเป็นฐานของการ "ค้นท่อใหม่ทุก pulse"
 * (ถ้า sameTopologyAs ผิด → รื้อ index ทุกรอบ หรือแย่กว่า: มองไม่เห็น output ใหม่)
 */
class PipeNetworkTest {

    private static Location loc(int x, int y, int z) {
        return new Location(null, x, y, z);
    }

    private static PipeNetwork net(Material glass, Location source,
                                   Set<Location> glassBlocks, List<PipeOutput> outputs) {
        Map<Location, List<PipeOutput>> byGlass = new LinkedHashMap<>();
        for (PipeOutput o : outputs) {
            byGlass.computeIfAbsent(o.glassBlock(), k -> new ArrayList<>()).add(o);
        }
        return new PipeNetwork(loc(0, 0, 0), source, glass, glassBlocks, outputs, byGlass);
    }

    private static PipeNetwork standard() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.CHEST));
        return net(Material.GLASS, loc(0, 0, -1), glass, outs);
    }

    @Test
    void identicalTopologiesAreEqual() {
        assertTrue(standard().sameTopologyAs(standard()));
    }

    @Test
    void nullIsNeverSameTopology() {
        assertFalse(standard().sameTopologyAs(null));
    }

    @Test
    void addedGlassMakesTopologyDifferent() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2), loc(0, 0, 3)));
        List<PipeOutput> outs = List.of(new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.CHEST));
        assertFalse(standard().sameTopologyAs(net(Material.GLASS, loc(0, 0, -1), glass, outs)));
    }

    /** เคสของบั๊ก: วาง piston ก่อน แล้วค่อยวางกล่อง -> output ใหม่โผล่มา ต้องนับว่าโทโพโลยีเปลี่ยน */
    @Test
    void newOutputMakesTopologyDifferent() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(
                new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.CHEST),
                new PipeOutput(loc(0, 0, 1), loc(1, 0, 1), loc(2, 0, 1), Material.CHEST));
        assertFalse(standard().sameTopologyAs(net(Material.GLASS, loc(0, 0, -1), glass, outs)),
                "output ที่เพิ่งเกิดต้องทำให้ถือว่าเป็นโทโพโลยีใหม่");
    }

    @Test
    void changedDestinationMakesTopologyDifferent() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(9, 9, 9), Material.CHEST));
        assertFalse(standard().sameTopologyAs(net(Material.GLASS, loc(0, 0, -1), glass, outs)));
    }

    @Test
    void swappingTheDestinationBlockTypeMakesTopologyDifferent() {
        // กล่องปลายทางที่ถูกเปลี่ยนชนิดในตำแหน่งเดิม (chest -> shulker) เปลี่ยนกฎว่าอะไรใส่ลงไปได้
        // ถ้ายังถือว่าเป็นโทโพโลยีเดิม ท่อจะใช้ Material เก่าที่จำไว้ไปคัดชนิดของ = ตัดสินผิด
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(
                new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.SHULKER_BOX));
        assertFalse(standard().sameTopologyAs(net(Material.GLASS, loc(0, 0, -1), glass, outs)));
    }

    @Test
    void differentGlassColourMakesTopologyDifferent() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.CHEST));
        assertFalse(standard().sameTopologyAs(net(Material.RED_STAINED_GLASS, loc(0, 0, -1), glass, outs)));
    }

    @Test
    void differentSourceMakesTopologyDifferent() {
        Set<Location> glass = new HashSet<>(List.of(loc(0, 0, 1), loc(0, 0, 2)));
        List<PipeOutput> outs = List.of(new PipeOutput(loc(0, 0, 2), loc(0, 0, 3), loc(0, 0, 4), Material.CHEST));
        assertFalse(standard().sameTopologyAs(net(Material.GLASS, loc(5, 5, 5), glass, outs)));
    }

    @Test
    void lockIsIndependentBetweenSeparateNetworks() {
        PipeNetwork a = standard();
        PipeNetwork b = standard();
        assertTrue(a.tryAcquire());
        assertTrue(b.tryAcquire(), "คนละท่อ (ยังไม่รับช่วงล็อก) ต้องไม่กันกัน");
    }

    /** โทโพโลยีเปลี่ยนกลางรอบที่ยังวิ่งอยู่ -> ตัวใหม่ต้องรับช่วงล็อกมา ไม่งั้นรอบจะซ้อนกัน */
    @Test
    void adoptedLockBlocksNewTransferWhileOldStillRunning() {
        PipeNetwork oldNet = standard();
        assertTrue(oldNet.tryAcquire(), "รอบเก่าเริ่มทำงาน");

        PipeNetwork newNet = standard();
        newNet.adoptLockFrom(oldNet);

        assertFalse(newNet.tryAcquire(), "รอบเก่ายังไม่จบ -> รอบใหม่ต้องถูกกัน");
    }

    @Test
    void releasingOldNetworkFreesAdoptedLock() {
        PipeNetwork oldNet = standard();
        oldNet.tryAcquire();
        PipeNetwork newNet = standard();
        newNet.adoptLockFrom(oldNet);

        oldNet.release(); // รอบเก่าจบ

        assertTrue(newNet.tryAcquire(), "ปลดล็อกจากตัวเก่าแล้ว ตัวใหม่ต้องเริ่มได้");
    }

    @Test
    void adoptFromNullKeepsOwnUsableLock() {
        PipeNetwork n = standard();
        n.adoptLockFrom(null);
        assertTrue(n.tryAcquire());
        n.release();
        assertTrue(n.tryAcquire());
    }
}
