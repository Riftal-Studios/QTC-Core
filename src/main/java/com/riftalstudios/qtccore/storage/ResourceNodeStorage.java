package com.riftalstudios.qtccore.storage;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.resources.NodeState;
import com.riftalstudios.qtccore.resources.ResourceNode;
import com.riftalstudios.qtccore.resources.ResourceType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
            String sql = """
                INSERT OR REPLACE INTO resource_nodes
                (id, world, x, y, z, type, max_hp, current_hp, state, depleted_at, chunk_x, chunk_z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, node.getId().toString());
                stmt.setString(2, node.getWorldName());
                stmt.setInt(3, node.getLocation().getBlockX());
                stmt.setInt(4, node.getLocation().getBlockY());
                stmt.setInt(5, node.getLocation().getBlockZ());
                stmt.setString(6, node.getType().name());
                stmt.setInt(7, node.getMaxHp());
                stmt.setInt(8, node.getCurrentHp());
                stmt.setString(9, node.getState().name());
                stmt.setLong(10, node.getDepletedAt());
                stmt.setInt(11, node.getChunkX());
                stmt.setInt(12, node.getChunkZ());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save node: " + e.getMessage());
            }
        });
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
        Location location = new Location(world, x, y, z);

        ResourceType type = ResourceType.valueOf(rs.getString("type"));
        int maxHp = rs.getInt("max_hp");

        ResourceNode node = new ResourceNode(id, location, type, maxHp);
        node.setCurrentHp(rs.getInt("current_hp"));
        node.setState(NodeState.valueOf(rs.getString("state")));

        return node;
    }
}
