package com.peakkup.pipeplugin;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * เก็บ cache ของท่อที่ค้นพบแล้ว พร้อม reverse index (block -> input piston ทุกตัวที่ใช้ block นั้น)
 * สำหรับ invalidate เมื่อบล็อกในท่อถูกเปลี่ยน
 *
 * reverse index เป็น "multimap" (block -> เซตของ input piston) เพราะบล็อกกระจกก้อนเดียว
 * อาจถูกใช้ร่วมโดยหลายท่อ (เช่น sticky piston 2 ตัวเกาะกระจกเส้นเดียวกัน) — ถ้าเก็บแบบ 1:1
 * การ invalidate บล็อกร่วมจะลบได้แค่ท่อเดียว อีกท่อจะค้าง cache เก่า (เดินผ่านท่อที่พังแล้ว)
 *
 * ทุก mutation ของเซตทำผ่าน {@code compute}/{@code computeIfPresent} เพื่อให้ atomic ต่อ key
 * (put กับ remove อาจมาจากคนละเธรดบน Folia) และค่าเป็น {@link ConcurrentHashMap#newKeySet}
 * เพื่อให้ iterate ใน {@link #invalidateByBlock} ได้อย่างปลอดภัย
 */
public final class NetworkRegistry {

    private final Map<Location, PipeNetwork> byInputPiston = new ConcurrentHashMap<>();
    private final Map<Location, Set<Location>> blockToInputs = new ConcurrentHashMap<>();

    public PipeNetwork get(Location inputPiston) {
        return byInputPiston.get(inputPiston);
    }

    public void put(PipeNetwork network) {
        Location input = network.inputPiston();
        // ลบของเก่า (ถ้ามี) ก่อน เพื่อไม่ให้ reverse index ค้าง
        remove(input);

        byInputPiston.put(input, network);
        index(network.inputPiston(), input);
        index(network.sourceContainer(), input);
        for (PipeOutput out : network.outputs()) {
            index(out.piston(), input);
            index(out.destContainer(), input);
        }
        for (Location block : network.pipeBlocks()) {
            index(block, input);
        }
    }

    private void index(Location block, Location input) {
        blockToInputs.compute(block, (k, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
            }
            set.add(input);
            return set;
        });
    }

    private void unindex(Location block, Location input) {
        // ลบ input ออกจากเซตของ block นี้ ถ้าเซตว่างก็เอา key ทิ้ง (atomic ต่อ key)
        blockToInputs.computeIfPresent(block, (k, set) -> {
            set.remove(input);
            return set.isEmpty() ? null : set;
        });
    }

    /** ลบ network ของ input piston นี้ ออกจาก cache ทั้งหมด */
    public void remove(Location inputPiston) {
        PipeNetwork old = byInputPiston.remove(inputPiston);
        if (old == null) {
            return;
        }
        unindex(old.inputPiston(), inputPiston);
        unindex(old.sourceContainer(), inputPiston);
        for (PipeOutput out : old.outputs()) {
            unindex(out.piston(), inputPiston);
            unindex(out.destContainer(), inputPiston);
        }
        for (Location block : old.pipeBlocks()) {
            unindex(block, inputPiston);
        }
    }

    /** block นี้เป็นส่วนของท่อที่ cache ไว้ (ท่อใด ๆ) หรือไม่ */
    public boolean isTracked(Location block) {
        return blockToInputs.containsKey(block);
    }

    /** ยังไม่มีท่อไหนถูก cache ไว้เลย — ใช้ตัดงาน invalidate ทิ้งตั้งแต่ต้นทาง */
    public boolean isEmpty() {
        return blockToInputs.isEmpty();
    }

    /** ถ้า block นี้เป็นส่วนของท่อใด ๆ ให้ invalidate ท่อ "ทุกเส้น" ที่ใช้ block นี้ */
    public void invalidateByBlock(Location block) {
        Set<Location> inputs = blockToInputs.get(block);
        if (inputs == null) {
            return;
        }
        // สำเนาก่อนวน เพราะ remove() จะไปแก้ blockToInputs (รวมถึงเซตนี้)
        for (Location input : new ArrayList<>(inputs)) {
            remove(input);
        }
    }

    public void clear() {
        byInputPiston.clear();
        blockToInputs.clear();
    }
}
