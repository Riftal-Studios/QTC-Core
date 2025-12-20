package com.riftalstudios.qtccore.resources.template;

import org.bukkit.Material;
import org.bukkit.Location;

/**
 * Represents a block position relative to a base location.
 */
public record BlockOffset(int x, int y, int z, Material material) {

    /**
     * Apply this offset to a base location to get the absolute location.
     */
    public Location apply(Location base) {
        return base.clone().add(x, y, z);
    }

    /**
     * Create a BlockOffset from two locations (calculates relative offset).
     */
    public static BlockOffset fromLocations(Location base, Location block, Material material) {
        int x = block.getBlockX() - base.getBlockX();
        int y = block.getBlockY() - base.getBlockY();
        int z = block.getBlockZ() - base.getBlockZ();
        return new BlockOffset(x, y, z, material);
    }
}
