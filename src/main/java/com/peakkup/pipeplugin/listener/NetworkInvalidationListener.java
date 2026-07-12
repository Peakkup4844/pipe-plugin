package com.peakkup.pipeplugin.listener;

import com.peakkup.pipeplugin.NetworkRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * ล้าง cache ของท่อเมื่อบล็อกที่เป็นส่วนของท่อถูกทำลาย/เปลี่ยน
 * ท่อจะถูกค้นหาใหม่อัตโนมัติในรอบ pulse ถัดไป
 */
public final class NetworkInvalidationListener implements Listener {

    private final NetworkRegistry registry;

    public NetworkInvalidationListener(NetworkRegistry registry) {
        this.registry = registry;
    }

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /** invalidate ทั้งบล็อกนี้และเพื่อนบ้าน 6 ทิศ (รองรับการต่อ/ถอดชิ้นส่วนข้างท่อ) */
    private void invalidateAround(Block block) {
        registry.invalidateByBlock(block.getLocation());
        for (BlockFace f : FACES) {
            registry.invalidateByBlock(block.getRelative(f).getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        invalidateAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        invalidateAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        registry.invalidateByBlock(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        registry.invalidateByBlock(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            registry.invalidateByBlock(block.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            registry.invalidateByBlock(block.getLocation());
        }
    }
}
