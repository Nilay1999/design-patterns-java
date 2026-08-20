package com.lld.questions.solutions.lru;

public class DLinkedList<K, V> {
    private DLinkedListNode<K, V> latest;
    private DLinkedListNode<K, V> oldest;
    private int size;

    public DLinkedList() {
        this.latest = new DLinkedListNode<>(null, null);
        this.oldest = new DLinkedListNode<>(null, null);
        this.size = 0;
        this.latest.next = this.oldest;
        this.oldest.prev = this.latest;
    }

    public void addToFront(DLinkedListNode<K, V> node) {
        if (node == null)
            return;
        DLinkedListNode<K, V> oldFirst = latest.next;
        node.prev = latest;
        node.next = oldFirst;
        latest.next = node;
        oldFirst.prev = node;
        size++;
    }

    public void removeNode(DLinkedListNode<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
        size--;
    }

    public void moveToFront(DLinkedListNode<K, V> node) {
        removeNode(node);
        addToFront(node);
    }

    public DLinkedListNode<K, V> removeLast() {
        if (isEmpty()) {
            return null;
        }
        DLinkedListNode<K, V> node = oldest.prev;
        removeNode(node);
        return node;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public DLinkedListNode<K, V> getHead() {
        return latest.next == oldest ? null : latest.next;
    }

    public DLinkedListNode<K, V> getTail() {
        return oldest.prev == latest ? null : oldest.prev;
    }
}
