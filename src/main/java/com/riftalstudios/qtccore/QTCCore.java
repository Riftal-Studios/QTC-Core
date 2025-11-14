package com.riftalstudios.qtccore;

import com.riftalstudios.qtccore.config.ConfigManager;
import com.riftalstudios.qtccore.config.MessageConfig;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class QTCCore extends JavaPlugin {

    private static QTCCore instance;
    private final List<Manager> managers = new ArrayList<>();
    private ConfigManager configManager;
    private MessageConfig messageConfig;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("QTC Core is starting up...");
        getLogger().info("Version: " + getDescription().getVersion());

        configManager = new ConfigManager(this);
        messageConfig = new MessageConfig(this);
        getLogger().info("Configuration loaded successfully");

        enableManagers();

        getLogger().info("QTC Core has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("QTC Core is shutting down...");

        disableManagers();

        getLogger().info("QTC Core has been disabled successfully!");
    }

    public static QTCCore getInstance() {
        return instance;
    }

    public void registerManager(Manager manager) {
        if (manager == null) {
            getLogger().warning("Attempted to register null manager");
            return;
        }
        managers.add(manager);
        getLogger().info("Registered manager: " + manager.getName());
    }

    private void enableManagers() {
        getLogger().info("Enabling " + managers.size() + " manager(s)...");
        for (Manager manager : managers) {
            try {
                manager.enable();
                getLogger().info("Enabled manager: " + manager.getName());
            } catch (Exception e) {
                getLogger().severe("Failed to enable manager: " + manager.getName());
                e.printStackTrace();
            }
        }
    }

    private void disableManagers() {
        getLogger().info("Disabling " + managers.size() + " manager(s)...");
        for (int i = managers.size() - 1; i >= 0; i--) {
            Manager manager = managers.get(i);
            try {
                manager.disable();
                getLogger().info("Disabled manager: " + manager.getName());
            } catch (Exception e) {
                getLogger().severe("Failed to disable manager: " + manager.getName());
                e.printStackTrace();
            }
        }
    }

    public List<Manager> getManagers() {
        return new ArrayList<>(managers);
    }

    public int getManagerCount() {
        return managers.size();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageConfig getMessageConfig() {
        return messageConfig;
    }
}
