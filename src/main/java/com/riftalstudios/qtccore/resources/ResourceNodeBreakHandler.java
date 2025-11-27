package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceNodeBreakHandler implements Listener {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 250;

    public ResourceNodeBreakHandler(QTCCore plugin, ResourceNodeManager nodeManager) {
        this.plugin = plugin;
        this.nodeManager = nodeManager;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ResourceNodeBreakHandler registered");
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Interaction interaction)) return;

        NodeDisplayManager displayManager = nodeManager.getDisplayManager();
        if (displayManager == null) return;
        if (!displayManager.isResourceNodeInteraction(interaction)) return;

        event.setCancelled(true);

        UUID playerId = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long lastTime = lastInteraction.get(playerId);
        if (lastTime != null && (now - lastTime) < INTERACTION_COOLDOWN_MS) {
            return;
        }
        lastInteraction.put(playerId, now);

        UUID nodeId = displayManager.getNodeIdFromInteraction(interaction);
        if (nodeId == null) return;

        ResourceNode node = nodeManager.getNode(nodeId);
        if (node == null || node.getState() != NodeState.ACTIVE) return;

        int damage = calculateDamage(player, node);
        nodeManager.damageNode(node, damage);

        player.sendActionBar(net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text(node.getType().name(), net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                .append(net.kyori.adventure.text.Component.text(node.getCurrentHp(), net.kyori.adventure.text.format.NamedTextColor.RED))
                .append(net.kyori.adventure.text.Component.text("/", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text(node.getMaxHp() + " HP", net.kyori.adventure.text.format.NamedTextColor.RED))
                .build());

        playHitEffects(player, node, interaction);

        if (node.getState() == NodeState.DEPLETED) {
            playDepletedEffects(node, interaction);
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "You gathered resources from the " + node.getType().name().toLowerCase().replace("_", " ") + "!",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
    }

    private void playDepletedEffects(ResourceNode node, Interaction interaction) {
        Location loc = interaction.getLocation().clone();
        Material blockMaterial = node.getType().getDisplayBlock();
        BlockData blockData = blockMaterial.createBlockData();

        // Play break sound
        Sound breakSound = getBreakSound(node.getType());
        loc.getWorld().playSound(loc, breakSound, 1.0f, 1.0f);

        // Spawn larger burst of particles for break
        loc.getWorld().spawnParticle(
                Particle.BLOCK,
                loc.add(0, 0.5, 0),
                30,
                0.4, 0.4, 0.4,
                0.1,
                blockData
        );
    }

    private Sound getBreakSound(ResourceType type) {
        String typeName = type.name();
        if (typeName.contains("TREE")) {
            return Sound.BLOCK_WOOD_BREAK;
        }
        if (typeName.contains("ORE")) {
            return Sound.BLOCK_STONE_BREAK;
        }
        return Sound.BLOCK_STONE_BREAK;
    }

    private void playHitEffects(Player player, ResourceNode node, Interaction interaction) {
        Location loc = interaction.getLocation().clone();
        Material blockMaterial = node.getType().getDisplayBlock();
        BlockData blockData = blockMaterial.createBlockData();

        // Play block hit sound
        Sound hitSound = getHitSound(node.getType());
        loc.getWorld().playSound(loc, hitSound, 1.0f, 0.8f + (float)(Math.random() * 0.4));

        // Spawn block crack particles at hit location
        loc.getWorld().spawnParticle(
                Particle.BLOCK,
                loc.add(0, 0.5, 0),
                10,
                0.3, 0.3, 0.3,
                0.1,
                blockData
        );

        // Play shake animation on the BlockDisplay
        NodeDisplayManager displayManager = nodeManager.getDisplayManager();
        if (displayManager != null) {
            displayManager.playHitAnimation(node.getId());
        }
    }

    private Sound getHitSound(ResourceType type) {
        String typeName = type.name();
        if (typeName.contains("TREE")) {
            return Sound.BLOCK_WOOD_HIT;
        }
        if (typeName.contains("ORE")) {
            return Sound.BLOCK_STONE_HIT;
        }
        return Sound.BLOCK_STONE_HIT;
    }

    private int calculateDamage(Player player, ResourceNode node) {
        Material tool = player.getInventory().getItemInMainHand().getType();
        ResourceType type = node.getType();

        if (isCorrectTool(tool, type)) {
            return getToolPower(tool);
        }
        return 1;
    }

    private boolean isCorrectTool(Material tool, ResourceType type) {
        String toolName = tool.name();
        String typeName = type.name();

        if (typeName.contains("TREE")) {
            return toolName.contains("AXE") && !toolName.contains("PICKAXE");
        }
        if (typeName.contains("ORE")) {
            return toolName.contains("PICKAXE");
        }
        return false;
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
