package com.riftalstudios.qtccore.resources;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Broad categories of resource nodes that define gameplay behavior.
 * Visual appearance is determined by templates, not by type.
 */
public enum ResourceType {
    TREE(Material.OAK_LOG, 10, 30, Sound.BLOCK_WOOD_HIT, Sound.BLOCK_WOOD_BREAK, "AXE"),
    ORE(Material.STONE, 15, 60, Sound.BLOCK_STONE_HIT, Sound.BLOCK_STONE_BREAK, "PICKAXE");

    private final Material fallbackMaterial; // Only used if template has no blocks
    private final int defaultMaxHp;
    private final int defaultRespawnSeconds;
    private final Sound hitSound;
    private final Sound breakSound;
    private final String requiredToolType; // AXE, PICKAXE, etc.

    ResourceType(Material fallbackMaterial, int defaultMaxHp, int defaultRespawnSeconds,
                 Sound hitSound, Sound breakSound, String requiredToolType) {
        this.fallbackMaterial = fallbackMaterial;
        this.defaultMaxHp = defaultMaxHp;
        this.defaultRespawnSeconds = defaultRespawnSeconds;
        this.hitSound = hitSound;
        this.breakSound = breakSound;
        this.requiredToolType = requiredToolType;
    }

    public Material getDisplayBlock() {
        return fallbackMaterial;
    }

    public int getDefaultMaxHp() {
        return defaultMaxHp;
    }

    public int getDefaultRespawnSeconds() {
        return defaultRespawnSeconds;
    }

    public Sound getHitSound() {
        return hitSound;
    }

    public Sound getBreakSound() {
        return breakSound;
    }

    public String getRequiredToolType() {
        return requiredToolType;
    }

    /**
     * Check if a tool is effective for this resource type.
     */
    public boolean isCorrectTool(Material tool) {
        String toolName = tool.name();
        return toolName.contains(requiredToolType);
    }

    /**
     * Alias for getDisplayBlock - used for template compatibility.
     */
    public Material getDefaultMaterial() {
        return fallbackMaterial;
    }

    /**
     * Alias for getDefaultMaxHp - used for template compatibility.
     */
    public int getDefaultHp() {
        return defaultMaxHp;
    }
}
