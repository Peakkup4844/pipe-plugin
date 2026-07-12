package com.peakkup.pipeplugin;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * เทสต์ตรรกะลำดับทิศ (pure ไม่พึ่ง server) — หัวใจของกฎ "ซ้ายก่อนขวา"
 */
class DirectionsTest {

    @Test
    void leftOfHorizontalIsCounterClockwise() {
        assertEquals(BlockFace.WEST, Directions.leftOf(BlockFace.NORTH));
        assertEquals(BlockFace.SOUTH, Directions.leftOf(BlockFace.WEST));
        assertEquals(BlockFace.EAST, Directions.leftOf(BlockFace.SOUTH));
        assertEquals(BlockFace.NORTH, Directions.leftOf(BlockFace.EAST));
    }

    @Test
    void leftOfVerticalFallsBackToNorth() {
        assertEquals(BlockFace.NORTH, Directions.leftOf(BlockFace.UP));
        assertEquals(BlockFace.NORTH, Directions.leftOf(BlockFace.DOWN));
    }

    @Test
    void orderedHorizontalIsFrontLeftRightUpDown() {
        assertEquals(
                List.of(BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN),
                Directions.ordered(BlockFace.NORTH));
        assertEquals(
                List.of(BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN),
                Directions.ordered(BlockFace.EAST));
    }

    @Test
    void orderedHorizontalNeverContainsBack() {
        for (BlockFace h : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            assertTrue(!Directions.ordered(h).contains(h.getOppositeFace()),
                    "ordered(" + h + ") ต้องไม่มีทิศย้อนกลับ");
        }
    }

    @Test
    void orderedVerticalContinuesThenSpreadsHorizontally() {
        assertEquals(
                List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST),
                Directions.ordered(BlockFace.UP));
        assertEquals(
                List.of(BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST),
                Directions.ordered(BlockFace.DOWN));
    }

    @Test
    void leftComesBeforeRightForEveryHorizontalHeading() {
        for (BlockFace h : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            List<BlockFace> order = Directions.ordered(h);
            BlockFace left = Directions.leftOf(h);
            BlockFace right = left.getOppositeFace();
            assertTrue(order.indexOf(left) < order.indexOf(right),
                    "heading " + h + ": ซ้าย (" + left + ") ต้องมาก่อนขวา (" + right + ")");
        }
    }

    @Test
    void orderedWithBackAppendsBackLast() {
        List<BlockFace> withBack = Directions.orderedWithBack(BlockFace.NORTH);
        assertEquals(
                List.of(BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST,
                        BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH),
                withBack);
    }

    @Test
    void orderedWithBackForVerticalAddsOpposite() {
        List<BlockFace> withBack = Directions.orderedWithBack(BlockFace.UP);
        assertEquals(BlockFace.DOWN, withBack.get(withBack.size() - 1));
        assertEquals(6, withBack.size());
    }
}
