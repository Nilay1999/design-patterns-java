package com.lld.questions.solutions.inmemorysql;

/** A single WHERE predicate: {@code column <op> value}. */
public record Condition(String column, Op op, Object value) {

    // Tiny factory helpers so call sites read like SQL: Condition.eq("age", 30)
    public static Condition eq(String column, Object value) {
        return new Condition(column, Op.EQ, value);
    }

    public static Condition ne(String column, Object value) {
        return new Condition(column, Op.NE, value);
    }

    public static Condition lt(String column, Object value) {
        return new Condition(column, Op.LT, value);
    }

    public static Condition lte(String column, Object value) {
        return new Condition(column, Op.LTE, value);
    }

    public static Condition gt(String column, Object value) {
        return new Condition(column, Op.GT, value);
    }

    public static Condition gte(String column, Object value) {
        return new Condition(column, Op.GTE, value);
    }
}
