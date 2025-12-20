package com.riftalstudios.qtccore.storage;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.NodeState;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.ResourceType;
import com.riftalstudios.qtccore.resources.template.BlockOffset;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ResourceNodeStorage {

    private final QTCCore plugin;
    private final DatabaseManager database;

    public ResourceNodeStorage(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Void> saveNode(ResourceNode node) {
        return CompletableFuture.runAsync(() -> {
            // H2 MERGE syntax for upsert
            String sql = """
                MERGE INTO resource_nodes
                (id, world, x, y, z, type, max_hp, current_hp, state, depleted_at, chunk_x, chunk_z, template_id, blocks_json)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, node.getId().toString());
                stmt.setString(2, node.getWorldName());
                stmt.setInt(3, node.getBaseLocation().getBlockX());
                stmt.setInt(4, node.getBaseLocation().getBlockY());
                stmt.setInt(5, node.getBaseLocation().getBlockZ());
                stmt.setString(6, node.getType().name());
                stmt.setInt(7, node.getMaxHp());
                stmt.setInt(8, node.getCurrentHp());
                stmt.setString(9, node.getState().name());
                stmt.setLong(10, node.getDepletedAt());
                stmt.setInt(11, node.getChunkX());
                stmt.setInt(12, node.getChunkZ());
                stmt.setString(13, node.getTemplateId());
                stmt.setString(14, serializeBlocks(node.getBlocks()));

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save node: " + e.getMessage());
            }
        });
    }

    /**
     * Serialize blocks to a simple JSON-like format.
     * Format: "x,y,z,MATERIAL;x,y,z,MATERIAL;..."
     */
    private String serializeBlocks(List<BlockOffset> blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            BlockOffset offset = blocks.get(i);
            if (i > 0) sb.append(";");
            sb.append(offset.x()).append(",")
              .append(offset.y()).append(",")
              .append(offset.z()).append(",")
              .append(offset.material().name());
        }
        return sb.toString();
    }

    /**
     * Deserialize blocks from a simple format.
     */
    private List<BlockOffset> deserializeBlocks(String blocksJson, ResourceType type) {
        List<BlockOffset> blocks = new ArrayList<>();

        if (blocksJson == null || blocksJson.isEmpty()) {
            // Default single block for legacy nodes
            blocks.add(new BlockOffset(0, 0, 0, type.getDefaultMaterial()));
            return blocks;
        }

        String[] parts = blocksJson.split(";");
        for (String part : parts) {
            String[] values = part.split(",");
            if (values.length >= 4) {
                int x = Integer.parseInt(values[0]);
                int y = Integer.parseInt(values[1]);
                int z = Integer.parseInt(values[2]);
                Material material = Material.valueOf(values[3]);
                blocks.add(new BlockOffset(x, y, z, material));
            }
        }

        return blocks;
    }

    public CompletableFuture<List<ResourceNode>> loadNodesInChunk(String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<ResourceNode> nodes = new ArrayList<>();
            String sql = "SELECT * FROM resource_nodes WHERE world = ? AND chunk_x = ? AND chunk_z = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, worldName);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    ResourceNode node = parseNode(rs);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load nodes in chunk: " + e.getMessage());
            }

            return nodes;
        });
    }

    public CompletableFuture<List<ResourceNode>> loadAllNodes(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            List<ResourceNode> nodes = new ArrayList<>();
            String sql = "SELECT * FROM resource_nodes WHERE world = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, worldName);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    ResourceNode node = parseNode(rs);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load all nodes: " + e.getMessage());
            }

            return nodes;
        });
    }

    public CompletableFuture<Void> deleteNode(UUID nodeId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM resource_nodes WHERE id = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, nodeId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete node: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Integer> getNodeCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM resource_nodes";

            try (Connection conn = database.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to count nodes: " + e.getMessage());
            }
            return 0;
        });
    }

    private ResourceNode parseNode(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        UUID id = UUID.fromString(rs.getString("id"));
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        Location baseLocation = new Location(world, x, y, z);

        ResourceType type = ResourceType.valueOf(rs.getString("type"));
        int maxHp = rs.getInt("max_hp");

        // Load template ID and blocks for multi-block support
        String templateId = rs.getString("template_id");
        String blocksJson = rs.getString("blocks_json");
        List<BlockOffset> blocks = deserializeBlocks(blocksJson, type);

        ResourceNode node = new ResourceNode(id, baseLocation, type, maxHp, templateId, blocks);
        node.setCurrentHp(rs.getInt("current_hp"));
        node.setState(NodeState.valueOf(rs.getString("state")));

        return node;
    }
}
