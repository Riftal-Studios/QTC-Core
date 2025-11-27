package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceNodeRegenerator {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final NodeDisplayManager displayManager;
    private final Map<UUID, BukkitTask> scheduledTasks = new ConcurrentHashMap<>();

    public ResourceNodeRegenerator(QTCCore plugin, ResourceNodeManager nodeManager, NodeDisplayManager displayManager) {
        this.plugin = plugin;
        this.nodeManager = nodeManager;
        this.displayManager = displayManager;
    }

    public void scheduleRegeneration(ResourceNode node) {
        if (scheduledTasks.containsKey(node.getId())) {
            return;
        }

        int respawnSeconds = node.getType().getDefaultRespawnSeconds();
        long respawnTicks = respawnSeconds * 20L;

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            regenerateNode(node);
        }, respawnTicks);

        scheduledTasks.put(node.getId(), task);
        plugin.getLogger().info("Scheduled regeneration for node " + node.getId().toString().substring(0, 8) +
                " in " + respawnSeconds + " seconds");
    }

    private void regenerateNode(ResourceNode node) {
        scheduledTasks.remove(node.getId());

        ResourceNode currentNode = nodeManager.getNode(node.getId());
        if (currentNode == null) {
            return;
        }

        currentNode.regenerate();
        displayManager.showNode(currentNode);

        plugin.getLogger().info("Node " + currentNode.getId().toString().substring(0, 8) + " regenerated");
    }

    public void cancelRegeneration(UUID nodeId) {
        BukkitTask task = scheduledTasks.remove(nodeId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasScheduled(UUID nodeId) {
        return scheduledTasks.containsKey(nodeId);
    }

    public void cancelAll() {
        for (BukkitTask task : scheduledTasks.values()) {
            task.cancel();
        }
        scheduledTasks.clear();
        plugin.getLogger().info("Cancelled all scheduled regenerations");
    }

    public int getScheduledCount() {
        return scheduledTasks.size();
    }
}
