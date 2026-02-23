package com.riftalstudios.qtccore.jobs;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JobStorage {

    private final QTCCore plugin;
    private final DatabaseManager database;

    public JobStorage(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Long> insertJob(Job job) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO jobs
                (poster_uuid, accepter_uuid, category, description, points_offered, status,
                 created_at, accepted_at, completed_at, verified_at, verified_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, job.getPosterUuid().toString());
                stmt.setString(2, job.getAccepterUuid() != null ? job.getAccepterUuid().toString() : null);
                stmt.setString(3, job.getCategory().name());
                stmt.setString(4, job.getDescription());
                stmt.setInt(5, job.getPointsOffered());
                stmt.setString(6, job.getStatus().name());
                stmt.setLong(7, job.getCreatedAt());
                stmt.setLong(8, job.getAcceptedAt());
                stmt.setLong(9, job.getCompletedAt());
                stmt.setLong(10, job.getVerifiedAt());
                stmt.setString(11, job.getVerifiedBy() != null ? job.getVerifiedBy().toString() : null);

                stmt.executeUpdate();

                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    long id = keys.getLong(1);
                    job.setId(id);
                    return id;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert job: " + e.getMessage());
            }
            return -1L;
        });
    }

    public CompletableFuture<Void> updateJob(Job job) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE jobs SET
                    accepter_uuid = ?, status = ?, accepted_at = ?,
                    completed_at = ?, verified_at = ?, verified_by = ?
                WHERE id = ?
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, job.getAccepterUuid() != null ? job.getAccepterUuid().toString() : null);
                stmt.setString(2, job.getStatus().name());
                stmt.setLong(3, job.getAcceptedAt());
                stmt.setLong(4, job.getCompletedAt());
                stmt.setLong(5, job.getVerifiedAt());
                stmt.setString(6, job.getVerifiedBy() != null ? job.getVerifiedBy().toString() : null);
                stmt.setLong(7, job.getId());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update job: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Job> getJob(long id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM jobs WHERE id = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, id);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return parseJob(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load job: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<List<Job>> getJobsByStatus(JobStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            List<Job> jobs = new ArrayList<>();
            String sql = "SELECT * FROM jobs WHERE status = ? ORDER BY created_at DESC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status.name());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    jobs.add(parseJob(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load jobs by status: " + e.getMessage());
            }
            return jobs;
        });
    }

    public CompletableFuture<List<Job>> getJobsByCategory(JobCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            List<Job> jobs = new ArrayList<>();
            String sql = "SELECT * FROM jobs WHERE category = ? AND status = 'OPEN' ORDER BY created_at DESC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, category.name());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    jobs.add(parseJob(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load jobs by category: " + e.getMessage());
            }
            return jobs;
        });
    }

    public CompletableFuture<List<Job>> getOpenJobs() {
        return getJobsByStatus(JobStatus.OPEN);
    }

    private Job parseJob(ResultSet rs) throws SQLException {
        String accepterStr = rs.getString("accepter_uuid");
        String verifiedByStr = rs.getString("verified_by");

        return new Job(
                rs.getLong("id"),
                UUID.fromString(rs.getString("poster_uuid")),
                accepterStr != null ? UUID.fromString(accepterStr) : null,
                JobCategory.valueOf(rs.getString("category")),
                rs.getString("description"),
                rs.getInt("points_offered"),
                JobStatus.valueOf(rs.getString("status")),
                rs.getLong("created_at"),
                rs.getLong("accepted_at"),
                rs.getLong("completed_at"),
                rs.getLong("verified_at"),
                verifiedByStr != null ? UUID.fromString(verifiedByStr) : null
        );
    }
}
