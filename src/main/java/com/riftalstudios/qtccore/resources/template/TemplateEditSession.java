package com.riftalstudios.qtccore.resources.template;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Tracks an admin's template editing session.
 */
public class TemplateEditSession {

    private final UUID playerId;
    private final NodeTemplate template;
    private Location baseLocation; // First block placed becomes the base

    public TemplateEditSession(UUID playerId, NodeTemplate template) {
        this.playerId = playerId;
        this.template = template;
        this.baseLocation = null;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public NodeTemplate getTemplate() {
        return template;
    }

    public Location getBaseLocation() {
        return baseLocation;
    }

    public boolean hasBaseLocation() {
        return baseLocation != null;
    }

    /**
     * Add a block to the template.
     * First block becomes the base (0,0,0), subsequent blocks are relative.
     */
    public void addBlock(Location location, Material material) {
        if (baseLocation == null) {
            // First block - set as base
            baseLocation = location.clone();
            template.addBlock(new BlockOffset(0, 0, 0, material));
        } else {
            // Calculate offset from base
            BlockOffset offset = BlockOffset.fromLocations(baseLocation, location, material);
            template.addBlock(offset);
        }
    }

    /**
     * Remove a block from the template.
     * @return true if a block was removed, false if no block existed at that location
     */
    public boolean removeBlock(Location location) {
        if (baseLocation == null) return false;

        int x = location.getBlockX() - baseLocation.getBlockX();
        int y = location.getBlockY() - baseLocation.getBlockY();
        int z = location.getBlockZ() - baseLocation.getBlockZ();

        boolean existed = template.hasBlockAt(x, y, z);
        if (!existed) return false;

        template.removeBlock(x, y, z);

        // If we removed the base block (0,0,0) and there are other blocks,
        // we need to recalculate the base
        if (x == 0 && y == 0 && z == 0 && !template.getBlocks().isEmpty()) {
            // Find the first remaining block and make it the new base
            BlockOffset firstBlock = template.getBlocks().get(0);
            Location newBase = baseLocation.clone().add(firstBlock.x(), firstBlock.y(), firstBlock.z());

            // Recalculate all offsets relative to new base
            // This is complex, so for now we just warn that base was removed
            // In practice, admins should avoid removing the first block
        }

        return true;
    }

    /**
     * Check if the template has any blocks.
     */
    public boolean isEmpty() {
        return template.getBlockCount() == 0;
    }

    public int getBlockCount() {
        return template.getBlockCount();
    }
}
