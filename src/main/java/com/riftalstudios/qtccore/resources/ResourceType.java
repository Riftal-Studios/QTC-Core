package com.riftalstudios.qtccore.resources;

import org.bukkit.Material;

public enum ResourceType {
    OAK_TREE(Material.OAK_LOG, 100, 60),
    BIRCH_TREE(Material.BIRCH_LOG, 80, 45),
    SPRUCE_TREE(Material.SPRUCE_LOG, 120, 75),
    DARK_OAK_TREE(Material.DARK_OAK_LOG, 150, 90),
    IRON_ORE(Material.IRON_ORE, 200, 120),
    GOLD_ORE(Material.GOLD_ORE, 250, 180),
    DIAMOND_ORE(Material.DIAMOND_ORE, 400, 300),
    COAL_ORE(Material.COAL_ORE, 100, 60);

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
