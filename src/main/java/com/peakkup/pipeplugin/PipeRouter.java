package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * จัดเส้นทางของไอเทมหนึ่งชนิดผ่านท่อ โดยเคารพ filter gate
 * เดินแบบ BFS (ใกล้ก่อน) และที่ทางแยกเรียงทิศ หน้า->ซ้าย->ขวา->บน->ล่าง (ซ้ายก่อนขวา)
 * คืนรายการ output ที่ไอเทมนี้ไปถึงได้และผ่าน gate ของ output นั้น เรียงตามลำดับความสำคัญ
 */
public final class PipeRouter {

    private final MatchMode matchMode;

    public PipeRouter(MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    private static final class Node {
        final Block block;
        final BlockFace heading;

        Node(Block block, BlockFace heading) {
            this.block = block;
            this.heading = heading;
        }
    }

    public List<PipeOutput> route(PipeNetwork net, ItemStack item, FrameIndex frames) {
        List<PipeOutput> result = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Queue<Node> queue = new ArrayDeque<>();

        Block input = net.inputPiston().getBlock();
        BlockFace facing = NetworkDiscovery.facingOf(input);
        if (facing == null) {
            return result;
        }
        // gate การดูดที่ input piston: ถ้าไอเทมไม่ผ่าน frame บน input piston -> ดูดไม่ได้เลย
        if (!frames.passes(input, item, matchMode)) {
            return result;
        }
        // ทิศ "หน้า" ของท่อ = ออกจากต้นทาง (ตรงข้ามกับด้านที่ piston หันเข้ากล่อง)
        BlockFace rootHeading = facing.getOppositeFace();

        // เริ่มจากกระจกที่ติด input piston (ผ่าน gate ของกระจกก้อนแรก)
        for (BlockFace d : Directions.ordered(rootHeading)) {
            Block n = input.getRelative(d);
            Location nl = n.getLocation();
            if (!net.pipeBlocks().contains(nl)) {
                continue;
            }
            if (!frames.passes(n, item, matchMode)) {
                continue;
            }
            if (visited.add(nl)) {
                queue.add(new Node(n, d));
            }
        }

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            Block g = node.block;
            BlockFace h = node.heading;

            // เก็บ output ที่เกาะกระจกก้อนนี้ เรียงตามลำดับทิศ (รวมด้านหลังไว้ท้ายสุด)
            List<PipeOutput> here = net.outputsAtGlass(g.getLocation());
            if (!here.isEmpty()) {
                for (BlockFace d : Directions.orderedWithBack(h)) {
                    Block pistonBlock = g.getRelative(d);
                    Location pistonLoc = pistonBlock.getLocation();
                    for (PipeOutput out : here) {
                        if (out.piston().equals(pistonLoc)
                                && frames.passes(pistonBlock, item, matchMode)
                                && !result.contains(out)) {
                            result.add(out);
                        }
                    }
                }
            }

            // ขยายต่อตามลำดับทิศ (ผ่าน gate ของกระจกก้อนถัดไป)
            for (BlockFace d : Directions.ordered(h)) {
                Block n = g.getRelative(d);
                Location nl = n.getLocation();
                if (!net.pipeBlocks().contains(nl) || visited.contains(nl)) {
                    continue;
                }
                if (!frames.passes(n, item, matchMode)) {
                    continue;
                }
                visited.add(nl);
                queue.add(new Node(n, d));
            }
        }

        return result;
    }
}
