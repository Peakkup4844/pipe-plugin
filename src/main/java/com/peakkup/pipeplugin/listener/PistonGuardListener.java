package com.peakkup.pipeplugin.listener;

import com.peakkup.pipeplugin.NetworkDiscovery;
import com.peakkup.pipeplugin.NetworkRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.List;

/**
 * กันไม่ให้ piston ที่เป็นส่วนของท่อ ขยับจริง (piston ในท่อเป็นแค่ marker/trigger)
 *
 * เดิมกันเฉพาะ piston ที่อยู่ใน cache (tracked) — แต่มีช่องโหว่ตอน cache ว่าง:
 * ก่อน discover ครั้งแรก และ "ทุกครั้งหลัง invalidate" (วาง/ทุบบล็อกใกล้ท่อ) จนกว่าจะ pulse ใหม่
 * ในช่วงนั้น block-update (เช่นวางกระจกติดท่อ) อาจกระตุ้น piston ที่ค้าง quasi-power ให้ยืดจริง
 * จนดันกระจก/กล่องแตก ท่อพัง
 *
 * จึงกันแบบ "เชิงโครงสร้าง": piston (sticky/ปกติ) ที่หันหัวเข้า container และมีกระจกท่อติดข้าง
 * = รูปร่างของ pipe piston ให้กันไว้เสมอ แม้ยังไม่ถูก cache
 * และกันการที่ piston "ภายนอก" จะดันบล็อกของท่อจนแตกด้วย
 */
public final class PistonGuardListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final NetworkRegistry registry;

    public PistonGuardListener(NetworkRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (shouldGuard(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (shouldGuard(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * ควรยกเลิกการขยับ piston ไหม:
     *  1) piston ตัวนี้เป็นส่วนของท่อที่ cache ไว้ (tracked) หรือ
     *  2) piston ตัวนี้ "มีรูปร่างเป็น pipe piston" แม้ยังไม่ถูก cache (ปิดช่องโหว่ช่วง cache ว่าง) หรือ
     *  3) บล็อกที่กำลังจะถูกดันมีบล็อกของท่อรวมอยู่ (กัน piston ภายนอกดันท่อแตก)
     */
    private boolean shouldGuard(Block piston, List<Block> moved) {
        if (registry.isTracked(piston.getLocation()) || isPipePiston(piston)) {
            return true;
        }
        for (Block b : moved) {
            if (registry.isTracked(b.getLocation())) {
                return true;
            }
        }
        return false;
    }

    /**
     * piston (sticky/ปกติ) ที่หันหัวเข้า container และมีกระจกท่อติดข้าง = โครงสร้างของ pipe piston
     *
     * <p>ลำดับการตรวจสำคัญ: เช็คกระจก (อ่านแค่ Material) <b>ก่อน</b> {@code getState()} เสมอ
     * เพราะเมธอดนี้ถูกเรียกกับ piston <b>ทุกตัวในเซิร์ฟ</b>ที่ขยับ ส่วน {@code getState()} ของกล่อง
     * ต้องถ่ายสำเนา tile entity ทั้งก้อน — ฟาร์ม piston ปกติจึงไม่ต้องจ่ายค่านั้นเลย
     */
    private boolean isPipePiston(Block block) {
        Material type = block.getType();
        if (type != Material.STICKY_PISTON && type != Material.PISTON) {
            return false;
        }
        boolean glassNearby = false;
        for (BlockFace f : FACES) {
            if (NetworkDiscovery.isPipeGlass(block.getRelative(f).getType())) {
                glassNearby = true;
                break;
            }
        }
        if (!glassNearby) {
            return false;
        }
        BlockFace facing = NetworkDiscovery.facingOf(block);
        if (facing == null) {
            return false;
        }
        BlockState front = block.getRelative(facing).getState();
        return front instanceof Container;
    }
}
