package com.lld.questions.solutions.inmemorysql;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks an index that can serve one of the conditions; else null = full scan.
 */
public final class QueryPlanner {

    private QueryPlanner() {
    }

    public static Set<Long> plan(List<Condition> conditions, Map<String, Index> indexes) {
        for (Condition c : conditions) {
            Index index = indexes.get(c.column());
            if (index != null) {
                Set<Long> ids = index.find(c.op(), c.value());
                if (ids != null)
                    return ids; // candidate set found; planner stops here
            }
        }
        return null; // no usable index -> caller falls back to scanning all rows
    }
}
