package com.lld.questions.solutions.inmemorysql;

import java.util.Set;

/**
 * Strategy interface for a secondary index. HashIndex serves equality;
 * SortedIndex serves ranges. The planner treats them identically.
 */
public interface Index {
    void add(Object value, long id);

    void remove(Object value, long id);

    /**
     * Candidate rowIds for this op/value, or null = "I can't serve this op"
     * (planner falls back to scan).
     */
    Set<Long> find(Op op, Object value);
}
