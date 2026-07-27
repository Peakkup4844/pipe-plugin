package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * การกระทำระดับ "ช่อง (slot)" กับ inventory ที่ต้องรักษาผังของกล่องต้นทางไว้
 *
 * ทำไมต้องมีคลาสนี้: เดิมเวลาปลายทางเต็ม ของถูกคืนด้วย {@code Inventory#addItem} ซึ่งจะยัดลง
 * "ช่องแรกที่ว่าง/ต่อ stack ได้" → ของที่เคยอยู่ช่องท้าย ๆ ค่อย ๆ ถูกย้ายมารวมที่ช่องต้น ๆ ทุก pulse
 * (ผู้เล่นเห็นเป็นอาการ "ของในกล่องขยับไปช่อง 1 เอง" เวลาจ่ายไฟค้างทั้งที่ปลายทางเต็ม)
 *
 * จึงจำ "ช่องที่ดูดออกมา" ไว้ แล้วตอนคืนให้คืนกลับช่องเดิมก่อนเสมอ — ถ้าปลายทางเต็มสนิท
 * ผังกล่องต้นทางจะกลับมาเหมือนเดิมเป๊ะ (no-op ที่มองเห็นได้)
 *
 * <b>การจับคู่ที่นี่เทียบแบบเป๊ะเสมอ ({@link ItemStack#isSimilar}) และห้ามใช้ {@link MatchMode}
 * ของ filter เด็ดขาด</b> — เพราะไอเทมที่ถูกจัดว่า "ชนิดเดียวกัน" จะถูก<b>สร้างใหม่จากตัวแทน
 * (proto) ตัวเดียว</b> ตอนส่งออกและตอนคืนของ ถ้าใช้ {@code filter-match: TYPE} ซึ่งเทียบแค่
 * Material ของที่มี NBT ต่างกันแต่ stack ได้ (ลูกศรอาบยาพิษ, พลุ) จะถูกกลืนรวมกันแล้วออกมาเป็น
 * ชนิดของตัวแรกในกล่องทั้งหมด = ผู้เล่นวางของแพงไว้ช่องแรกแล้วแปลงของถูกเป็นของแพงได้
 * โหมด filter มีผลเฉพาะกับ "ประตู item frame" ({@link FrameIndex}) เท่านั้น
 *
 * ทุกเมธอดต้องถูกเรียกบนเธรดของ region ที่เป็นเจ้าของ inventory นั้น (Folia)
 */
public final class InventoryOps {

    private InventoryOps() {
    }

    /** ไอเทมสองชิ้นเป็น "ของชิ้นเดียวกัน" ไหม (ชนิด + NBT ตรงกันเป๊ะ, ไม่นับจำนวน) */
    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isSimilar(b);
    }

    /** ผลการดูดของ: จำนวนรวมที่ดูดได้ + รายการช่องที่ดูดมา (เพื่อคืนกลับที่เดิม) */
    public static final class Taken {
        private final int total;
        private final List<int[]> slots; // แต่ละตัว = {slotIndex, amount}

        Taken(int total, List<int[]> slots) {
            this.total = total;
            this.slots = slots;
        }

        public int total() {
            return total;
        }

        /** ช่องที่ถูกดูด เรียงตามลำดับที่ดูด (index จากช่องต้น ๆ ไปท้าย ๆ) */
        public List<int[]> slots() {
            return Collections.unmodifiableList(slots);
        }
    }

    /**
     * นับจำนวนไอเทมทั้งหมดใน inv ที่เข้าคู่กับ proto
     *
     * <p>ใช้ตรวจ "การอนุรักษ์ของ" รอบต้นทาง: นับก่อนดูด แล้วนับใหม่หลังดูด ผลต่างต้องเท่ากับ
     * จำนวนที่เราดูดออกไปเป๊ะ ถ้าไม่เท่าแปลว่า inventory นั้นไม่ได้เก็บของจริง (ไอเทมโชว์ของปลั๊กอิน
     * storage / inventory อ่านอย่างเดียว) ซึ่งถ้าปล่อยผ่านจะกลายเป็นเสกของขึ้นมา = dupe
     */
    public static int countMatching(Inventory inv, ItemStack proto) {
        int total = 0;
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType() == Material.AIR || s.getAmount() <= 0) {
                continue;
            }
            if (same(proto, s)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    /**
     * ดูดของชนิดเดียวกับ proto ออกจาก inv ไม่เกิน max ชิ้น (ไล่จากช่องแรกไปท้าย)
     * คืนจำนวนที่ดูดได้ + ช่องที่ดูดมา
     */
    public static Taken removeUpTo(Inventory inv, ItemStack proto, int max) {
        int removed = 0;
        List<int[]> slots = new ArrayList<>();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && removed < max; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() == Material.AIR || s.getAmount() <= 0) {
                continue;
            }
            if (!same(proto, s)) {
                continue;
            }
            int take = Math.min(s.getAmount(), max - removed);
            removed += take;
            slots.add(new int[]{i, take});
            if (take >= s.getAmount()) {
                inv.setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
                inv.setItem(i, s);
            }
        }
        return new Taken(removed, slots);
    }

    /**
     * คืนของกลับ "ช่องเดิม" ที่ดูดมา (ตามรายการ slots) มากที่สุดเท่าที่ทำได้
     * คืนค่าจำนวนที่ยัง "คืนไม่ลง" เพื่อให้ผู้เรียกไปหาที่ยัดต่อ (addItem/drop)
     *
     * ช่องเดิมอาจถูกคนอื่นยึดไปแล้วระหว่างรอบ (ผู้เล่น/ปลั๊กอินอื่น) จึงตรวจสภาพช่องก่อนเสมอ:
     * ว่าง = วางลงตรง ๆ, มีของชนิดเดียวกันและยังไม่เต็ม stack = เติมเพิ่ม, นอกนั้นข้าม
     */
    public static int restoreToOriginalSlots(Inventory inv, ItemStack proto, int remaining,
                                             List<int[]> slots) {
        if (remaining <= 0 || slots == null || slots.isEmpty()) {
            return remaining;
        }
        int size = inv.getSize();
        int maxStack = Math.max(1, proto.getMaxStackSize());

        for (int[] taken : slots) {
            if (remaining <= 0) {
                break;
            }
            int slot = taken[0];
            if (slot < 0 || slot >= size) {
                continue; // กล่องถูกเปลี่ยนขนาด (เช่น double chest ถูกแยก) -> ข้าม
            }
            ItemStack cur = inv.getItem(slot);
            if (cur == null || cur.getType() == Material.AIR) {
                int amt = Math.min(remaining, Math.min(taken[1], maxStack));
                ItemStack put = proto.clone();
                put.setAmount(amt);
                inv.setItem(slot, put);
                remaining -= amt;
            } else if (same(proto, cur) && cur.getAmount() < maxStack) {
                int amt = Math.min(remaining, maxStack - cur.getAmount());
                cur.setAmount(cur.getAmount() + amt);
                inv.setItem(slot, cur);
                remaining -= amt;
            }
        }
        return remaining;
    }
}
