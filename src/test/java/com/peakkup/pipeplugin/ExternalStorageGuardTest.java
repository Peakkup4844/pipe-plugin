package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * เทสต์ตาข่ายชั้นที่ 2 ของ {@link ExternalStorageGuard} — ชั้นที่ <b>ไม่พึ่ง API ของปลั๊กอินใด</b>
 * จึงเป็นชั้นเดียวที่เทสต์ได้โดยไม่ต้องมี server (ชั้นที่ 1 ต้องมี WildChests จริง — ดู TESTING.md §0.4)
 *
 * ชั้นนี้จำเป็นเพราะเคยเกิดขึ้นจริง: hook ของ WildChests พังเงียบ ๆ (API เขาไม่ตรงกับที่เราคาด)
 * แล้วกล่องกลายเป็นไม่ถูกป้องกันเลย — ชั้นที่ 2 ทำให้ยังปลอดภัยแม้ชั้นที่ 1 พัง
 */
class ExternalStorageGuardTest {

    @Test
    void disabledGuardNeverBlocksAnything() {
        ExternalStorageGuard guard = ExternalStorageGuard.disabled();
        assertFalse(guard.isPluginManaged(new Location(null, 0, 0, 0)));
        assertFalse(guard.isPluginManaged(new Location(null, 100, 64, -250)));
    }

    @Test
    void disabledGuardHandlesNullLocation() {
        assertFalse(ExternalStorageGuard.disabled().isPluginManaged(null));
    }

    @Test
    void serverOwnedInventoryClassesAreAccepted() {
        // Spigot/Paper รุ่นที่ยังมีเลขเวอร์ชันในแพ็กเกจ
        assertTrue(ExternalStorageGuard.isServerInventoryClass(
                "org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventory"));
        // double chest
        assertTrue(ExternalStorageGuard.isServerInventoryClass(
                "org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventoryDoubleChest"));
        // Paper 1.20.5+ ตัดเลขเวอร์ชันออกจากแพ็กเกจแล้ว
        assertTrue(ExternalStorageGuard.isServerInventoryClass(
                "org.bukkit.craftbukkit.inventory.CraftInventory"));
        assertTrue(ExternalStorageGuard.isServerInventoryClass(
                "io.papermc.paper.inventory.SomethingPaperAdded"));
    }

    @Test
    void pluginOwnedInventoryClassesAreRejected() {
        assertFalse(ExternalStorageGuard.isServerInventoryClass(
                "com.bgsoftware.wildchests.objects.inventory.CraftWildInventory"));
        assertFalse(ExternalStorageGuard.isServerInventoryClass(
                "me.someone.backpacks.BackpackInventory"));
        assertFalse(ExternalStorageGuard.isServerInventoryClass(null));
        // ชื่อที่ "เกือบ" เหมือนของเซิร์ฟเวอร์ต้องไม่ผ่าน (ต้องขึ้นต้นเท่านั้น ไม่ใช่แค่มีคำนี้อยู่)
        assertFalse(ExternalStorageGuard.isServerInventoryClass(
                "net.example.org.bukkit.craftbukkit.inventory.CraftInventory"));
    }

    @Test
    void enabledGuardRejectsAnInventoryItDoesNotRecognise() {
        ExternalStorageGuard guard = new ExternalStorageGuard(true);
        // mock = คลาสที่ไม่ได้มาจากเซิร์ฟเวอร์ -> ต้องถูกปฏิเสธ เหมือน inventory ของปลั๊กอิน storage
        assertTrue(guard.isForeignInventory(mock(Inventory.class)));
        assertFalse(guard.isForeignInventory(null), "null ไม่ใช่กล่องแปลกปลอม แค่ไม่มีของให้ตรวจ");
    }

    @Test
    void turningTheOptionOffDisablesTheGenericNetToo() {
        // skip-plugin-managed-chests: false = แอดมินยอมรับความเสี่ยงเอง -> ต้องไม่บล็อกอะไรเลย
        ExternalStorageGuard guard = new ExternalStorageGuard(false);
        assertFalse(guard.isForeignInventory(mock(Inventory.class)));
    }

    @Test
    void withoutAStoragePluginNobodyReportsARealCount() {
        // ไม่มี WildChests -> ต้องคืน null (ไม่ใช่ 0) เพื่อให้ ContainerAccess ตกไปนับจาก inventory เอง
        // ถ้าเผลอคืน 0 กล่องธรรมดาทุกใบจะกลายเป็น "มีของ 0 ชิ้น" แล้วโดนขึ้นบัญชีดำทันที
        ExternalStorageGuard guard = new ExternalStorageGuard(true);
        assertNull(guard.reportedCount(new Location(null, 448, 70, -178),
                new ItemStack(Material.DIAMOND, 1)));
    }

    @Test
    void reportedCountIsNotSwitchedOffByTheConfigOption() {
        // สวิตช์คุมแค่ "ปฏิเสธกล่อง" ไม่ใช่ "ความถูกต้องของการนับ" — ทั้งสองค่าต้องตอบเหมือนกัน
        // (ที่นี่ = null เพราะไม่มีปลั๊กอิน storage; ประเด็นคือต้องไม่มีการลัดวงจรเพราะ enabled=false)
        Location loc = new Location(null, 448, 70, -178);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);
        assertNull(new ExternalStorageGuard(true).reportedCount(loc, proto));
        assertNull(new ExternalStorageGuard(false).reportedCount(loc, proto));
    }

    @Test
    void reportedCountHandlesNullsWithoutThrowing() {
        ExternalStorageGuard guard = new ExternalStorageGuard(true);
        assertNull(guard.reportedCount(null, new ItemStack(Material.DIAMOND, 1)));
        assertNull(guard.reportedCount(new Location(null, 0, 0, 0), null));
    }

    @Test
    void withoutAStoragePluginNoContainerRejectsAnyItemType() {
        // "ไม่รู้" ต้องแปลว่า "รับได้" เสมอ ไม่งั้นกล่องธรรมดาจะถูกกันไม่ให้รับของ
        ExternalStorageGuard guard = new ExternalStorageGuard(true);
        assertFalse(guard.rejectsType(new Location(null, 448, 70, -178),
                new ItemStack(Material.DIAMOND, 1)));
        assertFalse(guard.rejectsType(null, new ItemStack(Material.DIAMOND, 1)));
        assertFalse(guard.rejectsType(new Location(null, 0, 0, 0), null));
    }
}
