package com.riftalstudios.qtccore.storage;

import com.riftalstudios.qtccore.QTCCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final QTCCore plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(QTCCore plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        File dbFile = new File(plugin.getDataFolder(), "qtccore");

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connection established");

        createTables();
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed");
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS resource_nodes (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    max_hp INTEGER NOT NULL,
                    current_hp INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    depleted_at INTEGER DEFAULT 0,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    template_id TEXT,
                    blocks_json TEXT
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nodes_chunk ON resource_nodes(world, chunk_x, chunk_z)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nodes_world ON resource_nodes(world)");

            // Add columns if they don't exist (migration for existing databases)
            try {
                stmt.execute("ALTER TABLE resource_nodes ADD COLUMN IF NOT EXISTS template_id TEXT");
                stmt.execute("ALTER TABLE resource_nodes ADD COLUMN IF NOT EXISTS blocks_json TEXT");
            } catch (SQLException ignored) {
                // Columns already exist
            }

            plugin.getLogger().info("Database tables created");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
