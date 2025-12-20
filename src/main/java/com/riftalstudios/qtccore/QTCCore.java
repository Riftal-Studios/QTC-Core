package com.riftalstudios.qtccore;

import com.riftalstudios.qtccore.commands.QTCCommand;
import com.riftalstudios.qtccore.config.ConfigManager;
import com.riftalstudios.qtccore.config.MessageConfig;
import com.riftalstudios.qtccore.resources.ResourceNodeManager;
import com.riftalstudios.qtccore.resources.template.NodeTemplateManager;
import com.riftalstudios.qtccore.storage.DatabaseManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class QTCCore extends JavaPlugin {

    private static QTCCore instance;
    private final List<Manager> managers = new ArrayList<>();
    private ConfigManager configManager;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;
    private ResourceNodeManager resourceNodeManager;
    private NodeTemplateManager nodeTemplateManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("QTC Core is starting up...");
        getLogger().info("Version: " + getDescription().getVersion());

        configManager = new ConfigManager(this);
        messageConfig = new MessageConfig(this);
        getLogger().info("Configuration loaded successfully");

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        resourceNodeManager = new ResourceNodeManager(this, databaseManager);
        registerManager(resourceNodeManager);

        nodeTemplateManager = new NodeTemplateManager(this);
        registerManager(nodeTemplateManager);

        registerCommands();

        enableManagers();

        resourceNodeManager.initializeDisplay();
        getLogger().info("Node display manager initialized");

        getLogger().info("QTC Core has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("QTC Core is shutting down...");

        disableManagers();

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

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

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ResourceNodeManager getResourceNodeManager() {
        return resourceNodeManager;
    }

    public NodeTemplateManager getNodeTemplateManager() {
        return nodeTemplateManager;
    }

    private void registerCommands() {
        QTCCommand qtcCommand = new QTCCommand(this);
        PluginCommand command = getCommand("qtc");
        if (command != null) {
            command.setExecutor(qtcCommand);
            command.setTabCompleter(qtcCommand);
            getLogger().info("Registered command: /qtc");
        }
    }
}
