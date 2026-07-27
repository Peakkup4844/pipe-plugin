package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /**
     * กันไม่ให้รอบการย้ายของซ้อนกัน (pulse รัว) — thread-safe สำหรับ Folia
     *
     * ไม่ final เพราะเราค้นท่อใหม่ทุก pulse: ถ้าโทโพโลยีเปลี่ยน object ใหม่ต้อง "รับช่วง" ล็อกตัวเดิม
     * ({@link #adoptLockFrom}) ไม่งั้นรอบเก่าที่ยังวิ่งอยู่จะซ้อนกับรอบใหม่ที่เห็นล็อกว่าง
     */
    private volatile AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * แคชสิ่งที่คำนวณจากโทโพโลยีล้วน ๆ (จึงไม่มีวันเก่า ตราบใดที่ object นี้ยังถูกใช้อยู่)
     *
     * ทำแบบ lazy เพราะเราสร้าง PipeNetwork ใหม่ทุก pulse เพื่อเทียบโทโพโลยี แล้วทิ้งตัวใหม่
     * ถ้าเหมือนเดิม — คำนวณตอนสร้างจึงเสียเปล่าทุกรอบ ส่วนตัวที่ถูกใช้จริง (ตัวที่อยู่ใน cache)
     * จะคำนวณครั้งเดียวแล้วใช้ยาว
     */
    private volatile Set<Location> gateBlocks;
    private volatile int[][] frameChunks;

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

    /** บล็อกที่ item frame แปะแล้วมีผลเป็น gate: input piston + กระจกทุกก้อน + output piston ทุกตัว */
    public Set<Location> gateBlocks() {
        Set<Location> cached = gateBlocks;
        if (cached == null) {
            cached = new HashSet<>(pipeBlocks);
            cached.add(inputPiston);
            for (PipeOutput out : outputs) {
                cached.add(out.piston());
            }
            gateBlocks = cached;
        }
        return cached;
    }

    /**
     * chunk ที่ต้องสแกนหา item frame — chunk ของ gate block เอง + เพื่อนบ้านแนวนอน 4 ทิศ
     * (frame เกาะผิวนอก = อยู่ในบล็อกอากาศที่ติดกัน ซึ่งอาจข้าม chunk ที่ขอบพอดี
     * ส่วนแนวดิ่งอยู่คอลัมน์เดียวกันจึงไม่ต้องเพิ่ม) คืนคู่ {cx, cz} ที่ไม่ซ้ำกัน
     */
    public int[][] frameChunks() {
        int[][] cached = frameChunks;
        if (cached == null) {
            Map<Long, int[]> chunks = new LinkedHashMap<>();
            for (Location b : gateBlocks()) {
                int bx = b.getBlockX();
                int bz = b.getBlockZ();
                addChunk(chunks, bx, bz);
                addChunk(chunks, bx - 1, bz);
                addChunk(chunks, bx + 1, bz);
                addChunk(chunks, bx, bz - 1);
                addChunk(chunks, bx, bz + 1);
            }
            cached = chunks.values().toArray(new int[0][]);
            frameChunks = cached;
        }
        return cached;
    }

    private static void addChunk(Map<Long, int[]> chunks, int blockX, int blockZ) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        chunks.putIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), new int[]{cx, cz});
    }

    /**
     * โทโพโลยีเหมือน network อีกตัวไหม (ต้นทาง + สีกระจก + กระจกทุกก้อน + output ทุกตัว)
     * ใช้ตัดสินว่า re-discover แล้ว "ของเดิม" หรือเปล่า จะได้ไม่ต้องรื้อ reverse index ทุก pulse
     */
    public boolean sameTopologyAs(PipeNetwork other) {
        if (other == null) {
            return false;
        }
        return glassMaterial == other.glassMaterial
                && inputPiston.equals(other.inputPiston)
                && sourceContainer.equals(other.sourceContainer)
                && pipeBlocks.equals(other.pipeBlocks)
                && outputs.equals(other.outputs);
    }

    /**
     * รับช่วงล็อก busy จาก network ตัวก่อนหน้าของ input piston เดียวกัน
     * (ใช้ AtomicBoolean ตัวเดียวกันร่วมกัน → รอบที่ยังวิ่งค้างอยู่ปลดล็อกให้ตัวใหม่ได้ถูกต้อง)
     */
    public void adoptLockFrom(PipeNetwork previous) {
        if (previous != null) {
            this.busy = previous.busy;
        }
    }

    public boolean tryAcquire() {
        return busy.compareAndSet(false, true);
    }

    public void release() {
        busy.set(false);
    }
}
