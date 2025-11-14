package com.riftalstudios.qtccore.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.riftalstudios.qtccore.QTCCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    private ServerMock server;
    private QTCCore plugin;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(QTCCore.class);
        configManager = new ConfigManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testConfigManagerInitialization() {
        assertNotNull(configManager);
    }

    @Test
    void testConfigFileCreated() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        assertTrue(configFile.exists());
    }

    @Test
    void testGetString() {
        String language = configManager.getString("plugin.language");
        assertEquals("en_US", language);
    }

    @Test
    void testGetBoolean() {
        boolean debug = configManager.getBoolean("plugin.debug");
        assertFalse(debug);
    }

    @Test
    void testGetInt() {
        int port = configManager.getInt("storage.mysql.port");
        assertEquals(3306, port);
    }

    @Test
    void testNestedConfigAccess() {
        boolean resourceNodes = configManager.getBoolean("features.resource-nodes");
        assertTrue(resourceNodes);

        String storageType = configManager.getString("storage.type");
        assertEquals("SQLITE", storageType);
    }

    @Test
    void testReload() {
        boolean before = configManager.getBoolean("features.resource-nodes");
        assertTrue(before);

        configManager.reload();

        boolean after = configManager.getBoolean("features.resource-nodes");
        assertTrue(after);
    }
}
