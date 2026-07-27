package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * อ่านและถือค่าจาก config.yml
 */
public final class PipeConfig {

    /**
     * container ที่ห้ามใช้เสมอ ไม่ว่า allowed-containers จะตั้งอะไร
     *
     * <p>ENDER_CHEST: inventory ของมันไม่ได้เป็นของบล็อก แต่เป็นของ "ผู้เล่น" (ทุกกล่องในโลก
     * ใช้ของใบเดียวกันต่อผู้เล่นหนึ่งคน) การอ่าน/เขียนผ่านบล็อกจึงไม่มีเจ้าของที่ชัดเจน
     * = ทั้งเสี่ยง dupe และเสี่ยงขโมยของคนอื่น ปัจจุบัน Bukkit API ไม่ได้ทำให้มันเป็น
     * {@link org.bukkit.block.Container} อยู่แล้ว แต่กันไว้เผื่อเซิร์ฟเวอร์รุ่นใหม่เปลี่ยนใจ
     */
    private static final Set<Material> NEVER_ALLOWED = EnumSet.of(Material.ENDER_CHEST);

    private final int itemsPerCycle;
    private final int maxPipeLength;
    private final int minPulseIntervalTicks;
    private final boolean effects;
    private final boolean skipPluginManagedChests;
    private final boolean callInventoryMoveEvent;
    private final MatchMode matchMode;
    /** null = อนุญาตทุก Container */
    private final Set<Material> allowedContainers;

    public PipeConfig(PipePlugin plugin, PipeLang lang) {
        FileConfiguration cfg = plugin.getConfig();
        Logger log = plugin.getLogger();

        this.itemsPerCycle = Math.max(1, cfg.getInt("items-per-cycle", 32));
        this.maxPipeLength = Math.max(1, cfg.getInt("max-pipe-length", 64));
        this.minPulseIntervalTicks = Math.max(0, cfg.getInt("min-pulse-interval-ticks", 2));
        this.effects = cfg.getBoolean("effects", true);
        this.skipPluginManagedChests = cfg.getBoolean("skip-plugin-managed-chests", true);
        this.callInventoryMoveEvent = cfg.getBoolean("call-inventory-move-event", true);
        this.matchMode = MatchMode.fromConfig(cfg.getString("filter-match", "SIMILAR"));

        this.allowedContainers = readAllowedContainers(cfg.get("allowed-containers", "all"), lang, log);

        // บอกเสมอว่า "ตกลงแล้วใช้ค่าอะไร" — ตัวเลือกนี้พังแบบเงียบได้ง่ายที่สุดในไฟล์นี้
        // (เขียนเป็น list ซ้ำกับ key เดิม / พิมพ์ชื่อ material ผิด) แล้วแอดมินจะนึกว่าตั้งไปแล้ว
        // ทั้งที่ท่อยังรับทุกกล่องอยู่ ดูบรรทัดนี้บรรทัดเดียวก็รู้ว่าตรงกับที่ตั้งใจไหม
        log.info(lang.msg("config.allowed-containers",
                "value", allowedContainers == null ? "all" : describe(allowedContainers)));
    }

    /**
     * แปลงค่า {@code allowed-containers} เป็นเซ็ตที่ใช้จริง (null = ทุก Container)
     *
     * <p>รับได้ทั้ง list และค่าเดี่ยว: {@code all} = ทุกอย่าง, ค่าเดี่ยวอื่น ๆ ถือว่าเป็นรายการ
     * คั่นด้วยจุลภาค เพราะ {@code allowed-containers: CHEST} คือสิ่งที่คนเขียนโดยธรรมชาติ
     * และเดิมมันถูกตีเป็น "อนุญาตทุกอย่าง" เงียบ ๆ ซึ่งตรงข้ามกับที่ตั้งใจ
     *
     * <p>ถ้าเขียน list ไว้จริง จะยึดตาม list นั้นเสมอ แม้จะเหลือศูนย์ตัวหลังคัดชื่อผิดออก
     * ("ห้ามทุกกล่อง" ปลอดภัยกว่า "อนุญาตทุกกล่อง" เมื่อ config อ่านไม่ออก)
     */
    private static Set<Material> readAllowedContainers(Object raw, PipeLang lang, Logger log) {
        List<?> entries;
        if (raw instanceof List<?> list) {
            entries = list;
        } else {
            String text = String.valueOf(raw).trim();
            if (text.isEmpty() || text.equalsIgnoreCase("all")) {
                return null;
            }
            entries = List.of(text.split("[,\\s]+"));
        }

        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Object o : entries) {
            String name = String.valueOf(o).trim();
            if (name.isEmpty()) {
                continue;
            }
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                log.warning(lang.msg("config.unknown-container", "material", name));
            } else {
                set.add(m);
            }
        }
        return set;
    }

    private static String describe(Set<Material> materials) {
        StringBuilder sb = new StringBuilder();
        for (Material m : materials) {
            sb.append(sb.length() == 0 ? "" : ", ").append(m.name());
        }
        return sb.length() == 0 ? "none (no container can be used)" : sb.toString();
    }

    public int itemsPerCycle() {
        return itemsPerCycle;
    }

    public int maxPipeLength() {
        return maxPipeLength;
    }

    /** ช่วงเวลาขั้นต่ำระหว่าง 2 รอบของท่อเดียวกัน (มิลลิวินาที); 0 = ไม่จำกัด */
    public long minPulseIntervalMillis() {
        return minPulseIntervalTicks * 50L;
    }

    public boolean effectsEnabled() {
        return effects;
    }

    /** ข้ามกล่องที่ปลั๊กอินอื่นจัดการของเอง (เช่น WildChests) — true = ปลอดภัยไว้ก่อน กันของหาย */
    public boolean skipPluginManagedChests() {
        return skipPluginManagedChests;
    }

    /**
     * ยิง {@code InventoryMoveItemEvent} ทุกครั้งที่ท่อยัดของเข้าปลายทางไหม
     * true = ปลั๊กอินป้องกันพื้นที่/บันทึกประวัติเห็นและยับยั้งการขนของของท่อได้ (ค่าเริ่มต้น)
     */
    public boolean callInventoryMoveEvent() {
        return callInventoryMoveEvent;
    }

    /** โหมดเทียบไอเทมของ <b>ประตู item frame เท่านั้น</b> ไม่เกี่ยวกับการจัดกลุ่ม/คืนของ */
    public MatchMode matchMode() {
        return matchMode;
    }

    public boolean isContainerAllowed(Material material) {
        if (NEVER_ALLOWED.contains(material)) {
            return false;
        }
        return allowedContainers == null || allowedContainers.contains(material);
    }
}
