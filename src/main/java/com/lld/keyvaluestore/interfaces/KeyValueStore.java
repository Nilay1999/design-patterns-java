package com.lld.keyvaluestore.interfaces;

public interface KeyValueStore<K, V> {
    public V get(K key);

    public void set(K key, V value);

    public void setWithTTL(K key, V value, long ttlMillis);

    public boolean delete(K key);

    public long size();
}
