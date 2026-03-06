package com.lld.keyvaluestore;

class ValueWrapper<V> {
    final V value;
    final long expiryTime;

    ValueWrapper(V value) {
        this(value, -1);
    }

    ValueWrapper(V value, long ttlMillis) {
        this.value = value;
        this.expiryTime = ttlMillis > 0
                ? System.currentTimeMillis() + ttlMillis
                : -1;
    }

    boolean isExpired() {
        return expiryTime > 0 && System.currentTimeMillis() > expiryTime;
    }
}
