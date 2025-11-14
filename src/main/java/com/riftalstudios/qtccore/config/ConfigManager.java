package com.riftalstudios.qtccore.config;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final QTCCore plugin;
    private FileConfiguration config;

    public ConfigManager(QTCCore plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
}
