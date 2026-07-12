package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * ค้นหาและตรวจสอบ "โทโพโลยี" ของท่อจาก sticky piston ด้วย BFS
 * เก็บกระจกสีเดียวกันทั้งหมด + output piston ทุกตัวที่ติดท่อ (รองรับหลาย output)
 * ไม่สนใจ filter ที่ขั้นนี้ — filter เป็นเรื่อง flow ของไอเทม อ่านสดตอนย้ายของ
 */
public final class NetworkDiscovery {

    static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final PipeConfig config;

    public NetworkDiscovery(PipeConfig config) {
        this.config = config;
    }

    /** คืน network ถ้า block นี้เป็น input piston ที่ต่อท่อถึง output อย่างน้อย 1 จุด ไม่งั้นคืน null */
    public PipeNetwork discover(Block stickyPiston) {
        if (stickyPiston.getType() != Material.STICKY_PISTON) {
            return null;
        }

        BlockFace facing = facingOf(stickyPiston);
        if (facing == null) {
            return null;
        }
        Block source = stickyPiston.getRelative(facing);
        if (!isAllowedContainer(source)) {
            return null;
        }

        // หาสีท่อจากกระจกที่ติด sticky piston (ต้องมีสีเดียว)
        Material glassColor = null;
        List<Block> startGlass = new ArrayList<>();
        for (BlockFace f : FACES) {
            Block n = stickyPiston.getRelative(f);
            Material m = n.getType();
            if (!isPipeGlass(m)) {
                continue;
            }
            if (glassColor == null) {
                glassColor = m;
            } else if (glassColor != m) {
                return null; // ติดกระจกหลายสี -> reject
            }
            startGlass.add(n);
        }
        if (glassColor == null) {
            return null;
        }

        // BFS กระจกสีเดียวกันทั้งหมด + เก็บ output ระหว่างทาง
        Set<Location> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        for (Block g : startGlass) {
            if (visited.add(g.getLocation())) {
                queue.add(g);
            }
        }

        List<PipeOutput> outputs = new ArrayList<>();
        Map<Location, List<PipeOutput>> outputsByGlass = new HashMap<>();
        Set<Location> outputPistons = new HashSet<>();
        int maxLength = config.maxPipeLength();

        while (!queue.isEmpty()) {
            if (visited.size() > maxLength) {
                return null; // ท่อยาวเกินกำหนด
            }
            Block glass = queue.poll();

            for (BlockFace f : FACES) {
                Block n = glass.getRelative(f);
                Material nm = n.getType();

                // เจอ output piston (piston ธรรมดา หันเข้า container)
                if (nm == Material.PISTON && !outputPistons.contains(n.getLocation())) {
                    BlockFace pf = facingOf(n);
                    if (pf != null) {
                        Block dest = n.getRelative(pf);
                        if (isAllowedContainer(dest)) {
                            PipeOutput out = new PipeOutput(
                                    glass.getLocation(), n.getLocation(), dest.getLocation());
                            outputs.add(out);
                            outputPistons.add(n.getLocation());
                            outputsByGlass.computeIfAbsent(glass.getLocation(), k -> new ArrayList<>())
                                    .add(out);
                        }
                    }
                    continue;
                }

                // ขยาย BFS ผ่านกระจกสีเดียวกัน
                if (nm == glassColor && visited.add(n.getLocation())) {
                    queue.add(n);
                }
            }
        }

        if (outputs.isEmpty()) {
            return null; // ไม่มี output
        }

        return new PipeNetwork(
                stickyPiston.getLocation(),
                source.getLocation(),
                glassColor,
                visited,
                outputs,
                outputsByGlass
        );
    }

    /** เช็คเร็ว ๆ ว่า network ที่ cache ไว้ยังพอใช้ได้ (input + ต้นทาง + ยังมี output) */
    public boolean isStillValid(PipeNetwork net) {
        Block input = net.inputPiston().getBlock();
        if (input.getType() != Material.STICKY_PISTON) {
            return false;
        }
        if (!isAllowedContainer(net.sourceContainer().getBlock())) {
            return false;
        }
        return !net.outputs().isEmpty();
    }

    boolean isAllowedContainer(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Container)) {
            return false;
        }
        return config.isContainerAllowed(block.getType());
    }

    static BlockFace facingOf(Block block) {
        BlockData data = block.getBlockData();
        return (data instanceof Directional dir) ? dir.getFacing() : null;
    }

    public static boolean isPipeGlass(Material m) {
        if (m == Material.GLASS || m == Material.TINTED_GLASS) {
            return true;
        }
        String name = m.name();
        return name.endsWith("STAINED_GLASS") && !name.endsWith("PANE");
    }
}
