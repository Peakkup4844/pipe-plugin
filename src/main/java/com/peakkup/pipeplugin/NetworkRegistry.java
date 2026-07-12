package com.peakkup.pipeplugin;

import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * เก็บ cache ของท่อที่ค้นพบแล้ว พร้อม reverse index (block -> input piston)
 * สำหรับ invalidate เมื่อบล็อกในท่อถูกเปลี่ยน
 */
public final class NetworkRegistry {

    private final Map<Location, PipeNetwork> byInputPiston = new ConcurrentHashMap<>();
    private final Map<Location, Location> blockToInput = new ConcurrentHashMap<>();

    public PipeNetwork get(Location inputPiston) {
        return byInputPiston.get(inputPiston);
    }

    public void put(PipeNetwork network) {
        // ลบของเก่า (ถ้ามี) ก่อน เพื่อไม่ให้ reverse index ค้าง
        remove(network.inputPiston());

        byInputPiston.put(network.inputPiston(), network);
        Location input = network.inputPiston();
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
        blockToInput.put(block, input);
    }

    /** ลบ network ของ input piston นี้ ออกจาก cache ทั้งหมด */
    public void remove(Location inputPiston) {
        PipeNetwork old = byInputPiston.remove(inputPiston);
        if (old == null) {
            return;
        }
        blockToInput.remove(old.inputPiston(), inputPiston);
        blockToInput.remove(old.sourceContainer(), inputPiston);
        for (PipeOutput out : old.outputs()) {
            blockToInput.remove(out.piston(), inputPiston);
            blockToInput.remove(out.destContainer(), inputPiston);
        }
        for (Location block : old.pipeBlocks()) {
            // ลบเฉพาะถ้ายังชี้กลับมาที่ input เดิม (กันไปลบของ network อื่นที่ใช้ block ร่วม)
            blockToInput.remove(block, inputPiston);
        }
    }

    /** block นี้เป็นส่วนของท่อที่ cache ไว้หรือไม่ */
    public boolean isTracked(Location block) {
        return blockToInput.containsKey(block);
    }

    /** ถ้า block นี้เป็นส่วนของท่อใด ๆ ให้ invalidate ท่อนั้น */
    public void invalidateByBlock(Location block) {
        Location input = blockToInput.get(block);
        if (input != null) {
            remove(input);
        }
    }

    public void clear() {
        byInputPiston.clear();
        blockToInput.clear();
    }
}
