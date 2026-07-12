package com.peakkup.pipeplugin.listener;

import com.peakkup.pipeplugin.NetworkRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/**
 * กันไม่ให้ piston ที่เป็นส่วนของท่อ ขยับจริง (เผื่อกรณีหน้า piston เป็นบล็อกที่ดันได้)
 * piston ในท่อทำหน้าที่เป็นแค่ marker/trigger เท่านั้น
 */
public final class PistonGuardListener implements Listener {

    private final NetworkRegistry registry;

    public PistonGuardListener(NetworkRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (registry.isTracked(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (registry.isTracked(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
