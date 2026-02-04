package com.lld.garbagecollector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.lld.garbagecollector.models.GCObject;

public class HeapManager {
    Map<String, GCObject> objects = new HashMap<>();

    void add(GCObject obj) {
        objects.put(obj.id, obj);
    }

    Collection<GCObject> getAll() {
        return objects.values();
    }

    void remove(String id) {
        objects.remove(id);
    }
}
