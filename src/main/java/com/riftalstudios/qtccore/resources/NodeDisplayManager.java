package com.riftalstudios.qtccore.resources;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NodeDisplayManager {

    private final QTCCore plugin;
    private final NamespacedKey nodeIdKey;
    private final Map<UUID, UUID> nodeToDisplay = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> nodeToInteraction = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> interactionToNode = new ConcurrentHashMap<>();

    public NodeDisplayManager(QTCCore plugin) {
        this.plugin = plugin;
        this.nodeIdKey = new NamespacedKey(plugin, "resource_node_id");
    }

    public void showNode(ResourceNode node) {
        if (node.getState() != NodeState.ACTIVE) return;
        if (nodeToDisplay.containsKey(node.getId())) return;

        Location loc = node.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Location displayLoc = loc.clone().add(0.5, 0.5, 0.5);

        BlockDisplay display = world.spawn(displayLoc, BlockDisplay.class, entity -> {
            entity.setBlock(node.getType().getDisplayBlock().createBlockData());
            entity.setPersistent(true);
            entity.setInvulnerable(true);

            Transformation transform = new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            entity.setTransformation(transform);

            entity.getPersistentDataContainer().set(nodeIdKey, PersistentDataType.STRING, node.getId().toString());
        });

        Interaction interaction = world.spawn(displayLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.0f);
            entity.setResponsive(true);
            entity.setPersistent(true);

            entity.getPersistentDataContainer().set(nodeIdKey, PersistentDataType.STRING, node.getId().toString());
        });

        nodeToDisplay.put(node.getId(), display.getUniqueId());
        nodeToInteraction.put(node.getId(), interaction.getUniqueId());
        interactionToNode.put(interaction.getUniqueId(), node.getId());

        plugin.getLogger().info("Spawned display entities for node " + node.getId().toString().substring(0, 8));
    }

    public void hideNode(ResourceNode node) {
        UUID displayId = nodeToDisplay.remove(node.getId());
        UUID interactionId = nodeToInteraction.remove(node.getId());

        if (displayId != null) {
            Entity display = plugin.getServer().getEntity(displayId);
            if (display != null) display.remove();
        }

        if (interactionId != null) {
            interactionToNode.remove(interactionId);
            Entity interaction = plugin.getServer().getEntity(interactionId);
            if (interaction != null) interaction.remove();
        }

        plugin.getLogger().info("Removed display entities for node " + node.getId().toString().substring(0, 8));
    }

    public UUID getNodeIdFromInteraction(UUID interactionEntityId) {
        return interactionToNode.get(interactionEntityId);
    }

    public UUID getNodeIdFromInteraction(Interaction interaction) {
        UUID cached = interactionToNode.get(interaction.getUniqueId());
        if (cached != null) return cached;

        String nodeIdStr = interaction.getPersistentDataContainer().get(nodeIdKey, PersistentDataType.STRING);
        if (nodeIdStr != null) {
            UUID nodeId = UUID.fromString(nodeIdStr);
            interactionToNode.put(interaction.getUniqueId(), nodeId);
            nodeToInteraction.put(nodeId, interaction.getUniqueId());
            return nodeId;
        }
        return null;
    }

    public void refreshNode(ResourceNode node) {
        hideNode(node);
        if (node.getState() == NodeState.ACTIVE) {
            showNode(node);
        }
    }

    public void removeAllDisplays() {
        for (UUID displayId : nodeToDisplay.values()) {
            Entity entity = plugin.getServer().getEntity(displayId);
            if (entity != null) entity.remove();
        }
        for (UUID interactionId : nodeToInteraction.values()) {
            Entity entity = plugin.getServer().getEntity(interactionId);
            if (entity != null) entity.remove();
        }
        nodeToDisplay.clear();
        nodeToInteraction.clear();
        interactionToNode.clear();
    }

    public boolean isResourceNodeInteraction(Interaction interaction) {
        return interaction.getPersistentDataContainer().has(nodeIdKey, PersistentDataType.STRING);
    }

    public void playHitAnimation(UUID nodeId) {
        UUID displayId = nodeToDisplay.get(nodeId);
        if (displayId == null) return;

        Entity entity = plugin.getServer().getEntity(displayId);
        if (!(entity instanceof BlockDisplay display)) return;

        // Random shake direction
        float shakeX = (float) ((Math.random() - 0.5) * 0.1);
        float shakeZ = (float) ((Math.random() - 0.5) * 0.1);

        // Apply shake offset
        Transformation shakeTransform = new Transformation(
                new Vector3f(-0.5f + shakeX, -0.5f, -0.5f + shakeZ),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1, 1, 1),
                new AxisAngle4f(0, 0, 0, 1)
        );
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        display.setTransformation(shakeTransform);

        // Reset after 2 ticks
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            Transformation normalTransform = new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);
            display.setTransformation(normalTransform);
        }, 2L);
    }
}
