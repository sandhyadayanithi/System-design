package com.example.consistentHashing.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConsistentHashService {

    private final SortedMap<Long, String> ring = new TreeMap<>();
    private final Set<String> activeNodes = new LinkedHashSet<>();
    private final Map<String, String> nodeAddresses = new ConcurrentHashMap<>();

    private static final int VIRTUAL_NODES = 3;

    public ConsistentHashService() {
        // Register default node addresses
        nodeAddresses.put("mongo1", "localhost:27017");
        nodeAddresses.put("mongo2", "localhost:27018");
        nodeAddresses.put("mongo3", "localhost:27019");
        nodeAddresses.put("mongo4", "localhost:27020");

        // Initial 3-node cluster
        addNode("mongo1");
        addNode("mongo2");
        addNode("mongo3");
    }

    public synchronized void addNode(String node) {
        String normalized = normalizeNodeName(node);
        if (activeNodes.contains(normalized)) {
            throw new IllegalStateException("Node '" + normalized + "' is already present in the hash ring.");
        }

        activeNodes.add(normalized);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.put(hash(normalized + "#" + i), normalized);
        }
    }

    public synchronized void removeNode(String node) {
        String normalized = normalizeNodeName(node);
        if (!activeNodes.contains(normalized)) {
            throw new IllegalStateException("Node '" + normalized + "' is not present in the hash ring.");
        }
        if (activeNodes.size() <= 1) {
            throw new IllegalStateException("Cannot remove node '" + normalized + "'. At least one node must remain in the hash ring.");
        }

        activeNodes.remove(normalized);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.remove(hash(normalized + "#" + i));
        }
    }

    public synchronized String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty. No nodes available.");
        }

        long hash = hash(key);

        if (ring.containsKey(hash))
            return ring.get(hash);

        SortedMap<Long, String> tailMap = ring.tailMap(hash);

        if (tailMap.isEmpty())
            return ring.get(ring.firstKey());

        return tailMap.get(tailMap.firstKey());
    }

    public synchronized Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(activeNodes));
    }

    public synchronized boolean hasNode(String node) {
        return activeNodes.contains(normalizeNodeName(node));
    }

    public int getVirtualNodesCount() {
        return VIRTUAL_NODES;
    }

    public String normalizeNodeName(String node) {
        if (node == null) return "";
        String trimmed = node.trim();
        if (trimmed.startsWith("ch-")) {
            return trimmed.substring(3);
        }
        return trimmed;
    }

    public String getNodeAddress(String node) {
        String normalized = normalizeNodeName(node);
        return nodeAddresses.getOrDefault(normalized, "unknown:0");
    }

    public void setNodeAddress(String node, String address) {
        nodeAddresses.put(normalizeNodeName(node), address);
    }

    public Map<String, String> getAllNodeAddresses() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodeAddresses));
    }

    public synchronized SortedMap<Long, String> getRingCopy() {
        return new TreeMap<>(ring);
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;

            for (int i = 0; i < 4; i++) {
                hash <<= 8;
                hash |= (digest[i] & 0xFF);
            }

            return hash & 0xffffffffL;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void printRing() {
        System.out.println("------------- HASH RING -------------");
        ring.forEach((k, v) ->
                System.out.println(k + " -> " + v)
        );
    }
}