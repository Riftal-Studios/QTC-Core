package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.ResourceType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NodeCommand implements SubCommand {

    private final QTCCore plugin;

    public NodeCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "node";
    }

    @Override
    public String getPermission() {
        return "qtccore.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("errors.player-only"));
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String subAction = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subAction) {
            case "place" -> handlePlace(player, subArgs);
            case "info" -> handleInfo(player);
            case "remove" -> handleRemove(player);
            default -> sendUsage(player);
        }
    }

    private void handlePlace(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc node place <type>");
            player.sendMessage("§7Types: " + Arrays.stream(ResourceType.values())
                    .map(t -> t.name().toLowerCase())
                    .collect(Collectors.joining(", ")));
            return;
        }

        String typeName = args[0].toUpperCase();
        ResourceType type;
        try {
            type = ResourceType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid resource type: " + args[0]);
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cYou must be looking at a block within 5 blocks.");
            return;
        }

        Location loc = targetBlock.getLocation();
        ResourceNode node = new ResourceNode(loc, type);

        player.sendMessage("§aCreated " + type.name() + " node at " +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        player.sendMessage("§7HP: " + node.getCurrentHp() + "/" + node.getMaxHp());
        player.sendMessage("§7Respawn: " + type.getDefaultRespawnSeconds() + "s");
        player.sendMessage("§e(Node not persisted yet - just testing data model)");
    }

    private void handleInfo(Player player) {
        player.sendMessage("§e/qtc node info - Coming soon (needs ResourceNodeManager)");
    }

    private void handleRemove(Player player) {
        player.sendMessage("§e/qtc node remove - Coming soon (needs ResourceNodeManager)");
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Node Commands ===");
        player.sendMessage("§7/qtc node place <type> §8- Place a resource node");
        player.sendMessage("§7/qtc node info §8- Get info on nearest node");
        player.sendMessage("§7/qtc node remove §8- Remove nearest node");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("place", "info", "remove"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filterStartsWith(
                    Arrays.stream(ResourceType.values())
                            .map(t -> t.name().toLowerCase())
                            .toList(),
                    args[1]
            );
        }
        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
