package com.lld.garbagecollector.models;

import java.util.ArrayList;
import java.util.List;

public class GCObject {
    public String id;
    public List<GCObject> references = new ArrayList<>();
    public boolean marked = false;

    GCObject(String id) {
        this.id = id;
    }
}
