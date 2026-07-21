package com.peakkup.pipeplugin.listener;

import com.peakkup.pipeplugin.ItemTransferService;
import com.peakkup.pipeplugin.NetworkDiscovery;
import com.peakkup.pipeplugin.NetworkRegistry;
import com.peakkup.pipeplugin.PipeConfig;
import com.peakkup.pipeplugin.PipeNetwork;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ตรวจจับ rising-edge ของ redstone ที่จ่ายเข้า sticky piston -> สั่งย้ายของ 1 รอบ
 * ใช้ BlockRedstoneEvent (ไม่ใช่ piston event) เพราะ piston ที่หันเข้า container
 * จะดันไม่ได้และไม่ยิง extend event
 */
public final class RedstoneTriggerListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.SELF, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final FoliaLib foliaLib;
    private final NetworkRegistry registry;
    private final NetworkDiscovery discovery;
    private final ItemTransferService transferService;
    private final PipeConfig config;

    /** สถานะ powered ล่าสุดของ sticky piston แต่ละตัว เพื่อจับขอบขาขึ้น */
    private final Map<Location, Boolean> poweredState = new ConcurrentHashMap<>();
    /** เวลา (ms) ที่ท่อยิงรอบล่าสุด — ใช้บังคับ min-pulse-interval */
    private final Map<Location, Long> lastFire = new ConcurrentHashMap<>();

    public RedstoneTriggerListener(FoliaLib foliaLib,
                                   NetworkRegistry registry,
                                   NetworkDiscovery discovery,
                                   ItemTransferService transferService,
                                   PipeConfig config) {
        this.foliaLib = foliaLib;
        this.registry = registry;
        this.discovery = discovery;
        this.transferService = transferService;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        for (BlockFace face : NEIGHBOURS) {
            Block candidate = (face == BlockFace.SELF) ? block : block.getRelative(face);
            if (candidate.getType() != Material.STICKY_PISTON) {
                continue;
            }
            Location loc = candidate.getLocation();
            // ตัวกรองราคาถูก: ข้าม sticky piston ที่ "ไม่มีทางเป็นท่อ" (ไม่ใช่ท่อที่ cache ไว้
            // และไม่มีกระจกติดอยู่เลย) เพื่อไม่ schedule งานทุกครั้งที่ redstone ขยับใกล้
            // sticky piston ธรรมดา (เช่น ประตู/กลไก redstone ทั่วไป)
            if (registry.get(loc) == null && !hasAdjacentGlass(candidate)) {
                continue;
            }
            // เช็คกำลังไฟจริงบนเธรดของ region ที่เป็นเจ้าของ (tick ถัดไป, Folia-safe)
            foliaLib.getScheduler().runAtLocation(loc, t -> checkPiston(loc));
        }
    }

    /** มีกระจก (ชนิดที่ใช้ทำท่อ) ติด sticky piston นี้อยู่ไหม — เช็คเร็ว ไม่ต้องสร้าง BlockState */
    private static boolean hasAdjacentGlass(Block piston) {
        for (BlockFace f : NEIGHBOURS) {
            if (f == BlockFace.SELF) {
                continue;
            }
            if (NetworkDiscovery.isPipeGlass(piston.getRelative(f).getType())) {
                return true;
            }
        }
        return false;
    }

    private void checkPiston(Location loc) {
        Block piston = loc.getBlock();
        if (piston.getType() != Material.STICKY_PISTON) {
            poweredState.remove(loc);
            lastFire.remove(loc);
            return;
        }

        boolean powered = piston.isBlockIndirectlyPowered() || piston.isBlockPowered();
        Boolean prev = poweredState.put(loc, powered);
        boolean was = prev != null && prev;

        if (!powered || was) {
            return; // ไม่ใช่ขอบขาขึ้น
        }

        // rate limit: กัน clock เร็ว ๆ ยิงรัว (นับต่อท่อ ต่อ input piston)
        long interval = config.minPulseIntervalMillis();
        if (interval > 0) {
            long now = System.currentTimeMillis();
            Long last = lastFire.get(loc);
            if (last != null && now - last < interval) {
                return;
            }
            lastFire.put(loc, now);
        }

        // ขอบขาขึ้น -> หา/ตรวจท่อ แล้วย้ายของ 1 รอบ
        PipeNetwork net = registry.get(loc);
        if (net == null || !discovery.isStillValid(net)) {
            net = discovery.discover(piston);
            if (net != null) {
                registry.put(net);
            } else {
                registry.remove(loc);
            }
        }
        if (net != null) {
            transferService.transfer(net);
        }
    }
}
