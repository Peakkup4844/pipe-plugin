package com.peakkup.pipeplugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * อ่านและถือค่าจาก config.yml
 */
public final class PipeConfig {

    private final int itemsPerCycle;
    private final int maxPipeLength;
    private final MatchMode matchMode;
    /** null = อนุญาตทุก Container */
    private final Set<Material> allowedContainers;

    public PipeConfig(PipePlugin plugin, PipeLang lang) {
        FileConfiguration cfg = plugin.getConfig();
        Logger log = plugin.getLogger();

        this.itemsPerCycle = Math.max(1, cfg.getInt("items-per-cycle", 32));
        this.maxPipeLength = Math.max(1, cfg.getInt("max-pipe-length", 64));
        this.matchMode = MatchMode.fromConfig(cfg.getString("filter-match", "SIMILAR"));

        Object raw = cfg.get("allowed-containers", "all");
        if (raw instanceof List<?> list) {
            Set<Material> set = EnumSet.noneOf(Material.class);
            for (Object o : list) {
                Material m = Material.matchMaterial(String.valueOf(o));
                if (m == null) {
                    log.warning(lang.msg("config.unknown-container", "material", String.valueOf(o)));
                } else {
                    set.add(m);
                }
            }
            this.allowedContainers = set.isEmpty() ? null : set;
        } else {
            // "all" หรือค่าอื่น ๆ ที่ไม่ใช่ list -> อนุญาตทุก Container
            this.allowedContainers = null;
        }
    }

    public int itemsPerCycle() {
        return itemsPerCycle;
    }

    public int maxPipeLength() {
        return maxPipeLength;
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public boolean isContainerAllowed(Material material) {
        return allowedContainers == null || allowedContainers.contains(material);
    }
}
