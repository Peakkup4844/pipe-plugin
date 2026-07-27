package com.peakkup.pipeplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
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

    private final RegionExecutor regions;
    private final PipeConfig config;
    private final PipeRouter router;
    private final PipeLang lang;
    private final ContainerAccess containers;

    public ItemTransferService(RegionExecutor regions, PipeConfig config, PipeRouter router,
                               PipeLang lang, ContainerAccess containers) {
        this.regions = regions;
        this.config = config;
        this.router = router;
        this.lang = lang;
        this.containers = containers;
    }

    /** งานย้ายของหนึ่งชนิดต่อหนึ่งรอบ */
    private static final class TypeJob {
        final ItemStack proto;          // ตัวแทนของชนิดนี้ (amount = 1)
        final List<Location> dests;     // ปลายทางเรียงตามลำดับความสำคัญ
        final List<int[]> sourceSlots;  // ช่องต้นทางที่ดูดมา {slot, amount} — ใช้คืนกลับที่เดิม
        final Inventory sourceInventory; // ใช้ประกอบ InventoryMoveItemEvent เท่านั้น (อาจเป็น null)
        int remaining;                  // ยังเหลือต้องส่งอีกกี่ชิ้น

        TypeJob(ItemStack proto, int remaining, List<Location> dests, List<int[]> sourceSlots,
                Inventory sourceInventory) {
            this.proto = proto;
            this.remaining = remaining;
            this.dests = dests;
            this.sourceSlots = sourceSlots;
            this.sourceInventory = sourceInventory;
        }
    }

    public void transfer(PipeNetwork net) {
        if (!net.tryAcquire()) {
            return;
        }
        // การ acquire สำเร็จแล้ว: ต่อจากนี้ "ทุก" ทางออกต้อง release ไม่งั้นท่อค้างถาวร
        //  1) สแกน item frame แบบ region-safe ก่อน (buildAsync) -> ได้ FrameIndex
        //  2) extract บน region ต้นทาง -> distribute -> whenComplete(release)
        // ทุก path ที่พัง (schedule ไม่ติด / future จบแบบ exception) ต้อง release
        try {
            FrameIndex.buildAsync(regions, net, lang).whenComplete((frames, err) -> {
                if (err != null || frames == null) {
                    net.release();
                    LOG.log(Level.WARNING, lang.msg("transfer.frame-scan-failed"), err);
                    return;
                }
                try {
                    regions.at(net.sourceContainer(), () -> extractAndDistribute(net, frames))
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
            });
        } catch (RuntimeException | Error e) {
            net.release();
            LOG.log(Level.WARNING, lang.msg("transfer.schedule-failed"), e);
        }
    }

    // --- เฟส 1: ดูดของ + เตรียมเส้นทาง (บน region ต้นทาง) ---
    private void extractAndDistribute(PipeNetwork net, FrameIndex frames) {
        List<TypeJob> jobs;
        try {
            jobs = extract(net, frames);
        } catch (RuntimeException | Error e) {
            // อะไรก็ตามที่พังในเฟสดูด (เช่น world unload) ต้องปลด busy
            // ไม่งั้นท่อจะค้างถาวร (ของยังอยู่ต้นทาง = ไม่หาย)
            net.release();
            LOG.log(Level.WARNING, lang.msg("transfer.extract-phase-failed"), e);
            return;
        }
        distribute(net, jobs);
    }

    private List<TypeJob> extract(PipeNetwork net, FrameIndex frames) {
        Inventory source = inventoryAt(net.sourceContainer());
        if (source == null) {
            return Collections.emptyList();
        }

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
            // คัดปลายทางที่ "รับชนิดนี้ไม่ได้แน่ ๆ" ออกตั้งแต่ตอนนี้ (ตัดสินจาก Material ที่จำไว้ตอนค้นท่อ
            // จึงไม่ต้องแตะ region อื่น) ถ้าไม่เหลือปลายทางเลยให้ข้ามไปลองชนิดถัดไป — ไม่งั้นท่อจะดูด
            // ของชนิดนี้ออกมาแล้วคืนกลับวนทุก pulse จนของชนิดอื่นไม่มีวันได้ไป
            List<Location> dests = new ArrayList<>(route.size());
            for (PipeOutput out : route) {
                if (ContainerAccess.canHold(out.destMaterial(), proto)) {
                    dests.add(out.destContainer());
                }
            }
            if (dests.isEmpty()) {
                continue; // ชนิดนี้ไปไหนไม่ได้ -> ลองชนิดถัดไป
            }

            // เพดานต่อรอบ: ปกติ items-per-cycle; แต่ของที่ stack ไม่ได้ (maxStackSize<=1) ส่งได้แค่ 1 ชิ้น
            int cap = proto.getMaxStackSize() <= 1 ? 1 : config.itemsPerCycle();
            BigInteger before = containers.trueCount(net.sourceContainer(), source, proto);
            InventoryOps.Taken taken = InventoryOps.removeUpTo(source, proto, cap);
            if (taken.total() <= 0) {
                continue;
            }

            // --- ด่านกันของหาย/ของเพิ่มที่ต้นทาง: ต้นทางต้องเสียของไปเท่าที่เราดูดมาจริง ๆ ---
            // อ่านซ้ำในเธรดเดิม tick เดียวกัน จึงไม่มีใครมาแทรกกลางคัน ผลต่างที่ได้คือความจริง
            // "จำนวนจริง" มาจาก trueCount ซึ่งถามเจ้าของกล่องก่อน (storage unit นับจาก inventory ไม่ได้)
            // ถ้ายังไม่ตรง แปลว่ากล่องนี้ไม่มีใครรู้จัก และเราแยกไม่ออกเลยว่าเป็นแบบไหน:
            //   ก) "ไอเทมโชว์" — ของไม่เคยหายไปจริง  -> ส่งต่อ = เสกของ (dupe)
            //   ข) "หักจริงแต่วาดกลับ" — ปลั๊กอินหักจำนวนจริงไปแล้วแต่วาดสต๊ากเดิมคืน -> ทิ้ง = ของหาย
            // เราถือของอยู่ในมือ ณ จุดนี้ จึงต้อง "ยัดกลับทั้งหมด" เสมอ — ห้ามทิ้งเด็ดขาด
            // แล้วขึ้นบัญชีดำกล่องนี้ถาวร ความเสียหายจึงถูกจำกัดไว้ที่รอบเดียวต่อกล่อง ไม่ใช่ทุก pulse
            BigInteger removed =
                    before.subtract(containers.trueCount(net.sourceContainer(), source, proto));
            if (!removed.equals(BigInteger.valueOf(taken.total()))) {
                returnLeftovers(net, Collections.singletonList(new TypeJob(
                        proto, taken.total(), Collections.emptyList(), taken.slots(), source)));
                if (containers.distrust(net.sourceContainer())) {
                    LOG.warning(lang.msg("transfer.source-not-conserved",
                            "loc", describe(net.sourceContainer()),
                            "inv", source.getClass().getSimpleName(),
                            "took", String.valueOf(taken.total()),
                            "lost", removed.toString()));
                }
                return Collections.emptyList();
            }

            // ดูดของออกจากต้นทางสำเร็จ -> เอฟเฟกต์ที่ต้นทาง (เราอยู่บน region ต้นทางอยู่แล้ว)
            playEffect(net.sourceContainer());
            // ได้ชนิดที่จะส่งแล้ว -> คืน job เดียว แล้วหยุด (ไม่ส่งชนิดอื่นในรอบนี้)
            return Collections.singletonList(
                    new TypeJob(proto, taken.total(), dests, taken.slots(), source));
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
                    return regions.at(dest, () -> insertStep(net, job, dest));
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
                .thenCompose(v -> regions.at(net.sourceContainer(), () -> returnLeftovers(net, jobs)))
                .whenComplete((v, e) -> {
                    net.release();
                    if (e != null) {
                        // เฟสคืนของ schedule ไม่ติดเลย (world unload ฯลฯ) = ของที่ยังค้างในมือไม่มีที่ไป
                        // เคสสุดขอบเดียวที่ของหายได้ ต้องไม่เงียบ ให้แอดมินเห็นว่าหายไปกี่ชิ้น
                        int stranded = 0;
                        for (TypeJob job : jobs) {
                            stranded += Math.max(0, job.remaining);
                        }
                        LOG.log(Level.WARNING, lang.msg("transfer.return-schedule-failed",
                                "loc", describe(net.sourceContainer()),
                                "count", String.valueOf(stranded)), e);
                    }
                });
    }

    private void insertStep(PipeNetwork net, TypeJob job, Location dest) {
        Inventory inv;
        BigInteger before;
        try {
            inv = inventoryAt(dest);
            if (inv == null || job.remaining <= 0) {
                return;
            }
            // ปลายทางรับชนิดนี้ไม่ได้ (shulker ซ้อน shulker / storage unit ที่เก็บของคนละชนิด)
            // -> ข้ามไป output ถัดไป ถ้ายัดไปของจะตกพื้นหรือกล่องที่ทำงานถูกต้องจะโดนขึ้นบัญชีดำ
            if (!containers.acceptsType(dest, inv, job.proto)) {
                return;
            }
            // ให้ปลั๊กอินป้องกันพื้นที่/บันทึกประวัติได้เห็นและยับยั้งได้ ก่อนที่ของจะขยับจริง
            if (!allowedByListeners(net, job, inv)) {
                return;
            }
            before = containers.trueCount(dest, inv, job.proto);
        } catch (RuntimeException ex) {
            // ยังไม่ได้แตะปลายทางเลย -> ของยังอยู่ในมือครบ เฟสคืนของจะส่งกลับต้นทาง
            LOG.log(Level.WARNING, lang.msg("transfer.insert-failed"), ex);
            return;
        }

        int sent = job.remaining;
        int inserted;
        try {
            ItemStack toAdd = job.proto.clone();
            toAdd.setAmount(sent);
            int left = 0;
            for (ItemStack s : inv.addItem(toAdd).values()) {
                left += s.getAmount();
            }
            inserted = sent - left;
            job.remaining = left;
        } catch (RuntimeException ex) {
            // addItem พังกลางคัน: อาจใส่ลงไปได้บางส่วนแล้ว จึงต้อง "วัดของจริง" เอา
            // ถ้าเดามั่ว ๆ ว่าไม่เข้าเลยแล้วคืนต้นทางทั้งก้อน จะกลายเป็น dupe เท่าที่เข้าไปแล้ว
            LOG.log(Level.WARNING, lang.msg("transfer.insert-failed"), ex);
            job.remaining = sent - gainOf(dest, inv, job.proto, before, sent);
            return;
        }

        // --- ด่านฝั่งปลายทาง: กล่องต้องได้ของเพิ่มเท่าที่ addItem บอกว่ารับไป ---
        // ไม่สมมาตรกับฝั่งต้นทางโดยตั้งใจ: ตรงนี้ของ "ออกจากมือเราไปแล้ว" ถ้าเดาว่ามันไม่ได้เก็บ
        // แล้วดึงกลับไปคืนต้นทาง แต่จริง ๆ มันเก็บไว้ = dupe ทันที จึงได้แค่ตรวจจับ + ขึ้นบัญชีดำ
        // ไม่พยายามกู้ของรอบนี้ ผลคือแย่ที่สุด = เสียหายรอบเดียวต่อกล่อง แล้วไม่แตะมันอีกเลย
        int gained = gainOf(dest, inv, job.proto, before, sent);
        if (gained != inserted && containers.distrust(dest)) {
            LOG.warning(lang.msg("transfer.dest-not-conserved",
                    "loc", describe(dest),
                    "inv", inv.getClass().getSimpleName(),
                    "sent", String.valueOf(inserted),
                    "gained", String.valueOf(gained)));
        }

        if (inserted > 0) {
            // มีของเข้าปลายทางจริง -> เอฟเฟกต์ที่ปลายทาง (เราอยู่บน region ปลายทางอยู่แล้ว)
            playEffect(dest);
        }
    }

    /**
     * ยิง {@link InventoryMoveItemEvent} ให้ปลั๊กอินอื่นตัดสินก่อนย้ายของจริง — false = ถูกยกเลิก
     *
     * <p>นี่คือทางเดียวที่ปลั๊กอินป้องกันพื้นที่ (WorldGuard / GriefPrevention / Towny / LWC)
     * และปลั๊กอินบันทึกประวัติ (CoreProtect) จะ "เห็น" การขนของของท่อได้ ถ้าไม่ยิง ผู้เล่นจะเอา
     * sticky piston ไปจ่อกล่องในเขตของคนอื่นแล้วดูดของออกมาได้เลย โดยไม่มีอะไรขวางและไม่มีล็อก
     * — ช่องโหว่แบบเดียวกับ hopper ลอดเข้าเขต ซึ่งเซิร์ฟทั่วไปกันไว้ผ่านอีเวนต์นี้อยู่แล้ว
     *
     * <p>ยิงหนึ่งครั้งต่อหนึ่ง output ด้วยจำนวนทั้งก้อน (ไม่ใช่ทีละชิ้น) — ปลั๊กอินป้องกันดูตำแหน่ง
     * ต้นทาง/ปลายทางเป็นหลัก ไม่ได้ดูจำนวน จึงได้ผลเหมือนกันแต่ถูกกว่ามาก
     *
     * <p><b>Folia:</b> ยิงเฉพาะเมื่อกล่องต้นทางอยู่ใน region เดียวกับที่เรากำลังรันอยู่ เพราะ listener
     * ของคนอื่นอาจไปอ่าน {@code event.getSource()} ซึ่งเป็น inventory ของอีก region = ผิดกฎเธรดของ
     * Folia ท่อข้าม region จริง ๆ จึงข้ามการยิงอีเวนต์ไป (ระบุไว้ใน config.yml)
     */
    private boolean allowedByListeners(PipeNetwork net, TypeJob job, Inventory dest) {
        Inventory source = job.sourceInventory;
        if (!config.callInventoryMoveEvent() || source == null) {
            return true;
        }
        if (!regions.ownsCurrentThread(net.sourceContainer())) {
            return true;
        }
        try {
            ItemStack moved = job.proto.clone();
            moved.setAmount(job.remaining);
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, moved, dest, true);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } catch (Throwable t) {
            // อีเวนต์พัง (listener ของคนอื่นโยน exception / API ต่างเวอร์ชัน) -> ปล่อยผ่าน
            // ห้ามให้ของค้างในมือเพราะปลั๊กอินอื่นพัง
            LOG.log(Level.WARNING, lang.msg("transfer.move-event-failed"), t);
            return true;
        }
    }

    /** ของชนิดนี้ในกล่องเพิ่มขึ้นจริงกี่ชิ้น (บีบให้อยู่ในช่วง 0..sent เผื่ออ่านค่าไม่ได้) */
    private int gainOf(Location loc, Inventory inv, ItemStack proto, BigInteger before, int sent) {
        try {
            BigInteger gained = containers.trueCount(loc, inv, proto).subtract(before);
            if (gained.signum() <= 0) {
                return 0;
            }
            // storage unit เก็บได้เกิน int -> เทียบบน BigInteger ก่อนค่อยแปลง ไม่งั้น overflow
            return gained.compareTo(BigInteger.valueOf(sent)) >= 0 ? sent : gained.intValue();
        } catch (RuntimeException ex) {
            return 0; // อ่านไม่ได้ -> ถือว่าไม่เข้าเลย แล้วคืนต้นทาง (ของไม่หาย)
        }
    }

    // --- เฟส 3: คืนของที่เหลือกลับต้นทาง ---
    private void returnLeftovers(PipeNetwork net, List<TypeJob> jobs) {
        try {
            Inventory source = inventoryAt(net.sourceContainer());

            // 1) คืนเข้า "ช่องเดิม" ที่ดูดออกมาก่อน — ถ้าปลายทางเต็มสนิท ผังกล่องต้นทางจะเหมือนเดิมเป๊ะ
            //    (ไม่งั้น addItem จะยัดลงช่องแรกที่ว่าง = ของค่อย ๆ ถูกยุบมารวมช่องต้น ๆ ทุก pulse)
            if (source != null) {
                for (TypeJob job : jobs) {
                    job.remaining = InventoryOps.restoreToOriginalSlots(
                            source, job.proto, job.remaining, job.sourceSlots);
                }
            }

            // 2) ส่วนที่คืนช่องเดิมไม่ได้ (ถูกยึดไประหว่างรอบ) -> ยัดที่ไหนก็ได้ในต้นทาง
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

    private static String describe(Location loc) {
        String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        return world + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * เคยลองชนิดนี้ไปแล้วในรอบนี้ไหม — เทียบเป๊ะเสมอ ({@code isSimilar}) ห้ามใช้โหมดของ filter
     * เพราะชนิดที่ถูกจัดว่า "เหมือนกัน" จะถูกดูดรวมกันแล้วสร้างใหม่จาก proto ตัวเดียว (ดู {@link InventoryOps})
     */
    private static boolean alreadySeen(List<ItemStack> seen, ItemStack stack) {
        for (ItemStack s : seen) {
            if (s.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * เสียง + particle เล็ก ๆ ตอนย้ายของสำเร็จ (ให้ผู้เล่นแยกออกว่าท่อทำงาน ไม่ใช่พัง)
     * ต้องเรียกบน region ของ loc เท่านั้น (Folia). ห่อ Throwable ไว้เพราะชื่อ Particle/Sound
     * อาจต่างข้ามเวอร์ชัน — เอฟเฟกต์พังห้ามทำให้การย้ายของพัง/ของหาย
     */
    private void playEffect(Location loc) {
        if (!config.effectsEnabled()) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        try {
            Location at = loc.clone().add(0.5, 1.0, 0.5);
            world.spawnParticle(Particle.CRIT, at, 6, 0.2, 0.2, 0.2, 0.0);
            world.playSound(loc, Sound.BLOCK_DISPENSER_DISPENSE, 0.4f, 1.4f);
        } catch (Throwable ignored) {
            // เอฟเฟกต์เป็นของประดับ; ข้ามไปเงียบ ๆ ถ้า API ต่างเวอร์ชัน
        }
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
        return containers.inventoryAt(loc);
    }
}
