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
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** สถานะของ sticky piston หนึ่งตัว — รวมไว้ก้อนเดียวเพื่อค้น map ครั้งเดียวต่อ event */
    private static final class PistonState {
        /** มีงานเช็คไฟที่ queue ไว้แล้วและยังไม่ได้รัน */
        final AtomicBoolean queued = new AtomicBoolean();
        /** powered ครั้งล่าสุดที่ตรวจ — ใช้จับขอบขาขึ้น */
        volatile boolean powered;
        /** เวลา (ms) ที่ท่อยิงรอบล่าสุด — ใช้บังคับ min-pulse-interval; 0 = ยังไม่เคยยิง */
        volatile long lastFire;
    }

    private final Map<Location, PistonState> pistons = new ConcurrentHashMap<>();

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
            PistonState state = pistons.computeIfAbsent(loc, k -> new PistonState());
            // redstone หนึ่งครั้งยิง event ออกมาเป็นชุด (dust ทั้งเส้นอัปเดตทีละก้อน, ทั้งขาขึ้นและขาลง)
            // แต่ทุกก้อนถามคำถามเดียวกันคือ "ตอนงานรัน piston ตัวนี้มีไฟหรือยัง" ซึ่งอ่านสดตอนนั้นอยู่แล้ว
            // จึง queue ไว้ตัวเดียวพอ — กันคลื่นงานที่เช็คไฟซ้ำ ๆ ทั้งที่ได้คำตอบเดียวกัน
            if (!state.queued.compareAndSet(false, true)) {
                continue;
            }
            // เช็คกำลังไฟจริงบนเธรดของ region ที่เป็นเจ้าของ (tick ถัดไป, Folia-safe)
            // จงใจไม่รันทันที: ตอน event นี้ยิง ค่ากำลังไฟของบล็อกยังไม่ถูกเขียนลงโลก
            try {
                foliaLib.getScheduler().runAtLocation(loc, t -> checkPiston(loc, state));
            } catch (RuntimeException | Error e) {
                state.queued.set(false); // schedule ไม่ติด -> อย่าล็อกไว้จนท่อตายถาวร
                throw e;
            }
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

    private void checkPiston(Location loc, PistonState state) {
        state.queued.set(false); // ปลดก่อนอ่านค่า: event ที่มาทีหลังต้อง queue รอบใหม่ได้

        Block piston = loc.getBlock();
        if (piston.getType() != Material.STICKY_PISTON) {
            pistons.remove(loc); // ไม่ใช่ piston แล้ว -> ทิ้งสถานะ ไม่ให้ map บวมสะสม
            return;
        }

        boolean powered = piston.isBlockIndirectlyPowered() || piston.isBlockPowered();
        boolean was = state.powered;
        state.powered = powered;

        if (!powered || was) {
            return; // ไม่ใช่ขอบขาขึ้น
        }

        // rate limit: กัน clock เร็ว ๆ ยิงรัว (นับต่อท่อ ต่อ input piston)
        long interval = config.minPulseIntervalMillis();
        if (interval > 0) {
            long now = System.currentTimeMillis();
            if (state.lastFire != 0 && now - state.lastFire < interval) {
                return;
            }
            state.lastFire = now;
        }

        // ขอบขาขึ้น -> ค้นท่อ "ใหม่ทุกรอบ" แล้วย้ายของ 1 รอบ
        //
        // จงใจไม่เชื่อ cache ในการตัดสินโทโพโลยี เพราะการเปลี่ยนบล็อกหลายแบบไม่ยิง event ให้ invalidate
        // (WorldEdit / /fill / ปลั๊กอินอื่น) และบางลำดับการวางก็ไม่โดน invalidate ด้วย เช่น
        // "วาง output piston ก่อน แล้วค่อยวางกล่องปลายทาง" — ตอนวางกล่อง เพื่อนบ้านของมันคือ piston
        // ซึ่งยังไม่เคยถูก track (ตอนนั้นยังไม่ใช่ output) จึงไม่มีอะไรถูก invalidate → output ใหม่
        // "ล่องหน" จนกว่าจะไปทุบบล็อกอื่นให้ cache ล้าง
        //
        // BFS ถูกจำกัดด้วย max-pipe-length อยู่แล้ว (~64 บล็อก) + มี rate limit ต่อท่อ จึงถูกพอที่จะทำทุก pulse
        // cache ยังมีอยู่เพื่อ: กันการรื้อ reverse index ทุกรอบ (ถ้าโทโพโลยีเท่าเดิม) และให้ PistonGuard ใช้
        PipeNetwork fresh = discovery.discover(piston);
        if (fresh == null) {
            registry.remove(loc);
            return;
        }

        PipeNetwork cached = registry.get(loc);
        PipeNetwork net;
        if (cached != null && cached.sameTopologyAs(fresh)) {
            net = cached; // เหมือนเดิมทุกอย่าง -> ใช้ตัวเดิม (คงล็อก busy, ไม่ต้องแตะ index)
        } else {
            fresh.adoptLockFrom(cached); // รอบเก่าที่ยังวิ่งอยู่ต้องกันรอบใหม่ได้
            registry.put(fresh);
            net = fresh;
        }
        transferService.transfer(net);
    }
}
