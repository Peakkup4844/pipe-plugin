package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์ "บัญชีดำระหว่างรัน" ของ {@link ContainerAccess} — ชั้นที่ 3 ของการกันกล่องปลอม
 *
 * <p>เหตุผลที่ต้องมีชั้นนี้: storage unit ของ WildChests เป็นบล็อก CHEST จริง และ inventory
 * ที่ได้ก็เป็นคลาสของเซิร์ฟเวอร์เอง ทั้งชั้นที่ 1 (ถาม API) และชั้นที่ 2 (ดูคลาส inventory)
 * จึงมองไม่เห็นถ้า API ตอบว่าไม่รู้จักกล่องใบนั้น เหลือแค่การนับของจริงเท่านั้นที่จับได้
 * พอจับได้แล้วต้อง "เลิกยุ่งถาวร" ไม่งั้นจะเสียหายซ้ำทุก pulse
 */
class ContainerAccessTest {

    @BeforeAll
    static void bukkit() {
        BukkitStub.install(); // trueCount -> countMatching -> isSimilar ต้องมี ItemFactory
    }

    private static final World WORLD = mockWorld("world");

    private static World mockWorld(String name) {
        World w = mock(World.class);
        lenient().when(w.getName()).thenReturn(name);
        return w;
    }

    /** ContainerAccess ที่เปิดทุก container และปิดชั้นที่ 1/2 ไว้ เพื่อเทสบัญชีดำล้วน ๆ */
    private static ContainerAccess accessAllowingEverything() {
        return accessWith(ExternalStorageGuard.disabled());
    }

    /** เทียบไอเทมด้วย Material ล้วน — isSimilar ต้องใช้ ItemFactory ของเซิร์ฟเวอร์ซึ่งไม่มีในเทสต์ */
    private static ContainerAccess accessWith(ExternalStorageGuard guard) {
        YamlConfiguration raw = new YamlConfiguration();
        raw.set("filter-match", "TYPE");
        PipePlugin plugin = mock(PipePlugin.class);
        when(plugin.getConfig()).thenReturn(raw);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ContainerAccessTest"));
        return new ContainerAccess(new PipeConfig(plugin, mock(PipeLang.class)), guard);
    }

    /**
     * guard ปลอมที่ทำตัวเหมือนเจ้าของ storage unit: รู้จำนวนจริงของกล่องใบเดียวที่ระบุไว้
     * กล่องอื่น ๆ ตอบ null = "ไม่รู้จัก" เพื่อให้ ContainerAccess ตกไปนับจาก inventory เอง
     */
    private static final class FakeOwner extends ExternalStorageGuard {
        private final String unit;
        private BigInteger amount;

        FakeOwner(Location unit, BigInteger amount) {
            super(false);
            this.unit = unit.getBlockX() + "," + unit.getBlockY() + "," + unit.getBlockZ();
            this.amount = amount;
        }

        @Override
        public BigInteger reportedCount(Location loc, ItemStack proto) {
            if (loc == null) {
                return null;
            }
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            return unit.equals(key) ? amount : null;
        }
    }

    /** inventory ที่ "วาดสต๊าคโชว์" ค้างไว้ 64 ชิ้นตลอด ไม่ว่าของจริงจะเข้าออกเท่าไหร่ */
    private static Inventory redrawingDisplay() {
        Inventory inv = mock(Inventory.class);
        lenient().when(inv.getContents())
                .thenReturn(new ItemStack[]{new ItemStack(Material.DIAMOND, 64)});
        return inv;
    }

    /** chest ปกติที่มี inventory ใช้ได้ */
    private static Block chestAt(Location loc) {
        Inventory inv = mock(Inventory.class);
        Container state = mock(Container.class);
        lenient().when(state.getInventory()).thenReturn(inv);

        Block block = mock(Block.class);
        lenient().when(block.getType()).thenReturn(Material.CHEST);
        lenient().when(block.getLocation()).thenReturn(loc);
        lenient().when(block.getState()).thenReturn(state);
        return block;
    }

    @Test
    void anOrdinaryChestIsUsable() {
        ContainerAccess access = accessAllowingEverything();
        Block chest = chestAt(new Location(WORLD, 448, 70, -178));

        assertNotNull(access.inventoryOf(chest));
        assertTrue(access.isUsable(chest));
    }

    @Test
    void distrustedContainerIsNeverTouchedAgain() {
        ContainerAccess access = accessAllowingEverything();
        Location loc = new Location(WORLD, 448, 70, -178);
        Block chest = chestAt(loc);
        assertTrue(access.isUsable(chest), "ก่อนขึ้นบัญชีดำต้องใช้ได้ปกติ");

        assertTrue(access.distrust(loc), "ขึ้นบัญชีดำครั้งแรกต้องได้ true");

        assertNull(access.inventoryOf(chest), "ขึ้นบัญชีดำแล้วห้ามคืน inventory อีก");
        assertFalse(access.isUsable(chest), "ค้นท่อก็ต้องไม่เจอกล่องนี้อีก (ท่อหยุดเอง)");
        assertTrue(access.isDistrusted(loc));
    }

    @Test
    void distrustReportsOnlyTheFirstTimeSoTheWarningIsLoggedOnce() {
        // ท่อสองเส้นที่ใช้กล่องต้นทางใบเดียวกันอาจชนเงื่อนไขพร้อมกันคนละ region
        // ค่าที่คืนกลับคือสิ่งที่ใช้กันการเตือนซ้ำ จึงต้องเป็น true แค่ครั้งเดียวเท่านั้น
        ContainerAccess access = accessAllowingEverything();
        Location loc = new Location(WORLD, 10, 64, 10);

        assertTrue(access.distrust(loc));
        assertFalse(access.distrust(loc));
        assertFalse(access.distrust(new Location(WORLD, 10, 64, 10)), "Location คนละ object ก็ต้องนับเป็นใบเดิม");
    }

    @Test
    void distrustIgnoresYawAndPitch() {
        // Location.equals รวม yaw/pitch ด้วย ถ้าใช้ Location เป็น key ตรง ๆ กล่องใบเดียวกัน
        // ที่ถูกอ้างด้วยมุมต่างกันจะกลายเป็นคนละใบ = บัญชีดำรั่ว
        ContainerAccess access = accessAllowingEverything();
        access.distrust(new Location(WORLD, 5, 65, 5, 90f, 45f));

        assertTrue(access.isDistrusted(new Location(WORLD, 5, 65, 5)));
        assertNull(access.inventoryOf(chestAt(new Location(WORLD, 5, 65, 5))));
    }

    @Test
    void distrustingOneContainerDoesNotAffectItsNeighbour() {
        ContainerAccess access = accessAllowingEverything();
        Location bad = new Location(WORLD, 448, 70, -178);
        Location good = new Location(WORLD, 449, 70, -178);

        access.distrust(bad);

        assertNull(access.inventoryOf(chestAt(bad)));
        assertNotNull(access.inventoryOf(chestAt(good)), "กล่องข้าง ๆ ต้องไม่โดนหางเลข");
    }

    @Test
    void containersInDifferentWorldsAreTrackedSeparately() {
        ContainerAccess access = accessAllowingEverything();
        Location overworld = new Location(WORLD, 0, 64, 0);
        Location nether = new Location(mockWorld("world_nether"), 0, 64, 0);

        access.distrust(overworld);

        assertTrue(access.isDistrusted(overworld));
        assertFalse(access.isDistrusted(nether), "พิกัดเดียวกันคนละโลก = คนละกล่อง");
    }

    @Test
    void nullLocationIsHandledWithoutBlacklistingAnything() {
        ContainerAccess access = accessAllowingEverything();
        assertFalse(access.distrust(null));
        assertFalse(access.isDistrusted(null));
        assertNull(access.inventoryAt(null));
    }

    // ------------------------------------------------------------------
    // trueCount — ตัวเลขที่ด่านตรวจการอนุรักษ์ของใช้ตัดสิน
    // ------------------------------------------------------------------

    @Test
    void anOrdinaryContainerIsStillCountedFromItsOwnInventory() {
        ContainerAccess access = accessAllowingEverything(); // ไม่มีใครรายงานจำนวนจริงได้
        Inventory inv = mock(Inventory.class);
        when(inv.getContents()).thenReturn(new ItemStack[]{
                new ItemStack(Material.DIAMOND, 20), null, new ItemStack(Material.DIAMOND, 12)});

        assertEquals(BigInteger.valueOf(32),
                access.trueCount(new Location(WORLD, 1, 64, 1), inv, new ItemStack(Material.DIAMOND, 1)));
    }

    @Test
    void aStorageUnitIsCountedFromItsOwnerNotFromTheDisplayStack() {
        // นี่คือบั๊กที่ทำให้ท่อกลืนของหายทุก pulse: ดูดออก 32 แต่ inventory ยังนับได้ 64 เท่าเดิม
        // ด่านตรวจจึงสรุปว่า "ต้นทางไม่เสียของ" ทั้งที่เสียไปจริง เพราะไปนับจากสต๊าคที่วาดโชว์
        Location unit = new Location(WORLD, 448, 70, -178);
        FakeOwner owner = new FakeOwner(unit, BigInteger.valueOf(1000));
        ContainerAccess access = accessWith(owner);
        Inventory display = redrawingDisplay();
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        BigInteger before = access.trueCount(unit, display, proto);
        owner.amount = BigInteger.valueOf(968); // ปลั๊กอินหักของจริงไป 32 แล้ววาดสต๊าค 64 กลับมา
        BigInteger after = access.trueCount(unit, display, proto);

        assertEquals(BigInteger.valueOf(64), BigInteger.valueOf(
                        InventoryOps.countMatching(display, proto)),
                "สต๊าคที่วาดโชว์ต้องเท่าเดิม — ไม่งั้นเทสต์นี้ไม่ได้จำลองอาการจริง");
        assertEquals(BigInteger.valueOf(32), before.subtract(after),
                "ต้องเห็นว่าของหายไป 32 จริง ท่อจึงจะยอมส่งของต่อแทนที่จะขึ้นบัญชีดำ");
    }

    @Test
    void aStorageUnitHoldingMoreThanIntMaxIsStillMeasuredExactly() {
        // storage unit เก็บของเป็น BigInteger ได้เกิน int หลายเท่า ถ้าเผลอย่อเป็น int
        // ผลต่างจะกลายเป็น 0 (หรือติดลบ) แล้วกล่องที่ทำงานถูกต้องจะโดนขึ้นบัญชีดำ
        Location unit = new Location(WORLD, 448, 70, -178);
        BigInteger huge = BigInteger.valueOf(Integer.MAX_VALUE).multiply(BigInteger.valueOf(5));
        FakeOwner owner = new FakeOwner(unit, huge);
        ContainerAccess access = accessWith(owner);
        ItemStack proto = new ItemStack(Material.DIAMOND, 1);

        BigInteger before = access.trueCount(unit, redrawingDisplay(), proto);
        owner.amount = huge.subtract(BigInteger.valueOf(32));
        BigInteger after = access.trueCount(unit, redrawingDisplay(), proto);

        assertEquals(BigInteger.valueOf(32), before.subtract(after));
    }

    /** mock inventory ของบล็อกชนิดที่ระบุ (ใช้ตัดสินกฎ shulker ซ้อน shulker) */
    private static Inventory invOfType(InventoryType type) {
        Inventory inv = mock(Inventory.class);
        lenient().when(inv.getType()).thenReturn(type);
        return inv;
    }

    @Test
    void anOrdinaryContainerAcceptsEveryItemType() {
        // ต้องไม่มีทางที่กล่องธรรมดาจะถูกกันไม่ให้รับของ — ตอบ "ไม่รู้" ต้องแปลว่า "รับได้"
        ContainerAccess access = accessAllowingEverything();
        Inventory chest = invOfType(InventoryType.CHEST);
        assertTrue(access.acceptsType(new Location(WORLD, 1, 64, 1), chest, new ItemStack(Material.DIRT, 1)));
        assertTrue(access.acceptsType(null, chest, new ItemStack(Material.DIRT, 1)));
        assertTrue(access.acceptsType(null, null, new ItemStack(Material.DIRT, 1)));
    }

    @Test
    void aShulkerBoxRefusesAnotherShulkerBox() {
        // Inventory#addItem ไม่เช็คกฎ canPlaceItem ของ vanilla เลย ถ้าไม่กันตรงนี้ ท่อจะยัด shulker
        // ซ้อนใน shulker ได้ทั้งที่ hopper ในเกมทำไม่ได้ = ผู้เล่นทำ NBT ซ้อนกันจนบวมเพื่อถ่วงเซิร์ฟได้
        ContainerAccess access = accessAllowingEverything();
        Location loc = new Location(WORLD, 1, 64, 1);
        Inventory shulker = invOfType(InventoryType.SHULKER_BOX);

        assertFalse(access.acceptsType(loc, shulker, new ItemStack(Material.SHULKER_BOX, 1)),
                "shulker ใส่ shulker ไม่ได้");
        assertFalse(access.acceptsType(loc, shulker, new ItemStack(Material.RED_SHULKER_BOX, 1)),
                "สีไหนก็ห้ามเหมือนกัน");
        assertTrue(access.acceptsType(loc, shulker, new ItemStack(Material.DIRT, 1)),
                "ของอย่างอื่นยังใส่ shulker ได้ตามปกติ");
        assertTrue(access.acceptsType(loc, invOfType(InventoryType.CHEST),
                        new ItemStack(Material.SHULKER_BOX, 1)),
                "shulker ลงกล่องธรรมดาได้ — vanilla ก็ให้ทำ");
    }

    @Test
    void theSameShulkerRuleIsAnsweredFromTheDestinationMaterialAlone() {
        // ต้องตอบได้โดยไม่ต้องมี Inventory ของปลายทาง ไม่งั้นฝั่งต้นทาง (คนละ region บน Folia)
        // จะคัดชนิดนี้ทิ้งตั้งแต่ต้นไม่ได้ แล้วท่อจะดูด-คืน shulker วนทุก pulse จนของอื่นไม่ได้ไป
        assertFalse(ContainerAccess.canHold(Material.SHULKER_BOX, new ItemStack(Material.SHULKER_BOX, 1)));
        assertFalse(ContainerAccess.canHold(Material.LIME_SHULKER_BOX, new ItemStack(Material.SHULKER_BOX, 1)));
        assertTrue(ContainerAccess.canHold(Material.SHULKER_BOX, new ItemStack(Material.DIRT, 1)));
        assertTrue(ContainerAccess.canHold(Material.CHEST, new ItemStack(Material.SHULKER_BOX, 1)));
        assertTrue(ContainerAccess.canHold(Material.BARREL, new ItemStack(Material.DIRT, 1)));
    }

    @Test
    void aStorageUnitHoldingAnotherTypeIsSkippedInsteadOfBlacklisted() {
        // storage unit รับได้ชนิดเดียว ถ้ายัดผิดชนิด WildChests จะโยนของทิ้งพื้นแต่ addItem บอกว่ารับครบ
        // -> ถ้าไม่ถามก่อน ด่านนับของฝั่งปลายทางจะขึ้นบัญชีดำกล่องที่ทำงานถูกต้อง
        Location unit = new Location(WORLD, 448, 70, -178);
        ContainerAccess access = accessWith(new ExternalStorageGuard(true) {
            @Override
            public boolean rejectsType(Location loc, ItemStack proto) {
                return loc != null && loc.getBlockX() == 448 && proto.getType() != Material.DIAMOND;
            }
        });
        Inventory chest = invOfType(InventoryType.CHEST);

        assertFalse(access.acceptsType(unit, chest, new ItemStack(Material.DIRT, 1)), "คนละชนิด -> ต้องข้ามไป");
        assertTrue(access.acceptsType(unit, chest, new ItemStack(Material.DIAMOND, 1)), "ชนิดเดียวกัน -> ต้องรับ");
        assertTrue(access.acceptsType(new Location(WORLD, 1, 64, 1), chest, new ItemStack(Material.DIRT, 1)),
                "กล่องอื่นต้องไม่โดนหางเลข");
    }

    @Test
    void aContainerTheOwnerDoesNotClaimFallsBackToCountingItsInventory() {
        // กล่องอื่นในโลกเดียวกันต้องไม่ถูกตอบด้วยตัวเลขของ storage unit
        Location unit = new Location(WORLD, 448, 70, -178);
        ContainerAccess access = accessWith(new FakeOwner(unit, BigInteger.valueOf(1000)));
        Inventory chest = mock(Inventory.class);
        when(chest.getContents()).thenReturn(new ItemStack[]{new ItemStack(Material.DIAMOND, 7)});

        assertEquals(BigInteger.valueOf(7),
                access.trueCount(new Location(WORLD, 449, 70, -178), chest,
                        new ItemStack(Material.DIAMOND, 1)));
    }
}
