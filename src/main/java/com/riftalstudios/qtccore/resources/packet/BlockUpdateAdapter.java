package com.riftalstudios.qtccore.resources.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.PlayerNodeState;
import com.riftalstudios.qtccore.resources.PlayerNodeStateManager;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts BLOCK_CHANGE and MULTI_BLOCK_CHANGE packets to maintain the
 * node block illusion when underlying blocks change.
 *
 * When the server sends a block update for a position where a node exists:
 * - If the player sees the node (viewer + not depleted): show node block
 * - If the node is depleted for this player: let real block show through
 */
public class BlockUpdateAdapter extends PacketListenerAbstract {

    private final QTCCore plugin;
    private final NodeBlockManager blockManager;
    private final PlayerNodeStateManager stateManager;

    public BlockUpdateAdapter(QTCCore plugin, NodeBlockManager blockManager, PlayerNodeStateManager stateManager) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.blockManager = blockManager;
        this.stateManager = stateManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            handleBlockChange(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            handleMultiBlockChange(event, player);
        }
    }

    private void handleBlockChange(PacketSendEvent event, Player player) {
        WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
        Vector3i position = packet.getBlockPosition();
        String worldName = player.getWorld().getName();

        ResourceNode node = blockManager.getNodeAt(worldName, position.getX(), position.getY(), position.getZ());
        if (node == null) return;

        // Check if player should see this node
        if (!shouldShowNode(player, node, position.getX(), position.getY(), position.getZ())) {
            return; // Let real block show through
        }

        // Get the material for this block position
        BlockOffset offset = node.getBlockAt(new Location(player.getWorld(), position.getX(), position.getY(), position.getZ()));
        if (offset == null) return;

        // Override with node block
        StateType stateType = materialToStateType(offset.material());
        WrappedBlockState nodeState = stateType.createBlockState();
        packet.setBlockState(nodeState);
    }

    private void handleMultiBlockChange(PacketSendEvent event, Player player) {
        WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
        WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = packet.getBlocks();
        String worldName = player.getWorld().getName();

        List<WrapperPlayServerMultiBlockChange.EncodedBlock> modifiedBlocks = new ArrayList<>();
        boolean anyModified = false;

        for (WrapperPlayServerMultiBlockChange.EncodedBlock encodedBlock : blocks) {
            int x = encodedBlock.getX();
            int y = encodedBlock.getY();
            int z = encodedBlock.getZ();

            ResourceNode node = blockManager.getNodeAt(worldName, x, y, z);
            if (node != null && shouldShowNode(player, node, x, y, z)) {
                // Get the material for this block position
                BlockOffset offset = node.getBlockAt(new Location(player.getWorld(), x, y, z));
                if (offset != null) {
                    StateType stateType = materialToStateType(offset.material());
                    WrappedBlockState nodeState = stateType.createBlockState();
                    modifiedBlocks.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(
                            nodeState, x, y, z
                    ));
                    anyModified = true;
                    continue;
                }
            }

            // Keep original block
            modifiedBlocks.add(encodedBlock);
        }

        if (anyModified) {
            packet.setBlocks(modifiedBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]));
        }
    }

    /**
     * Check if a node should be shown to a player at this position.
     */
    private boolean shouldShowNode(Player player, ResourceNode node, int x, int y, int z) {
        // Must be a viewer
        if (!blockManager.isViewer(node, player)) {
            return false;
        }

        // Check if depleted for this player
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state != null && state.isDepleted()) {
            return false;
        }

        return true;
    }

    /**
     * Convert Bukkit Material to PacketEvents StateType.
     */
    private StateType materialToStateType(Material material) {
        String name = material.name().toLowerCase();
        StateType stateType = StateTypes.getByName("minecraft:" + name);
        return stateType != null ? stateType : StateTypes.AIR;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("BlockUpdateAdapter registered");
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
