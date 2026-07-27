package com.peakkup.pipeplugin;

import com.peakkup.pipeplugin.listener.NetworkInvalidationListener;
import com.peakkup.pipeplugin.listener.PistonGuardListener;
import com.peakkup.pipeplugin.listener.RedstoneTriggerListener;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ระบบท่อขนไอเทมแบบ Create:
 *   sticky piston (input, หันเข้า container) -> ท่อกระจกสีเดียวกัน -> piston (output, หันเข้า container)
 * จ่าย redstone pulse 1 ครั้ง = ย้ายของ items-per-cycle ชิ้น
 *
 * รองรับ Bukkit/Spigot, Paper และ Folia (ผ่าน FoliaLib) ตั้งแต่ MC 1.20.1 ขึ้นไป
 */
public final class PipePlugin extends JavaPlugin {

    private FoliaLib foliaLib;
    private PipeConfig pipeConfig;
    private PipeLang lang;
    private NetworkRegistry registry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.foliaLib = new FoliaLib(this);
        this.lang = new PipeLang(this);
        this.pipeConfig = new PipeConfig(this, lang);
        this.registry = new NetworkRegistry();

        ExternalStorageGuard storageGuard = new ExternalStorageGuard(this, pipeConfig, lang);
        ContainerAccess containers = new ContainerAccess(pipeConfig, storageGuard);
        NetworkDiscovery discovery = new NetworkDiscovery(pipeConfig, containers);
        PipeRouter router = new PipeRouter(pipeConfig.matchMode());
        RegionExecutor regions = new RegionExecutor(foliaLib);
        ItemTransferService transferService =
                new ItemTransferService(regions, pipeConfig, router, lang, containers);

        getServer().getPluginManager().registerEvents(
                new RedstoneTriggerListener(foliaLib, registry, discovery, transferService, pipeConfig), this);
        getServer().getPluginManager().registerEvents(
                new PistonGuardListener(registry), this);
        getServer().getPluginManager().registerEvents(
                new NetworkInvalidationListener(registry), this);

        getLogger().info(lang.msg("plugin-enabled",
                "platform", foliaLib.isFolia() ? "Folia" : "Bukkit/Paper"));
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.clear();
        }
    }
}
