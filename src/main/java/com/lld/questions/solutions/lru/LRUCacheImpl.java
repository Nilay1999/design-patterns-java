package com.lld.questions.solutions.lru;

import java.util.HashMap;
import java.util.Map;

/**
 * LRU Cache Implementation using HashMap + Doubly Linked List
 * Time Complexity: O(1) for get and put
 * Space Complexity: O(capacity)
 */
public class LRUCacheImpl<K, V> implements LRUCache<K, V> {
    private final int capacity;
    private final Map<K, DLinkedListNode<K, V>> cacheMap;
    private final DLinkedList<K, V> lruList;
    private int hitCount = 0;
    private int missCount = 0;

    public LRUCacheImpl(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.cacheMap = new HashMap<>(capacity);
        this.lruList = new DLinkedList<>();
    }

    @Override
    public V get(K key) {
        DLinkedListNode<K, V> node = cacheMap.get(key);

        if (node == null) {
            missCount++;
            return null;
        }

        // Update node as most recently used
        lruList.moveToFront(node);
        node.timestamp = System.currentTimeMillis();
        hitCount++;

        return node.value;
    }

    @Override
    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // If key already exists, update value and move to front
        if (cacheMap.containsKey(key)) {
            DLinkedListNode<K, V> node = cacheMap.get(key);
            node.value = value;
            node.timestamp = System.currentTimeMillis();
            lruList.moveToFront(node);
            return;
        }

        // If cache is full, evict LRU item
        if (cacheMap.size() >= capacity) {
            evict();
        }

        // Create new node and add to cache
        DLinkedListNode<K, V> newNode = new DLinkedListNode<>(key, value);
        cacheMap.put(key, newNode);
        lruList.addToFront(newNode);
    }

    @Override
    public void delete(K key) {
        DLinkedListNode<K, V> node = cacheMap.remove(key);
        if (node != null) {
            lruList.removeNode(node);
        }
    }

    @Override
    public void clear() {
        cacheMap.clear();
        // Reset the linked list
        while (!lruList.isEmpty()) {
            lruList.removeLast();
        }
        hitCount = 0;
        missCount = 0;
    }

    @Override
    public int size() {
        return cacheMap.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean containsKey(K key) {
        return cacheMap.containsKey(key);
    }

    @Override
    public void printCache() {
        System.out.print("LRU Cache [MRU -> LRU]: ");
        DLinkedListNode<K, V> current = lruList.getHead();
        while (current != null && current.key != null) {
            System.out.print(current.key + "=" + current.value + " ");
            current = current.next;
        }
        System.out.println();
    }

    /**
     * Evict the least recently used item
     */
    private void evict() {
        DLinkedListNode<K, V> lruNode = lruList.removeLast();
        if (lruNode != null) {
            cacheMap.remove(lruNode.key);
            System.out.println("Evicted: " + lruNode.key + "=" + lruNode.value);
        }
    }
}