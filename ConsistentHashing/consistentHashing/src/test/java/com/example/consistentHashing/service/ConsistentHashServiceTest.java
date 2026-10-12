package com.example.consistentHashing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashServiceTest {

    private ConsistentHashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new ConsistentHashService();
    }

    @Test
    void testInitialRingState() {
        Set<String> activeNodes = hashService.getActiveNodes();
        assertEquals(3, activeNodes.size());
        assertTrue(activeNodes.contains("mongo1"));
        assertTrue(activeNodes.contains("mongo2"));
        assertTrue(activeNodes.contains("mongo3"));

        // Each node has 3 virtual nodes -> total 9 ring entries
        Map<Long, String> ring = hashService.getRingCopy();
        assertEquals(9, ring.size());
    }

    @Test
    void testNodeAssignmentIsDeterministic() {
        String node1 = hashService.getNode("101");
        String node2 = hashService.getNode("101");
        assertEquals(node1, node2);
        assertTrue(hashService.getActiveNodes().contains(node1));
    }

    @Test
    void testAddNodeExpandsRing() {
        int initialSize = hashService.getRingCopy().size();
        hashService.addNode("mongo4");

        assertTrue(hashService.hasNode("mongo4"));
        assertEquals(4, hashService.getActiveNodes().size());
        assertEquals(initialSize + 3, hashService.getRingCopy().size());
    }

    @Test
    void testAddDuplicateNodeThrowsException() {
        assertThrows(IllegalStateException.class, () -> hashService.addNode("mongo1"));
        assertThrows(IllegalStateException.class, () -> hashService.addNode("ch-mongo1"));
    }

    @Test
    void testRemoveNodeShrinksRing() {
        int initialSize = hashService.getRingCopy().size();
        hashService.removeNode("mongo3");

        assertFalse(hashService.hasNode("mongo3"));
        assertEquals(2, hashService.getActiveNodes().size());
        assertEquals(initialSize - 3, hashService.getRingCopy().size());
    }

    @Test
    void testRemoveNonExistentNodeThrowsException() {
        assertThrows(IllegalStateException.class, () -> hashService.removeNode("nonExistentNode"));
    }

    @Test
    void testCannotRemoveAllNodes() {
        hashService.removeNode("mongo1");
        hashService.removeNode("mongo2");
        assertEquals(1, hashService.getActiveNodes().size());

        // Removing the last node should fail
        assertThrows(IllegalStateException.class, () -> hashService.removeNode("mongo3"));
    }

    @Test
    void testNodeAddressLookup() {
        assertEquals("localhost:27017", hashService.getNodeAddress("mongo1"));
        assertEquals("localhost:27018", hashService.getNodeAddress("ch-mongo2"));
        assertEquals("localhost:27019", hashService.getNodeAddress("mongo3"));
        assertEquals("localhost:27020", hashService.getNodeAddress("ch-mongo4"));
    }
}
