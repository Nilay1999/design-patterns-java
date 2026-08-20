package com.lld.questions.solutions.inmemorysql;

import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Tier 3 — sorted index for range queries. A TreeMap (red-black tree) keeps
 * keys ordered, so {@code headMap}/{@code tailMap}/{@code subMap} give
 * O(log n)-located range slices for free — no hand-rolled binary search.
 */
public class SortedIndex implements Index {

    private final NavigableMap<Object, Set<Long>> map = new TreeMap<>();

    @Override
    public void add(Object value, long id) {
        map.computeIfAbsent(value, k -> new HashSet<>()).add(id);
    }

    @Override
    public void remove(Object value, long id) {
        Set<Long> ids = map.get(value);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty())
                map.remove(value);
        }
    }

    @Override
    public Set<Long> find(Op op, Object value) {
        SortedMap<Object, Set<Long>> sub = switch (op) {
            case EQ -> map.subMap(value, true, value, true);
            case LT -> map.headMap(value, false);
            case LTE -> map.headMap(value, true);
            case GT -> map.tailMap(value, false);
            case GTE -> map.tailMap(value, true);
            default -> null; // NE -> not worth an index; let the planner scan
        };
        if (sub == null)
            return null;
        Set<Long> out = new HashSet<>();
        for (Set<Long> ids : sub.values())
            out.addAll(ids);
        return out;
    }
}
