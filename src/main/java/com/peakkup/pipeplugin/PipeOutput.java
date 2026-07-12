package com.peakkup.pipeplugin;

import org.bukkit.Location;

/**
 * จุดปล่อยของหนึ่งจุด: piston (output) ที่ติดกับกระจกก้อนหนึ่งของท่อ และหันเข้า container
 */
public final class PipeOutput {

    private final Location glassBlock;   // กระจกในท่อที่ output piston เกาะอยู่
    private final Location piston;       // ตัว output piston
    private final Location destContainer; // กล่องปลายทางที่ piston หันเข้า

    public PipeOutput(Location glassBlock, Location piston, Location destContainer) {
        this.glassBlock = glassBlock;
        this.piston = piston;
        this.destContainer = destContainer;
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
}
