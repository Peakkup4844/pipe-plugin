package com.peakkup.pipeplugin;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ดัชนีของ item frame ที่ทำหน้าที่ "ประตู (gate) รายบล็อก" บนท่อ
 *
 * โมเดล = block gate: frame ที่แปะบนบล็อก B (กระจก / input piston / output piston)
 * ทำให้ไอเทมจะ "ผ่าน/เข้า" บล็อก B ได้ก็ต่อเมื่อ match กับ frame อย่างน้อยหนึ่งอันบน B
 *  - แปะบน input piston  = คุมการดูด (ดูดเฉพาะของที่ตรง)
 *  - แปะบนกระจก           = ของต้องตรงถึงจะผ่านกระจกก้อนนั้น (ปิดกั้นแขนง)
 *  - แปะบน output piston  = output นั้นรับเฉพาะของที่ตรง
 * ไม่มี frame บนบล็อก = ผ่านเสมอ
 *
 * Folia: frame แต่ละอันอยู่ใน chunk (และ region) ของมันเอง จึงสแกนแยกทีละ chunk
 * บน region ที่เป็นเจ้าของ chunk นั้น ({@link #buildAsync}) แล้ว chain รวมผลกลับมา
 * — การันตีว่าอ่าน frame ได้ถูกต้องแม้ท่อจะพาดข้าม region
 */
public final class FrameIndex {

    private static final Logger LOG = Logger.getLogger("PipePlugin");

    /** block location -> ไอเทมของ frame ทุกอันที่แปะ (gate) บล็อกนั้น */
    private final Map<Location, List<ItemStack>> gatesByBlock;

    private FrameIndex(Map<Location, List<ItemStack>> gatesByBlock) {
        this.gatesByBlock = gatesByBlock;
    }

    /** ดัชนีว่าง (ไม่มี frame เลย -> passes() คืน true เสมอ) — ใช้เมื่อไม่ต้องการ gate / ในเทสต์ */
    public static FrameIndex empty() {
        return new FrameIndex(new HashMap<>());
    }

    /** สร้าง index จาก gate map ตรง ๆ (block location -> ไอเทมของ frame บนบล็อกนั้น) — เทสต์เท่านั้น */
    static FrameIndex forTesting(Map<Location, List<ItemStack>> gatesByBlock) {
        return new FrameIndex(gatesByBlock);
    }

    /**
     * สแกน item frame ของทั้งท่อแบบ region-safe แล้วคืน {@link FrameIndex}
     *
     * รวบ chunk ที่อาจมี frame (chunk ของแต่ละ gate block และ chunk เพื่อนบ้านแนวนอน
     * เพราะ frame เกาะผิวนอก = อยู่ในบล็อกอากาศที่ติดกัน ซึ่งอาจข้าม chunk ที่ขอบ)
     * แล้ว schedule สแกนทีละ chunk บน region ของมัน ต่อกันด้วย CompletableFuture (ลำดับ = ปลอด race)
     * ความผิดพลาดของ chunk ใด chunk หนึ่งถูกกลืน (log) และถือว่า chunk นั้นไม่มี gate (fail-open)
     */
    public static CompletableFuture<FrameIndex> buildAsync(RegionExecutor regions, PipeNetwork net, PipeLang lang) {
        Map<Location, List<ItemStack>> gates = new HashMap<>();
        World world = net.inputPiston().getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(new FrameIndex(gates));
        }

        // รายการ gate block และ chunk ที่ต้องสแกนขึ้นกับโทโพโลยีล้วน ๆ -> ให้ PipeNetwork จำไว้
        // ท่อที่โทโพโลยีไม่เปลี่ยนจึงไม่ต้องประกอบเซ็ตพวกนี้ใหม่ทุก pulse
        Set<Location> gateBlocks = net.gateBlocks();
        int y0 = net.inputPiston().getBlockY();

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int[] c : net.frameChunks()) {
            final int cx = c[0];
            final int cz = c[1];
            Location at = new Location(world, (cx << 4) + 8, y0, (cz << 4) + 8);
            chain = chain.thenCompose(v -> regions
                    .at(at, () -> scanChunk(world, cx, cz, gateBlocks, gates))
                    .exceptionally(e -> {
                        // อ่าน frame ใน chunk นี้ไม่ได้ -> ข้าม (ของไม่หาย, แค่ filter chunk นี้ถูกละไว้)
                        LOG.log(Level.WARNING, lang.msg("transfer.frame-scan-failed"), e);
                        return null;
                    }));
        }
        return chain.thenApply(v -> new FrameIndex(gates));
    }

    /** สแกนทุก item frame ใน chunk (cx,cz) แล้ว map เข้า gate block ที่มันเกาะ (ต้องรันบน region ของ chunk นี้) */
    private static void scanChunk(World world, int cx, int cz,
                                  Set<Location> gateBlocks,
                                  Map<Location, List<ItemStack>> gates) {
        if (!world.isChunkLoaded(cx, cz)) {
            return; // chunk ไม่โหลด -> ไม่มี frame ให้อ่าน
        }
        Chunk chunk = world.getChunkAt(cx, cz);
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            // frame อยู่ในบล็อกอากาศ; บล็อกที่มันเกาะ (support) = air + ทิศที่เกาะ
            Block air = frame.getLocation().getBlock();
            Location supportLoc = air.getRelative(frame.getAttachedFace()).getLocation();
            if (!gateBlocks.contains(supportLoc)) {
                // เผื่อ server เก่าที่ getLocation คืนบล็อก support โดยตรง
                Location airLoc = air.getLocation();
                if (gateBlocks.contains(airLoc)) {
                    supportLoc = airLoc;
                } else {
                    continue;
                }
            }
            ItemStack item = frame.getItem();
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            gates.computeIfAbsent(supportLoc, k -> new ArrayList<>()).add(item);
        }
    }

    /**
     * ไอเทม item ผ่าน/เข้าบล็อก block ได้ไหม
     * ไม่มี frame บนบล็อก = ผ่าน; มี frame = ต้อง match อย่างน้อยหนึ่งอัน
     */
    public boolean passes(Block block, ItemStack item, MatchMode mode) {
        List<ItemStack> gates = gatesByBlock.get(block.getLocation());
        if (gates == null || gates.isEmpty()) {
            return true;
        }
        for (ItemStack filter : gates) {
            if (mode.matches(filter, item)) {
                return true;
            }
        }
        return false;
    }
}
