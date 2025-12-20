package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.resources.template.BlockOffset;
import com.riftalstudios.qtccore.resources.template.NodeTemplate;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ResourceNode {

    private final UUID id;
    private final Location baseLocation;
    private final ResourceType type;
    private final int maxHp;
    private int currentHp;
    private NodeState state;
    private long depletedAt;

    // Multi-block support
    private final String templateId;
    private final List<BlockOffset> blocks;

    /**
     * Legacy constructor for single-block nodes.
     */
    public ResourceNode(Location location, ResourceType type) {
        this(UUID.randomUUID(), location, type, type.getDefaultMaxHp(), null,
             List.of(new BlockOffset(0, 0, 0, type.getDefaultMaterial())));
    }

    /**
     * Constructor for template-based multi-block nodes.
     */
    public ResourceNode(Location baseLocation, NodeTemplate template) {
        this(UUID.randomUUID(), baseLocation, template.getType(), template.getType().getDefaultMaxHp(),
             template.getId(), template.getBlocks());
    }

    /**
     * Full constructor for loading from database.
     */
    public ResourceNode(UUID id, Location baseLocation, ResourceType type, int maxHp,
                        String templateId, List<BlockOffset> blocks) {
        this.id = id;
        this.baseLocation = baseLocation;
        this.type = type;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.state = NodeState.ACTIVE;
        this.depletedAt = 0;
        this.templateId = templateId;
        this.blocks = new ArrayList<>(blocks);
    }

    /**
     * Legacy constructor for database loading (backward compatibility).
     */
    public ResourceNode(UUID id, Location location, ResourceType type, int maxHp) {
        this(id, location, type, maxHp, null,
             List.of(new BlockOffset(0, 0, 0, type.getDefaultMaterial())));
    }

    public UUID getId() {
        return id;
    }

    /**
     * Get the base location of this node.
     */
    public Location getBaseLocation() {
        return baseLocation;
    }

    /**
     * Legacy method - returns base location.
     */
    public Location getLocation() {
        return baseLocation;
    }

    public ResourceType getType() {
        return type;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int hp) {
        this.currentHp = Math.max(0, Math.min(hp, maxHp));
        if (this.currentHp == 0 && state == NodeState.ACTIVE) {
            deplete();
        }
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public long getDepletedAt() {
        return depletedAt;
    }

    public boolean damage(int amount) {
        if (state != NodeState.ACTIVE) return false;
        int previousHp = currentHp;
        setCurrentHp(currentHp - amount);
        return previousHp > 0 && currentHp == 0;
    }

    public void deplete() {
        this.state = NodeState.DEPLETED;
        this.currentHp = 0;
        this.depletedAt = System.currentTimeMillis();
    }

    public void regenerate() {
        this.state = NodeState.ACTIVE;
        this.currentHp = maxHp;
        this.depletedAt = 0;
    }

    public boolean isDepleted() {
        return state == NodeState.DEPLETED || state == NodeState.REGENERATING;
    }

    // ==================== Multi-Block Support ====================

    /**
     * Get the template ID this node was created from (null for legacy nodes).
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * Get all block offsets that make up this node.
     */
    public List<BlockOffset> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    /**
     * Get the number of blocks in this node.
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Get all absolute locations of blocks in this node.
     */
    public List<Location> getAllBlockLocations() {
        List<Location> locations = new ArrayList<>();
        for (BlockOffset offset : blocks) {
            locations.add(offset.apply(baseLocation));
        }
        return locations;
    }

    /**
     * Check if a location is part of this node.
     */
    public boolean containsLocation(Location loc) {
        int targetX = loc.getBlockX();
        int targetY = loc.getBlockY();
        int targetZ = loc.getBlockZ();
        int baseX = baseLocation.getBlockX();
        int baseY = baseLocation.getBlockY();
        int baseZ = baseLocation.getBlockZ();

        for (BlockOffset offset : blocks) {
            if (baseX + offset.x() == targetX &&
                baseY + offset.y() == targetY &&
                baseZ + offset.z() == targetZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the block offset at a specific location (or null if not part of this node).
     */
    public BlockOffset getBlockAt(Location loc) {
        int targetX = loc.getBlockX();
        int targetY = loc.getBlockY();
        int targetZ = loc.getBlockZ();
        int baseX = baseLocation.getBlockX();
        int baseY = baseLocation.getBlockY();
        int baseZ = baseLocation.getBlockZ();

        for (BlockOffset offset : blocks) {
            if (baseX + offset.x() == targetX &&
                baseY + offset.y() == targetY &&
                baseZ + offset.z() == targetZ) {
                return offset;
            }
        }
        return null;
    }

    // ==================== Chunk Tracking ====================

    public int getChunkX() {
        return baseLocation.getBlockX() >> 4;
    }

    public int getChunkZ() {
        return baseLocation.getBlockZ() >> 4;
    }

    public String getWorldName() {
        return baseLocation.getWorld() != null ? baseLocation.getWorld().getName() : "unknown";
    }
}
