package com.lld.questions.solutions.lru;

public interface LRUCache<K, V> {
    V get(K key);

    void put(K key, V value);

    void delete(K key);

    void clear();

    int size();

    int capacity();

    boolean containsKey(K key);

    void printCache();
}
