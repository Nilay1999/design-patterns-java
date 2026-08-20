package com.lld.questions.solutions.lru;

/**
 * Factory for creating different types of caches
 */
public class CacheFactory {
    public enum CacheType {
        LRU,
        THREAD_SAFE_LRU
    }

    public static <K, V> LRUCache<K, V> createCache(CacheType type, int capacity) {
        switch (type) {
            case LRU:
                return new LRUCacheImpl<>(capacity);
            case THREAD_SAFE_LRU:
                return new ThreadSafeLRUCache<>(capacity);
            default:
                throw new IllegalArgumentException("Unsupported cache type: " + type);
        }
    }
}
