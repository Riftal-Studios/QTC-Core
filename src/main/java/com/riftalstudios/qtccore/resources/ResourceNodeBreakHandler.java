package com.riftalstudios.qtccore.resources;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceNodeBreakHandler extends PacketListenerAbstract {

    private final QTCCore plugin;
    private final ResourceNodeManager nodeManager;
    private final Map<UUID, BreakingContext> breakingPlayers = new ConcurrentHashMap<>();
    private BukkitRunnable breakingTask;

    public ResourceNodeBreakHandler(QTCCore plugin, ResourceNodeManager nodeManager) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.nodeManager = nodeManager;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        startBreakingTask();
        plugin.getLogger().info("ResourceNodeBreakHandler registered");
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        if (breakingTask != null) {
            breakingTask.cancel();
        }
        for (UUID playerId : breakingPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeMiningFatigue(player);
            }
        }
        breakingPlayers.clear();
        plugin.getLogger().info("ResourceNodeBreakHandler unregistered");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        Player player = (Player) event.getPlayer();
        WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
        Vector3i position = packet.getBlockPosition();
        Location location = new Location(player.getWorld(), position.getX(), position.getY(), position.getZ());

        ResourceNode node = nodeManager.getNearestNode(location, 0.5);
        if (node == null || node.getState() != NodeState.ACTIVE) return;

        switch (packet.getAction()) {
            case START_DIGGING -> handleStartDigging(player, node, location);
            case CANCELLED_DIGGING -> handleCancelDigging(player);
            case FINISHED_DIGGING -> handleFinishDigging(player, node);
        }
    }

    private void handleStartDigging(Player player, ResourceNode node, Location location) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int breakSpeed = calculateBreakSpeed(player, node);

            applyMiningFatigue(player);

            breakingPlayers.put(player.getUniqueId(), new BreakingContext(node, location, breakSpeed));
        });
    }

    private void handleCancelDigging(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            BreakingContext context = breakingPlayers.remove(player.getUniqueId());
            if (context != null) {
                removeMiningFatigue(player);
                sendBreakAnimation(player, context.location, -1);
            }
        });
    }

    private void handleFinishDigging(Player player, ResourceNode node) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            BreakingContext context = breakingPlayers.remove(player.getUniqueId());
            if (context != null) {
                removeMiningFatigue(player);
                sendBreakAnimation(player, context.location, -1);
            }
        });
    }

    private void startBreakingTask() {
        breakingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, BreakingContext> entry : breakingPlayers.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null) {
                        breakingPlayers.remove(entry.getKey());
                        continue;
                    }

                    BreakingContext context = entry.getValue();
                    ResourceNode node = context.node;

                    if (node.getState() != NodeState.ACTIVE) {
                        breakingPlayers.remove(entry.getKey());
                        removeMiningFatigue(player);
                        sendBreakAnimation(player, context.location, -1);
                        continue;
                    }

                    context.ticksElapsed++;

                    float progress = (float) context.ticksElapsed / (float) context.breakSpeed;
                    int stage = Math.min((int) (progress * 10f), 9);

                    if (stage != context.lastStage) {
                        sendBreakAnimation(player, context.location, stage);
                        context.lastStage = stage;
                    }

                    if (progress >= 1.0f) {
                        completeBreak(player, context);
                    }
                }
            }
        };
        breakingTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void completeBreak(Player player, BreakingContext context) {
        breakingPlayers.remove(player.getUniqueId());
        removeMiningFatigue(player);
        sendBreakAnimation(player, context.location, -1);

        ResourceNode node = context.node;
        int damage = calculateDamage(player, node);

        nodeManager.damageNode(node, damage);

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7" + node.getType().name() + " §8| §c" + node.getCurrentHp() + "§7/§c" + node.getMaxHp() + " HP"
        ));

        if (node.getState() == NodeState.ACTIVE) {
            nodeManager.getDisplayManager().showNodeToPlayer(player, node);
        }
    }

    private int calculateBreakSpeed(Player player, ResourceNode node) {
        Material tool = player.getInventory().getItemInMainHand().getType();
        ResourceType type = node.getType();

        if (isCorrectTool(tool, type)) {
            return 20;
        }
        return 40;
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

    private void applyMiningFatigue(Player player) {
        WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(
                player.getEntityId(),
                PotionTypes.MINING_FATIGUE,
                127,
                Integer.MAX_VALUE,
                (byte) 0
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void removeMiningFatigue(Player player) {
        WrapperPlayServerRemoveEntityEffect packet = new WrapperPlayServerRemoveEntityEffect(
                player.getEntityId(),
                PotionTypes.MINING_FATIGUE
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void sendBreakAnimation(Player player, Location loc, int stage) {
        Vector3i position = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(
                player.getEntityId(),
                position,
                (byte) stage
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private static class BreakingContext {
        final ResourceNode node;
        final Location location;
        final int breakSpeed;
        int ticksElapsed = 0;
        int lastStage = -1;

        BreakingContext(ResourceNode node, Location location, int breakSpeed) {
            this.node = node;
            this.location = location;
            this.breakSpeed = breakSpeed;
        }
    }
}
