package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeDisplayManager {

    private final QTCCore plugin;
    private final Map<Material, StateType> materialToStateType = new ConcurrentHashMap<>();

    public NodeDisplayManager(QTCCore plugin) {
        this.plugin = plugin;
        initializeMaterialMapping();
    }

    private void initializeMaterialMapping() {
        materialToStateType.put(Material.OAK_LOG, StateTypes.OAK_LOG);
        materialToStateType.put(Material.BIRCH_LOG, StateTypes.BIRCH_LOG);
        materialToStateType.put(Material.SPRUCE_LOG, StateTypes.SPRUCE_LOG);
        materialToStateType.put(Material.DARK_OAK_LOG, StateTypes.DARK_OAK_LOG);
        materialToStateType.put(Material.IRON_ORE, StateTypes.IRON_ORE);
        materialToStateType.put(Material.GOLD_ORE, StateTypes.GOLD_ORE);
        materialToStateType.put(Material.DIAMOND_ORE, StateTypes.DIAMOND_ORE);
        materialToStateType.put(Material.COAL_ORE, StateTypes.COAL_ORE);
    }

    public void showNodeToPlayer(Player player, ResourceNode node) {
        if (node.getState() != NodeState.ACTIVE) return;

        Location loc = node.getLocation();
        StateType stateType = materialToStateType.get(node.getType().getDisplayBlock());
        if (stateType == null) {
            plugin.getLogger().warning("No StateType mapping for: " + node.getType().getDisplayBlock());
            return;
        }

        sendBlockChange(player, loc, stateType);
    }

    public void hideNodeFromPlayer(Player player, ResourceNode node) {
        Location loc = node.getLocation();
        Material originalMaterial = loc.getBlock().getType();
        StateType stateType = getStateTypeForMaterial(originalMaterial);

        sendBlockChange(player, loc, stateType);
    }

    public void showNodeToAllPlayers(ResourceNode node) {
        if (node.getState() != NodeState.ACTIVE) return;

        Collection<? extends Player> nearbyPlayers = getPlayersInRange(node);
        for (Player player : nearbyPlayers) {
            showNodeToPlayer(player, node);
        }
    }

    public void hideNodeFromAllPlayers(ResourceNode node) {
        Collection<? extends Player> nearbyPlayers = getPlayersInRange(node);
        for (Player player : nearbyPlayers) {
            hideNodeFromPlayer(player, node);
        }
    }

    public void showAllNodesToPlayer(Player player, Collection<ResourceNode> nodes) {
        for (ResourceNode node : nodes) {
            showNodeToPlayer(player, node);
        }
    }

    public void refreshNodeDisplay(ResourceNode node) {
        if (node.getState() == NodeState.ACTIVE) {
            showNodeToAllPlayers(node);
        } else {
            hideNodeFromAllPlayers(node);
        }
    }

    public void showNodesInChunk(Chunk chunk, Collection<ResourceNode> nodes) {
        Collection<? extends Player> playersInChunk = chunk.getWorld().getPlayers().stream()
                .filter(p -> isPlayerNearChunk(p, chunk))
                .toList();

        for (Player player : playersInChunk) {
            for (ResourceNode node : nodes) {
                if (node.getState() == NodeState.ACTIVE) {
                    showNodeToPlayer(player, node);
                }
            }
        }
    }

    private void sendBlockChange(Player player, Location loc, StateType stateType) {
        Vector3i position = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        WrappedBlockState blockState = stateType.createBlockState();

        WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(position, blockState.getGlobalId());

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private StateType getStateTypeForMaterial(Material material) {
        StateType cached = materialToStateType.get(material);
        if (cached != null) return cached;

        return switch (material) {
            case AIR -> StateTypes.AIR;
            case STONE -> StateTypes.STONE;
            case DIRT -> StateTypes.DIRT;
            case GRASS_BLOCK -> StateTypes.GRASS_BLOCK;
            default -> StateTypes.AIR;
        };
    }

    private Collection<? extends Player> getPlayersInRange(ResourceNode node) {
        Location loc = node.getLocation();
        if (loc.getWorld() == null) return java.util.Collections.emptyList();

        int viewDistance = plugin.getServer().getViewDistance() * 16;
        return loc.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(loc) <= viewDistance)
                .toList();
    }

    private boolean isPlayerNearChunk(Player player, Chunk chunk) {
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        int viewDistance = plugin.getServer().getViewDistance();

        return Math.abs(playerChunkX - chunk.getX()) <= viewDistance
                && Math.abs(playerChunkZ - chunk.getZ()) <= viewDistance;
    }
}
