package com.riftalstudios.qtccore.profile;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.rank.Rank;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ProfileStorage {

    private final QTCCore plugin;
    private final DatabaseManager database;

    public ProfileStorage(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_profiles WHERE uuid = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    PlayerProfile profile = new PlayerProfile(uuid);
                    profile.setDiscordId(rs.getString("discord_id"));
                    profile.setRoleIcon(rs.getString("role_icon"));
                    profile.setRepBalance(rs.getLong("rep_balance"));
                    profile.setRepTotal(rs.getLong("rep_total"));
                    profile.setTaskPointBalance(rs.getLong("task_point_balance"));
                    profile.setTaskPointTotal(rs.getLong("task_point_total"));
                    profile.setStaff(rs.getBoolean("is_staff"));
                    profile.setLastActive(rs.getLong("last_active"));
                    profile.setCreatedAt(rs.getLong("created_at"));

                    String rankName = rs.getString("current_rank");
                    try {
                        profile.setCurrentRank(Rank.valueOf(rankName));
                    } catch (IllegalArgumentException e) {
                        profile.setCurrentRank(Rank.fromRepTotal(profile.getRepTotal()));
                    }

                    // Load selected badges from JSON
                    profile.setSelectedBadges(deserializeBadges(rs.getString("badges_json")));
                    profile.setSelectedIdentities(deserializeIdentities(rs.getString("identities_json")));

                    return profile;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load profile for " + uuid + ": " + e.getMessage());
            }

            return null;
        });
    }

    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                MERGE INTO player_profiles
                (uuid, discord_id, role_icon, badges_json, identities_json, rep_balance, rep_total,
                 task_point_balance, task_point_total, current_rank, is_staff, last_active, created_at)
                KEY (uuid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, profile.getUuid().toString());
                stmt.setString(2, profile.getDiscordId());
                stmt.setString(3, profile.getRoleIcon());
                stmt.setString(4, serializeBadges(profile.getSelectedBadges()));
                stmt.setString(5, serializeIdentities(profile.getSelectedIdentities()));
                stmt.setLong(6, profile.getRepBalance());
                stmt.setLong(7, profile.getRepTotal());
                stmt.setLong(8, profile.getTaskPointBalance());
                stmt.setLong(9, profile.getTaskPointTotal());
                stmt.setString(10, profile.getCurrentRank().name());
                stmt.setBoolean(11, profile.isStaff());
                stmt.setLong(12, profile.getLastActive());
                stmt.setLong(13, profile.getCreatedAt());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save profile for " + profile.getUuid() + ": " + e.getMessage());
            }
        });
    }

    // ==================== Badges ====================

    public CompletableFuture<List<Badge>> loadAllBadges(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Badge> badges = new ArrayList<>();
            String sql = "SELECT * FROM player_badges WHERE uuid = ? ORDER BY earned_at ASC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    badges.add(new Badge(
                            rs.getString("badge_id"),
                            rs.getString("badge_name"),
                            rs.getLong("earned_at")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load badges for " + uuid + ": " + e.getMessage());
            }

            return badges;
        });
    }

    public CompletableFuture<Void> awardBadge(UUID uuid, String badgeId, String badgeName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "MERGE INTO player_badges (uuid, badge_id, badge_name, earned_at) KEY (uuid, badge_id) VALUES (?, ?, ?, ?)";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, badgeId);
                stmt.setString(3, badgeName);
                stmt.setLong(4, System.currentTimeMillis());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to award badge: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removeBadge(UUID uuid, String badgeId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_badges WHERE uuid = ? AND badge_id = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, badgeId);

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove badge: " + e.getMessage());
            }
        });
    }

    // ==================== Identities ====================

    public CompletableFuture<List<ServiceIdentity>> loadAllIdentities(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ServiceIdentity> identities = new ArrayList<>();
            String sql = "SELECT * FROM player_identities WHERE uuid = ? ORDER BY earned_at ASC";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    identities.add(new ServiceIdentity(
                            rs.getString("identity_id"),
                            rs.getString("identity_name"),
                            rs.getLong("earned_at")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load identities for " + uuid + ": " + e.getMessage());
            }

            return identities;
        });
    }

    public CompletableFuture<Void> awardIdentity(UUID uuid, String identityId, String identityName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "MERGE INTO player_identities (uuid, identity_id, identity_name, earned_at) KEY (uuid, identity_id) VALUES (?, ?, ?, ?)";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, identityId);
                stmt.setString(3, identityName);
                stmt.setLong(4, System.currentTimeMillis());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to award identity: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removeIdentity(UUID uuid, String identityId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_identities WHERE uuid = ? AND identity_id = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, identityId);

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove identity: " + e.getMessage());
            }
        });
    }

    // ==================== Serialization ====================

    private String serializeBadges(List<Badge> badges) {
        if (badges == null || badges.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < badges.size(); i++) {
            Badge b = badges.get(i);
            if (i > 0) sb.append(";");
            sb.append(b.id()).append(",").append(b.name()).append(",").append(b.earnedAt());
        }
        return sb.toString();
    }

    private List<Badge> deserializeBadges(String json) {
        List<Badge> badges = new ArrayList<>();
        if (json == null || json.isEmpty()) return badges;
        for (String part : json.split(";")) {
            String[] values = part.split(",", 3);
            if (values.length >= 3) {
                badges.add(new Badge(values[0], values[1], Long.parseLong(values[2])));
            }
        }
        return badges;
    }

    private String serializeIdentities(List<ServiceIdentity> identities) {
        if (identities == null || identities.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < identities.size(); i++) {
            ServiceIdentity si = identities.get(i);
            if (i > 0) sb.append(";");
            sb.append(si.id()).append(",").append(si.name()).append(",").append(si.earnedAt());
        }
        return sb.toString();
    }

    private List<ServiceIdentity> deserializeIdentities(String json) {
        List<ServiceIdentity> identities = new ArrayList<>();
        if (json == null || json.isEmpty()) return identities;
        for (String part : json.split(";")) {
            String[] values = part.split(",", 3);
            if (values.length >= 3) {
                identities.add(new ServiceIdentity(values[0], values[1], Long.parseLong(values[2])));
            }
        }
        return identities;
    }
}
