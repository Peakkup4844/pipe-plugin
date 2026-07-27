package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * กันไม่ให้ท่อไปยุ่งกับ "กล่องที่ปลั๊กอินอื่นถือของจริงไว้เอง" ซึ่งของไม่ได้อยู่ใน tile entity ของบล็อก
 *
 * <p>ปัญหา: ปลั๊กอินอย่าง <b>WildChests</b> วางบล็อกเป็น CHEST ปกติ (ดังนั้น
 * {@code getState() instanceof Container} เป็นจริง) แต่ของจริงเก็บอยู่ในโครงสร้างของปลั๊กอินเอง
 * (paged inventory / storage unit ที่นับจำนวนเป็น BigInteger) ไม่ใช่ใน vanilla inventory ของบล็อก
 * ถ้าเราอ่าน/เขียน vanilla inventory ตรง ๆ:
 * <ul>
 *   <li>เป็นต้นทาง  &rarr; อ่านได้ของว่าง (ไม่เสียหาย แค่ท่อไม่ทำงาน) <b>หรือแย่กว่านั้น</b>
 *       ถ้าปลั๊กอินวาง "ไอเทมสำหรับโชว์" ไว้ในช่อง การดูดออกจะกลายเป็นเสกของขึ้นมา = dupe</li>
 *   <li>เป็นปลายทาง &rarr; ของถูกเขียนลง vanilla inventory ที่ปลั๊กอินนั้นไม่ได้ใช้/ไม่แสดง
 *       พอทุบกล่อง ปลั๊กอินจะคืนเฉพาะของของตัวเอง &rArr; <b>ของผู้เล่นหาย</b></li>
 * </ul>
 *
 * <p>ทั้งสองเคสละเมิด invariant "ของต้องไม่หาย/ไม่เพิ่ม" จึง default = ข้ามกล่องพวกนี้ไปเลย
 * (ท่อไม่ทำงานกับกล่องนั้น ปลอดภัยกว่ากินของหาย) ปิดได้ด้วย {@code skip-plugin-managed-chests: false}
 *
 * <p>คลาสนี้คือ<b>สองชั้นแรก</b>ของการป้องกัน เพราะชั้นแรกพึ่ง API ของคนอื่นซึ่งเปลี่ยนได้:
 * <ol>
 *   <li>{@link #isPluginManaged(Location)} — ถาม API ของ WildChests ตรง ๆ (แม่นสุด รู้จักทั้ง
 *       chest ปกติ / linked / storage unit) โดย<b>ยกเว้น storage unit</b> ที่อ่านจำนวนจริงได้
 *       เพราะกล่องพวกนั้นตรวจการอนุรักษ์ของได้เป๊ะแล้ว ไม่ต้องปฏิเสธทิ้ง</li>
 *   <li>{@link #isForeignInventory(Inventory)} — ตาข่ายกันพลาดแบบ<b>ไม่ผูกกับปลั๊กอินใดเลย</b>:
 *       ถ้า {@code Inventory} ที่ได้จากบล็อกไม่ใช่คลาสของเซิร์ฟเวอร์เอง แปลว่ามีปลั๊กอินสวมของตัวเอง
 *       เข้ามา &rarr; ไม่แตะ ครอบคลุมปลั๊กอิน storage ทุกตัวโดยไม่ต้องรู้จักชื่อมัน</li>
 * </ol>
 *
 * <p>ชั้นที่ 3 อยู่นอกคลาสนี้ (ดู {@link ItemTransferService} + {@link ContainerAccess#distrust}):
 * นับของก่อน/หลังจริงทุกรอบ ถ้ากล่องไหนนับไม่ตรงจะถูกขึ้นบัญชีดำถาวรทันที — จำเป็นเพราะ
 * storage unit ของ WildChests ใช้บล็อก CHEST + inventory ของ<b>เซิร์ฟเวอร์เอง</b>เป็น "ไอเทมโชว์"
 * ทั้งสองชั้นบนจึงมองไม่เห็นถ้า API ตอบว่าไม่รู้จักกล่องใบนั้น
 *
 * <p><b>{@link #reportedCount} คือส่วนที่ทำให้ชั้นที่ 3 ใช้กับ storage unit ได้จริง</b>
 * (ไม่ใช่แค่ปฏิเสธมันทิ้ง): storage unit เก็บจำนวนเป็น {@link BigInteger} ก้อนเดียว แล้ววาด
 * "สต๊าคโชว์" ลงช่องเดียวเสมอ — ดูดออก 32 ชิ้นแล้ว inventory ก็ยังนับได้เท่าเดิมเป๊ะ ทั้งที่ของ
 * หายไปจริง 32 การนับจาก inventory จึงตอบผิดเสมอ ต้องถามเจ้าของกล่องเอาตัวเลขจริงมาแทน
 * ตรงนี้ทำงาน<b>ตลอดเวลา</b> ไม่ขึ้นกับ {@code skip-plugin-managed-chests} เพราะเป็นเรื่อง
 * "นับให้ถูก" ไม่ใช่นโยบาย "แตะได้/ไม่ได้"
 *
 * <p>ตรวจด้วย reflection ล้วน (ไม่มี compile dependency ใด ๆ) จึงไม่กระทบกฎ "Bukkit API เท่านั้น"
 * และถ้า reflection พังด้วยเหตุใดก็ตาม ชั้นที่ 2 ยังทำงานอยู่
 *
 * <p>ไม่ {@code final} เพื่อให้เทสต์ override {@link #reportedCount} จำลองกล่องของปลั๊กอินอื่นได้
 * โดยไม่ต้องมี server จริง
 */
public class ExternalStorageGuard {

    /**
     * แพ็กเกจของคลาส Inventory ที่ถือว่า "เซิร์ฟเวอร์เป็นเจ้าของ" (vanilla)
     * chest/barrel/hopper ปกติจะได้ {@code org.bukkit.craftbukkit...CraftInventory*} เสมอ
     * (double chest = {@code CraftInventoryDoubleChest}) ทั้งบน Spigot, Paper และ Folia
     */
    private static final String[] SERVER_INVENTORY_PACKAGES = {
            "org.bukkit.craftbukkit.",
            "io.papermc.paper."
    };

    /** เมธอด lookup หนึ่งตัวที่ resolve ได้แล้ว; {@code target == null} = เรียกแบบ static */
    private static final class Lookup {
        final Object target;
        final Method method;

        Lookup(Object target, Method method) {
            this.target = target;
            this.method = method;
        }
    }

    private final JavaPlugin plugin;
    private final PipeLang lang;
    private final Logger log;
    private final boolean enabled;

    private volatile Lookup[] wildChests;
    /** true = ลอง resolve ไปแล้ว (ไม่ว่าสำเร็จหรือไม่) จะไม่ลองซ้ำอีก */
    private volatile boolean resolveAttempted;

    /** interface {@code StorageChest} + เมธอดอ่านจำนวนจริงของมัน (null = เวอร์ชันนี้ไม่มี) */
    private volatile Class<?> storageChestType;
    private volatile Method storageChestItem;   // StorageChest#getItemStack()
    private volatile Method storageChestAmount; // StorageChest#getAmount() -> BigInteger

    /** ชื่อคลาส inventory แปลกปลอมที่เตือนไปแล้ว — เตือนคลาสละครั้ง ไม่สแปม console */
    private final Set<String> reportedForeign = ConcurrentHashMap.newKeySet();

    public ExternalStorageGuard(JavaPlugin plugin, PipeConfig config, PipeLang lang) {
        this.plugin = plugin;
        this.lang = lang;
        this.log = plugin.getLogger();
        this.enabled = config.skipPluginManagedChests();
        // hook ตั้งแต่ตอน enable เพื่อให้แอดมินเห็น log ทันที (plugin.yml softdepend การันตีว่า
        // WildChests ถูก enable ก่อนเราแล้ว) ถ้ายังไม่เจอจะลองใหม่ตอนใช้งานจริง
        //
        // ทำเสมอแม้ config จะปิดอยู่: ปิดสวิตช์ = "ยอมให้ท่อแตะกล่องพวกนี้" ไม่ใช่ "ห้ามถามเจ้าของกล่อง"
        // ตอนนั้นแหละที่ reportedCount จำเป็นที่สุด เพราะเป็นทางเดียวที่จะนับของใน storage unit ได้ถูก
        resolveWildChests();
    }

    /** ปิดการตรวจทั้งหมด (ใช้เมื่อ config ปิด / ในเทสต์) */
    public static ExternalStorageGuard disabled() {
        return new ExternalStorageGuard(false);
    }

    /** สร้างแบบไม่มี plugin จริง — ใช้ในเทสต์เพื่อทดสอบตาข่ายชั้นที่ 2 ล้วน ๆ */
    ExternalStorageGuard(boolean enabled) {
        this.plugin = null;
        this.lang = null;
        this.log = null;
        this.enabled = enabled;
        this.resolveAttempted = true;
    }

    // ------------------------------------------------------------------
    // ชั้นที่ 1: ถาม API ของ WildChests
    // ------------------------------------------------------------------

    /**
     * บล็อกที่ตำแหน่งนี้เป็นกล่องที่ปลั๊กอินอื่นจัดการของเองหรือไม่
     * true = ห้ามใช้เป็นต้นทาง/ปลายทาง (ของจริงไม่ได้อยู่ใน vanilla inventory)
     */
    public boolean isPluginManaged(Location loc) {
        if (!enabled || loc == null) {
            return false;
        }
        Lookup[] hooks = hooks();
        if (hooks == null) {
            return false;
        }
        boolean claimed = false;
        for (Lookup lookup : hooks) {
            try {
                Object chest = lookup.method.invoke(lookup.target, loc);
                if (chest == null) {
                    continue;
                }
                // ยกเว้น storage unit: เราอ่าน "จำนวนจริง" ของมันได้ (ดู reportedCount) จึงตรวจ
                // การอนุรักษ์ของได้เป๊ะทั้งขาเข้าและขาออก ปลอดภัยเท่ากล่องธรรมดา -> ให้ท่อใช้ได้
                // ของที่ใส่เข้าไปจะกลายเป็นจำนวนของ storage unit เอง ไม่ใช่ค้างใน vanilla inventory
                // จึงไม่หายตอนทุบกล่อง ซึ่งเป็นเหตุผลเดียวที่เราบล็อกกล่องพวกนี้ตั้งแต่แรก
                if (measurable(chest)) {
                    return false;
                }
                claimed = true; // chest ปกติ/linked: ของจริงอยู่ในหน้า page ของเขา -> ห้ามแตะ
            } catch (Throwable ignored) {
                // reflection พัง -> ปล่อยให้ตาข่ายชั้นที่ 2 (isForeignInventory) จัดการต่อ
            }
        }
        return claimed;
    }

    /** true = กล่องนี้เป็น storage unit ที่เราถามจำนวนจริงได้ */
    private boolean measurable(Object chest) {
        return storageUnitsReadable() && storageChestType.isInstance(chest);
    }

    /**
     * จำนวน<b>ของจริง</b>ของไอเทมชนิด proto ที่ตำแหน่งนี้ ตามที่ปลั๊กอินเจ้าของกล่องรายงานเอง
     *
     * <p>ตอนนี้รองรับ <b>storage unit ของ WildChests</b> ซึ่งเป็นเคสที่นับจาก inventory ไม่ได้เลย:
     * ของจริงคือ {@code StorageChest#getAmount()} (BigInteger ก้อนเดียว) ส่วนในช่อง inventory เป็น
     * แค่สต๊าคที่วาดโชว์เท่ากับ {@code min(amount, maxStackSize)} — เขียนทับสต๊าคนั้นด้วยจำนวนที่น้อยลง
     * ปลั๊กอินจะ<b>หักของจริงตามส่วนต่าง แล้ววาดสต๊าคเดิมกลับมาทันที</b> การนับซ้ำจึงได้เท่าเดิมเสมอ
     * ทั้งที่ของหายไปจริงแล้ว (นี่คือสาเหตุที่ท่อเคยกลืนของหายทุก pulse)
     *
     * <p>กล่องชนิดอื่นของ WildChests (chest ปกติ / linked) เก็บของใน Bukkit inventory จริง
     * จึงคืน null ให้ผู้เรียกไปนับจาก inventory เองตามปกติ
     *
     * <p><b>ไม่</b>ขึ้นกับ {@code skip-plugin-managed-chests} — สวิตช์นั้นคุมแค่ว่าจะ "ปฏิเสธกล่อง"
     * ไหม ส่วนนี่คือความถูกต้องของการนับ ซึ่งต้องถูกเสมอไม่ว่าตั้งค่าไว้อย่างไร
     *
     * @return จำนวนจริง หรือ null ถ้าไม่มีใครรายงานได้ (ผู้เรียกต้องนับจาก inventory เอง)
     */
    public BigInteger reportedCount(Location loc, ItemStack proto) {
        Lookup[] hooks = hooks();
        if (loc == null || proto == null || hooks == null
                || storageChestType == null || storageChestItem == null || storageChestAmount == null) {
            return null;
        }
        for (Lookup lookup : hooks) {
            try {
                Object chest = lookup.method.invoke(lookup.target, loc);
                if (chest == null || !storageChestType.isInstance(chest)) {
                    continue; // ไม่ใช่ storage unit -> ตัวนี้ไม่มีตัวเลขจริงให้; ลองตัวถัดไป
                }
                Object stored = storageChestItem.invoke(chest);
                if (!(stored instanceof ItemStack item) || item.getType() == Material.AIR) {
                    return BigInteger.ZERO; // storage unit ว่าง
                }
                if (!proto.isSimilar(item)) {
                    return BigInteger.ZERO; // เก็บของคนละชนิด -> ชนิดที่ถามมีอยู่ 0 ชิ้น
                }
                Object amount = storageChestAmount.invoke(chest);
                if (amount instanceof BigInteger exact) {
                    return exact;
                }
                return null; // API เปลี่ยนชนิดค่าที่คืน -> ถือว่าไม่รู้ ดีกว่าเดาผิด
            } catch (Throwable ignored) {
                // ตัวนี้ตอบไม่ได้ -> ลองตัวถัดไป; ถ้าไม่มีใครตอบได้เลยจะคืน null
            }
        }
        return null;
    }

    /**
     * true = ตำแหน่งนี้เป็น storage unit ที่<b>เก็บของคนละชนิด</b>กับ proto จึงรับของชนิดนี้ไม่ได้
     *
     * <p>storage unit เก็บได้ชนิดเดียวเท่านั้น ถ้ายัดชนิดอื่นเข้าไป WildChests จะ
     * <b>โยนของทิ้งลงพื้น</b>ทันที ({@code canPlaceItemThroughFace} เป็น false) แต่
     * {@code Inventory#addItem} ยังรายงานว่ารับไปครบ → ด่านฝั่งปลายทางจะเห็นว่ากล่องไม่ได้ของเพิ่ม
     * แล้วขึ้นบัญชีดำกล่องที่จริง ๆ ทำงานถูกต้อง (แถมของ 1 รอบไปกองอยู่บนพื้น)
     * จึงต้องรู้ล่วงหน้าแล้วข้ามไป output ตัวถัดไป — ของจะไหลไปที่อื่นหรือคืนต้นทางตามปกติ
     *
     * <p>กล่องว่างไม่ถือว่าปฏิเสธ (รับชนิดแรกที่ใส่เข้าไป) และกล่องที่ไม่ใช่ storage unit
     * ก็ไม่ปฏิเสธเช่นกัน — เมื่อไม่รู้ต้องตอบ false เสมอ ไม่งั้นจะไปปิดกั้นกล่องธรรมดา
     */
    public boolean rejectsType(Location loc, ItemStack proto) {
        Lookup[] hooks = hooks();
        if (loc == null || proto == null || hooks == null) {
            return false;
        }
        for (Lookup lookup : hooks) {
            try {
                Object chest = lookup.method.invoke(lookup.target, loc);
                if (chest == null || !measurable(chest)) {
                    continue;
                }
                Object stored = storageChestItem.invoke(chest);
                if (!(stored instanceof ItemStack item) || item.getType() == Material.AIR) {
                    return false; // ว่าง -> รับชนิดไหนก็ได้
                }
                return !proto.isSimilar(item);
            } catch (Throwable ignored) {
                // ถามไม่ได้ -> ถือว่าไม่ปฏิเสธ (ด่านนับของฝั่งปลายทางยังคุมอยู่)
            }
        }
        return false;
    }

    /**
     * lookup ที่ resolve ไว้ (resolve ให้ครั้งแรกที่ถูกเรียก) หรือ null ถ้าไม่มี WildChests
     *
     * <p>เมธอดนี้ถูกเรียก<b>ทุกครั้งที่ท่อแตะกล่อง</b> จึงต้องถูกที่สุดในเคสปกติ (ไม่มี WildChests ลงอยู่)
     * เดิมมันตกไปเรียก {@link #resolveWildChests()} ซึ่งเป็น {@code synchronized} ทุกครั้ง — บน Folia
     * แปลว่าทุก region thread มาแย่งล็อกตัวเดียวกันเพื่อได้คำตอบว่า "ไม่มี" เหมือนเดิมทุกรอบ
     * ตอนนี้เช็คด้วยการถาม PluginManager เฉย ๆ (HashMap lookup, ไม่มีล็อก) ก่อน แล้วค่อยเข้าไป
     * resolve จริงเมื่อปลั๊กอินโผล่มาแล้วเท่านั้น จึงยังรองรับการโหลดปลั๊กอินทีหลังได้เหมือนเดิม
     */
    private Lookup[] hooks() {
        Lookup[] hooks = wildChests;
        if (hooks != null || resolveAttempted || plugin == null) {
            return hooks;
        }
        if (plugin.getServer().getPluginManager().getPlugin("WildChests") == null) {
            return null;
        }
        return resolveWildChests();
    }

    /**
     * resolve API ของ WildChests ครั้งเดียว
     *
     * <p>เก็บ<b>ทั้งสองรูปแบบ</b>ที่หาเจอ ไม่ใช่หยุดที่อันแรก เพราะ API เขาย้ายที่มาแล้วครั้งหนึ่ง
     * และเราไม่มีทางรู้ว่ารูปแบบไหนตอบครบทุกชนิดกล่อง (chest ปกติ / linked / storage unit):
     * <ol>
     *   <li>static {@code WildChestsAPI.getChest/getLinkedChest/getStorageChest(Location)}
     *       (รูปแบบปัจจุบัน — ยืนยันจาก WildChests 2026.2)</li>
     *   <li>instance {@code WildChestsAPI.getInstance().getChestsManager().getChest(...)}</li>
     * </ol>
     * ถามทุกตัวที่ resolve ได้ ตัวใดตอบว่า "ใช่" ก็ถือว่าใช่ (ปลอดภัยไว้ก่อน)
     *
     * <p>โหลดคลาสผ่าน classloader <b>ของ WildChests เอง</b> ไม่ใช่ของเรา เพราะเซิร์ฟเวอร์รุ่นใหม่
     * แยก classloader ระหว่างปลั๊กอิน — {@code Class.forName} เฉย ๆ อาจมองไม่เห็น
     */
    private synchronized Lookup[] resolveWildChests() {
        if (wildChests != null || resolveAttempted) {
            return wildChests;
        }
        Plugin wildChestsPlugin = plugin.getServer().getPluginManager().getPlugin("WildChests");
        if (wildChestsPlugin == null) {
            return null; // ยังไม่โหลด -> ลองใหม่ตอนใช้งานจริง
        }
        resolveAttempted = true;

        ClassLoader loader = wildChestsPlugin.getClass().getClassLoader();
        List<Lookup> found = new ArrayList<>();

        Class<?> api = null;
        try {
            api = Class.forName("com.bgsoftware.wildchests.api.WildChestsAPI", false, loader);
            for (Method m : chestLookups(api, true)) {
                found.add(new Lookup(null, m));
            }
        } catch (Throwable ignored) {
            // ไม่มีคลาส API เลย -> ตกไปที่ตาข่ายชั้นที่ 2
        }

        if (api != null) {
            try {
                Class<?> apiInterface =
                        Class.forName("com.bgsoftware.wildchests.api.WildChests", false, loader);
                Class<?> managerInterface =
                        Class.forName("com.bgsoftware.wildchests.api.handlers.ChestsManager", false, loader);
                Object instance = api.getMethod("getInstance").invoke(null);
                Object manager = apiInterface.getMethod("getChestsManager").invoke(instance);
                if (manager != null) {
                    for (Method m : chestLookups(managerInterface, false)) {
                        found.add(new Lookup(manager, m));
                    }
                }
            } catch (Throwable ignored) {
                // เวอร์ชันนี้ไม่มีทางเข้าแบบ instance -> ใช้เฉพาะ static ที่หาเจอ
            }
        }

        resolveStorageChest(loader);

        wildChests = found.isEmpty() ? null : found.toArray(new Lookup[0]);
        // บรรทัดนี้คือหน้าต่างเดียวที่แอดมินมองเห็นว่า hook ติดแค่ไหน จึงต้อง "บอกสิ่งที่ทำได้จริง"
        // ไม่ใช่สิ่งที่ตั้งใจจะทำ — การหา chest lookup เจอ กับการอ่านจำนวนจริงของ storage unit ได้
        // เป็นคนละเรื่องกัน (คนละคลาส คนละ resolve) ถ้าอันหลังพลาด storage unit จะถูก "ข้าม"
        // เหมือนกล่องอื่นของ WildChests ซึ่งปลอดภัยแต่ไม่ใช่สิ่งที่ข้อความเดิมบอก
        if (wildChests == null) {
            log.warning(lang.msg("external-storage.hook-failed", "plugin", "WildChests"));
        } else if (storageUnitsReadable()) {
            log.info(lang.msg("external-storage.hooked", "plugin", "WildChests"));
        } else {
            log.warning(lang.msg("external-storage.hooked-no-storage-units", "plugin", "WildChests"));
        }
        return wildChests;
    }

    /** true = resolve ทางอ่าน "จำนวนจริง" ของ storage unit ได้ครบ (ดู {@link #resolveStorageChest}) */
    private boolean storageUnitsReadable() {
        return storageChestType != null && storageChestItem != null && storageChestAmount != null;
    }

    /**
     * หา {@code StorageChest} + เมธอดอ่านจำนวนจริงของมัน (ยืนยันรูปแบบจาก WildChests 2026.2)
     *
     * <p>อ่านจาก <b>interface</b> ของ API ไม่ใช่คลาส implementation เพราะ interface คือสัญญาที่
     * เขาการันตี ส่วนคลาสจริง ({@code WStorageChest}) เปลี่ยนชื่อ/ย้ายที่ได้ทุกเวอร์ชัน
     * ถ้าเวอร์ชันไหนไม่มีคลาสนี้เลยก็ปล่อยเป็น null แล้วกลับไปนับจาก inventory ตามเดิม
     */
    private void resolveStorageChest(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.bgsoftware.wildchests.api.objects.chests.StorageChest", false, loader);
            Method item = type.getMethod("getItemStack");
            Method amount = type.getMethod("getAmount");
            if (!BigInteger.class.isAssignableFrom(amount.getReturnType())
                    || !ItemStack.class.isAssignableFrom(item.getReturnType())) {
                return; // ชนิดค่าที่คืนไม่ตรงกับที่เราเข้าใจ -> ไม่แตะ ดีกว่าเดา
            }
            storageChestType = type;
            storageChestItem = item;
            storageChestAmount = amount;
        } catch (Throwable ignored) {
            // เวอร์ชันนี้ไม่มี storage unit (หรือ API ย้ายที่) -> ตกกลับไปนับจาก inventory
        }
    }

    /** หา getChest/getLinkedChest/getStorageChest(Location) บนคลาสที่ให้มา (อาจได้ว่าง) */
    private static List<Method> chestLookups(Class<?> owner, boolean wantStatic) {
        String[] names = {"getChest", "getLinkedChest", "getStorageChest"};
        List<Method> found = new ArrayList<>(names.length);
        for (String name : names) {
            try {
                Method m = owner.getMethod(name, Location.class);
                if (Modifier.isStatic(m.getModifiers()) == wantStatic) {
                    found.add(m);
                }
            } catch (Throwable ignored) {
                // เมธอดนี้ไม่มี -> ข้าม (เวอร์ชันเก่าอาจไม่มี storage unit)
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // ชั้นที่ 2: inventory ไม่ใช่ของเซิร์ฟเวอร์ = ปลั๊กอินอื่นเป็นเจ้าของ
    // ------------------------------------------------------------------

    /**
     * true = {@code Inventory} นี้ถูกปลั๊กอินอื่นสวมของตัวเองเข้ามา จึงห้ามใช้
     *
     * <p>ไม่ผูกกับปลั๊กอินตัวใดเลย: container ของ vanilla จะได้คลาสจาก craftbukkit เสมอ
     * ถ้าได้อย่างอื่นแปลว่ามีคนแทน storage ไว้ ซึ่งเราไม่รู้ว่ามันเก็บของไว้ที่ไหนจริง ๆ
     */
    public boolean isForeignInventory(Inventory inv) {
        if (!enabled || inv == null) {
            return false;
        }
        String className = inv.getClass().getName();
        if (isServerInventoryClass(className)) {
            return false;
        }
        if (log != null && lang != null && reportedForeign.add(className)) {
            log.warning(lang.msg("external-storage.foreign-inventory", "class", className));
        }
        return true;
    }

    /** true = คลาส inventory นี้เป็นของเซิร์ฟเวอร์เอง (ไม่ใช่ของปลั๊กอิน) */
    public static boolean isServerInventoryClass(String className) {
        if (className == null) {
            return false;
        }
        for (String pkg : SERVER_INVENTORY_PACKAGES) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }
}
