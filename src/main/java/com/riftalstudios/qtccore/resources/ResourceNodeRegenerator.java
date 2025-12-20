package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles per-player node regeneration.
 * Runs a periodic task to check and regenerate depleted nodes for each player.
 */
public class ResourceNodeRegenerator {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final PlayerNodeStateManager stateManager;
    private final NodeDisplayManager displayManager;

    private BukkitTask regenerationTask;
    private final Map<UUID, BukkitTask> legacyTasks = new ConcurrentHashMap<>(); // For backwards compat

    public ResourceNodeRegenerator(QTCCore plugin, ResourceNodeManager nodeManager,
                                   PlayerNodeStateManager stateManager, NodeDisplayManager displayManager) {
        this.plugin = plugin;
        this.nodeManager = nodeManager;
        this.stateManager = stateManager;
        this.displayManager = displayManager;
    }

    /**
     * Start the periodic regeneration check task.
     */
    public void startRegenerationTask() {
        if (regenerationTask != null) {
            regenerationTask.cancel();
        }

        // Run every second (20 ticks)
        regenerationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkRegenerations, 20L, 20L);
        plugin.getLogger().info("ResourceNodeRegenerator started");
    }

    /**
     * Check all player states for regeneration.
     */
    private void checkRegenerations() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            checkPlayerRegenerations(player);
        }
    }

    /**
     * Check and regenerate nodes for a specific player.
     */
    private void checkPlayerRegenerations(Player player) {
        Map<UUID, PlayerNodeState> states = stateManager.getPlayerStates(player.getUniqueId());

        for (PlayerNodeState state : states.values()) {
            if (state.isDepleted() && state.shouldRegenerate()) {
                regenerateForPlayer(player, state);
            }
        }
    }

    /**
     * Regenerate a node for a specific player.
     */
    private void regenerateForPlayer(Player player, PlayerNodeState state) {
        ResourceNode node = nodeManager.getNode(state.getNodeId());
        if (node == null) return;

        // Reset player's HP for this node
        state.resetHp();

        // Show the display to this player again
        displayManager.showNodeToPlayer(player, node);

        plugin.getLogger().fine("Node " + state.getNodeId().toString().substring(0, 8) +
                " regenerated for player " + player.getName());
    }

    /**
     * Legacy method for backwards compatibility.
     */
    public void scheduleRegeneration(ResourceNode node) {
        // No-op in per-player system - handled by periodic task
    }

    public void cancelRegeneration(UUID nodeId) {
        BukkitTask task = legacyTasks.remove(nodeId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasScheduled(UUID nodeId) {
        return legacyTasks.containsKey(nodeId);
    }

    public void cancelAll() {
        if (regenerationTask != null) {
            regenerationTask.cancel();
            regenerationTask = null;
        }

        for (BukkitTask task : legacyTasks.values()) {
            task.cancel();
        }
        legacyTasks.clear();

        plugin.getLogger().info("ResourceNodeRegenerator stopped");
    }

    public int getScheduledCount() {
        return legacyTasks.size();
    }
}
