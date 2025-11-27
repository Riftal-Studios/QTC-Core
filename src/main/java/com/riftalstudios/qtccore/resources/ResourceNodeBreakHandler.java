package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Material;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceNodeBreakHandler implements Listener {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 500;

    public ResourceNodeBreakHandler(QTCCore plugin, ResourceNodeManager nodeManager) {
        this.plugin = plugin;
        this.nodeManager = nodeManager;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ResourceNodeBreakHandler registered");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        NodeDisplayManager displayManager = nodeManager.getDisplayManager();
        if (displayManager == null) return;
        if (!displayManager.isResourceNodeInteraction(interaction)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
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

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7" + node.getType().name() + " §8| §c" + node.getCurrentHp() + "§7/§c" + node.getMaxHp() + " HP"
        ));

        if (node.getState() == NodeState.DEPLETED) {
            player.sendMessage("§aYou gathered resources from the " + node.getType().name().toLowerCase().replace("_", " ") + "!");
        }
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
