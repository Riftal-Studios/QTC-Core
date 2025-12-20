package com.riftalstudios.qtccore.resources;

import org.bukkit.Material;

public enum ResourceType {
    OAK_TREE(Material.OAK_LOG, 5, 10),
    BIRCH_TREE(Material.BIRCH_LOG, 5, 10),
    SPRUCE_TREE(Material.SPRUCE_LOG, 5, 10),
    DARK_OAK_TREE(Material.DARK_OAK_LOG, 5, 10),
    IRON_ORE(Material.IRON_ORE, 5, 10),
    GOLD_ORE(Material.GOLD_ORE, 5, 10),
    DIAMOND_ORE(Material.DIAMOND_ORE, 5, 10),
    COAL_ORE(Material.COAL_ORE, 5, 10);

    private final Material displayBlock;
    private final int defaultMaxHp;
    private final int defaultRespawnSeconds;

    ResourceType(Material displayBlock, int defaultMaxHp, int defaultRespawnSeconds) {
        this.displayBlock = displayBlock;
        this.defaultMaxHp = defaultMaxHp;
        this.defaultRespawnSeconds = defaultRespawnSeconds;
    }

    public Material getDisplayBlock() {
        return displayBlock;
    }

    public int getDefaultMaxHp() {
        return defaultMaxHp;
    }

    public int getDefaultRespawnSeconds() {
        return defaultRespawnSeconds;
    }
}
