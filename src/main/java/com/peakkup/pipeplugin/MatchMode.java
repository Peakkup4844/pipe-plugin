package com.peakkup.pipeplugin;

import org.bukkit.inventory.ItemStack;

/**
 * โหมดเทียบไอเทมของ filter (item frame)
 */
public enum MatchMode {
    /** เทียบเฉพาะชนิด (Material) */
    TYPE,
    /** เทียบ NBT เป๊ะ: ชนิด + ชื่อ + enchant + meta (ไม่นับจำนวน) */
    SIMILAR;

    public boolean matches(ItemStack filter, ItemStack item) {
        if (filter == null || item == null) {
            return false;
        }
        return this == TYPE ? filter.getType() == item.getType() : filter.isSimilar(item);
    }

    public static MatchMode fromConfig(String raw) {
        if (raw != null && raw.equalsIgnoreCase("TYPE")) {
            return TYPE;
        }
        return SIMILAR;
    }
}
