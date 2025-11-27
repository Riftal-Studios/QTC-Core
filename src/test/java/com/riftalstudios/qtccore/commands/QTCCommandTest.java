package com.riftalstudios.qtccore.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QTCCommandTest {

    private ServerMock server;
    private QTCCore plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(QTCCore.class);
        player = server.addPlayer();
        player.setOp(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testCommandRegistered() {
        PluginCommand command = plugin.getCommand("qtc");
        assertNotNull(command, "Command /qtc should be registered");
        assertEquals("qtc", command.getName());
    }

    @Test
    void testNoArgsShowsHelp() {
        player.performCommand("qtc");
        String lastMessage = player.nextMessage();
        assertNotNull(lastMessage, "Player should receive a message");
        assertTrue(lastMessage.contains("QTC") || lastMessage.contains("help") || lastMessage.contains("Usage"),
                "Should show help or usage info");
    }

    @Test
    void testUnknownSubcommandShowsError() {
        player.performCommand("qtc unknowncommand");
        String lastMessage = player.nextMessage();
        assertNotNull(lastMessage, "Player should receive error message");
    }

    @Test
    void testReloadSubcommand() {
        player.performCommand("qtc reload");
        String lastMessage = player.nextMessage();
        assertNotNull(lastMessage, "Player should receive reload confirmation");
    }

    @Test
    void testTabCompletionReturnsSubcommands() {
        PluginCommand command = plugin.getCommand("qtc");
        assertNotNull(command);

        List<String> completions = command.tabComplete(player, "qtc", new String[]{""});
        assertNotNull(completions);
        assertTrue(completions.contains("reload"), "Tab completion should include 'reload'");
    }

    @Test
    void testTabCompletionFiltersPartialInput() {
        PluginCommand command = plugin.getCommand("qtc");
        assertNotNull(command);

        List<String> completions = command.tabComplete(player, "qtc", new String[]{"re"});
        assertNotNull(completions);
        assertTrue(completions.contains("reload"), "Tab completion should include 'reload' for 're' input");
    }
}
