package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.packet.NodeBlockManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handles resource node breaking via packet interception.
 *
 * In the pure packet-based system, we only handle PLAYER_DIGGING packets.
 * No entity interaction handling needed since we no longer spawn interaction entities.
 *
 * Detection flow:
 * 1. Player left-clicks a block position
 * 2. PLAYER_DIGGING packet is sent with START_DIGGING action
 * 3. We check if that position has a visible node for this player
 * 4. If yes, cancel the packet and handle node interaction
 */
public class ResourceNodeBreakHandler extends PacketListenerAbstract {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final PlayerNodeStateManager stateManager;
    private final NodeBlockManager blockManager;
    private static final long INTERACTION_COOLDOWN_MS = 250;

    public ResourceNodeBreakHandler(QTCCore plugin, ResourceNodeManager nodeManager,
                                    PlayerNodeStateManager stateManager, NodeBlockManager blockManager) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.nodeManager = nodeManager;
        this.stateManager = stateManager;
        this.blockManager = blockManager;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("ResourceNodeBreakHandler registered (packet listener)");
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Only handle block digging (left click on block position)
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        handleBlockDigging(event);
    }

    private void handleBlockDigging(PacketReceiveEvent event) {
        WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = packet.getAction();

        // We only care about start digging (left click on block)
        if (action != DiggingAction.START_DIGGING) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        Vector3i blockPos = packet.getBlockPosition();
        String worldName = player.getWorld().getName();

        // Check if there's a resource node at this position that the player can see
        ResourceNode node = blockManager.getNodeAt(worldName, blockPos.getX(), blockPos.getY(), blockPos.getZ());
        if (node == null) return;

        // Verify player is a viewer of this node
        if (!blockManager.isViewer(node, player)) return;

        // Check player's state - if depleted for them, don't handle
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state != null && state.isDepleted()) return;

        // Cancel the dig packet - don't let it through
        event.setCancelled(true);

        Location loc = new Location(player.getWorld(), blockPos.getX(), blockPos.getY(), blockPos.getZ());

        // Run on main thread (Bukkit API requires it)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            handleNodeInteraction(player, node, loc);
        });
    }

    private void handleNodeInteraction(Player player, ResourceNode node, Location loc) {
        UUID playerId = player.getUniqueId();

        // Get or create per-player state
        PlayerNodeState state = stateManager.getOrCreateState(playerId, node);

        // Check cooldown
        if (state.isOnCooldown(INTERACTION_COOLDOWN_MS)) {
            return;
        }
        state.setLastHitTime(System.currentTimeMillis());

        // If node is depleted for this player, ignore
        if (state.isDepleted()) {
            return;
        }

        // Calculate and apply damage
        int damage = calculateDamage(player, node);
        state.damage(damage);

        // Show action bar with HP
        player.sendActionBar(Component.text()
                .append(Component.text(node.getType().name(), NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(state.getCurrentHp(), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(state.getMaxHp() + " HP", NamedTextColor.RED))
                .build());

        // Play hit effects
        playHitEffects(player, node, loc);

        // Check if depleted for this player
        if (state.isDepleted()) {
            handleNodeDepleted(player, node, state, loc);
        }
    }

    private void handleNodeDepleted(Player player, ResourceNode node, PlayerNodeState state, Location loc) {
        // Play depletion effects
        playDepletedEffects(player, node, loc);

        // Hide the display for this player
        NodeDisplayManager displayManager = nodeManager.getDisplayManager();
        if (displayManager != null) {
            displayManager.hideNodeFromPlayer(player, node);
        }

        // Drop loot based on the blocks in the node
        dropNodeLoot(player, node, loc);

        // Schedule regeneration for this player
        long regenTimeMs = node.getType().getDefaultRespawnSeconds() * 1000L;
        state.scheduleRegeneration(regenTimeMs);

        // Notify player
        player.sendMessage(Component.text(
                "You gathered resources from the " + node.getType().name().toLowerCase().replace("_", " ") + "!",
                NamedTextColor.GREEN));

        // TODO: Grant XP via MMOCore integration
    }

    private void dropNodeLoot(Player player, ResourceNode node, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        org.bukkit.inventory.ItemStack tool = player.getInventory().getItemInMainHand();
        Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);

        // Drop items for each block in the node
        for (var offset : node.getBlocks()) {
            Material material = offset.material();

            // Skip non-item materials (like air)
            if (!material.isItem()) continue;

            // Get the block location for this offset
            Location blockLoc = node.getBaseLocation().clone().add(offset.x(), offset.y(), offset.z());

            // Temporarily place the block to get proper drops
            Material originalMaterial = blockLoc.getBlock().getType();
            blockLoc.getBlock().setType(material, false);

            // Get drops with the player's tool (respects fortune, silk touch, etc.)
            Collection<org.bukkit.inventory.ItemStack> drops = blockLoc.getBlock().getDrops(tool);

            // Restore original block state
            blockLoc.getBlock().setType(originalMaterial, false);

            // If no drops (shouldn't happen for valid blocks), drop the material itself
            if (drops.isEmpty()) {
                drops = List.of(new org.bukkit.inventory.ItemStack(material));
            }

            // Drop all items
            for (org.bukkit.inventory.ItemStack drop : drops) {
                world.dropItemNaturally(dropLoc, drop);
            }
        }
    }

    private void playHitEffects(Player player, ResourceNode node, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Material blockMaterial = node.getType().getDisplayBlock();
        BlockData blockData = blockMaterial.createBlockData();

        // Play block hit sound with slight pitch variation
        Sound hitSound = getHitSound(node.getType());
        world.playSound(loc.clone().add(0.5, 0.5, 0.5), hitSound, 1.0f, 0.8f + (float)(Math.random() * 0.4));

        // Particle burst for hit feedback
        world.spawnParticle(
                Particle.BLOCK,
                loc.clone().add(0.5, 0.5, 0.5),
                15,
                0.3, 0.3, 0.3,
                0.1,
                blockData
        );

        // Play break animation (shows crack stages on the block)
        // Get state to pass current HP directly
        PlayerNodeState state = stateManager.getState(player.getUniqueId(), node.getId());
        if (state != null) {
            NodeDisplayManager displayManager = nodeManager.getDisplayManager();
            if (displayManager != null) {
                displayManager.getBreakAnimationTask().updateBreakAnimation(
                        player, node, state.getCurrentHp(), state.getMaxHp()
                );
            }
        }
    }

    private void playDepletedEffects(Player player, ResourceNode node, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Material blockMaterial = node.getType().getDisplayBlock();
        BlockData blockData = blockMaterial.createBlockData();

        // Play break sound
        Sound breakSound = getBreakSound(node.getType());
        world.playSound(loc.clone().add(0.5, 0.5, 0.5), breakSound, 1.0f, 1.0f);

        // Spawn larger burst of particles for all blocks in the node
        for (Location blockLoc : node.getAllBlockLocations()) {
            world.spawnParticle(
                    Particle.BLOCK,
                    blockLoc.clone().add(0.5, 0.5, 0.5),
                    30,
                    0.4, 0.4, 0.4,
                    0.1,
                    blockData
            );
        }
    }

    private Sound getHitSound(ResourceType type) {
        return type.getHitSound();
    }

    private Sound getBreakSound(ResourceType type) {
        return type.getBreakSound();
    }

    private int calculateDamage(Player player, ResourceNode node) {
        Material tool = player.getInventory().getItemInMainHand().getType();
        ResourceType type = node.getType();

        if (type.isCorrectTool(tool)) {
            return getToolPower(tool);
        }
        return 1;
    }

    private int getToolPower(Material tool) {
        String name = tool.name();
        if (name.startsWith("NETHERITE_")) return 25;
        if (name.startsWith("DIAMOND_")) return 20;
        if (name.startsWith("IRON_")) return 15;
        if (name.startsWith("GOLDEN_")) return 12;
        if (name.startsWith("STONE_")) return 10;
        if (name.startsWith("WOODEN_")) return 5;
        return 5;
    }
}
