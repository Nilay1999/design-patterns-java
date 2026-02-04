package com.lld.garbagecollector;

import java.util.Iterator;

import com.lld.garbagecollector.interfaces.GarbageCollector;
import com.lld.garbagecollector.models.GCObject;

public class MarkAndSweepGC implements GarbageCollector {

    private HeapManager heap;
    private RootsManager roots;

    MarkAndSweepGC(HeapManager heap, RootsManager roots) {
        this.heap = heap;
        this.roots = roots;
    }

    public void collect() {
        mark();
        sweep();
    }

    private void mark() {
        for (GCObject root : roots.getRoots()) {
            dfs(root);
        }
    }

    private void dfs(GCObject obj) {
        if (obj.marked)
            return;
        obj.marked = true;
        for (GCObject child : obj.references) {
            dfs(child);
        }
    }

    private void sweep() {
        Iterator<GCObject> it = heap.getAll().iterator();
        while (it.hasNext()) {
            GCObject obj = it.next();
            if (!obj.marked) {
                it.remove();
            } else {
                obj.marked = false;
            }
        }
    }
}
