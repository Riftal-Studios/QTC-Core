package com.riftalstudios.qtccore.resources.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.ResourceNode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages block break animation for resource nodes.
 * Sends WrapperPlayServerBlockBreakAnimation packets to show crack stages (0-9).
 * Stage -1 clears the animation.
 *
 * Each player has their own animation state per node to support
 * per-player HP/depletion system.
 */
public class NodeBreakAnimationTask {

    private final QTCCore plugin;

    // Unique entity ID counter for break animations
    // Uses negative IDs to avoid collision with real entities (which have positive IDs)
    // The client accepts negative entity IDs for break animations
    private final AtomicInteger animationIdCounter = new AtomicInteger(-1);

    // Player UUID -> (Node ID -> animation state)
    private final Map<UUID, Map<UUID, AnimationState>> playerAnimations = new ConcurrentHashMap<>();

    public NodeBreakAnimationTask(QTCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Play or update break animation for a node based on HP percentage.
     * Animates all blocks in multi-block nodes.
     *
     * Always sends the animation packet on each hit for visual feedback,
     * even if the stage hasn't changed. This provides responsive feedback
     * that was previously provided by entity movement/shake in the old system.
     *
     * @param player The player to show the animation to
     * @param node The resource node being broken
     * @param currentHp Current HP of the node
     * @param maxHp Maximum HP of the node
     */
    public void updateBreakAnimation(Player player, ResourceNode node, int currentHp, int maxHp) {
        if (!player.isOnline()) return;

        // Calculate stage (0-9 based on HP remaining)
        // Stage 0 = barely damaged, Stage 9 = almost broken
        int stage = calculateStage(currentHp, maxHp);

        plugin.getLogger().info("[Animation] HP: " + currentHp + "/" + maxHp + " -> Stage: " + stage);

        if (stage < 0) return; // No damage yet, nothing to show

        AnimationState state = getOrCreateState(player.getUniqueId(), node);
        state.currentStage = stage;

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            plugin.getLogger().warning("[Animation] User is null for player: " + player.getName());
            return;
        }

        // Send animation for all blocks in the node
        // Always send even if stage unchanged - this "refreshes" the visual crack effect
        // and provides responsive feedback on each hit
        for (Location blockLoc : node.getAllBlockLocations()) {
            int animationId = state.getAnimationId(blockLoc);
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());

            plugin.getLogger().info("[Animation] Sending stage " + stage + " for block at " +
                    blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() +
                    " with entityId " + animationId);

            WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(
                    animationId, blockPos, (byte) stage
            );
            user.sendPacket(packet);
        }
    }

    /**
     * Clear break animation for a node (on full break or reset).
     */
    public void clearBreakAnimation(Player player, ResourceNode node) {
        if (!player.isOnline()) return;

        Map<UUID, AnimationState> nodeStates = playerAnimations.get(player.getUniqueId());
        if (nodeStates == null) return;

        AnimationState state = nodeStates.get(node.getId());
        if (state == null) return;

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // Send stage -1 to clear animation for all blocks
        for (Location blockLoc : node.getAllBlockLocations()) {
            int animationId = state.getAnimationId(blockLoc);
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());

            WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(
                    animationId, blockPos, (byte) -1
            );
            user.sendPacket(packet);
        }

        // Remove state
        nodeStates.remove(node.getId());
        state.currentStage = -1;
    }

    /**
     * Clear all animation state for a player (on quit).
     */
    public void clearPlayer(UUID playerId) {
        playerAnimations.remove(playerId);
    }

    /**
     * Clear all animation states.
     */
    public void clear() {
        playerAnimations.clear();
    }

    /**
     * Calculate break animation stage from HP percentage.
     * Stage 0 = 100% HP (no damage visible)
     * Stage 9 = ~10% HP (almost fully cracked)
     */
    private int calculateStage(int currentHp, int maxHp) {
        if (currentHp >= maxHp) return -1; // No damage
        if (currentHp <= 0) return 9;

        // Calculate percentage of damage done (0.0 to 1.0)
        double damagePercent = 1.0 - ((double) currentHp / maxHp);

        // Map to stage 0-9
        return Math.min(9, (int) (damagePercent * 10));
    }

    private AnimationState getOrCreateState(UUID playerId, ResourceNode node) {
        Map<UUID, AnimationState> nodeStates = playerAnimations.computeIfAbsent(
                playerId, k -> new ConcurrentHashMap<>()
        );

        return nodeStates.computeIfAbsent(node.getId(), k -> new AnimationState(this));
    }

    private int nextAnimationId() {
        return animationIdCounter.getAndDecrement();
    }

    /**
     * Tracks animation state for a single node for a single player.
     */
    private static class AnimationState {
        private final NodeBreakAnimationTask parent;
        private int currentStage = -1;
        private final Map<Long, Integer> blockAnimationIds = new ConcurrentHashMap<>();

        AnimationState(NodeBreakAnimationTask parent) {
            this.parent = parent;
        }

        /**
         * Get a consistent animation ID for a block location.
         */
        int getAnimationId(Location loc) {
            long key = packLocation(loc);
            return blockAnimationIds.computeIfAbsent(key, k -> parent.nextAnimationId());
        }

        private long packLocation(Location loc) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
        }
    }
}
