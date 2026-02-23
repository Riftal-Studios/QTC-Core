package com.riftalstudios.qtccore.tasks;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StaffTaskStorage {

    private final QTCCore plugin;
    private final DatabaseManager database;

    public StaffTaskStorage(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Long> insertTask(StaffTask task) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO staff_tasks
                (title, description, creator_uuid, assignee_uuid, suggested_points, approved_points,
                 status, approved_by, created_at, assigned_at, submitted_at, resolved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, task.getTitle());
                stmt.setString(2, task.getDescription());
                stmt.setString(3, task.getCreatorUuid().toString());
                stmt.setString(4, task.getAssigneeUuid() != null ? task.getAssigneeUuid().toString() : null);
                stmt.setInt(5, task.getSuggestedPoints());
                stmt.setInt(6, task.getApprovedPoints());
                stmt.setString(7, task.getStatus().name());
                stmt.setString(8, task.getApprovedBy() != null ? task.getApprovedBy().toString() : null);
                stmt.setLong(9, task.getCreatedAt());
                stmt.setLong(10, task.getAssignedAt());
                stmt.setLong(11, task.getSubmittedAt());
                stmt.setLong(12, task.getResolvedAt());

                stmt.executeUpdate();

                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    long id = keys.getLong(1);
                    task.setId(id);
                    return id;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert staff task: " + e.getMessage());
            }
            return -1L;
        });
    }

    public CompletableFuture<Void> updateTask(StaffTask task) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE staff_tasks SET
                    assignee_uuid = ?, suggested_points = ?, approved_points = ?,
                    status = ?, approved_by = ?, assigned_at = ?, submitted_at = ?, resolved_at = ?
                WHERE id = ?
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, task.getAssigneeUuid() != null ? task.getAssigneeUuid().toString() : null);
                stmt.setInt(2, task.getSuggestedPoints());
                stmt.setInt(3, task.getApprovedPoints());
                stmt.setString(4, task.getStatus().name());
                stmt.setString(5, task.getApprovedBy() != null ? task.getApprovedBy().toString() : null);
                stmt.setLong(6, task.getAssignedAt());
                stmt.setLong(7, task.getSubmittedAt());
                stmt.setLong(8, task.getResolvedAt());
                stmt.setLong(9, task.getId());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update staff task: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<StaffTask> getTask(long id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM staff_tasks WHERE id = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, id);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return parseTask(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load staff task: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<List<StaffTask>> getTasksByStatus(TaskStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            List<StaffTask> tasks = new ArrayList<>();
            String sql = "SELECT * FROM staff_tasks WHERE status = ? ORDER BY created_at DESC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status.name());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    tasks.add(parseTask(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load staff tasks by status: " + e.getMessage());
            }
            return tasks;
        });
    }

    public CompletableFuture<List<StaffTask>> getTasksByAssignee(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<StaffTask> tasks = new ArrayList<>();
            String sql = "SELECT * FROM staff_tasks WHERE assignee_uuid = ? ORDER BY created_at DESC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    tasks.add(parseTask(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load staff tasks by assignee: " + e.getMessage());
            }
            return tasks;
        });
    }

    public CompletableFuture<Void> insertTaskPointTransaction(TaskPointTransaction txn) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO task_point_ledger
                (uuid, amount, reason, task_id, balance_after, total_after, approved_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, txn.uuid().toString());
                stmt.setLong(2, txn.amount());
                stmt.setString(3, txn.reason());
                stmt.setLong(4, txn.taskId());
                stmt.setLong(5, txn.balanceAfter());
                stmt.setLong(6, txn.totalAfter());
                stmt.setString(7, txn.approvedBy() != null ? txn.approvedBy().toString() : null);
                stmt.setLong(8, txn.createdAt());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert task point transaction: " + e.getMessage());
            }
        });
    }

    private StaffTask parseTask(ResultSet rs) throws SQLException {
        String assigneeStr = rs.getString("assignee_uuid");
        String approvedByStr = rs.getString("approved_by");

        return new StaffTask(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                UUID.fromString(rs.getString("creator_uuid")),
                assigneeStr != null ? UUID.fromString(assigneeStr) : null,
                rs.getInt("suggested_points"),
                rs.getInt("approved_points"),
                TaskStatus.valueOf(rs.getString("status")),
                approvedByStr != null ? UUID.fromString(approvedByStr) : null,
                rs.getLong("created_at"),
                rs.getLong("assigned_at"),
                rs.getLong("submitted_at"),
                rs.getLong("resolved_at")
        );
    }
}
