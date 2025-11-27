package com.riftalstudios.qtccore.resources;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceNodeTest {

    private ServerMock server;
    private WorldMock world;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
        testLocation = new Location(world, 100, 64, 200);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testNodeCreation() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);

        assertNotNull(node.getId());
        assertEquals(testLocation, node.getLocation());
        assertEquals(ResourceType.OAK_TREE, node.getType());
        assertEquals(100, node.getMaxHp());
        assertEquals(100, node.getCurrentHp());
        assertEquals(NodeState.ACTIVE, node.getState());
    }

    @Test
    void testNodeTakesDamage() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);

        node.damage(30);

        assertEquals(70, node.getCurrentHp());
        assertEquals(NodeState.ACTIVE, node.getState());
    }

    @Test
    void testNodeDepletesAtZeroHp() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);

        node.damage(100);

        assertEquals(0, node.getCurrentHp());
        assertEquals(NodeState.DEPLETED, node.getState());
        assertTrue(node.isDepleted());
        assertTrue(node.getDepletedAt() > 0);
    }

    @Test
    void testNodeRegenerates() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);
        node.damage(100);

        node.regenerate();

        assertEquals(100, node.getCurrentHp());
        assertEquals(NodeState.ACTIVE, node.getState());
        assertFalse(node.isDepleted());
    }

    @Test
    void testDamageIgnoredWhenDepleted() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);
        node.deplete();

        node.damage(50);

        assertEquals(0, node.getCurrentHp());
    }

    @Test
    void testChunkCoordinates() {
        ResourceNode node = new ResourceNode(testLocation, ResourceType.OAK_TREE);

        assertEquals(6, node.getChunkX());
        assertEquals(12, node.getChunkZ());
    }

    @Test
    void testDifferentResourceTypes() {
        ResourceNode ironNode = new ResourceNode(testLocation, ResourceType.IRON_ORE);
        ResourceNode diamondNode = new ResourceNode(testLocation, ResourceType.DIAMOND_ORE);

        assertEquals(200, ironNode.getMaxHp());
        assertEquals(400, diamondNode.getMaxHp());
    }
}
