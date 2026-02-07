package com.riftalstudios.qtccore.resources.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.PlayerNodeState;
import com.riftalstudios.qtccore.resources.PlayerNodeStateManager;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.packet.NodeBlockManager.WorldPosition;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Intercepts CHUNK_DATA packets and modifies chunk sections to include
 * visible resource node blocks for the receiving player.
 *
 * This is the core of the pure packet-based display system - no entities needed.
 * When a chunk is sent to a player, we inject the node block data directly
 * into the chunk sections.
 */
public class ChunkDataAdapter extends PacketListenerAbstract {

    private final QTCCore plugin;
    private final NodeBlockManager blockManager;
    private final PlayerNodeStateManager stateManager;

    public ChunkDataAdapter(QTCCore plugin, NodeBlockManager blockManager, PlayerNodeStateManager stateManager) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.blockManager = blockManager;
        this.stateManager = stateManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) return;

        Player player = event.getPlayer();
        if (player == null) return;

        WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
        int chunkX = packet.getColumn().getX();
        int chunkZ = packet.getColumn().getZ();
        String worldName = player.getWorld().getName();

        // Get nodes visible to this player in this chunk
        Map<WorldPosition, ResourceNode> visibleNodes = blockManager.getVisibleNodesInChunk(player, worldName, chunkX, chunkZ);
        if (visibleNodes.isEmpty()) return;

        BaseChunk[] sections = packet.getColumn().getChunks();
        int absMinHeight = Math.abs(player.getWorld().getMinHeight());

        // Modify chunk sections to include node blocks
        for (Map.Entry<WorldPosition, ResourceNode> entry : visibleNodes.entrySet()) {
            WorldPosition pos = entry.getKey();
            ResourceNode node = entry.getValue();

            // Check if this player sees the node as depleted
            PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
            if (state != null && state.isDepleted()) {
                continue; // Don't show depleted nodes
            }

            // Get the block offset for this position to find the correct material
            BlockOffset blockOffset = node.getBlockAt(new Location(
                    player.getWorld(), pos.x(), pos.y(), pos.z()
            ));
            if (blockOffset == null) continue;

            Material material = blockOffset.material();
            StateType stateType = materialToStateType(material);
            WrappedBlockState blockState = stateType.createBlockState();

            // Calculate chunk-relative coordinates
            int xInChunk = pos.x() & 0xF;
            int y = pos.y();
            int zInChunk = pos.z() & 0xF;

            int sectionIndex = (y >> 4) + (absMinHeight >> 4);
            int yInSection = y & 0xF;

            if (sectionIndex < 0 || sectionIndex >= sections.length) continue;

            BaseChunk section = sections[sectionIndex];
            if (section == null) continue;

            // Set the block in the chunk section
            section.set(
                    PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(),
                    xInChunk,
                    yInSection,
                    zInChunk,
                    blockState.getGlobalId()
            );
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

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("ChunkDataAdapter registered");
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
