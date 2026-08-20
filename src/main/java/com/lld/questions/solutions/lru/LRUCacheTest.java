package com.lld.questions.solutions.lru;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test client to demonstrate LRU Cache functionality
 */
public class LRUCacheTest {
    public static void main(String[] args) {
        System.out.println("=== Testing LRU Cache ===");

        // Test basic LRU cache
        LRUCache<Integer, String> cache = CacheFactory.createCache(
                CacheFactory.CacheType.LRU, 3);

        // Test put operations
        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");
        cache.printCache(); // Output: 3=Three 2=Two 1=One

        // Test get operations
        System.out.println("Get 1: " + cache.get(1)); // Moves 1 to front
        cache.printCache(); // Output: 1=One 3=Three 2=Two

        // Test eviction
        cache.put(4, "Four"); // Should evict 2 (LRU)
        cache.printCache(); // Output: 4=Four 1=One 3=Three

        System.out.println("\n=== Testing Thread-Safe LRU Cache ===");

        // Test thread-safe cache
        LRUCache<String, Integer> threadSafeCache = CacheFactory.createCache(
                CacheFactory.CacheType.THREAD_SAFE_LRU, 2);

        // Simulate concurrent access
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                threadSafeCache.put("Key" + threadId, threadId);
                System.out.println("Thread " + threadId + " added key");
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        threadSafeCache.printCache();
    }
}