package com.riftalstudios.qtccore;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManagerTest {

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
    void testPluginStartsWithNoManagers() {
        assertEquals(0, plugin.getManagerCount());
    }

    @Test
    void testRegisterManager() {
        TestManager testManager = new TestManager();
        plugin.registerManager(testManager);

        assertEquals(1, plugin.getManagerCount());
        assertTrue(plugin.getManagers().contains(testManager));
    }

    @Test
    void testRegisterMultipleManagers() {
        TestManager manager1 = new TestManager("Manager1");
        TestManager manager2 = new TestManager("Manager2");
        TestManager manager3 = new TestManager("Manager3");

        plugin.registerManager(manager1);
        plugin.registerManager(manager2);
        plugin.registerManager(manager3);

        assertEquals(3, plugin.getManagerCount());
    }

    @Test
    void testRegisterNullManager() {
        plugin.registerManager(null);
        assertEquals(0, plugin.getManagerCount());
    }


    private static class TestManager implements Manager {
        private final String name;

        public TestManager() {
            this("TestManager");
        }

        public TestManager(String name) {
            this.name = name;
        }

        @Override
        public void enable() {
        }

        @Override
        public void disable() {
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
