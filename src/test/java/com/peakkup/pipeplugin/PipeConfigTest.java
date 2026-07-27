package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์การอ่าน/ตรวจค่า config (default, clamp, allowed-containers list vs all, material ผิด)
 * ใช้ YamlConfiguration จริง + mock plugin/lang — ไม่ต้องรัน server
 */
class PipeConfigTest {

    private static PipeConfig configFrom(YamlConfiguration yaml) {
        PipePlugin plugin = mock(PipePlugin.class);
        when(plugin.getConfig()).thenReturn(yaml);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PipeConfigTest"));
        PipeLang lang = mock(PipeLang.class);
        lenient().when(lang.msg(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn("warn");
        return new PipeConfig(plugin, lang);
    }

    @Test
    void defaultsWhenConfigEmpty() {
        PipeConfig c = configFrom(new YamlConfiguration());
        assertEquals(32, c.itemsPerCycle());
        assertEquals(64, c.maxPipeLength());
        assertEquals(2 * 50L, c.minPulseIntervalMillis());
        assertTrue(c.effectsEnabled());
        assertEquals(MatchMode.SIMILAR, c.matchMode());
        assertTrue(c.isContainerAllowed(Material.HOPPER), "default = อนุญาตทุก container");
        assertTrue(c.skipPluginManagedChests(), "default = ข้ามกล่องของปลั๊กอินอื่น (ปลอดภัยไว้ก่อน)");
        assertTrue(c.callInventoryMoveEvent(),
                "default = ยิงอีเวนต์ให้ปลั๊กอินป้องกันพื้นที่เห็น ไม่งั้นท่อลอดเข้าเขตคนอื่นได้");
    }

    @Test
    void callInventoryMoveEventCanBeDisabled() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("call-inventory-move-event", false);
        assertFalse(configFrom(y).callInventoryMoveEvent());
    }

    @Test
    void skipPluginManagedChestsCanBeDisabled() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("skip-plugin-managed-chests", false);
        assertFalse(configFrom(y).skipPluginManagedChests());
    }

    @Test
    void readsExplicitValues() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("items-per-cycle", 5);
        y.set("max-pipe-length", 8);
        y.set("min-pulse-interval-ticks", 20);
        y.set("effects", false);
        y.set("filter-match", "TYPE");

        PipeConfig c = configFrom(y);
        assertEquals(5, c.itemsPerCycle());
        assertEquals(8, c.maxPipeLength());
        assertEquals(20 * 50L, c.minPulseIntervalMillis());
        assertFalse(c.effectsEnabled());
        assertEquals(MatchMode.TYPE, c.matchMode());
    }

    @Test
    void clampsNonPositiveNumbers() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("items-per-cycle", 0);
        y.set("max-pipe-length", -3);
        y.set("min-pulse-interval-ticks", -5);

        PipeConfig c = configFrom(y);
        assertEquals(1, c.itemsPerCycle(), "items-per-cycle ต่ำสุด = 1");
        assertEquals(1, c.maxPipeLength(), "max-pipe-length ต่ำสุด = 1");
        assertEquals(0L, c.minPulseIntervalMillis(), "interval ต่ำสุด = 0 (ไม่จำกัด)");
    }

    @Test
    void allowedContainersListRestrictsToListedMaterials() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", List.of("CHEST", "BARREL"));

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.CHEST));
        assertTrue(c.isContainerAllowed(Material.BARREL));
        assertFalse(c.isContainerAllowed(Material.HOPPER), "hopper ไม่อยู่ใน list");
    }

    @Test
    void allowedContainersAllAllowsEverything() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", "all");

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.HOPPER));
        assertTrue(c.isContainerAllowed(Material.SHULKER_BOX));
    }

    @Test
    void unknownMaterialInListIsSkippedButValidOnesKept() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", List.of("CHEST", "NOT_A_REAL_BLOCK"));

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.CHEST), "ของจริงต้องยังใช้ได้");
        assertFalse(c.isContainerAllowed(Material.BARREL), "ที่ไม่อยู่ใน list ต้องถูกปิด");
    }

    @Test
    void enderChestIsNeverAllowedEvenIfListed() {
        // inventory ของ ender chest เป็นของ "ผู้เล่นที่เปิด" ไม่ใช่ของบล็อก
        // -> ถ้าท่อแตะได้เมื่อไหร่คือทั้ง dupe และขโมยของคนอื่น ต้องปฏิเสธเสมอ
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", List.of("CHEST", "ENDER_CHEST"));
        PipeConfig listed = configFrom(y);
        assertFalse(listed.isContainerAllowed(Material.ENDER_CHEST), "ระบุใน list ก็ต้องไม่ผ่าน");
        assertTrue(listed.isContainerAllowed(Material.CHEST));

        assertFalse(configFrom(new YamlConfiguration()).isContainerAllowed(Material.ENDER_CHEST),
                "โหมด all ก็ต้องไม่ผ่าน");
    }

    @Test
    void aListOfOnlyBadNamesAllowsNothingRatherThanEverything() {
        // เขียน list ไว้ = ตั้งใจจำกัด ถ้าอ่านชื่อไม่ออกสักตัว การ "เปิดทุกกล่อง" คือตรงข้ามกับที่สั่ง
        // ปิดหมดแล้วท่อหยุดเห็นชัด (พร้อม warning ต่อชื่อ) ปลอดภัยกว่าเปิดหมดแบบเงียบ ๆ
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", List.of("NOPE", "ALSO_NOPE"));

        PipeConfig c = configFrom(y);
        assertFalse(c.isContainerAllowed(Material.CHEST));
        assertFalse(c.isContainerAllowed(Material.HOPPER));
    }

    @Test
    void aSingleMaterialWrittenWithoutAListStillRestricts() {
        // "allowed-containers: CHEST" คือสิ่งที่คนเขียนเองโดยธรรมชาติ เดิมมันถูกตีว่า "all" เงียบ ๆ
        // แล้วแอดมินก็เจอว่า shulker/barrel ยังใช้ได้ทั้งที่สั่งจำกัดไปแล้ว
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", "CHEST");

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.CHEST));
        assertFalse(c.isContainerAllowed(Material.BARREL));
        assertFalse(c.isContainerAllowed(Material.BLACK_SHULKER_BOX));
    }

    @Test
    void severalMaterialsOnOneLineAreSplitApart() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", "CHEST, BARREL");

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.CHEST));
        assertTrue(c.isContainerAllowed(Material.BARREL));
        assertFalse(c.isContainerAllowed(Material.HOPPER));
    }

    @Test
    void allIsRecognisedWhateverItsCase() {
        for (String written : new String[] {"all", "ALL", " All "}) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("allowed-containers", written);
            assertTrue(configFrom(y).isContainerAllowed(Material.HOPPER), "'" + written + "'");
        }
    }

    @Test
    void aMaterialThatIsNotABlockIsRejected() {
        // เช่น DIAMOND — ไม่มีทางเป็นกล่องได้ ต้องเตือนและไม่นับเข้า list
        YamlConfiguration y = new YamlConfiguration();
        y.set("allowed-containers", List.of("CHEST", "DIAMOND"));

        PipeConfig c = configFrom(y);
        assertTrue(c.isContainerAllowed(Material.CHEST));
        assertFalse(c.isContainerAllowed(Material.DIAMOND));
    }
}
