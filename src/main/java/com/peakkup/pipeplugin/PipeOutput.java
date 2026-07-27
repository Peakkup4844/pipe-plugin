package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Objects;

/**
 * จุดปล่อยของหนึ่งจุด: piston (output) ที่ติดกับกระจกก้อนหนึ่งของท่อ และหันเข้า container
 *
 * เป็น value object (equals/hashCode ตาม 3 ตำแหน่ง + ชนิดกล่องปลายทาง) เพื่อให้เทียบ
 * "โทโพโลยีเดิมไหม" ได้ตอน re-discover ทุก pulse และให้ {@code List#contains} ใน PipeRouter
 * ถูกต้องเชิงความหมาย — รวมชนิดกล่องไว้ด้วยเพราะการเปลี่ยนกล่องปลายทาง (เช่น chest เป็น shulker)
 * เปลี่ยนกฎว่าอะไรใส่ลงไปได้ ต้องนับว่าเป็นคนละโทโพโลยี
 */
public final class PipeOutput {

    private final Location glassBlock;   // กระจกในท่อที่ output piston เกาะอยู่
    private final Location piston;       // ตัว output piston
    private final Location destContainer; // กล่องปลายทางที่ piston หันเข้า
    /** ชนิดบล็อกของกล่องปลายทาง ณ ตอนค้นท่อ — ใช้คัดชนิดของได้ตั้งแต่ฝั่งต้นทาง (ดู ContainerAccess#canHold) */
    private final Material destMaterial;

    public PipeOutput(Location glassBlock, Location piston, Location destContainer, Material destMaterial) {
        this.glassBlock = glassBlock;
        this.piston = piston;
        this.destContainer = destContainer;
        this.destMaterial = destMaterial;
    }

    public Location glassBlock() {
        return glassBlock;
    }

    public Location piston() {
        return piston;
    }

    public Location destContainer() {
        return destContainer;
    }

    public Material destMaterial() {
        return destMaterial;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PipeOutput other)) {
            return false;
        }
        return destMaterial == other.destMaterial
                && Objects.equals(glassBlock, other.glassBlock)
                && Objects.equals(piston, other.piston)
                && Objects.equals(destContainer, other.destContainer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(glassBlock, piston, destContainer, destMaterial);
    }
}
