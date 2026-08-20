package com.lld.questions.solutions.inmemorysql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tier 2/3 — a table with secondary indexes. Maintains index consistency on
 * every write and uses the QueryPlanner in select(). In a real interview you'd
 * fold this into Table; it's a subclass here only to highlight what indexing
 * adds.
 */
public class IndexedTable extends Table {

    private final Map<String, Index> indexes = new HashMap<>();

    /** Create a hash (equality) index and back-fill existing rows. */
    public void createIndex(String column) {
        createIndex(column, false);
    }

    /** sorted=true builds a range-capable SortedIndex instead of a HashIndex. */
    public void createIndex(String column, boolean sorted) {
        Index index = sorted ? new SortedIndex() : new HashIndex();
        for (Map<String, Object> row : rows.values()) // back-fill — the classic bug if skipped
            index.add(row.get(column), (Long) row.get("id"));
        indexes.put(column, index);
    }

    @Override
    public Map<String, Object> insert(Map<String, Object> row) {
        Map<String, Object> stored = super.insert(row);
        long id = (Long) stored.get("id");
        for (Map.Entry<String, Index> e : indexes.entrySet())
            e.getValue().add(stored.get(e.getKey()), id); // keep every index in sync
        return stored;
    }

    @Override
    public int update(List<Condition> conditions, Map<String, Object> changes) {
        int n = 0;
        for (Map<String, Object> r : rows.values()) {
            if (!Predicates.allMatch(r, conditions))
                continue;
            long id = (Long) r.get("id");
            // For each indexed column that's actually changing: remove old, then add new.
            for (Map.Entry<String, Index> e : indexes.entrySet()) {
                String col = e.getKey();
                if (changes.containsKey(col))
                    e.getValue().remove(r.get(col), id);
            }
            r.putAll(changes);
            for (Map.Entry<String, Index> e : indexes.entrySet()) {
                String col = e.getKey();
                if (changes.containsKey(col))
                    e.getValue().add(r.get(col), id);
            }
            n++;
        }
        return n;
    }

    @Override
    public int delete(List<Condition> conditions) {
        int n = 0;
        for (Iterator<Map<String, Object>> it = rows.values().iterator(); it.hasNext();) {
            Map<String, Object> r = it.next();
            if (!Predicates.allMatch(r, conditions))
                continue;
            long id = (Long) r.get("id");
            for (Map.Entry<String, Index> e : indexes.entrySet())
                e.getValue().remove(r.get(e.getKey()), id); // remove from indexes BEFORE dropping the row
            it.remove();
            n++;
        }
        return n;
    }

    @Override
    public List<Map<String, Object>> select(List<Condition> conditions) {
        Set<Long> candidates = QueryPlanner.plan(conditions, indexes);
        Collection<Map<String, Object>> scan = candidates == null ? rows.values() // no usable index -> full scan
                : candidates.stream().map(rows::get).toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : scan)
            if (r != null && Predicates.allMatch(r, conditions))
                out.add(r);
        return out;
    }
}
