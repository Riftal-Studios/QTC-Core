package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.packet.NodeBlockManager;
import com.riftalstudios.qtccore.resources.packet.NodeBreakAnimationTask;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Manages client-side display for resource nodes using pure packet-based block modification.
 *
 * Key changes from entity-based system:
 * - No entities spawned - blocks are injected into CHUNK_DATA packets via ChunkDataAdapter
 * - Block updates intercepted via BlockUpdateAdapter to maintain illusion
 * - Break animations use WrapperPlayServerBlockBreakAnimation
 * - Much simpler state management - just track viewers in NodeBlockManager
 *
 * The actual packet interception happens in ChunkDataAdapter and BlockUpdateAdapter.
 * This class coordinates the viewer state and sends immediate block change packets
 * when nodes are shown/hidden mid-session.
 */
public class NodeDisplayManager {

    private final QTCCore plugin;
    private final PlayerNodeStateManager stateManager;
    private final NodeBlockManager blockManager;
    private final NodeBreakAnimationTask breakAnimationTask;

    public NodeDisplayManager(QTCCore plugin, PlayerNodeStateManager stateManager, NodeBlockManager blockManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.blockManager = blockManager;
        this.breakAnimationTask = new NodeBreakAnimationTask(plugin);
    }

    /**
     * Get the node block manager.
     */
    public NodeBlockManager getBlockManager() {
        return blockManager;
    }

    /**
     * Get the break animation task.
     */
    public NodeBreakAnimationTask getBreakAnimationTask() {
        return breakAnimationTask;
    }

    /**
     * Show a node to a specific player by registering them as a viewer
     * and sending immediate block change packets.
     *
     * For chunk loads, the ChunkDataAdapter handles this automatically.
     * This method is used for:
     * - Player joins (chunks already loaded)
     * - Node regeneration (make visible again after depletion)
     * - New node creation
     */
    public void showNodeToPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getOrCreateState(player.getUniqueId(), node);

        // Don't show if already viewing or depleted for this player
        if (state.isViewer()) return;
        if (state.isDepleted()) return;

        // Register as viewer
        blockManager.addViewer(node, player);
        state.setViewer(true);

        // Send immediate block change packets for all blocks in the node
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        Location baseLoc = node.getBaseLocation();
        for (BlockOffset offset : node.getBlocks()) {
            Location blockLoc = offset.apply(baseLoc);
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());

            // Send the node block
            StateType stateType = materialToStateType(offset.material());
            WrappedBlockState blockState = stateType.createBlockState();
            WrapperPlayServerBlockChange blockPacket = new WrapperPlayServerBlockChange(blockPos, blockState.getGlobalId());
            user.sendPacket(blockPacket);
        }
    }

    /**
     * Hide a node from a specific player by removing them as a viewer
     * and restoring real blocks.
     *
     * Used when:
     * - Node is depleted for this player
     * - Node is removed
     * - Player quits (cleanup)
     */
    public void hideNodeFromPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());

        // Remove from viewer list
        blockManager.removeViewer(node, player);
        if (state != null) {
            state.setViewer(false);
        }

        // Clear any break animation
        breakAnimationTask.clearBreakAnimation(player, node);

        // Send real block states to restore the actual world
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        for (Location blockLoc : node.getAllBlockLocations()) {
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            // Use SpigotConversionUtil to preserve block data (orientation, properties, etc.)
            WrappedBlockState realState = SpigotConversionUtil.fromBukkitBlockData(blockLoc.getBlock().getBlockData());
            WrapperPlayServerBlockChange restorePacket = new WrapperPlayServerBlockChange(blockPos, realState.getGlobalId());
            user.sendPacket(restorePacket);
        }
    }

    /**
     * Show a node to all online players.
     */
    public void showNodeToAll(ResourceNode node) {
        // Register node with block manager
        blockManager.registerNode(node);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            showNodeToPlayer(player, node);
        }
    }

    /**
     * Hide a node from all online players.
     */
    public void hideNodeFromAll(ResourceNode node) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hideNodeFromPlayer(player, node);
        }

        // Unregister from block manager
        blockManager.unregisterNode(node);
    }

    /**
     * Play hit animation (break progress) on a node for a specific player.
     */
    public void playHitAnimation(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state == null || !state.isViewer()) return;

        // Update break animation based on current HP
        breakAnimationTask.updateBreakAnimation(player, node, state.getCurrentHp(), state.getMaxHp());
    }

    /**
     * Refresh node display for a player (hide and show again).
     */
    public void refreshNodeForPlayer(Player player, ResourceNode node) {
        hideNodeFromPlayer(player, node);
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state != null && !state.isDepleted()) {
            showNodeToPlayer(player, node);
        }
    }

    /**
     * Send all active nodes to a player (on join/respawn).
     */
    public void sendAllNodesToPlayer(Player player, Iterable<ResourceNode> nodes) {
        for (ResourceNode node : nodes) {
            if (node.getState() == NodeState.ACTIVE) {
                showNodeToPlayer(player, node);
            }
        }
    }

    /**
     * Remove all displays for a player (on quit).
     */
    public void removeAllForPlayer(Player player) {
        // Remove from all viewer lists
        blockManager.removePlayerFromAllNodes(player.getUniqueId());

        // Clear break animations
        breakAnimationTask.clearPlayer(player.getUniqueId());

        // Restore all blocks (in case needed)
        var states = stateManager.getPlayerStates(player.getUniqueId());
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        for (PlayerNodeState state : states.values()) {
            ResourceNode node = plugin.getResourceNodeManager().getNode(state.getNodeId());
            if (node != null) {
                for (Location blockLoc : node.getAllBlockLocations()) {
                    Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                    WrappedBlockState realState = SpigotConversionUtil.fromBukkitBlockData(blockLoc.getBlock().getBlockData());
                    WrapperPlayServerBlockChange restorePacket = new WrapperPlayServerBlockChange(blockPos, realState.getGlobalId());
                    user.sendPacket(restorePacket);
                }
            }
        }
    }

    /**
     * Convert Bukkit Material to PacketEvents StateType.
     */
    private StateType materialToStateType(Material material) {
        String name = material.name().toLowerCase();
        StateType stateType = StateTypes.getByName("minecraft:" + name);
        return stateType != null ? stateType : StateTypes.AIR;
    }
}
