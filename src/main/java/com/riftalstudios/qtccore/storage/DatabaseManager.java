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

            // Player profiles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_profiles (
                    uuid TEXT PRIMARY KEY,
                    discord_id TEXT,
                    role_icon TEXT,
                    badges_json TEXT,
                    identities_json TEXT,
                    rep_balance BIGINT DEFAULT 0,
                    rep_total BIGINT DEFAULT 0,
                    task_point_balance BIGINT DEFAULT 0,
                    task_point_total BIGINT DEFAULT 0,
                    current_rank TEXT DEFAULT 'OUTCAST',
                    is_staff BOOLEAN DEFAULT FALSE,
                    last_active BIGINT DEFAULT 0,
                    created_at BIGINT DEFAULT 0
                )
            """);

            // Reputation ledger
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reputation_ledger (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    reason TEXT NOT NULL,
                    source_type TEXT,
                    source_id TEXT,
                    balance_after BIGINT NOT NULL,
                    total_after BIGINT NOT NULL,
                    created_at BIGINT NOT NULL
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rep_ledger_uuid ON reputation_ledger(uuid)");

            // Task point ledger
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_point_ledger (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    reason TEXT NOT NULL,
                    task_id BIGINT DEFAULT 0,
                    balance_after BIGINT NOT NULL,
                    total_after BIGINT NOT NULL,
                    approved_by TEXT,
                    created_at BIGINT NOT NULL
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tp_ledger_uuid ON task_point_ledger(uuid)");

            // Player badges (earned, not just selected)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_badges (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    badge_id TEXT NOT NULL,
                    badge_name TEXT NOT NULL,
                    earned_at BIGINT NOT NULL,
                    CONSTRAINT uq_player_badge UNIQUE (uuid, badge_id)
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_badges_uuid ON player_badges(uuid)");

            // Player identities (earned, not just selected)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_identities (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    identity_id TEXT NOT NULL,
                    identity_name TEXT NOT NULL,
                    earned_at BIGINT NOT NULL,
                    CONSTRAINT uq_player_identity UNIQUE (uuid, identity_id)
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_identities_uuid ON player_identities(uuid)");

            // Jobs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS jobs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    poster_uuid TEXT NOT NULL,
                    accepter_uuid TEXT,
                    category TEXT NOT NULL,
                    description TEXT NOT NULL,
                    points_offered INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    accepted_at BIGINT DEFAULT 0,
                    completed_at BIGINT DEFAULT 0,
                    verified_at BIGINT DEFAULT 0,
                    verified_by TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_jobs_poster ON jobs(poster_uuid)");

            // Staff tasks
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS staff_tasks (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    creator_uuid TEXT NOT NULL,
                    assignee_uuid TEXT,
                    suggested_points INTEGER DEFAULT 0,
                    approved_points INTEGER DEFAULT 0,
                    status TEXT NOT NULL,
                    approved_by TEXT,
                    created_at BIGINT NOT NULL,
                    assigned_at BIGINT DEFAULT 0,
                    submitted_at BIGINT DEFAULT 0,
                    resolved_at BIGINT DEFAULT 0
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_status ON staff_tasks(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON staff_tasks(assignee_uuid)");

            plugin.getLogger().info("Database tables created");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
