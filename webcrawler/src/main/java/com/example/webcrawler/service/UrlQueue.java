package com.example.webcrawler.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class UrlQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public void add(String url) {
        queue.offer(url);
    }

    public String getNext() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}