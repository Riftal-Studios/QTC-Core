package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages client-side display entities for resource nodes using PacketEvents.
 * Entities exist only on the client - zero server overhead.
 * Supports multi-block nodes with templates.
 */
public class NodeDisplayManager {

    private final QTCCore plugin;
    private final PlayerNodeStateManager stateManager;

    // Atomic counter for generating unique entity IDs
    private final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

    public NodeDisplayManager(QTCCore plugin, PlayerNodeStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
    }

    /**
     * Generate a unique entity ID for packet entities.
     */
    private int nextEntityId() {
        return entityIdCounter.getAndIncrement();
    }

    /**
     * Show a node to a specific player by sending spawn packets.
     * Supports multi-block nodes - spawns entities for each block.
     */
    public void showNodeToPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getOrCreateState(player.getUniqueId(), node);

        if (state.isDisplaySpawned()) return;
        if (state.isDepleted()) return;

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // Clear any old entity IDs
        state.clearEntityIds();

        Location baseLoc = node.getBaseLocation();
        List<BlockOffset> blocks = node.getBlocks();

        // Spawn entities for each block in the node
        for (BlockOffset offset : blocks) {
            Location blockLoc = offset.apply(baseLoc);
            int displayEntityId = nextEntityId();
            int interactionEntityId = nextEntityId();

            // Track entity IDs in state
            state.addDisplayEntityId(displayEntityId);
            state.addInteractionEntityId(interactionEntityId);

            // Position at block center for display
            Vector3d displayPosition = new Vector3d(
                    blockLoc.getX() + 0.5,
                    blockLoc.getY() + 0.5,
                    blockLoc.getZ() + 0.5
            );

            // --- BlockDisplay entity ---
            WrapperPlayServerSpawnEntity displaySpawnPacket = new WrapperPlayServerSpawnEntity(
                    displayEntityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.BLOCK_DISPLAY,
                    displayPosition,
                    0f, 0f, 0f,
                    0,
                    Optional.empty()
            );

            // Use block's specific material from template
            StateType stateType = materialToStateType(offset.material());
            WrappedBlockState blockState = stateType.createBlockState();

            List<EntityData> displayMetadata = List.of(
                    new EntityData(23, EntityDataTypes.BLOCK_STATE, blockState.getGlobalId()),
                    new EntityData(11, EntityDataTypes.VECTOR3F, new Vector3f(-0.5f, -0.5f, -0.5f))
            );

            WrapperPlayServerEntityMetadata displayMetadataPacket = new WrapperPlayServerEntityMetadata(
                    displayEntityId, displayMetadata
            );

            // --- Interaction entity (hitbox) ---
            Vector3d interactionPosition = new Vector3d(
                    blockLoc.getX() + 0.5,
                    blockLoc.getY(),
                    blockLoc.getZ() + 0.5
            );

            WrapperPlayServerSpawnEntity interactionSpawnPacket = new WrapperPlayServerSpawnEntity(
                    interactionEntityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.INTERACTION,
                    interactionPosition,
                    0f, 0f, 0f,
                    0,
                    Optional.empty()
            );

            List<EntityData> interactionMetadata = List.of(
                    new EntityData(8, EntityDataTypes.FLOAT, 1.0f),  // Width
                    new EntityData(9, EntityDataTypes.FLOAT, 1.0f)   // Height
            );

            WrapperPlayServerEntityMetadata interactionMetadataPacket = new WrapperPlayServerEntityMetadata(
                    interactionEntityId, interactionMetadata
            );

            // --- Barrier block for collision ---
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            WrappedBlockState barrierState = StateTypes.BARRIER.createBlockState();
            WrapperPlayServerBlockChange barrierPacket = new WrapperPlayServerBlockChange(blockPos, barrierState.getGlobalId());

            // Send all packets for this block
            user.sendPacket(displaySpawnPacket);
            user.sendPacket(displayMetadataPacket);
            user.sendPacket(interactionSpawnPacket);
            user.sendPacket(interactionMetadataPacket);
            user.sendPacket(barrierPacket);
        }

        state.setDisplaySpawned(true);
    }

    /**
     * Hide a node from a specific player by sending destroy packet.
     * Supports multi-block nodes - restores all blocks and destroys all entities.
     */
    public void hideNodeFromPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // Always restore all blocks (remove barrier collisions)
        // This is done regardless of displaySpawned state to ensure barrier cleanup
        for (Location blockLoc : node.getAllBlockLocations()) {
            Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            // Use SpigotConversionUtil to preserve block data (orientation, properties, etc.)
            WrappedBlockState realState = SpigotConversionUtil.fromBukkitBlockData(blockLoc.getBlock().getBlockData());
            WrapperPlayServerBlockChange restorePacket = new WrapperPlayServerBlockChange(blockPos, realState.getGlobalId());
            user.sendPacket(restorePacket);
        }

        // Only destroy entities if they were spawned
        if (state != null && state.isDisplaySpawned()) {
            int[] entityIds = state.getAllEntityIds();
            if (entityIds.length > 0) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
                user.sendPacket(destroyPacket);
            }
            state.clearEntityIds();
            state.setDisplaySpawned(false);
        }
    }

    /**
     * Show a node to all online players.
     */
    public void showNodeToAll(ResourceNode node) {
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
    }

    /**
     * Play shake animation on a node for a specific player.
     * For multi-block nodes, all blocks shake together.
     */
    public void playHitAnimation(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state == null || !state.isDisplaySpawned()) return;

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        List<Integer> displayIds = state.getDisplayEntityIds();
        if (displayIds.isEmpty()) return;

        // Random shake offset (same for all blocks in this node)
        float shakeX = (float) ((Math.random() - 0.5) * 0.1);
        float shakeZ = (float) ((Math.random() - 0.5) * 0.1);

        // Shake all blocks
        for (int entityId : displayIds) {
            List<EntityData> shakeMetadata = List.of(
                    new EntityData(11, EntityDataTypes.VECTOR3F,
                            new Vector3f(-0.5f + shakeX, -0.5f, -0.5f + shakeZ)),
                    new EntityData(9, EntityDataTypes.INT, 1) // Interpolation duration
            );

            WrapperPlayServerEntityMetadata shakePacket = new WrapperPlayServerEntityMetadata(
                    entityId, shakeMetadata
            );
            user.sendPacket(shakePacket);
        }

        // Capture IDs for reset task
        List<Integer> idsToReset = new ArrayList<>(displayIds);

        // Reset after 2 ticks
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            var resetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (resetUser == null) return;

            for (int entityId : idsToReset) {
                List<EntityData> resetMetadata = List.of(
                        new EntityData(11, EntityDataTypes.VECTOR3F,
                                new Vector3f(-0.5f, -0.5f, -0.5f)),
                        new EntityData(9, EntityDataTypes.INT, 2)
                );

                WrapperPlayServerEntityMetadata resetPacket = new WrapperPlayServerEntityMetadata(
                        entityId, resetMetadata
                );
                resetUser.sendPacket(resetPacket);
            }
        }, 2L);
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
     * Supports multi-block nodes.
     */
    public void removeAllForPlayer(Player player) {
        var states = stateManager.getPlayerStates(player.getUniqueId());
        if (states.isEmpty()) return;

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // Collect all entity IDs from spawned states (multi-block aware)
        List<Integer> allEntityIds = new ArrayList<>();
        for (PlayerNodeState state : states.values()) {
            if (state.isDisplaySpawned()) {
                int[] ids = state.getAllEntityIds();
                for (int id : ids) {
                    allEntityIds.add(id);
                }
            }
        }

        // Destroy all spawned display entities
        if (!allEntityIds.isEmpty()) {
            int[] entityIds = allEntityIds.stream().mapToInt(Integer::intValue).toArray();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
            user.sendPacket(destroyPacket);
        }

        // Restore all barrier blocks (for all blocks in each node)
        for (PlayerNodeState state : states.values()) {
            ResourceNode node = plugin.getResourceNodeManager().getNode(state.getNodeId());
            if (node != null) {
                for (Location blockLoc : node.getAllBlockLocations()) {
                    Vector3i blockPos = new Vector3i(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                    // Use SpigotConversionUtil to preserve block data (orientation, properties, etc.)
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
        // Use PacketEvents' built-in lookup to support all block types
        String name = material.name().toLowerCase();
        StateType stateType = StateTypes.getByName("minecraft:" + name);
        return stateType != null ? stateType : StateTypes.AIR;
    }
}
