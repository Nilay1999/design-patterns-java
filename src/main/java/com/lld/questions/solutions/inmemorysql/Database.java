package com.lld.questions.solutions.inmemorysql;

import java.util.HashMap;
import java.util.Map;

/** Top-level container: name -> table. */
public class Database {

    private final Map<String, IndexedTable> tables = new HashMap<>();

    public IndexedTable createTable(String name) {
        IndexedTable table = new IndexedTable();
        tables.put(name, table);
        return table;
    }

    public IndexedTable table(String name) {
        IndexedTable t = tables.get(name);
        if (t == null)
            throw new IllegalArgumentException("no such table: " + name);
        return t;
    }
}
