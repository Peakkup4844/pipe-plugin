package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * โลกจำลองสำหรับเทสต์: กริดบล็อก mock ที่เดิน getRelative/getLocation/getBlockAt ได้ถูกต้อง
 * ไม่ต้องรัน server จริง — ใช้ทดสอบตรรกะ traversal ของ {@link PipeRouter}
 */
final class GridWorld {

    final World world = mock(World.class);
    private final Map<String, Block> cache = new HashMap<>();

    GridWorld() {
        when(world.getBlockAt(any(Location.class))).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            return block(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        });
    }

    /** บล็อกที่พิกัด (สร้าง lazy, cache ให้เป็น instance เดียวกันเสมอ) */
    Block block(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        Block existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        Block b = mock(Block.class);
        cache.put(key, b); // ใส่ก่อนผูก getRelative กันลูปไม่รู้จบ
        Location loc = new Location(world, x, y, z);
        when(b.getLocation()).thenReturn(loc);
        when(b.getWorld()).thenReturn(world);
        when(b.getRelative(any(BlockFace.class))).thenAnswer(inv -> {
            BlockFace f = inv.getArgument(0);
            return block(x + f.getModX(), y + f.getModY(), z + f.getModZ());
        });
        return b;
    }

    Location loc(int x, int y, int z) {
        return block(x, y, z).getLocation();
    }

    /** ตั้งให้บล็อกนี้เป็น piston/sticky piston ที่หันหน้าไปทาง facing (เพื่อให้ facingOf อ่านได้) */
    void setFacing(int x, int y, int z, BlockFace facing) {
        Directional dir = mock(Directional.class);
        when(dir.getFacing()).thenReturn(facing);
        when(block(x, y, z).getBlockData()).thenReturn(dir);
    }
}
