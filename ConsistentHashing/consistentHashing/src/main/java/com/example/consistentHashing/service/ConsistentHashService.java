package com.example.consistentHashing.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

@Service
public class ConsistentHashService {

    private final SortedMap<Long, String> ring = new TreeMap<>();

    private static final int VIRTUAL_NODES = 3;

    public ConsistentHashService() {

        addNode("mongo1");
        addNode("mongo2");
        addNode("mongo3");

    }

    public void addNode(String node) {

        for (int i = 0; i < VIRTUAL_NODES; i++) {

            ring.put(hash(node + "#" + i), node);

        }

    }

    public void removeNode(String node) {

        for (int i = 0; i < VIRTUAL_NODES; i++) {

            ring.remove(hash(node + "#" + i));

        }

    }

    public String getNode(String key) {

        long hash = hash(key);

        if (ring.containsKey(hash))
            return ring.get(hash);

        SortedMap<Long, String> tailMap = ring.tailMap(hash);

        if (tailMap.isEmpty())
            return ring.get(ring.firstKey());

        return tailMap.get(tailMap.firstKey());

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