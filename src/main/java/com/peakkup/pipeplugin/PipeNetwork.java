package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * โมเดลของท่อหนึ่งเส้น (input เดียว, ท่อสีเดียว, output ได้หลายจุด)
 *
 * เก็บเฉพาะ "โทโพโลยี" (กระจก + รายการ output) เพราะ filter (item frame) อ่านสดตอนย้ายของ
 * ทุก Location เก็บแบบ block location (จาก Block#getLocation) เพื่อใช้เป็น key ได้สม่ำเสมอ
 */
public final class PipeNetwork {

    private final Location inputPiston;
    private final Location sourceContainer;
    private final Material glassMaterial;
    private final Set<Location> pipeBlocks;
    private final List<PipeOutput> outputs;
    /** กระจก -> output ที่เกาะกระจกก้อนนั้น (อาจมีหลายตัวต่อก้อน) */
    private final Map<Location, List<PipeOutput>> outputsByGlass;

    /** กันไม่ให้รอบการย้ายของซ้อนกัน (pulse รัว) — thread-safe สำหรับ Folia */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public PipeNetwork(Location inputPiston,
                       Location sourceContainer,
                       Material glassMaterial,
                       Set<Location> pipeBlocks,
                       List<PipeOutput> outputs,
                       Map<Location, List<PipeOutput>> outputsByGlass) {
        this.inputPiston = inputPiston;
        this.sourceContainer = sourceContainer;
        this.glassMaterial = glassMaterial;
        this.pipeBlocks = pipeBlocks;
        this.outputs = outputs;
        this.outputsByGlass = outputsByGlass;
    }

    public Location inputPiston() {
        return inputPiston;
    }

    public Location sourceContainer() {
        return sourceContainer;
    }

    public Material glassMaterial() {
        return glassMaterial;
    }

    public Set<Location> pipeBlocks() {
        return pipeBlocks;
    }

    public List<PipeOutput> outputs() {
        return outputs;
    }

    public List<PipeOutput> outputsAtGlass(Location glass) {
        return outputsByGlass.getOrDefault(glass, Collections.emptyList());
    }

    public boolean tryAcquire() {
        return busy.compareAndSet(false, true);
    }

    public void release() {
        busy.set(false);
    }
}
