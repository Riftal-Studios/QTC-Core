package com.riftalstudios.qtccore.api;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.listeners.DiscordIntegrationListener;
import org.bukkit.Bukkit;

/**
 * Manager for the Discord bot API integration.
 * Reads config, creates the HTTP client, registers event listeners.
 * Implements the Manager interface for lifecycle management by QTCCore.
 */
public class DiscordAPIManager implements Manager {

    private final QTCCore plugin;
    private DiscordAPIClient client;

    public DiscordAPIManager(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        // Check if integration is enabled in config
        if (!plugin.getConfig().getBoolean("discord-api.enabled", true)) {
            plugin.getLogger().info("Discord API integration is disabled in config");
            return;
        }

        // Read config values
        String url = plugin.getConfig().getString("discord-api.url", "http://localhost:8080");
        String key = plugin.getConfig().getString("discord-api.key", "");
        int timeout = plugin.getConfig().getInt("discord-api.timeout", 5000);

        // Validate required config
        if (key.isEmpty()) {
            plugin.getLogger().warning("Discord API key not set in config.yml — integration disabled. "
                    + "Set 'discord-api.key' to the same value as API_KEY in the bot's .env file.");
            return;
        }

        // Create HTTP client
        client = new DiscordAPIClient(url, key, timeout, plugin.getLogger());
        plugin.getLogger().info("Discord API client created for " + url);

        // Register event listener
        DiscordIntegrationListener listener = new DiscordIntegrationListener(plugin, client);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        plugin.getLogger().info("Discord integration listener registered");

        // Optional: health check on startup
        client.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                plugin.getLogger().info("Discord API health check passed");
            } else {
                plugin.getLogger().warning("Discord API health check failed — "
                        + "is the bot running? Check discord-api.url in config.yml");
            }
        });
    }

    @Override
    public void disable() {
        if (client != null) {
            client.shutdown();
            client = null;
            plugin.getLogger().info("Discord API client shut down");
        }
    }

    @Override
    public String getName() {
        return "DiscordAPIManager";
    }

    /**
     * Get the API client instance (may be null if disabled or not configured).
     */
    public DiscordAPIClient getClient() {
        return client;
    }
}
