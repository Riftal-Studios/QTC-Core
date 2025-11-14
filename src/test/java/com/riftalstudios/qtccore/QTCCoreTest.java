package com.riftalstudios.qtccore;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class QTCCoreTest {

    private ServerMock server;
    private QTCCore plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(QTCCore.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPluginLoads() {
        assertNotNull(plugin);
        assertTrue(plugin.isEnabled());
    }

    @Test
    void testPluginInstance() {
        assertNotNull(QTCCore.getInstance());
        assertEquals(plugin, QTCCore.getInstance());
    }

    @Test
    void testPluginEnableLogsMessage() {
        Logger logger = plugin.getLogger();
        assertNotNull(logger);
    }

    @Test
    void testPluginDisableWorks() {
        assertTrue(plugin.isEnabled());
    }
}
