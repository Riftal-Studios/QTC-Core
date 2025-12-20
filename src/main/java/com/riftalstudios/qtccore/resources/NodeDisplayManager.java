package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages client-side display entities for resource nodes using PacketEvents.
 * Entities exist only on the client - zero server overhead.
 */
public class NodeDisplayManager {

    private final QTCCore plugin;
    private final PlayerNodeStateManager stateManager;

    public NodeDisplayManager(QTCCore plugin, PlayerNodeStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
    }

    /**
     * Show a node to a specific player by sending spawn packets.
     */
    public void showNodeToPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getOrCreateState(player.getUniqueId(), node);

        if (state.isDisplaySpawned()) return;
        if (state.isDepleted()) return;

        Location loc = node.getLocation();
        int displayEntityId = state.getDisplayEntityId();
        int interactionEntityId = state.getInteractionEntityId();

        // Position at block center
        Vector3d position = new Vector3d(
                loc.getX() + 0.5,
                loc.getY() + 0.5,
                loc.getZ() + 0.5
        );

        // --- BlockDisplay entity ---
        WrapperPlayServerSpawnEntity displaySpawnPacket = new WrapperPlayServerSpawnEntity(
                displayEntityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.BLOCK_DISPLAY,
                position,
                0f, 0f, 0f,
                0,
                Optional.empty()
        );

        StateType stateType = materialToStateType(node.getType().getDisplayBlock());
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
                loc.getX() + 0.5,
                loc.getY(),
                loc.getZ() + 0.5
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
        Vector3i blockPos = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        WrappedBlockState barrierState = StateTypes.BARRIER.createBlockState();
        WrapperPlayServerBlockChange barrierPacket = new WrapperPlayServerBlockChange(blockPos, barrierState.getGlobalId());

        // Send all packets to player
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        user.sendPacket(displaySpawnPacket);
        user.sendPacket(displayMetadataPacket);
        user.sendPacket(interactionSpawnPacket);
        user.sendPacket(interactionMetadataPacket);
        user.sendPacket(barrierPacket);

        state.setDisplaySpawned(true);
    }

    /**
     * Hide a node from a specific player by sending destroy packet.
     */
    public void hideNodeFromPlayer(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state == null || !state.isDisplaySpawned()) return;

        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(
                state.getDisplayEntityId(),
                state.getInteractionEntityId()
        );

        // Restore the real block (remove barrier collision)
        Location loc = node.getLocation();
        Vector3i blockPos = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Material realBlock = loc.getBlock().getType();
        StateType realStateType = materialToStateType(realBlock);
        WrappedBlockState realState = realStateType.createBlockState();
        WrapperPlayServerBlockChange restorePacket = new WrapperPlayServerBlockChange(blockPos, realState.getGlobalId());

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        user.sendPacket(destroyPacket);
        user.sendPacket(restorePacket);

        state.setDisplaySpawned(false);
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
     */
    public void playHitAnimation(Player player, ResourceNode node) {
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state == null || !state.isDisplaySpawned()) return;

        int entityId = state.getDisplayEntityId();

        // Random shake offset
        float shakeX = (float) ((Math.random() - 0.5) * 0.1);
        float shakeZ = (float) ((Math.random() - 0.5) * 0.1);

        // Shake metadata
        List<EntityData> shakeMetadata = List.of(
                new EntityData(11, EntityDataTypes.VECTOR3F,
                        new Vector3f(-0.5f + shakeX, -0.5f, -0.5f + shakeZ)),
                new EntityData(9, EntityDataTypes.INT, 1) // Interpolation duration
        );

        WrapperPlayServerEntityMetadata shakePacket = new WrapperPlayServerEntityMetadata(
                entityId, shakeMetadata
        );

        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        user.sendPacket(shakePacket);

        // Reset after 2 ticks
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            List<EntityData> resetMetadata = List.of(
                    new EntityData(11, EntityDataTypes.VECTOR3F,
                            new Vector3f(-0.5f, -0.5f, -0.5f)),
                    new EntityData(9, EntityDataTypes.INT, 2)
            );

            WrapperPlayServerEntityMetadata resetPacket = new WrapperPlayServerEntityMetadata(
                    entityId, resetMetadata
            );

            PacketEvents.getAPI().getPlayerManager().getUser(player).sendPacket(resetPacket);
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
     */
    public void removeAllForPlayer(Player player) {
        var states = stateManager.getPlayerStates(player.getUniqueId());
        if (states.isEmpty()) return;

        // Collect both display and interaction entity IDs
        var spawnedStates = states.values().stream()
                .filter(PlayerNodeState::isDisplaySpawned)
                .toList();

        if (spawnedStates.isEmpty()) return;

        int[] entityIds = new int[spawnedStates.size() * 2];
        int i = 0;
        for (PlayerNodeState state : spawnedStates) {
            entityIds[i++] = state.getDisplayEntityId();
            entityIds[i++] = state.getInteractionEntityId();
        }

        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user != null) {
            user.sendPacket(destroyPacket);
        }
    }

    /**
     * Convert Bukkit Material to PacketEvents StateType.
     */
    private StateType materialToStateType(Material material) {
        String name = material.name();
        return switch (name) {
            case "AIR" -> StateTypes.AIR;
            case "CAVE_AIR" -> StateTypes.CAVE_AIR;
            case "VOID_AIR" -> StateTypes.VOID_AIR;
            case "GRASS_BLOCK" -> StateTypes.GRASS_BLOCK;
            case "DIRT" -> StateTypes.DIRT;
            case "OAK_LOG" -> StateTypes.OAK_LOG;
            case "BIRCH_LOG" -> StateTypes.BIRCH_LOG;
            case "SPRUCE_LOG" -> StateTypes.SPRUCE_LOG;
            case "JUNGLE_LOG" -> StateTypes.JUNGLE_LOG;
            case "ACACIA_LOG" -> StateTypes.ACACIA_LOG;
            case "DARK_OAK_LOG" -> StateTypes.DARK_OAK_LOG;
            case "MANGROVE_LOG" -> StateTypes.MANGROVE_LOG;
            case "CHERRY_LOG" -> StateTypes.CHERRY_LOG;
            case "IRON_ORE" -> StateTypes.IRON_ORE;
            case "GOLD_ORE" -> StateTypes.GOLD_ORE;
            case "DIAMOND_ORE" -> StateTypes.DIAMOND_ORE;
            case "COAL_ORE" -> StateTypes.COAL_ORE;
            case "COPPER_ORE" -> StateTypes.COPPER_ORE;
            case "STONE" -> StateTypes.STONE;
            case "BARRIER" -> StateTypes.BARRIER;
            default -> StateTypes.AIR; // Default to air for unknown blocks
        };
    }
}
