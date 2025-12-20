package com.riftalstudios.qtccore.resources.template;

import com.riftalstudios.qtccore.resources.ResourceType;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A reusable template defining the shape of a resource node.
 * Templates are created by admins and can be placed multiple times.
 */
public class NodeTemplate {

    private final String id;
    private final ResourceType type;
    private final List<BlockOffset> blocks;

    public NodeTemplate(String id, ResourceType type) {
        this.id = id;
        this.type = type;
        this.blocks = new ArrayList<>();
    }

    public NodeTemplate(String id, ResourceType type, List<BlockOffset> blocks) {
        this.id = id;
        this.type = type;
        this.blocks = new ArrayList<>(blocks);
    }

    public String getId() {
        return id;
    }

    public ResourceType getType() {
        return type;
    }

    public List<BlockOffset> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public void addBlock(BlockOffset offset) {
        // Don't add duplicates at same position
        for (BlockOffset existing : blocks) {
            if (existing.x() == offset.x() && existing.y() == offset.y() && existing.z() == offset.z()) {
                return;
            }
        }
        blocks.add(offset);
    }

    public void removeBlock(int x, int y, int z) {
        blocks.removeIf(b -> b.x() == x && b.y() == y && b.z() == z);
    }

    public boolean hasBlockAt(int x, int y, int z) {
        return blocks.stream().anyMatch(b -> b.x() == x && b.y() == y && b.z() == z);
    }

    /**
     * Get all absolute locations for this template placed at a base location.
     */
    public List<Location> getLocationsAt(Location base) {
        List<Location> locations = new ArrayList<>();
        for (BlockOffset offset : blocks) {
            locations.add(offset.apply(base));
        }
        return locations;
    }

    /**
     * Check if this template is valid (has at least one block).
     */
    public boolean isValid() {
        return !blocks.isEmpty();
    }
}
