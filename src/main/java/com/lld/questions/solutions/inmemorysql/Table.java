package com.lld.questions.solutions.inmemorysql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier 1 — a working table with full-scan CRUD, no index.
 *
 * <p>
 * Storage model: outer map is rowId -> row; a row is itself a map of
 * columnName -> value. LinkedHashMap preserves insertion order so scans
 * (and demo output) are deterministic.
 */
public class Table {

    // rowId -> row(columnName -> value)
    protected final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
    private long nextId = 1;

    public Map<String, Object> insert(Map<String, Object> row) {
        long id = nextId++;
        Map<String, Object> stored = new HashMap<>(row);
        stored.put("id", id);
        rows.put(id, stored);
        return stored;
    }

    /** O(n): scan every row, keep those passing all conditions. */
    public List<Map<String, Object>> select(List<Condition> conditions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows.values())
            if (Predicates.allMatch(r, conditions))
                out.add(r);
        return out;
    }

    public int update(List<Condition> conditions, Map<String, Object> changes) {
        int n = 0;
        for (Map<String, Object> r : rows.values())
            if (Predicates.allMatch(r, conditions)) {
                r.putAll(changes);
                n++;
            }
        return n;
    }

    public int delete(List<Condition> conditions) {
        int n = 0;
        for (Iterator<Map<String, Object>> it = rows.values().iterator(); it.hasNext();)
            if (Predicates.allMatch(it.next(), conditions)) {
                it.remove();
                n++;
            }
        return n;
    }
}
