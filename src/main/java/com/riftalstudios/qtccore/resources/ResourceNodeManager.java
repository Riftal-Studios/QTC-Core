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
import org.bukkit.event.player.PlayerJoinEvent;
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
    private NodeDisplayManager displayManager;
    private ResourceNodeRegenerator regenerator;
    private ResourceNodeBreakHandler breakHandler;

    public ResourceNodeManager(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.storage = new ResourceNodeStorage(plugin, database);
    }

    public void initializeDisplay() {
        this.displayManager = new NodeDisplayManager(plugin);
        this.regenerator = new ResourceNodeRegenerator(plugin, this, displayManager);
        this.breakHandler = new ResourceNodeBreakHandler(plugin, this);
        this.breakHandler.register();
    }

    public NodeDisplayManager getDisplayManager() {
        return displayManager;
    }

    public ResourceNodeRegenerator getRegenerator() {
        return regenerator;
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
        saveAllNodes();
        nodes.clear();
        chunkNodeMap.clear();
        plugin.getLogger().info("ResourceNodeManager disabled");
    }

    @Override
    public String getName() {
        return "ResourceNodeManager";
    }

    public void createNode(ResourceNode node) {
        nodes.put(node.getId(), node);
        addToChunkMap(node);
        storage.saveNode(node);
        if (displayManager != null) {
            displayManager.showNodeToAllPlayers(node);
        }
        plugin.getLogger().info("Created node: " + node.getId() + " at " + formatLocation(node.getLocation()));
    }

    public void removeNode(UUID nodeId) {
        ResourceNode node = nodes.remove(nodeId);
        if (node != null) {
            removeFromChunkMap(node);
            storage.deleteNode(nodeId);
            if (displayManager != null) {
                displayManager.hideNodeFromAllPlayers(node);
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

    public void damageNode(ResourceNode node, int damage) {
        boolean wasDepleted = node.damage(damage);
        storage.saveNode(node);

        if (wasDepleted) {
            if (displayManager != null) {
                displayManager.hideNodeFromAllPlayers(node);
            }
            if (regenerator != null) {
                regenerator.scheduleRegeneration(node);
            }
            plugin.getLogger().info("Node " + node.getId().toString().substring(0, 8) + " depleted");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (displayManager == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (ResourceNode node : nodes.values()) {
                if (node.getState() == NodeState.ACTIVE &&
                        node.getWorldName().equals(player.getWorld().getName())) {
                    displayManager.showNodeToPlayer(player, node);
                }
            }
        }, 20L);
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
                    }
                }
                if (displayManager != null && !loadedNodes.isEmpty()) {
                    displayManager.showNodesInChunk(chunk, loadedNodes);
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
                    storage.saveNode(node);
                }
            }
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

    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
