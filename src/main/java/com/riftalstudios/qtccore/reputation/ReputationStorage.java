package com.riftalstudios.qtccore.reputation;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReputationStorage {

    private final QTCCore plugin;
    private final DatabaseManager database;

    public ReputationStorage(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Void> insertTransaction(ReputationTransaction txn) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO reputation_ledger
                (uuid, amount, reason, source_type, source_id, balance_after, total_after, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, txn.uuid().toString());
                stmt.setLong(2, txn.amount());
                stmt.setString(3, txn.reason());
                stmt.setString(4, txn.sourceType());
                stmt.setString(5, txn.sourceId());
                stmt.setLong(6, txn.balanceAfter());
                stmt.setLong(7, txn.totalAfter());
                stmt.setLong(8, txn.createdAt());

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert reputation transaction: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<ReputationTransaction>> getHistory(UUID uuid, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            List<ReputationTransaction> transactions = new ArrayList<>();
            String sql = "SELECT * FROM reputation_ledger WHERE uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setInt(2, pageSize);
                stmt.setInt(3, page * pageSize);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    transactions.add(new ReputationTransaction(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getLong("amount"),
                            rs.getString("reason"),
                            rs.getString("source_type"),
                            rs.getString("source_id"),
                            rs.getLong("balance_after"),
                            rs.getLong("total_after"),
                            rs.getLong("created_at")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load reputation history: " + e.getMessage());
            }

            return transactions;
        });
    }

    public CompletableFuture<Integer> getTransactionCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reputation_ledger WHERE uuid = ?";

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to count reputation transactions: " + e.getMessage());
            }
            return 0;
        });
    }
}
