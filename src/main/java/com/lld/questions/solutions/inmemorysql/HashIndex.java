package com.lld.questions.solutions.inmemorysql;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tier 2 — inverted index for O(1) equality lookups. Backs {@code col = x}.
 * Maps a column value to the set of rowIds that hold it.
 */
public class HashIndex implements Index {

    private final Map<Object, Set<Long>> map = new HashMap<>();

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
        // Only equality is servable by a hash index; everything else -> null -> scan.
        return op == Op.EQ ? new HashSet<>(map.getOrDefault(value, Set.of())) : null;
    }
}
