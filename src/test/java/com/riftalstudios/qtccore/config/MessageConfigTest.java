package com.riftalstudios.qtccore.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageConfigTest {

    private ServerMock server;
    private QTCCore plugin;
    private MessageConfig messageConfig;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(QTCCore.class);
        messageConfig = new MessageConfig(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testMessageConfigInitialization() {
        assertNotNull(messageConfig);
    }

    @Test
    void testGetMessage() {
        String message = messageConfig.getMessage("plugin.enabled");
        assertNotNull(message);
        assertTrue(message.contains("QTC Core"));
    }

    @Test
    void testLegacyColorCodes() {
        String message = messageConfig.getMessage("test.colors");
        assertTrue(message.contains(ChatColor.RED.toString()));
    }

    @Test
    void testPlaceholderReplacement() {
        String message = messageConfig.getMessage("test.placeholder", Map.of("player", "Steve"));
        assertTrue(message.contains("Steve"));
    }
}
