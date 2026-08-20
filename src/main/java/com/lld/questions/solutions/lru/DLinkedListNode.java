package com.lld.questions.solutions.lru;

public class DLinkedListNode<K, V> {
    K key;
    V value;
    DLinkedListNode<K, V> next;
    DLinkedListNode<K, V> prev;
    long timestamp;

    public DLinkedListNode(K key, V value) {
        this.key = key;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
        this.next = null;
        this.prev = null;
    }

    @Override
    public String toString() {
        return "Node{" + key + "=" + value + "}";
    }
}
