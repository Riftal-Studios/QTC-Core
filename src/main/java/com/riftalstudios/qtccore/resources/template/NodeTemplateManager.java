package com.riftalstudios.qtccore.resources.template;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.ResourceType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages node templates - creation, saving, loading, and editing sessions.
 */
public class NodeTemplateManager implements Manager, Listener {

    private final QTCCore plugin;
    private final Map<String, NodeTemplate> templates = new ConcurrentHashMap<>();
    private final Map<UUID, TemplateEditSession> editSessions = new ConcurrentHashMap<>();

    private File templatesFile;
    private FileConfiguration templatesConfig;

    public NodeTemplateManager(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        loadTemplates();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("NodeTemplateManager enabled with " + templates.size() + " templates");
    }

    @Override
    public void disable() {
        saveTemplates();
        editSessions.clear();
        templates.clear();
        plugin.getLogger().info("NodeTemplateManager disabled");
    }

    @Override
    public String getName() {
        return "NodeTemplateManager";
    }

    // ==================== Template Management ====================

    public NodeTemplate getTemplate(String id) {
        return templates.get(id.toLowerCase());
    }

    public Collection<NodeTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public boolean templateExists(String id) {
        return templates.containsKey(id.toLowerCase());
    }

    public void saveTemplate(NodeTemplate template) {
        templates.put(template.getId().toLowerCase(), template);
        saveTemplates();
    }

    public void deleteTemplate(String id) {
        templates.remove(id.toLowerCase());
        saveTemplates();
    }

    // ==================== Edit Sessions ====================

    /**
     * Start a new template editing session for a player.
     */
    public TemplateEditSession startEditing(Player player, String templateId, ResourceType type) {
        NodeTemplate template = new NodeTemplate(templateId.toLowerCase(), type);
        TemplateEditSession session = new TemplateEditSession(player.getUniqueId(), template);
        editSessions.put(player.getUniqueId(), session);
        return session;
    }

    /**
     * Get the current editing session for a player.
     */
    public TemplateEditSession getEditSession(Player player) {
        return editSessions.get(player.getUniqueId());
    }

    /**
     * Check if a player is currently editing a template.
     */
    public boolean isEditing(Player player) {
        return editSessions.containsKey(player.getUniqueId());
    }

    /**
     * Save and finish the current editing session.
     */
    public NodeTemplate finishEditing(Player player) {
        TemplateEditSession session = editSessions.remove(player.getUniqueId());
        if (session == null) return null;

        NodeTemplate template = session.getTemplate();
        if (template.isValid()) {
            saveTemplate(template);
            return template;
        }
        return null;
    }

    /**
     * Cancel the current editing session without saving.
     */
    public void cancelEditing(Player player) {
        editSessions.remove(player.getUniqueId());
    }

    // ==================== Event Handlers ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        TemplateEditSession session = editSessions.get(player.getUniqueId());

        if (session == null) return;

        Location loc = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        // Check adjacency - first block is always allowed, subsequent blocks must be adjacent
        if (session.hasBaseLocation() && !isAdjacentToTemplate(session, loc)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text()
                    .append(Component.text("Block must be adjacent to existing template blocks!", NamedTextColor.RED))
                    .build());
            return;
        }

        // Add block to template - let the block actually be placed for visual feedback
        session.addBlock(loc, material);

        // Show feedback
        player.sendActionBar(Component.text()
                .append(Component.text("Added block at [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "] ", NamedTextColor.GREEN))
                .append(Component.text("(" + session.getBlockCount() + " blocks total)", NamedTextColor.GRAY))
                .build());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        TemplateEditSession session = editSessions.get(player.getUniqueId());

        if (session == null) return;

        // Remove block from template - let the block actually break for visual feedback
        Location loc = event.getBlock().getLocation();
        boolean removed = session.removeBlock(loc);

        // Ensure items drop
        event.setDropItems(true);

        // Only show feedback if we actually removed a template block
        if (removed) {
            player.sendActionBar(Component.text()
                    .append(Component.text("Removed block at [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "] ", NamedTextColor.RED))
                    .append(Component.text("(" + session.getBlockCount() + " blocks total)", NamedTextColor.GRAY))
                    .build());
        }
    }

    /**
     * Check if a location is adjacent to any block in the template.
     */
    private boolean isAdjacentToTemplate(TemplateEditSession session, Location loc) {
        Location base = session.getBaseLocation();
        if (base == null) return true; // First block

        int newX = loc.getBlockX();
        int newY = loc.getBlockY();
        int newZ = loc.getBlockZ();

        // Check each existing block in the template
        for (BlockOffset offset : session.getTemplate().getBlocks()) {
            int blockX = base.getBlockX() + offset.x();
            int blockY = base.getBlockY() + offset.y();
            int blockZ = base.getBlockZ() + offset.z();

            // Check if adjacent (within 1 block in any direction, including diagonals)
            int dx = Math.abs(newX - blockX);
            int dy = Math.abs(newY - blockY);
            int dz = Math.abs(newZ - blockZ);

            // Adjacent means at most 1 block away in each axis
            if (dx <= 1 && dy <= 1 && dz <= 1 && (dx + dy + dz) > 0) {
                return true;
            }
        }

        return false;
    }

    // ==================== Persistence ====================

    private void loadTemplates() {
        templatesFile = new File(plugin.getDataFolder(), "templates.yml");

        if (!templatesFile.exists()) {
            try {
                templatesFile.getParentFile().mkdirs();
                templatesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create templates.yml: " + e.getMessage());
                return;
            }
        }

        templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        ConfigurationSection templatesSection = templatesConfig.getConfigurationSection("templates");

        if (templatesSection == null) return;

        for (String templateId : templatesSection.getKeys(false)) {
            ConfigurationSection templateSection = templatesSection.getConfigurationSection(templateId);
            if (templateSection == null) continue;

            String typeName = templateSection.getString("type", "OAK_TREE");
            ResourceType type;
            try {
                type = ResourceType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid resource type for template " + templateId + ": " + typeName);
                continue;
            }

            List<BlockOffset> blocks = new ArrayList<>();
            List<Map<?, ?>> blocksList = templateSection.getMapList("blocks");

            for (Map<?, ?> blockMap : blocksList) {
                int x = ((Number) blockMap.get("x")).intValue();
                int y = ((Number) blockMap.get("y")).intValue();
                int z = ((Number) blockMap.get("z")).intValue();
                String materialName = (String) blockMap.get("material");
                Material material = Material.valueOf(materialName);
                blocks.add(new BlockOffset(x, y, z, material));
            }

            NodeTemplate template = new NodeTemplate(templateId, type, blocks);
            templates.put(templateId.toLowerCase(), template);
        }

        plugin.getLogger().info("Loaded " + templates.size() + " node templates");
    }

    private void saveTemplates() {
        if (templatesConfig == null) return;

        templatesConfig.set("templates", null); // Clear existing

        for (NodeTemplate template : templates.values()) {
            String path = "templates." + template.getId();
            templatesConfig.set(path + ".type", template.getType().name());

            List<Map<String, Object>> blocksList = new ArrayList<>();
            for (BlockOffset offset : template.getBlocks()) {
                Map<String, Object> blockMap = new HashMap<>();
                blockMap.put("x", offset.x());
                blockMap.put("y", offset.y());
                blockMap.put("z", offset.z());
                blockMap.put("material", offset.material().name());
                blocksList.add(blockMap);
            }
            templatesConfig.set(path + ".blocks", blocksList);
        }

        try {
            templatesConfig.save(templatesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save templates.yml: " + e.getMessage());
        }
    }
}
