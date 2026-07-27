package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * จุด<b>เดียว</b>ที่ตัดสินว่า "ท่อแตะ container ตัวนี้ได้ไหม" และคืน inventory ที่ใช้ได้จริง
 *
 * <p>เดิมตรรกะนี้ถูกเขียนซ้ำสองที่ (ตอนค้นท่อ กับ ตอนย้ายของ) ซึ่งเสี่ยงจะเพี้ยนไปคนละทาง
 * รวมไว้ที่นี่เพื่อให้ "กล่องที่ค้นเจอ" กับ "กล่องที่ยอมเขียนลงไป" เป็นชุดเดียวกันเสมอ
 *
 * <p>ลำดับการตรวจเรียงจากถูกไปแพง และจาก "ปฏิเสธชัด ๆ" ไป "ปฏิเสธเพราะไม่ไว้ใจ":
 * <ol>
 *   <li>material อยู่ใน {@code allowed-containers} ไหม (ไม่ต้องสร้าง BlockState = ถูกที่สุด)</li>
 *   <li>เคยจับได้ว่ากล่องนี้ "นับของไม่ตรง" มาก่อนไหม ({@link #distrust(Location)})</li>
 *   <li>เป็น {@link Container} จริงไหม</li>
 *   <li>เป็นกล่องที่ปลั๊กอินอื่นถือของจริงไว้เองไหม (ถาม API ของปลั๊กอินนั้น)</li>
 *   <li>inventory ที่ได้เป็นของเซิร์ฟเวอร์เองไหม (ตาข่ายกันพลาดแบบไม่ผูกกับปลั๊กอินใด)</li>
 * </ol>
 *
 * <p>ทุกอย่างที่อ่านบล็อก/inventory ต้องถูกเรียกบนเธรดของ region ที่เป็นเจ้าของตำแหน่งนั้น (Folia)
 * ส่วน blacklist ใช้ {@link ConcurrentHashMap} จึงเขียน/อ่านข้าม region ได้
 */
public final class ContainerAccess {

    private final PipeConfig config;
    private final ExternalStorageGuard storageGuard;

    /**
     * ตำแหน่งกล่องที่ "พิสูจน์แล้วว่านับของไม่ตรง" ระหว่างรันจริง — ห้ามแตะอีกจนกว่าจะรีสตาร์ต
     *
     * <p>นี่คือชั้นสุดท้ายที่ทำให้ความเสียหายของกล่องแปลก ๆ ที่ทั้งสองชั้นแรกจับไม่ได้
     * <b>มีขอบเขตจำกัด</b>: อย่างมากที่สุดคือรอบเดียว (≤ items-per-cycle ชิ้น) ต่อกล่องหนึ่งใบ
     * ต่อการรันเซิร์ฟเวอร์หนึ่งครั้ง แทนที่จะเสียหายทุก pulse ไปเรื่อย ๆ
     *
     * <p>เก็บเป็นสตริงแทน {@link Location} เพราะ {@code Location.equals} รวม yaw/pitch ด้วย
     * และเพื่อไม่ถือ reference ของ World ไว้ (กัน world unload แล้วปล่อยหน่วยความจำไม่ได้)
     */
    private final Set<String> distrusted = ConcurrentHashMap.newKeySet();

    public ContainerAccess(PipeConfig config, ExternalStorageGuard storageGuard) {
        this.config = config;
        this.storageGuard = storageGuard;
    }

    /** inventory ที่ท่อใช้ได้ที่ตำแหน่งนี้ หรือ null ถ้าห้ามแตะ */
    public Inventory inventoryAt(Location loc) {
        return loc == null ? null : inventoryOf(loc.getBlock());
    }

    /** inventory ที่ท่อใช้ได้ของบล็อกนี้ หรือ null ถ้าห้ามแตะ */
    public Inventory inventoryOf(Block block) {
        Material type = block.getType();
        if (type.isAir() || !config.isContainerAllowed(type)) {
            return null;
        }

        Location loc = block.getLocation();
        // isEmpty() ก่อนเสมอ: บัญชีดำว่างในเซิร์ฟปกติ จะได้ไม่ต้องประกอบสตริง key ทุกครั้งที่แตะกล่อง
        if (!distrusted.isEmpty() && distrusted.contains(key(loc))) {
            return null; // เคยจับได้ว่าโกหกเรื่องจำนวนของ -> เลิกยุ่งถาวร
        }

        Container container;
        try {
            if (!(block.getState() instanceof Container state)) {
                return null;
            }
            container = state;
        } catch (Throwable t) {
            return null; // อ่าน BlockState ไม่ได้ (chunk/ปลั๊กอินอื่นแทรก) -> ไม่แตะ ปลอดภัยไว้ก่อน
        }

        // ของจริงไม่ได้อยู่ใน tile entity -> เขียนลงไปแล้วของหาย, ดูดออกมาแล้วอาจเสกของ
        if (storageGuard.isPluginManaged(loc)) {
            return null;
        }

        Inventory inventory;
        try {
            inventory = container.getInventory();
        } catch (Throwable t) {
            return null;
        }
        return storageGuard.isForeignInventory(inventory) ? null : inventory;
    }

    /**
     * จำนวน "ของจริง" ของไอเทมชนิดนี้ในกล่องที่ตำแหน่งนี้ — ตัวเลขที่ใช้ตรวจการอนุรักษ์ของ
     *
     * <p>ถาม<b>เจ้าของกล่อง</b>ก่อนเสมอ แล้วค่อยตกมานับจาก inventory เอง เพราะกล่องบางชนิด
     * (storage unit ของ WildChests) เก็บจำนวนจริงไว้ข้างนอก inventory แล้ววาดสต๊าคโชว์ค้างไว้
     * ในช่องเดิมตลอด — นับจาก inventory จะได้เลขเท่าเดิมทุกครั้งไม่ว่าของจะเข้าออกไปเท่าไหร่
     * ด่านตรวจการอนุรักษ์จึงฟ้องผิดทุกรอบ แล้วท่อจะขึ้นบัญชีดำกล่องที่จริง ๆ ทำงานถูกต้อง
     *
     * <p>คืนเป็น {@link BigInteger} เพราะ storage unit เก็บของได้เกิน {@code int} หลายเท่า
     * ผลต่างต้องคำนวณบนชนิดนี้ ไม่งั้น overflow แล้วกลายเป็น "นับไม่ตรง" ทั้งที่ตรง
     */
    public BigInteger trueCount(Location loc, Inventory inv, ItemStack proto) {
        BigInteger reported = storageGuard.reportedCount(loc, proto);
        return reported != null
                ? reported
                : BigInteger.valueOf(InventoryOps.countMatching(inv, proto));
    }

    /**
     * ปลายทางนี้รับไอเทมชนิดนี้ได้ไหม — กล่องธรรมดาตอบ true เสมอ
     *
     * <p>สองเคสที่ตอบ false:
     * <ol>
     *   <li><b>กล่อง shulker ที่จะโดนยัด shulker เข้าไปอีกใบ</b> — vanilla ห้ามไว้ผ่าน
     *       {@code canPlaceItem} แต่ {@link Inventory#addItem} <b>ไม่ได้เช็คกฎนั้นเลย</b>
     *       ท่อจึงซ้อน shulker ในตัวเองได้ทั้งที่ hopper ในเกมทำไม่ได้ = ช่องโหว่ที่ผู้เล่นใช้
     *       ทำ NBT ซ้อนกันจนบวมเพื่อถ่วง/ทำให้ไคลเอนต์ค้างได้ จึงกันไว้ตรงนี้
     *       (เทียบด้วยชื่อ Material ลงท้าย SHULKER_BOX เพื่อให้ครอบคลุมทั้ง 17 สีทุกเวอร์ชัน)</li>
     *   <li>กล่องที่รับได้ชนิดเดียว (storage unit) ซึ่งจะ<b>โยนของทิ้งลงพื้น</b>ถ้าใส่ผิดชนิด
     *       โดยที่ {@code addItem} ยังบอกว่ารับครบ ถ้าไม่ถามก่อน ด่านนับของฝั่งปลายทางจะขึ้นบัญชีดำ
     *       กล่องที่ทำงานถูกต้อง ดู {@link ExternalStorageGuard#rejectsType}</li>
     * </ol>
     */
    public boolean acceptsType(Location loc, Inventory inv, ItemStack proto) {
        if (inv != null && inv.getType() == InventoryType.SHULKER_BOX && isShulkerBox(proto)) {
            return false;
        }
        return !storageGuard.rejectsType(loc, proto);
    }

    /**
     * ปลายทางที่เป็นบล็อกชนิดนี้ รับไอเทมชนิดนี้ได้ไหม — ตัดสินจาก <b>Material อย่างเดียว</b>
     *
     * <p>มีคู่กับ {@link #acceptsType} เพราะเวอร์ชันนั้นต้องมี {@code Inventory} ของปลายทาง ซึ่งบน
     * Folia อ่านได้เฉพาะบน region ของมัน = รู้ตอนดูดของไม่ได้ ผลคือถ้ามี shulker หลงอยู่ในกล่อง
     * ต้นทางของท่อที่ปลายทางเป็น shulker ท่อจะเลือกชนิดนั้นทุกรอบ ดูดออกมาแล้วยัดไม่ลง คืนกลับ
     * วนแบบนี้ตลอดไป = ของชนิดอื่นไม่มีวันได้ไป (ผู้เล่นโยน shulker ใบเดียวลงกล่องก็ล็อกท่อคนอื่นได้)
     * Material ของกล่องปลายทางถูกอ่านไว้ตั้งแต่ตอนค้นท่อแล้ว จึงใช้คัดชนิดตั้งแต่ต้นทางได้เลย
     */
    public static boolean canHold(Material destBlock, ItemStack proto) {
        return !(isShulkerBox(destBlock) && isShulkerBox(proto));
    }

    static boolean isShulkerBox(ItemStack item) {
        return item != null && isShulkerBox(item.getType());
    }

    static boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    /** ท่อใช้บล็อกนี้เป็นต้นทาง/ปลายทางได้ไหม */
    public boolean isUsable(Block block) {
        return inventoryOf(block) != null;
    }

    /**
     * ขึ้นบัญชีดำกล่องที่ตำแหน่งนี้ถาวร (จนกว่าจะรีสตาร์ต/reload)
     *
     * @return true ถ้าเพิ่งถูกขึ้นบัญชีดำครั้งแรก — ใช้เป็นตัวกันการเตือนซ้ำแบบ atomic
     *         (ท่อสองเส้นที่ใช้กล่องเดียวกันคนละ region ก็จะเตือนแค่ครั้งเดียว)
     */
    public boolean distrust(Location loc) {
        return loc != null && distrusted.add(key(loc));
    }

    /** true = กล่องนี้ถูกขึ้นบัญชีดำแล้ว */
    public boolean isDistrusted(Location loc) {
        return loc != null && distrusted.contains(key(loc));
    }

    private static String key(Location loc) {
        String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        return world + ':' + loc.getBlockX() + ',' + loc.getBlockY() + ',' + loc.getBlockZ();
    }
}
