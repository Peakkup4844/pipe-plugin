package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ดัชนีของ item frame ที่อยู่บนท่อ — สแกน entity "ครั้งเดียวต่อ 1 pulse" แล้วนำกลับมาใช้ซ้ำ
 * ทุกชนิดของไอเทม (เดิม FilterGate สแกน getNearbyEntities ใหม่ทุก edge ทุกชนิด = ช้ามาก)
 *
 * frame ทำหน้าที่เป็น "ประตูบนรอยต่อ": frame ที่กินพื้นที่บล็อก X และเกาะ (attach) ไปทาง Y
 * จะคุมรอยต่อ X<->Y — ของจะข้ามได้ก็ต่อเมื่อ match กับ frame อย่างน้อยหนึ่งอันบนรอยต่อนั้น
 *
 * หมายเหตุ Folia: build() เรียก getNearbyEntities ต้องอยู่บนเธรดของ region ที่เป็นเจ้าของพื้นที่
 * จึงควรเรียกบน region ของ input และให้ท่อหนึ่งเครือข่ายอยู่ใน region เดียว
 */
public final class FrameIndex {

    /** frame หนึ่งอัน: ทิศที่เกาะ + ไอเทมในเฟรม */
    private static final class Entry {
        final BlockFace attachedFace;
        final ItemStack item;

        Entry(BlockFace attachedFace, ItemStack item) {
            this.attachedFace = attachedFace;
            this.item = item;
        }
    }

    /** block location -> frame ที่กินพื้นที่บล็อกนั้น */
    private final Map<Location, List<Entry>> byBlock;

    private FrameIndex(Map<Location, List<Entry>> byBlock) {
        this.byBlock = byBlock;
    }

    /** ดัชนีว่าง (ไม่มี frame เลย -> passes() คืน true เสมอ) — ใช้เมื่อไม่ต้องการ gate / ในเทสต์ */
    public static FrameIndex empty() {
        return new FrameIndex(new HashMap<>());
    }

    /** สแกนเฟรมรอบ ๆ ทุกบล็อกที่อาจมี gate (กระจกทั้งหมด + input piston + output piston ทุกตัว) */
    public static FrameIndex build(PipeNetwork net) {
        Map<Location, List<Entry>> map = new HashMap<>();
        World world = net.inputPiston().getWorld();
        if (world == null) {
            return new FrameIndex(map); // world ไม่โหลด -> ไม่มี frame -> ผ่านหมด
        }

        Set<Location> blocks = new HashSet<>(net.pipeBlocks());
        blocks.add(net.inputPiston());
        for (PipeOutput out : net.outputs()) {
            blocks.add(out.piston());
        }

        for (Location loc : blocks) {
            Block b = loc.getBlock();
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            for (Entity entity : world.getNearbyEntities(center, 0.75, 0.75, 0.75)) {
                if (!(entity instanceof ItemFrame frame)) {
                    continue;
                }
                if (!frame.getLocation().getBlock().equals(b)) {
                    continue;
                }
                ItemStack item = frame.getItem();
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                map.computeIfAbsent(loc, k -> new ArrayList<>())
                        .add(new Entry(frame.getAttachedFace(), item));
            }
        }
        return new FrameIndex(map);
    }

    /**
     * ของ item ข้ามรอยต่อ a<->b ได้ไหม
     * ไม่มี frame บนรอยต่อ = ผ่าน; มี frame = ต้อง match อย่างน้อยหนึ่งอัน
     */
    public boolean passes(Block a, Block b, ItemStack item, MatchMode mode) {
        List<ItemStack> filters = new ArrayList<>();
        gather(a, b, filters);
        gather(b, a, filters);
        if (filters.isEmpty()) {
            return true;
        }
        for (ItemStack filter : filters) {
            if (mode.matches(filter, item)) {
                return true;
            }
        }
        return false;
    }

    /** เก็บ frame ที่กินพื้นที่ frameBlock และเกาะไปทาง support (= คุมรอยต่อ frameBlock<->support) */
    private void gather(Block frameBlock, Block support, List<ItemStack> out) {
        List<Entry> entries = byBlock.get(frameBlock.getLocation());
        if (entries == null) {
            return;
        }
        for (Entry e : entries) {
            if (frameBlock.getRelative(e.attachedFace).equals(support)) {
                out.add(e.item);
            }
        }
    }
}
