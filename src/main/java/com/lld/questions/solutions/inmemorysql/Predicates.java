package com.lld.questions.solutions.inmemorysql;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates WHERE conditions against a row. A row is a column-name -> value
 * map.
 */
public final class Predicates {

    private Predicates() {
    }

    @SuppressWarnings("unchecked")
    public static boolean matches(Map<String, Object> row, Condition c) {
        Object v = row.get(c.column());
        // EQ/NE use value equality (never == on boxed types — that compares identity).
        if (c.op() == Op.EQ)
            return Objects.equals(v, c.value());
        if (c.op() == Op.NE)
            return !Objects.equals(v, c.value());
        // Ranges: cast to Comparable. Throws if a column mixes incomparable types —
        // fine here.
        int cmp = ((Comparable<Object>) v).compareTo(c.value());
        return switch (c.op()) {
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            default -> false;
        };
    }

    /** Conditions are ANDed together; short-circuits on the first failure. */
    public static boolean allMatch(Map<String, Object> row, List<Condition> conditions) {
        for (Condition c : conditions)
            if (!matches(row, c))
                return false;
        return true;
    }
}
