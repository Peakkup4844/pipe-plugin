package com.peakkup.pipeplugin;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * ลำดับทิศสำหรับการเดินท่อ: เมื่อกำลังมุ่งหน้าทิศ heading
 * ลำดับความสำคัญคือ หน้า -> ซ้าย -> ขวา -> บน -> ล่าง (ไม่ย้อนกลับหลัง)
 * ใช้กำหนดว่าที่ทางแยกให้เลือกซ้ายก่อนขวา
 */
public final class Directions {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private Directions() {
    }

    /** ทิศถัดไปเรียงตามลำดับความสำคัญ (ไม่รวมทิศย้อนกลับ) */
    public static List<BlockFace> ordered(BlockFace heading) {
        List<BlockFace> dirs = new ArrayList<>(5);
        if (heading == BlockFace.UP || heading == BlockFace.DOWN) {
            // แนวดิ่ง: ไปต่อทางเดิมก่อน แล้วกระจายแนวนอนตามเข็ม N,E,S,W
            dirs.add(heading);
            for (BlockFace f : HORIZONTAL) {
                dirs.add(f);
            }
            return dirs;
        }
        BlockFace left = leftOf(heading);
        dirs.add(heading);                 // หน้า
        dirs.add(left);                    // ซ้าย
        dirs.add(left.getOppositeFace());  // ขวา
        dirs.add(BlockFace.UP);            // บน
        dirs.add(BlockFace.DOWN);          // ล่าง
        return dirs;
    }

    /** ทุกทิศเรียงตามลำดับความสำคัญ รวมทิศย้อนกลับไว้ท้ายสุด (ใช้ตอนหา output รอบกระจก) */
    public static List<BlockFace> orderedWithBack(BlockFace heading) {
        List<BlockFace> dirs = ordered(heading);
        BlockFace back = heading.getOppositeFace();
        if (!dirs.contains(back)) {
            dirs.add(back);
        }
        return dirs;
    }

    /** ทิศซ้ายเมื่อหันหน้าไป heading (อ้างอิงแกนตั้งของโลก) */
    public static BlockFace leftOf(BlockFace heading) {
        switch (heading) {
            case NORTH:
                return BlockFace.WEST;
            case WEST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.NORTH;
            default:
                return BlockFace.NORTH;
        }
    }
}
