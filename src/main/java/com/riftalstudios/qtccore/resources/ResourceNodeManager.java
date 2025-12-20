package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.storage.DatabaseManager;
import com.riftalstudios.qtccore.storage.ResourceNodeStorage;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceNodeManager implements Manager, Listener {

    private final QTCCore plugin;
    private final DatabaseManager database;
    private final ResourceNodeStorage storage;
    private final Map<UUID, ResourceNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> chunkNodeMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<UUID>> locationNodeMap = new ConcurrentHashMap<>();

    private PlayerNodeStateManager stateManager;
    private NodeDisplayManager displayManager;
    private ResourceNodeRegenerator regenerator;
    private ResourceNodeBreakHandler breakHandler;

    public ResourceNodeManager(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.storage = new ResourceNodeStorage(plugin, database);
    }

    public void initializeDisplay() {
        this.stateManager = new PlayerNodeStateManager();
        this.displayManager = new NodeDisplayManager(plugin, stateManager);
        this.regenerator = new ResourceNodeRegenerator(plugin, this, stateManager, displayManager);
        this.breakHandler = new ResourceNodeBreakHandler(plugin, this, stateManager);
        this.breakHandler.register();
        this.regenerator.startRegenerationTask();
    }

    public NodeDisplayManager getDisplayManager() {
        return displayManager;
    }

    public ResourceNodeRegenerator getRegenerator() {
        return regenerator;
    }

    public PlayerNodeStateManager getStateManager() {
        return stateManager;
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ResourceNodeManager enabled");
    }

    @Override
    public void disable() {
        if (breakHandler != null) {
            breakHandler.unregister();
        }
        if (regenerator != null) {
            regenerator.cancelAll();
        }
        if (stateManager != null) {
            stateManager.clear();
        }
        saveAllNodes();
        nodes.clear();
        chunkNodeMap.clear();
        locationNodeMap.clear();
        plugin.getLogger().info("ResourceNodeManager disabled");
    }

    @Override
    public String getName() {
        return "ResourceNodeManager";
    }

    public void createNode(ResourceNode node) {
        nodes.put(node.getId(), node);
        addToChunkMap(node);
        addToLocationMap(node);
        storage.saveNode(node);

        // Clear any existing block at the node location
        clearBlockAtNode(node);

        // Show to all online players
        if (displayManager != null) {
            displayManager.showNodeToAll(node);
        }
        plugin.getLogger().info("Created node: " + node.getId() + " at " + formatLocation(node.getLocation()));
    }

    public void removeNode(UUID nodeId) {
        ResourceNode node = nodes.remove(nodeId);
        if (node != null) {
            removeFromChunkMap(node);
            removeFromLocationMap(node);
            storage.deleteNode(nodeId);

            // Hide from all players and clean up state
            if (displayManager != null) {
                displayManager.hideNodeFromAll(node);
            }
            if (stateManager != null) {
                stateManager.removeNode(nodeId);
            }
            if (regenerator != null) {
                regenerator.cancelRegeneration(nodeId);
            }
            plugin.getLogger().info("Removed node: " + nodeId);
        }
    }

    public ResourceNode getNode(UUID nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Get a node at the specified location.
     */
    public ResourceNode getNodeAt(Location location) {
        long key = locationKey(location);
        Set<UUID> nodeIds = locationNodeMap.get(key);
        if (nodeIds == null || nodeIds.isEmpty()) return null;

        for (UUID id : nodeIds) {
            ResourceNode node = nodes.get(id);
            if (node != null && node.getState() == NodeState.ACTIVE) {
                return node;
            }
        }
        return null;
    }

    public ResourceNode getNearestNode(Location location, double maxDistance) {
        ResourceNode nearest = null;
        double nearestDistance = maxDistance;

        for (ResourceNode node : nodes.values()) {
            if (!node.getWorldName().equals(location.getWorld().getName())) continue;

            double distance = node.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = node;
            }
        }

        return nearest;
    }

    public List<ResourceNode> getNodesInChunk(String world, int chunkX, int chunkZ) {
        String key = chunkKey(world, chunkX, chunkZ);
        Set<UUID> nodeIds = chunkNodeMap.get(key);
        if (nodeIds == null) return new ArrayList<>();

        List<ResourceNode> result = new ArrayList<>();
        for (UUID id : nodeIds) {
            ResourceNode node = nodes.get(id);
            if (node != null) result.add(node);
        }
        return result;
    }

    public Collection<ResourceNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int getNodeCount() {
        return nodes.size();
    }

    // Player join - send all nearby nodes
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay slightly to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (displayManager != null) {
                displayManager.sendAllNodesToPlayer(player, nodes.values());
            }
        }, 5L);
    }

    // Player quit - clean up state
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (displayManager != null) {
            displayManager.removeAllForPlayer(player);
        }
        if (stateManager != null) {
            stateManager.removePlayer(player.getUniqueId());
        }
    }

    // Player respawn - resend nodes
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Reset display states and resend
            if (stateManager != null) {
                var states = stateManager.getPlayerStates(player.getUniqueId());
                for (PlayerNodeState state : states.values()) {
                    state.setDisplaySpawned(false);
                }
            }

            if (displayManager != null) {
                for (ResourceNode node : nodes.values()) {
                    PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
                    if (state == null || !state.isDepleted()) {
                        displayManager.showNodeToPlayer(player, node);
                    }
                }
            }
        }, 5L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String world = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        storage.loadNodesInChunk(world, chunkX, chunkZ).thenAccept(loadedNodes -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (ResourceNode node : loadedNodes) {
                    if (!nodes.containsKey(node.getId())) {
                        nodes.put(node.getId(), node);
                        addToChunkMap(node);
                        addToLocationMap(node);

                        // Show to all online players
                        if (displayManager != null && node.getState() == NodeState.ACTIVE) {
                            displayManager.showNodeToAll(node);
                        }
                    }
                }
            });
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String key = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        Set<UUID> nodeIds = chunkNodeMap.remove(key);
        if (nodeIds != null) {
            for (UUID id : nodeIds) {
                ResourceNode node = nodes.remove(id);
                if (node != null) {
                    removeFromLocationMap(node);
                    storage.saveNode(node);

                    if (displayManager != null) {
                        displayManager.hideNodeFromAll(node);
                    }
                }
            }
        }
    }

    // Block placement at node locations (even when depleted)
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        if (hasNodeAtLocation(loc)) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if there's a node at this location (regardless of state).
     */
    public boolean hasNodeAtLocation(Location location) {
        long key = locationKey(location);
        Set<UUID> nodeIds = locationNodeMap.get(key);
        return nodeIds != null && !nodeIds.isEmpty();
    }

    /**
     * Clear any block at the node location (used before spawning).
     */
    public void clearBlockAtNode(ResourceNode node) {
        Location loc = node.getLocation();
        if (loc.getBlock().getType() != org.bukkit.Material.AIR) {
            loc.getBlock().setType(org.bukkit.Material.AIR, false);
        }
    }

    private void saveAllNodes() {
        for (ResourceNode node : nodes.values()) {
            storage.saveNode(node);
        }
    }

    private void addToChunkMap(ResourceNode node) {
        String key = chunkKey(node.getWorldName(), node.getChunkX(), node.getChunkZ());
        chunkNodeMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(node.getId());
    }

    private void removeFromChunkMap(ResourceNode node) {
        String key = chunkKey(node.getWorldName(), node.getChunkX(), node.getChunkZ());
        Set<UUID> nodeIds = chunkNodeMap.get(key);
        if (nodeIds != null) {
            nodeIds.remove(node.getId());
        }
    }

    private void addToLocationMap(ResourceNode node) {
        long key = locationKey(node.getLocation());
        locationNodeMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(node.getId());
    }

    private void removeFromLocationMap(ResourceNode node) {
        long key = locationKey(node.getLocation());
        Set<UUID> nodeIds = locationNodeMap.get(key);
        if (nodeIds != null) {
            nodeIds.remove(node.getId());
        }
    }

    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    private long locationKey(Location loc) {
        // Pack x, y, z into a long for fast lookup
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
