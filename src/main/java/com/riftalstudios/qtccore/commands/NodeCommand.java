package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.ResourceNodeManager;
import com.riftalstudios.qtccore.resources.ResourceType;
import com.riftalstudios.qtccore.resources.template.NodeTemplate;
import com.riftalstudios.qtccore.resources.template.NodeTemplateManager;
import com.riftalstudios.qtccore.resources.template.TemplateEditSession;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
            case "template" -> handleTemplate(player, subArgs);
            case "place" -> handlePlace(player, subArgs);
            case "info" -> handleInfo(player);
            case "remove" -> handleRemove(player);
            case "list" -> handleList(player);
            default -> sendUsage(player);
        }
    }

    // ==================== Template Commands ====================

    private void handleTemplate(Player player, String[] args) {
        if (args.length == 0) {
            sendTemplateUsage(player);
            return;
        }

        String action = args[0].toLowerCase();
        String[] actionArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "create" -> handleTemplateCreate(player, actionArgs);
            case "save" -> handleTemplateSave(player);
            case "cancel" -> handleTemplateCancel(player);
            case "list" -> handleTemplateList(player);
            case "delete" -> handleTemplateDelete(player, actionArgs);
            case "info" -> handleTemplateInfo(player, actionArgs);
            default -> sendTemplateUsage(player);
        }
    }

    private void handleTemplateCreate(Player player, String[] args) {
        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();

        // Check if already editing
        if (templateManager.isEditing(player)) {
            TemplateEditSession session = templateManager.getEditSession(player);
            player.sendMessage("§cYou're already editing template '§4" + session.getTemplate().getId() + "§c'.");
            player.sendMessage("§7Use §f/qtc node template save §7or §f/qtc node template cancel §7first.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /qtc node template create <id> <type>");
            player.sendMessage("§7Types: " + Arrays.stream(ResourceType.values())
                    .map(t -> t.name().toLowerCase())
                    .collect(Collectors.joining(", ")));
            return;
        }

        String templateId = args[0].toLowerCase();
        String typeName = args[1].toUpperCase();

        // Check if template already exists
        if (templateManager.templateExists(templateId)) {
            player.sendMessage("§cA template with ID '§4" + templateId + "§c' already exists.");
            return;
        }

        // Parse resource type
        ResourceType type;
        try {
            type = ResourceType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid resource type: " + args[1]);
            return;
        }

        // Start editing session
        templateManager.startEditing(player, templateId, type);
        player.sendMessage("§aStarted creating template '§2" + templateId + "§a' (Type: " + type.name() + ")");
        player.sendMessage("§7Place blocks to define the node shape.");
        player.sendMessage("§7Break blocks to remove them from the template.");
        player.sendMessage("§7The first block you place will be the base (origin).");
        player.sendMessage("§7Use §f/qtc node template save §7when done.");
    }

    private void handleTemplateSave(Player player) {
        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();

        if (!templateManager.isEditing(player)) {
            player.sendMessage("§cYou're not editing any template.");
            return;
        }

        TemplateEditSession session = templateManager.getEditSession(player);

        if (session.isEmpty()) {
            player.sendMessage("§cCannot save empty template. Place at least one block.");
            return;
        }

        NodeTemplate template = templateManager.finishEditing(player);
        if (template != null) {
            player.sendMessage("§aSaved template '§2" + template.getId() + "§a' with §2" +
                    template.getBlockCount() + "§a blocks.");
        } else {
            player.sendMessage("§cFailed to save template.");
        }
    }

    private void handleTemplateCancel(Player player) {
        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();

        if (!templateManager.isEditing(player)) {
            player.sendMessage("§cYou're not editing any template.");
            return;
        }

        TemplateEditSession session = templateManager.getEditSession(player);
        String templateId = session.getTemplate().getId();
        templateManager.cancelEditing(player);
        player.sendMessage("§cCancelled editing template '§4" + templateId + "§c'.");
    }

    private void handleTemplateList(Player player) {
        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();
        Collection<NodeTemplate> templates = templateManager.getAllTemplates();

        if (templates.isEmpty()) {
            player.sendMessage("§7No templates exist. Create one with §f/qtc node template create <id> <type>");
            return;
        }

        player.sendMessage("§6=== Node Templates (" + templates.size() + ") ===");
        for (NodeTemplate template : templates) {
            player.sendMessage("§7- §f" + template.getId() + " §8(" + template.getBlockCount() +
                    " blocks, " + template.getType().name() + ")");
        }
    }

    private void handleTemplateDelete(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc node template delete <id>");
            return;
        }

        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();
        String templateId = args[0].toLowerCase();

        if (!templateManager.templateExists(templateId)) {
            player.sendMessage("§cTemplate '§4" + templateId + "§c' does not exist.");
            return;
        }

        templateManager.deleteTemplate(templateId);
        player.sendMessage("§aDeleted template '§2" + templateId + "§a'.");
    }

    private void handleTemplateInfo(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc node template info <id>");
            return;
        }

        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();
        String templateId = args[0].toLowerCase();

        NodeTemplate template = templateManager.getTemplate(templateId);
        if (template == null) {
            player.sendMessage("§cTemplate '§4" + templateId + "§c' does not exist.");
            return;
        }

        player.sendMessage("§6=== Template: " + template.getId() + " ===");
        player.sendMessage("§7Type: §f" + template.getType().name());
        player.sendMessage("§7Blocks: §f" + template.getBlockCount());
        player.sendMessage("§7HP: §f" + template.getType().getDefaultHp());
        player.sendMessage("§7Respawn: §f" + template.getType().getDefaultRespawnSeconds() + "s");
    }

    // ==================== Node Instance Commands ====================

    private void handlePlace(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc node place <template_id>");
            player.sendMessage("§7Use §f/qtc node template list §7to see available templates.");
            return;
        }

        String templateId = args[0].toLowerCase();
        NodeTemplateManager templateManager = plugin.getNodeTemplateManager();
        NodeTemplate template = templateManager.getTemplate(templateId);

        if (template == null) {
            player.sendMessage("§cTemplate '§4" + templateId + "§c' does not exist.");
            player.sendMessage("§7Use §f/qtc node template list §7to see available templates.");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cYou must be looking at a block within 5 blocks.");
            return;
        }

        Location baseLoc = targetBlock.getLocation().add(0, 1, 0);
        ResourceNode node = new ResourceNode(baseLoc, template);

        plugin.getResourceNodeManager().createNode(node);

        player.sendMessage("§aPlaced '§2" + templateId + "§a' node at " +
                baseLoc.getBlockX() + ", " + baseLoc.getBlockY() + ", " + baseLoc.getBlockZ());
        player.sendMessage("§7Blocks: §f" + template.getBlockCount());
        player.sendMessage("§7HP: §f" + node.getMaxHp());
        player.sendMessage("§7Node ID: §f" + node.getId().toString().substring(0, 8));
    }

    private void handleInfo(Player player) {
        ResourceNodeManager manager = plugin.getResourceNodeManager();
        ResourceNode node = manager.getNearestNode(player.getLocation(), 10);

        if (node == null) {
            player.sendMessage("§cNo resource node found within 10 blocks.");
            return;
        }

        player.sendMessage("§6=== Node Info ===");
        player.sendMessage("§7Template: §f" + (node.getTemplateId() != null ? node.getTemplateId() : "legacy"));
        player.sendMessage("§7Type: §f" + node.getType().name());
        player.sendMessage("§7Blocks: §f" + node.getBlockCount());
        player.sendMessage("§7HP: §f" + node.getCurrentHp() + "/" + node.getMaxHp());
        player.sendMessage("§7State: §f" + node.getState().name());
        player.sendMessage("§7Base: §f" + node.getBaseLocation().getBlockX() + ", " +
                node.getBaseLocation().getBlockY() + ", " + node.getBaseLocation().getBlockZ());
        player.sendMessage("§7ID: §f" + node.getId().toString().substring(0, 8));
    }

    private void handleRemove(Player player) {
        ResourceNodeManager manager = plugin.getResourceNodeManager();
        ResourceNode node = manager.getNearestNode(player.getLocation(), 10);

        if (node == null) {
            player.sendMessage("§cNo resource node found within 10 blocks.");
            return;
        }

        String templateId = node.getTemplateId() != null ? node.getTemplateId() : node.getType().name();
        manager.removeNode(node.getId());
        player.sendMessage("§aRemoved '§2" + templateId + "§a' node.");
    }

    private void handleList(Player player) {
        ResourceNodeManager manager = plugin.getResourceNodeManager();
        Collection<ResourceNode> nodes = manager.getAllNodes();

        if (nodes.isEmpty()) {
            player.sendMessage("§7No resource nodes exist.");
            return;
        }

        player.sendMessage("§6=== Resource Nodes (" + nodes.size() + ") ===");
        int count = 0;
        for (ResourceNode node : nodes) {
            if (count >= 10) {
                player.sendMessage("§7... and " + (nodes.size() - 10) + " more");
                break;
            }
            String templateId = node.getTemplateId() != null ? node.getTemplateId() : node.getType().name();
            player.sendMessage("§7- §f" + templateId + " §7at " +
                    node.getBaseLocation().getBlockX() + ", " +
                    node.getBaseLocation().getBlockY() + ", " +
                    node.getBaseLocation().getBlockZ() +
                    " §8(" + node.getBlockCount() + " blocks)");
            count++;
        }
    }

    // ==================== Usage ====================

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Node Commands ===");
        player.sendMessage("§7/qtc node template ... §8- Manage node templates");
        player.sendMessage("§7/qtc node place <template> §8- Place a node instance");
        player.sendMessage("§7/qtc node info §8- Get info on nearest node");
        player.sendMessage("§7/qtc node remove §8- Remove nearest node");
        player.sendMessage("§7/qtc node list §8- List all placed nodes");
    }

    private void sendTemplateUsage(Player player) {
        player.sendMessage("§6=== Template Commands ===");
        player.sendMessage("§7/qtc node template create <id> <type> §8- Start creating template");
        player.sendMessage("§7/qtc node template save §8- Save current template");
        player.sendMessage("§7/qtc node template cancel §8- Cancel editing");
        player.sendMessage("§7/qtc node template list §8- List all templates");
        player.sendMessage("§7/qtc node template delete <id> §8- Delete a template");
        player.sendMessage("§7/qtc node template info <id> §8- View template info");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("template", "place", "info", "remove", "list"), args[0]);
        }

        if (args[0].equalsIgnoreCase("template")) {
            if (args.length == 2) {
                return filterStartsWith(Arrays.asList("create", "save", "cancel", "list", "delete", "info"), args[1]);
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("create")) {
                    return filterStartsWith(List.of("<template_id>"), args[2]);
                }
                if (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("info")) {
                    return filterStartsWith(
                            plugin.getNodeTemplateManager().getAllTemplates().stream()
                                    .map(NodeTemplate::getId)
                                    .toList(),
                            args[2]
                    );
                }
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                return filterStartsWith(
                        Arrays.stream(ResourceType.values())
                                .map(t -> t.name().toLowerCase())
                                .toList(),
                        args[3]
                );
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filterStartsWith(
                    plugin.getNodeTemplateManager().getAllTemplates().stream()
                            .map(NodeTemplate::getId)
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
