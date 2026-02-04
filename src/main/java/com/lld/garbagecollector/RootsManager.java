package com.lld.garbagecollector;

import java.util.HashSet;
import java.util.Set;

import com.lld.garbagecollector.models.GCObject;

public class RootsManager {
    Set<GCObject> roots = new HashSet<>();

    void addRoot(GCObject obj) {
        roots.add(obj);
    }

    Set<GCObject> getRoots() {
        return roots;
    }
}