package com.peakkup.pipeplugin;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ย้ายของต่อ 1 pulse:
 *   1) extract (region ต้นทาง): เลือก "ชนิดเดียว" ต่อรอบ (ชนิดแรกตามลำดับช่องที่ route ไปถึง output ได้)
 *      ดูดสูงสุด items-per-cycle ชิ้น; ของที่ stack ไม่ได้ (maxStackSize<=1) ดูดได้แค่ 1 ชิ้น/รอบ
 *   2) distribute: route ชนิดนั้นด้วย {@link PipeRouter} แล้วเติม output ตามลำดับ (priority fill)
 *      การใส่ของแต่ละ output ทำบน region ของ output นั้น (Folia-safe) ต่อกันด้วย CompletableFuture
 *   3) return (region ต้นทาง): ของที่เหลือ (ทุก output เต็ม) คืนต้นทาง ถ้าต้นทางเต็มอีกก็ drop กันของหาย
 */
public final class ItemTransferService {

    private static final Logger LOG = Logger.getLogger("PipePlugin");

    private final FoliaLib foliaLib;
    private final PipeConfig config;
    private final PipeRouter router;
    private final PipeLang lang;

    public ItemTransferService(FoliaLib foliaLib, PipeConfig config, PipeRouter router, PipeLang lang) {
        this.foliaLib = foliaLib;
        this.config = config;
        this.router = router;
        this.lang = lang;
    }

    /** งานย้ายของหนึ่งชนิดต่อหนึ่งรอบ */
    private static final class TypeJob {
        final ItemStack proto;          // ตัวแทนของชนิดนี้ (amount = 1)
        final List<Location> dests;     // ปลายทางเรียงตามลำดับความสำคัญ
        int remaining;                  // ยังเหลือต้องส่งอีกกี่ชิ้น

        TypeJob(ItemStack proto, int remaining, List<Location> dests) {
            this.proto = proto;
            this.remaining = remaining;
            this.dests = dests;
        }
    }

    public void transfer(PipeNetwork net) {
        if (!net.tryAcquire()) {
            return;
        }
        // การ acquire สำเร็จแล้ว: ต่อจากนี้ "ทุก" ทางออกต้อง release ไม่งั้นท่อค้างถาวร
        //  - เส้นทางปกติ: extractAndDistribute -> distribute -> whenComplete(release)
        //  - แต่ถ้า schedule ไม่ติด (throw ทันที) หรือ task ไม่เคยถูกรัน (future จบแบบ exception)
        //    ต้อง release ที่นี่ ไม่งั้น busy จะค้าง true ตลอดกาล
        try {
            foliaLib.getScheduler()
                    .runAtLocation(net.sourceContainer(), t -> extractAndDistribute(net))
                    .exceptionally(e -> {
                        // task body ไม่เคยรันสำเร็จ (extractAndDistribute จัดการ release เองไม่ได้)
                        net.release();
                        LOG.log(Level.WARNING, lang.msg("transfer.extract-task-failed"), e);
                        return null;
                    });
        } catch (RuntimeException | Error e) {
            net.release();
            LOG.log(Level.WARNING, lang.msg("transfer.schedule-failed"), e);
        }
    }

    // --- เฟส 1: ดูดของ + เตรียมเส้นทาง (บน region ต้นทาง) ---
    private void extractAndDistribute(PipeNetwork net) {
        List<TypeJob> jobs;
        try {
            jobs = extract(net);
        } catch (RuntimeException | Error e) {
            // อะไรก็ตามที่พังในเฟสดูด (เช่น world unload, getNearbyEntities ข้าม region บน Folia)
            // ต้องปลด busy ไม่งั้นท่อจะค้างถาวร (ของยังอยู่ต้นทาง = ไม่หาย)
            net.release();
            LOG.log(Level.WARNING, lang.msg("transfer.extract-phase-failed"), e);
            return;
        }
        distribute(net, jobs);
    }

    private List<TypeJob> extract(PipeNetwork net) {
        Inventory source = inventoryAt(net.sourceContainer());
        if (source == null) {
            return Collections.emptyList();
        }

        // สแกน item frame ของทั้งท่อครั้งเดียว (ใช้ตัดสิน route ของชนิดที่จะเลือก)
        FrameIndex frames = FrameIndex.build(net);

        // 1 รอบ = 1 ชนิดเท่านั้น: เลือกชนิดแรก (ตามลำดับช่อง) ที่ route ไปถึง output ได้
        // (ถ้าชนิดแรกไปไม่ได้เพราะโดน filter/ไม่มีปลายทาง จะข้ามไปลองชนิดถัดไป เพื่อไม่ให้ท่อตัน)
        List<ItemStack> seen = new ArrayList<>();
        for (ItemStack stack : source.getContents()) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            if (alreadySeen(seen, stack)) {
                continue;
            }
            seen.add(stack.clone());

            ItemStack proto = stack.clone();
            proto.setAmount(1);

            List<PipeOutput> route = router.route(net, proto, frames);
            if (route.isEmpty()) {
                continue; // ชนิดนี้ไปไหนไม่ได้ -> ลองชนิดถัดไป
            }

            // เพดานต่อรอบ: ปกติ items-per-cycle; แต่ของที่ stack ไม่ได้ (maxStackSize<=1) ส่งได้แค่ 1 ชิ้น
            int cap = proto.getMaxStackSize() <= 1 ? 1 : config.itemsPerCycle();
            int removed = removeUpTo(source, proto, cap);
            if (removed <= 0) {
                continue;
            }

            List<Location> dests = new ArrayList<>(route.size());
            for (PipeOutput out : route) {
                dests.add(out.destContainer());
            }
            // ได้ชนิดที่จะส่งแล้ว -> คืน job เดียว แล้วหยุด (ไม่ส่งชนิดอื่นในรอบนี้)
            return Collections.singletonList(new TypeJob(proto, removed, dests));
        }
        return Collections.emptyList();
    }

    // --- เฟส 2: เติมของลง output ตามลำดับ (chain ข้าม region) ---
    private void distribute(PipeNetwork net, List<TypeJob> jobs) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (TypeJob job : jobs) {
            for (Location dest : job.dests) {
                chain = chain.thenCompose(v -> {
                    if (job.remaining <= 0) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return foliaLib.getScheduler().runAtLocation(dest, t -> insertStep(job, dest));
                });
            }
        }
        // handle(): กลืน exception จาก insert ทุกขั้น เพื่อ "การันตี" ว่าเฟสคืนของยังทำงานเสมอ
        // (ถ้าปล่อยให้ exception ลามผ่าน thenCompose เฟสคืนของจะถูกข้าม = ของที่ดูดมาแล้วหาย)
        chain.handle((v, e) -> {
                    if (e != null) {
                        LOG.log(Level.WARNING, lang.msg("transfer.insert-phase-error"), e);
                    }
                    return null;
                })
                .thenCompose(v -> foliaLib.getScheduler()
                        .runAtLocation(net.sourceContainer(), t -> returnLeftovers(net, jobs)))
                .whenComplete((v, e) -> net.release());
    }

    private void insertStep(TypeJob job, Location dest) {
        try {
            Inventory inv = inventoryAt(dest);
            if (inv == null || job.remaining <= 0) {
                return;
            }
            ItemStack toAdd = job.proto.clone();
            toAdd.setAmount(job.remaining);
            Map<Integer, ItemStack> leftover = inv.addItem(toAdd);
            int left = 0;
            for (ItemStack s : leftover.values()) {
                left += s.getAmount();
            }
            job.remaining = left;
        } catch (RuntimeException ex) {
            // อย่าให้ของหาย: คง job.remaining ไว้เท่าเดิม เพื่อให้เฟสคืนของส่งกลับต้นทาง
            LOG.log(Level.WARNING, lang.msg("transfer.insert-failed"), ex);
        }
    }

    // --- เฟส 3: คืนของที่เหลือกลับต้นทาง ---
    private void returnLeftovers(PipeNetwork net, List<TypeJob> jobs) {
        try {
            List<ItemStack> toReturn = new ArrayList<>();
            for (TypeJob job : jobs) {
                while (job.remaining > 0) {
                    int amt = Math.min(job.remaining, job.proto.getMaxStackSize());
                    ItemStack s = job.proto.clone();
                    s.setAmount(amt);
                    toReturn.add(s);
                    job.remaining -= amt;
                }
            }
            if (toReturn.isEmpty()) {
                return;
            }

            Inventory source = inventoryAt(net.sourceContainer());
            if (source != null) {
                Map<Integer, ItemStack> still = source.addItem(toReturn.toArray(new ItemStack[0]));
                if (!still.isEmpty()) {
                    dropItems(net.sourceContainer(), still.values());
                }
            } else {
                dropItems(net.sourceContainer(), toReturn);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, lang.msg("transfer.return-failed"), ex);
        }
    }

    private boolean alreadySeen(List<ItemStack> seen, ItemStack stack) {
        for (ItemStack s : seen) {
            if (config.matchMode().matches(s, stack)) {
                return true;
            }
        }
        return false;
    }

    private int removeUpTo(Inventory inv, ItemStack proto, int n) {
        int removed = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && removed < n; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() == Material.AIR || s.getAmount() <= 0) {
                continue;
            }
            if (!config.matchMode().matches(proto, s)) {
                continue;
            }
            int take = Math.min(s.getAmount(), n - removed);
            removed += take;
            if (take >= s.getAmount()) {
                inv.setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
                inv.setItem(i, s);
            }
        }
        return removed;
    }

    private void dropItems(Location loc, Iterable<ItemStack> items) {
        if (loc.getWorld() == null) {
            return; // world ไม่โหลด -> ทิ้งไม่ได้ (หลีกเลี่ยง NPE); ของจะหายในเคสสุดขอบนี้เท่านั้น
        }
        Location drop = loc.clone().add(0.5, 1.0, 0.5);
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                loc.getWorld().dropItemNaturally(drop, item);
            }
        }
    }

    private Inventory inventoryAt(Location loc) {
        Block block = loc.getBlock();
        BlockState state = block.getState();
        if (state instanceof Container container && config.isContainerAllowed(block.getType())) {
            return container.getInventory();
        }
        return null;
    }
}
