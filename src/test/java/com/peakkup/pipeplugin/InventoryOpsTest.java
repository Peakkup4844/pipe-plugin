package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์การดูด/คืนของระดับช่อง — หัวใจของการ "ไม่ทำผังกล่องต้นทางเพี้ยน" และการอนุรักษ์จำนวนของ
 *
 * ใช้ ItemStack ของจริง (ไม่มี meta) คู่กับ {@link BukkitStub} เพราะการจับคู่ภายในเทียบด้วย
 * {@code isSimilar} เสมอ ซึ่งต้องมี Bukkit ItemFactory ให้เรียก
 */
class InventoryOpsTest {

    @BeforeAll
    static void bukkit() {
        BukkitStub.install();
    }

    /** inventory จำลองที่หนุนด้วย array — พอสำหรับ getContents/getItem/setItem/getSize */
    private static Inventory fakeInventory(ItemStack[] slots) {
        Inventory inv = mock(Inventory.class);
        when(inv.getSize()).thenReturn(slots.length);
        when(inv.getContents()).thenAnswer(i -> slots.clone());
        when(inv.getItem(anyInt())).thenAnswer(i -> slots[(int) i.getArgument(0)]);
        doAnswer(i -> {
            slots[(int) i.getArgument(0)] = i.getArgument(1);
            return null;
        }).when(inv).setItem(anyInt(), any());
        return inv;
    }

    private static int totalOf(ItemStack[] slots, Material type) {
        int n = 0;
        for (ItemStack s : slots) {
            if (s != null && s.getType() == type) {
                n += s.getAmount();
            }
        }
        return n;
    }

    @Test
    void removeUpToTakesRequestedAmountAndRecordsSlots() {
        ItemStack[] slots = new ItemStack[9];
        slots[3] = new ItemStack(Material.DIAMOND, 10);
        slots[7] = new ItemStack(Material.DIAMOND, 10);
        Inventory inv = fakeInventory(slots);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(
                inv, new ItemStack(Material.DIAMOND, 1), 15);

        assertEquals(15, taken.total());
        assertEquals(2, taken.slots().size());
        assertEquals(3, taken.slots().get(0)[0]);
        assertEquals(10, taken.slots().get(0)[1]);
        assertEquals(7, taken.slots().get(1)[0]);
        assertEquals(5, taken.slots().get(1)[1]);
        assertNull(slots[3], "ช่องที่ดูดหมดต้องกลายเป็นว่าง");
        assertEquals(5, slots[7].getAmount(), "ช่องที่ดูดบางส่วนต้องเหลือของ");
    }

    @Test
    void removeUpToIgnoresOtherItemTypes() {
        ItemStack[] slots = new ItemStack[9];
        slots[0] = new ItemStack(Material.IRON_INGOT, 20);
        slots[1] = new ItemStack(Material.DIAMOND, 5);
        Inventory inv = fakeInventory(slots);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(
                inv, new ItemStack(Material.DIAMOND, 1), 32);

        assertEquals(5, taken.total());
        assertEquals(20, slots[0].getAmount(), "ของชนิดอื่นต้องไม่ถูกแตะ");
    }

    @Test
    void removeUpToStopsAtCap() {
        ItemStack[] slots = new ItemStack[9];
        slots[0] = new ItemStack(Material.DIAMOND, 64);
        Inventory inv = fakeInventory(slots);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(
                inv, new ItemStack(Material.DIAMOND, 1), 32);

        assertEquals(32, taken.total());
        assertEquals(32, slots[0].getAmount());
    }

    /** เคสหลักของบั๊ก: ปลายทางเต็ม -> ของทั้งหมดต้องกลับไปช่องเดิม กล่องเหมือนไม่เคยถูกแตะ */
    @Test
    void fullRestorePutsEverythingBackInOriginalSlots() {
        ItemStack[] slots = new ItemStack[9];
        slots[4] = new ItemStack(Material.DIAMOND, 12);
        slots[8] = new ItemStack(Material.DIAMOND, 20);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 32);
        assertEquals(32, taken.total());
        assertNull(slots[4]);
        assertNull(slots[8]);

        // ปลายทางเต็มสนิท -> remaining = ทั้งหมด
        int left = InventoryOps.restoreToOriginalSlots(
                inv, proto, taken.total(), taken.slots());

        assertEquals(0, left, "ต้องคืนได้ครบ");
        assertNotNull(slots[4]);
        assertNotNull(slots[8]);
        assertEquals(12, slots[4].getAmount(), "ช่อง 4 ต้องกลับมาเท่าเดิม");
        assertEquals(20, slots[8].getAmount(), "ช่อง 8 ต้องกลับมาเท่าเดิม");
        assertNull(slots[0], "ห้ามมีของไปโผล่ช่องแรก (อาการบั๊กเดิม)");
        assertEquals(32, totalOf(slots, Material.DIAMOND));
    }

    @Test
    void partialRestoreFillsOriginalSlotsFirst() {
        ItemStack[] slots = new ItemStack[9];
        slots[4] = new ItemStack(Material.DIAMOND, 12);
        slots[8] = new ItemStack(Material.DIAMOND, 20);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 32);
        // ปลายทางรับไป 20 เหลือคืน 12
        int left = InventoryOps.restoreToOriginalSlots(inv, proto, 12, taken.slots());

        assertEquals(0, left);
        assertEquals(12, slots[4].getAmount(), "เติมช่องเดิมตัวแรกก่อน");
        assertNull(slots[8], "ช่องหลังยังว่างเพราะของถูกส่งออกไปแล้ว");
        assertNull(slots[0]);
    }

    @Test
    void restoreTopsUpOriginalSlotWhenSomeoneElsePartiallyRefilledIt() {
        ItemStack[] slots = new ItemStack[9];
        slots[4] = new ItemStack(Material.DIAMOND, 30);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 30);
        // ระหว่างรอบ มีคนใส่ของชนิดเดียวกันกลับมา 10 ชิ้นในช่องเดิม
        slots[4] = new ItemStack(Material.DIAMOND, 10);

        int left = InventoryOps.restoreToOriginalSlots(inv, proto, 30, taken.slots());

        // diamond stack ได้สูงสุด 64 -> เติมได้อีก 54 จึงคืนได้ครบ 30
        assertEquals(0, left);
        assertEquals(40, slots[4].getAmount());
    }

    @Test
    void restoreSkipsSlotTakenByAnotherItemAndReportsLeftover() {
        ItemStack[] slots = new ItemStack[9];
        slots[4] = new ItemStack(Material.DIAMOND, 12);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 12);
        // ระหว่างรอบ มีคนเอาของอื่นมาวางทับช่องเดิม
        slots[4] = new ItemStack(Material.IRON_INGOT, 1);

        int left = InventoryOps.restoreToOriginalSlots(inv, proto, 12, taken.slots());

        assertEquals(12, left, "คืนช่องเดิมไม่ได้ -> ต้องรายงานเป็น leftover ให้ผู้เรียกไปหาที่ยัดต่อ");
        assertEquals(Material.IRON_INGOT, slots[4].getType(), "ห้ามทับของคนอื่น");
    }

    // --- ของชนิดเดียวกันแต่ NBT ต่างกัน ต้องไม่ถูกกลืนรวมเป็นชนิดเดียว ---
    //
    // เดิม InventoryOps รับ MatchMode ของ filter มาใช้จัดกลุ่มด้วย พอตั้ง filter-match: TYPE
    // ของที่ stack ได้แต่ NBT ต่างกัน (ลูกศรอาบยา, พลุ) จะถูกดูดรวมกันแล้ว "สร้างใหม่" จาก proto
    // ตัวแรกทั้งหมด = ผู้เล่นเอาของแพงวางไว้ช่องแรก แล้วแปลงของถูกทั้งกล่องเป็นของแพงได้
    // ตอนนี้เทียบด้วย isSimilar เสมอ โหมด filter มีผลแค่กับประตู item frame เท่านั้น

    /** ItemStack จำลองที่ NBT ต่างกันได้จริง — สร้างของมี meta ในเทสต์ยูนิตไม่ได้ */
    private static ItemStack arrow(int amount) {
        ItemStack s = mock(ItemStack.class);
        lenient().when(s.getType()).thenReturn(Material.TIPPED_ARROW);
        lenient().when(s.getAmount()).thenReturn(amount);
        lenient().when(s.getMaxStackSize()).thenReturn(64);
        return s;
    }

    @Test
    void twoStacksOfTheSameMaterialButDifferentNbtAreNeverTakenTogether() {
        ItemStack proto = arrow(1);
        ItemStack sameNbt = arrow(10);
        ItemStack otherNbt = arrow(10);
        lenient().when(proto.isSimilar(sameNbt)).thenReturn(true);
        lenient().when(proto.isSimilar(otherNbt)).thenReturn(false);

        ItemStack[] slots = {sameNbt, otherNbt};
        Inventory inv = fakeInventory(slots);

        assertEquals(10, InventoryOps.countMatching(inv, proto),
                "นับต้องนับเฉพาะที่ตรงกันเป๊ะ ไม่ใช่ 20 ชิ้นทั้งกล่อง");

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 32);

        assertEquals(10, taken.total(), "ดูดได้เฉพาะสต๊าคที่ NBT ตรงกันจริง");
        assertEquals(1, taken.slots().size());
        assertEquals(0, taken.slots().get(0)[0], "ต้องเป็นช่องแรกเท่านั้น");
        assertNull(slots[0], "ช่องที่ตรงกันถูกดูดออกหมด");
        assertSame(otherNbt, slots[1], "ของ NBT อื่นต้องอยู่ที่เดิมครบ");
    }

    @Test
    void restoreDoesNotTopUpASlotHoldingTheSameMaterialWithDifferentNbt() {
        ItemStack proto = arrow(1);
        ItemStack mine = arrow(8);
        ItemStack imposter = arrow(8);
        lenient().when(proto.isSimilar(mine)).thenReturn(true);
        lenient().when(proto.isSimilar(imposter)).thenReturn(false);

        ItemStack[] slots = {mine};
        Inventory inv = fakeInventory(slots);
        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 8);
        // ระหว่างรอบ มีคนเอาลูกศรอีกแบบมาวางในช่องเดิม — เติมทับไม่ได้ ต้องรายงานเป็น leftover
        slots[0] = imposter;

        assertEquals(8, InventoryOps.restoreToOriginalSlots(inv, proto, 8, taken.slots()));
        assertSame(imposter, slots[0], "ห้ามแปลงของคนอื่นเป็นของเรา");
        assertEquals(8, imposter.getAmount(), "และห้ามเพิ่มจำนวนให้มันด้วย");
    }

    @Test
    void restoreNeverExceedsMaxStackSizeOfOriginalSlot() {
        ItemStack[] slots = new ItemStack[9];
        // ของ stack ไม่ได้ (ดาบ = max stack 1)
        slots[2] = new ItemStack(Material.DIAMOND_SWORD, 1);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND_SWORD, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 1);
        assertEquals(1, taken.total());

        int left = InventoryOps.restoreToOriginalSlots(inv, proto, 1, taken.slots());

        assertEquals(0, left);
        assertEquals(1, slots[2].getAmount());
    }

    @Test
    void restoreIsNoOpWhenNothingRemains() {
        ItemStack[] slots = new ItemStack[9];
        slots[4] = new ItemStack(Material.DIAMOND, 12);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 12);
        int left = InventoryOps.restoreToOriginalSlots(inv, proto, 0, taken.slots());

        assertEquals(0, left);
        assertNull(slots[4], "ส่งออกหมด -> ช่องเดิมต้องยังว่าง");
    }

    @Test
    void restoreHandlesShrunkInventoryWithoutThrowing() {
        ItemStack[] big = new ItemStack[54];
        big[50] = new ItemStack(Material.DIAMOND, 8);
        Inventory large = fakeInventory(big);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);
        InventoryOps.Taken taken = InventoryOps.removeUpTo(large, proto, 8);

        // double chest ถูกแยกกลางรอบ -> กล่องเหลือ 27 ช่อง, slot 50 ไม่มีแล้ว
        Inventory small = fakeInventory(new ItemStack[27]);
        int left = InventoryOps.restoreToOriginalSlots(small, proto, 8, taken.slots());

        assertEquals(8, left, "ช่องเดิมหายไป -> ต้องรายงานเป็น leftover ไม่ใช่ crash");
    }

    @Test
    void conservationHoldsAcrossRemoveThenFullRestore() {
        ItemStack[] slots = new ItemStack[27];
        slots[0] = new ItemStack(Material.DIAMOND, 64);
        slots[5] = new ItemStack(Material.DIAMOND, 33);
        slots[9] = new ItemStack(Material.IRON_INGOT, 12);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        int before = totalOf(slots, Material.DIAMOND);

        // จำลองปลายทางเต็มติดกันหลายรอบ
        for (int pulse = 0; pulse < 10; pulse++) {
            InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 32);
            int left = InventoryOps.restoreToOriginalSlots(
                    inv, proto, taken.total(), taken.slots());
            assertEquals(0, left, "รอบที่ " + pulse + ": ต้องคืนเข้าช่องเดิมได้ครบ");
        }

        assertEquals(before, totalOf(slots, Material.DIAMOND), "จำนวนรวมต้องไม่เปลี่ยน");
        assertEquals(64, slots[0].getAmount(), "ผังกล่องต้องเหมือนเดิมเป๊ะ");
        assertEquals(33, slots[5].getAmount(), "ผังกล่องต้องเหมือนเดิมเป๊ะ");
        assertEquals(12, slots[9].getAmount(), "ของชนิดอื่นไม่ถูกแตะ");
    }

    // --- countMatching + ด่านกัน dupe ที่ต้นทาง ---

    @Test
    void countMatchingSumsOnlyTheMatchingType() {
        ItemStack[] slots = new ItemStack[9];
        slots[0] = new ItemStack(Material.DIAMOND, 64);
        slots[2] = new ItemStack(Material.IRON_INGOT, 40);
        slots[6] = new ItemStack(Material.DIAMOND, 7);
        Inventory inv = fakeInventory(slots);

        assertEquals(71, InventoryOps.countMatching(
                inv, new ItemStack(Material.DIAMOND, 1)));
        assertEquals(0, InventoryOps.countMatching(
                inv, new ItemStack(Material.GOLD_INGOT, 1)));
    }

    @Test
    void countMatchingIgnoresEmptySlots() {
        ItemStack[] slots = new ItemStack[9];
        slots[1] = new ItemStack(Material.AIR, 5);
        slots[3] = new ItemStack(Material.DIAMOND, 0);
        slots[4] = new ItemStack(Material.DIAMOND, 3);
        Inventory inv = fakeInventory(slots);

        assertEquals(3, InventoryOps.countMatching(
                inv, new ItemStack(Material.DIAMOND, 1)));
    }

    /**
     * ด่านกัน dupe: จำลอง inventory แบบ "ไอเทมโชว์" ของปลั๊กอิน storage — setItem ไม่มีผลจริง
     * ถ้าเชื่อค่าที่ removeUpTo คืนมาแล้วส่งของออกไป = เสกของจากอากาศทุก pulse
     * การนับซ้ำต้องจับได้ว่าต้นทาง "ไม่ได้เสียของไปเลย"
     */
    @Test
    void countMatchingExposesAnInventoryThatNeverActuallyLosesItems() {
        // อ่านกี่ครั้งก็ได้ค่าเดิมเสมอ (ไอเทมโชว์ที่ปลั๊กอินวาดใหม่ทุกครั้ง) และเขียนแล้วไม่มีผล
        Inventory lying = mock(Inventory.class);
        when(lying.getSize()).thenReturn(1);
        when(lying.getContents()).thenAnswer(i -> new ItemStack[]{new ItemStack(Material.DIAMOND, 64)});
        when(lying.getItem(anyInt())).thenAnswer(i -> new ItemStack(Material.DIAMOND, 64));
        doAnswer(i -> null).when(lying).setItem(anyInt(), any());

        ItemStack proto = new ItemStack(Material.DIAMOND, 1);
        int before = InventoryOps.countMatching(lying, proto);
        InventoryOps.Taken taken = InventoryOps.removeUpTo(lying, proto, 32);
        int after = InventoryOps.countMatching(lying, proto);

        assertEquals(32, taken.total(), "removeUpTo คิดว่าดูดมาได้ 32");
        assertEquals(0, before - after, "แต่ต้นทางไม่ได้เสียของไปเลย");
        assertNotEquals(taken.total(), before - after,
                "ตัวเลขไม่ตรง = ItemTransferService ต้องยกเลิกรอบนี้ ไม่ส่งของออกไป");
    }

    /**
     * ⚠️ เคสที่ทำให้ "ทิ้งของที่ดูดมา" เป็นบั๊กของหาย — storage unit แบบ <b>หักจริงแต่วาดกลับ</b>
     *
     * <p>กล่องแบบนี้เก็บจำนวนจริงไว้ในตัวนับข้างหลัง ส่วนช่องที่เห็นเป็นแค่ "ไอเทมโชว์" ที่ถูกวาดเต็ม
     * สต๊าคใหม่ทุกครั้ง เขียนลงไปเท่าไหร่ = หักของจริงเท่านั้น แต่พออ่านซ้ำก็ได้ค่าเดิมเสมอ
     *
     * <p>ผลคือหน้าตาการอ่านของมัน <b>เหมือนกันเป๊ะ</b> กับกล่อง "ไอเทมโชว์ที่เขียนแล้วไม่มีผล"
     * ในเทสต์ก่อนหน้า ทั้งที่ผลลัพธ์ตรงข้ามกันสุดขั้ว: อันนั้นของยังอยู่ อันนี้ของหายไปแล้วจริง ๆ
     * แยกจากภายนอกไม่ได้ → "lost 0" แปลว่า <b>ไม่รู้</b> ไม่ใช่ "ของยังอยู่"
     * ดังนั้นตอนยกเลิกรอบต้องยัดของกลับ<b>ทั้งหมด</b>เสมอ แล้วเลิกใช้กล่องนั้นถาวร
     */
    @Test
    void anInventoryThatDeductsButRedrawsItsDisplayReadsExactlyLikeOneThatIgnoredTheWrite() {
        java.util.concurrent.atomic.AtomicInteger realStock =
                new java.util.concurrent.atomic.AtomicInteger(1000);
        Inventory unit = mock(Inventory.class);
        when(unit.getSize()).thenReturn(1);
        when(unit.getContents()).thenAnswer(i -> new ItemStack[]{new ItemStack(Material.DIAMOND, 64)});
        when(unit.getItem(anyInt())).thenAnswer(i -> new ItemStack(Material.DIAMOND, 64));
        doAnswer(i -> {
            ItemStack written = i.getArgument(1);
            int shown = written == null ? 0 : written.getAmount();
            realStock.addAndGet(shown - 64); // เขียนน้อยลงเท่าไหร่ = หักของจริงเท่านั้น
            return null;
        }).when(unit).setItem(anyInt(), any());

        ItemStack proto = new ItemStack(Material.DIAMOND, 1);
        int before = InventoryOps.countMatching(unit, proto);
        InventoryOps.Taken taken = InventoryOps.removeUpTo(unit, proto, 32);
        int after = InventoryOps.countMatching(unit, proto);

        assertEquals(32, taken.total());
        assertEquals(0, before - after, "อ่านซ้ำแล้วเหมือนไม่มีอะไรเกิดขึ้น (เหมือนเคสไอเทมโชว์เป๊ะ)");
        assertEquals(1000 - 32, realStock.get(),
                "แต่ของจริงหายไป 32 แล้ว -> ถ้าทิ้งของที่ดูดมา ผู้เล่นจะเสียของจริงทุก pulse");
    }

    /** เคสปกติ: จำนวนที่หายไปจากต้นทางต้องเท่ากับที่ดูดมาเป๊ะ (ด่านต้องไม่เตือนผิด) */
    @Test
    void conservationCheckPassesOnAnHonestInventory() {
        ItemStack[] slots = new ItemStack[27];
        slots[3] = new ItemStack(Material.DIAMOND, 20);
        slots[11] = new ItemStack(Material.DIAMOND, 20);
        Inventory inv = fakeInventory(slots);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        int before = InventoryOps.countMatching(inv, proto);
        InventoryOps.Taken taken = InventoryOps.removeUpTo(inv, proto, 32);
        int after = InventoryOps.countMatching(inv, proto);

        assertEquals(32, taken.total());
        assertEquals(taken.total(), before - after);
    }
}
