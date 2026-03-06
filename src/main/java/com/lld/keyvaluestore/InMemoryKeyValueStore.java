package com.lld.keyvaluestore;

import java.util.concurrent.ConcurrentHashMap;

import com.lld.keyvaluestore.interfaces.KeyValueStore;

public class InMemoryKeyValueStore<K, V> implements KeyValueStore<K, V> {
    private final ConcurrentHashMap<K, ValueWrapper<V>> store;

    public InMemoryKeyValueStore(int capacity) {
        this.store = new ConcurrentHashMap<>(capacity);
    }

    public V get(K key) {
        ValueWrapper<V> wrapper = store.get(key);
        if (wrapper == null) {
            return null;
        }
        if (wrapper.isExpired()) {
            store.remove(key);
            return null;
        }
        return wrapper.value;
    }

    public void set(K key, V value) {
        this.store.put(key, new ValueWrapper<>(value));
    }

    public void setWithTTL(K key, V value, long ttl) {
        this.store.put(key, new ValueWrapper<>(value, ttl));
    }

    public boolean delete(K key) {
        if (store.contains(key)) {
            store.remove(key);
            return true;
        }
        return false;
    }

    public long size() {
        return store.size();
    }
}
