package com.peakkup.pipeplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * เทสต์ตรรกะการจัดเส้นทางของ {@link PipeRouter} ด้วยโลกจำลอง (ไม่มี filter — ใช้ FrameIndex.empty)
 *
 * วางผังให้ input piston อยู่ที่ (0,0,0) หันหน้า NORTH (ต้นทางอยู่ z-1)
 * => "หน้า" ของท่อ = SOUTH (+z), ซ้าย = EAST (leftOf(SOUTH)), ขวา = WEST
 */
class PipeRouterTest {

    private final PipeRouter router = new PipeRouter(MatchMode.SIMILAR);
    private final ItemStack anyItem = mock(ItemStack.class);

    /** ตัวช่วยสร้าง PipeNetwork จากกริด */
    private static final class Builder {
        final GridWorld grid = new GridWorld();
        final Set<Location> pipe = new HashSet<>();
        final List<PipeOutput> outputs = new ArrayList<>();
        final Map<Location, List<PipeOutput>> byGlass = new HashMap<>();

        Builder() {
            grid.setFacing(0, 0, 0, BlockFace.NORTH); // input piston
        }

        Builder glass(int x, int y, int z) {
            pipe.add(grid.loc(x, y, z));
            return this;
        }

        /** output: piston ที่ (px,py,pz) เกาะกระจก (gx,gy,gz) ปล่อยลง dest (dx,dy,dz) */
        PipeOutput output(int gx, int gy, int gz, int px, int py, int pz, int dx, int dy, int dz) {
            PipeOutput out = new PipeOutput(grid.loc(gx, gy, gz), grid.loc(px, py, pz), grid.loc(dx, dy, dz));
            outputs.add(out);
            byGlass.computeIfAbsent(grid.loc(gx, gy, gz), k -> new ArrayList<>()).add(out);
            return out;
        }

        PipeNetwork build() {
            return new PipeNetwork(
                    grid.loc(0, 0, 0),
                    grid.loc(0, 0, -1),
                    Material.GLASS,
                    pipe, outputs, byGlass);
        }
    }

    @Test
    void straightLineReachesSingleOutput() {
        Builder b = new Builder();
        b.glass(0, 0, 1).glass(0, 0, 2).glass(0, 0, 3);
        PipeOutput out = b.output(0, 0, 3, 0, 0, 4, 0, 0, 5);

        List<PipeOutput> route = router.route(b.build(), anyItem, FrameIndex.empty());

        assertEquals(1, route.size());
        assertSame(out, route.get(0));
    }

    @Test
    void noOutputGivesEmptyRoute() {
        Builder b = new Builder();
        b.glass(0, 0, 1).glass(0, 0, 2);

        List<PipeOutput> route = router.route(b.build(), anyItem, FrameIndex.empty());

        assertTrue(route.isEmpty());
    }

    @Test
    void nearerOutputComesBeforeFartherOutput() {
        Builder b = new Builder();
        b.glass(0, 0, 1).glass(0, 0, 2).glass(0, 0, 3).glass(0, 0, 4);
        PipeOutput near = b.output(0, 0, 2, 1, 0, 2, 2, 0, 2);  // east piston ที่ระยะ 2
        PipeOutput far = b.output(0, 0, 4, 1, 0, 4, 2, 0, 4);   // east piston ที่ระยะ 4

        List<PipeOutput> route = router.route(b.build(), anyItem, FrameIndex.empty());

        assertEquals(List.of(near, far), route);
    }

    @Test
    void atJunctionLeftBranchComesBeforeRightBranch() {
        Builder b = new Builder();
        // จุดแยกที่ (0,0,1): ซ้าย = EAST(+x), ขวา = WEST(-x)
        b.glass(0, 0, 1);
        b.glass(1, 0, 1);   // กิ่งซ้าย
        b.glass(-1, 0, 1);  // กิ่งขวา
        PipeOutput left = b.output(1, 0, 1, 2, 0, 1, 3, 0, 1);
        PipeOutput right = b.output(-1, 0, 1, -2, 0, 1, -3, 0, 1);

        List<PipeOutput> route = router.route(b.build(), anyItem, FrameIndex.empty());

        assertEquals(List.of(left, right), route,
                "ที่ทางแยก output กิ่งซ้ายต้องมาก่อนกิ่งขวา");
    }

    @Test
    void multipleOutputsOnSameGlassAreAllReturned() {
        Builder b = new Builder();
        b.glass(0, 0, 1);
        // กระจกก้อนเดียวมี output 2 ตัว (ซ้าย=east, ขวา=west)
        PipeOutput left = b.output(0, 0, 1, 1, 0, 1, 2, 0, 1);
        PipeOutput right = b.output(0, 0, 1, -1, 0, 1, -2, 0, 1);

        List<PipeOutput> route = router.route(b.build(), anyItem, FrameIndex.empty());

        assertEquals(List.of(left, right), route);
    }
}
