package com.riftalstudios.qtccore.resources.packet;

import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunk-indexed node storage and per-player viewer tracking.
 * Core component for the pure packet-based node display system.
 *
 * Key design:
 * - Nodes indexed by chunk position for fast CHUNK_DATA packet modification
 * - Individual block locations indexed for fast BLOCK_CHANGE interception
 * - Per-player viewer tracking (node is visible only if player is a viewer)
 */
public class NodeBlockManager {

    // ChunkPosition key -> Map of (block position key -> ResourceNode)
    // Each node may appear in multiple block positions (multi-block support)
    private final Map<ChunkPosition, Map<WorldPosition, ResourceNode>> chunkBlocks = new ConcurrentHashMap<>();

    // NodeId -> Set of player UUIDs who can see this node
    private final Map<UUID, Set<UUID>> nodeViewers = new ConcurrentHashMap<>();

    // Quick lookup: block position -> node
    private final Map<WorldPosition, ResourceNode> blockToNode = new ConcurrentHashMap<>();

    /**
     * Register a node for packet-based display.
     * Adds all block positions to the chunk index.
     */
    public void registerNode(ResourceNode node) {
        Location base = node.getBaseLocation();
        World world = base.getWorld();
        if (world == null) return;

        String worldName = world.getName();

        for (BlockOffset offset : node.getBlocks()) {
            Location blockLoc = offset.apply(base);
            WorldPosition pos = new WorldPosition(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            ChunkPosition chunk = new ChunkPosition(worldName, blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4);

            chunkBlocks.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>()).put(pos, node);
            blockToNode.put(pos, node);
        }

        // Initialize empty viewer set
        nodeViewers.computeIfAbsent(node.getId(), k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Unregister a node from the system.
     */
    public void unregisterNode(ResourceNode node) {
        Location base = node.getBaseLocation();
        World world = base.getWorld();
        if (world == null) return;

        String worldName = world.getName();

        for (BlockOffset offset : node.getBlocks()) {
            Location blockLoc = offset.apply(base);
            WorldPosition pos = new WorldPosition(worldName, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            ChunkPosition chunk = new ChunkPosition(worldName, blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4);

            Map<WorldPosition, ResourceNode> chunkMap = chunkBlocks.get(chunk);
            if (chunkMap != null) {
                chunkMap.remove(pos);
                if (chunkMap.isEmpty()) {
                    chunkBlocks.remove(chunk);
                }
            }
            blockToNode.remove(pos);
        }

        nodeViewers.remove(node.getId());
    }

    /**
     * Get a node at the specified block position.
     * @return The node at this position, or null if none
     */
    public ResourceNode getNodeAt(Location location) {
        if (location.getWorld() == null) return null;
        WorldPosition pos = new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
        return blockToNode.get(pos);
    }

    /**
     * Get a node at the specified block position.
     */
    public ResourceNode getNodeAt(String worldName, int x, int y, int z) {
        return blockToNode.get(new WorldPosition(worldName, x, y, z));
    }

    /**
     * Get all nodes in a specific chunk (for CHUNK_DATA modification).
     * Returns map of block position -> node.
     */
    public Map<WorldPosition, ResourceNode> getNodesInChunk(String worldName, int chunkX, int chunkZ) {
        ChunkPosition chunk = new ChunkPosition(worldName, chunkX, chunkZ);
        return chunkBlocks.getOrDefault(chunk, Collections.emptyMap());
    }

    /**
     * Get all nodes in a chunk that are visible to a specific player.
     */
    public Map<WorldPosition, ResourceNode> getVisibleNodesInChunk(Player player, String worldName, int chunkX, int chunkZ) {
        Map<WorldPosition, ResourceNode> allNodes = getNodesInChunk(worldName, chunkX, chunkZ);
        if (allNodes.isEmpty()) return Collections.emptyMap();

        Map<WorldPosition, ResourceNode> visible = new HashMap<>();
        UUID playerId = player.getUniqueId();

        for (Map.Entry<WorldPosition, ResourceNode> entry : allNodes.entrySet()) {
            ResourceNode node = entry.getValue();
            if (isViewer(node.getId(), playerId)) {
                visible.put(entry.getKey(), node);
            }
        }
        return visible;
    }

    /**
     * Check if a player is a viewer of a specific node.
     */
    public boolean isViewer(UUID nodeId, UUID playerId) {
        Set<UUID> viewers = nodeViewers.get(nodeId);
        return viewers != null && viewers.contains(playerId);
    }

    /**
     * Check if a player is a viewer of a specific node.
     */
    public boolean isViewer(ResourceNode node, Player player) {
        return isViewer(node.getId(), player.getUniqueId());
    }

    /**
     * Add a player as a viewer of a node.
     */
    public void addViewer(UUID nodeId, UUID playerId) {
        nodeViewers.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    /**
     * Add a player as a viewer of a node.
     */
    public void addViewer(ResourceNode node, Player player) {
        addViewer(node.getId(), player.getUniqueId());
    }

    /**
     * Remove a player from viewing a node.
     */
    public void removeViewer(UUID nodeId, UUID playerId) {
        Set<UUID> viewers = nodeViewers.get(nodeId);
        if (viewers != null) {
            viewers.remove(playerId);
        }
    }

    /**
     * Remove a player from viewing a node.
     */
    public void removeViewer(ResourceNode node, Player player) {
        removeViewer(node.getId(), player.getUniqueId());
    }

    /**
     * Remove a player from all nodes (on quit).
     */
    public void removePlayerFromAllNodes(UUID playerId) {
        for (Set<UUID> viewers : nodeViewers.values()) {
            viewers.remove(playerId);
        }
    }

    /**
     * Get all viewers of a node.
     */
    public Set<UUID> getViewers(UUID nodeId) {
        return nodeViewers.getOrDefault(nodeId, Collections.emptySet());
    }

    /**
     * Get all node IDs that a player is viewing.
     */
    public Set<UUID> getNodesViewedBy(UUID playerId) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : nodeViewers.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Clear all data.
     */
    public void clear() {
        chunkBlocks.clear();
        nodeViewers.clear();
        blockToNode.clear();
    }

    /**
     * Get total registered node count.
     */
    public int getNodeCount() {
        return nodeViewers.size();
    }

    /**
     * Get total block position count.
     */
    public int getBlockPositionCount() {
        return blockToNode.size();
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a chunk position (world + chunk X/Z).
     */
    public record ChunkPosition(String worldName, int chunkX, int chunkZ) {
        public static ChunkPosition of(Location loc) {
            return new ChunkPosition(
                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getBlockX() >> 4,
                    loc.getBlockZ() >> 4
            );
        }
    }

    /**
     * Represents a world block position.
     */
    public record WorldPosition(String worldName, int x, int y, int z) {
        public static WorldPosition of(Location loc) {
            return new WorldPosition(
                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            );
        }
    }
}
