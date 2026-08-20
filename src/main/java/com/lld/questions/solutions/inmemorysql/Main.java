package com.lld.questions.solutions.inmemorysql;

import java.util.List;
import java.util.Map;

/**
 * Runnable demo: insert -> create index -> indexed select -> range select
 * -> update -> delete, printing results at each step.
 *
 * Compile & run from the repo root (solutions/ is the package root):
 * javac solutions/inmemorysql/*.java
 * java solutions.inmemorysql.Main
 */
public class Main {

    public static void main(String[] args) {
        Database db = new Database();
        IndexedTable users = db.createTable("users");

        users.insert(Map.of("name", "Sam", "age", 30, "city", "NYC"));
        users.insert(Map.of("name", "Ada", "age", 30, "city", "LA"));
        users.insert(Map.of("name", "Lee", "age", 25, "city", "NYC"));
        users.insert(Map.of("name", "Ravi", "age", 40, "city", "NYC"));

        // Equality index on age (created AFTER inserts -> exercises back-fill).
        users.createIndex("age");
        // Sorted index on age would be needed for ranges; create one on a copy column:
        IndexedTable ranged = db.createTable("ranged");
        users.select(List.of()).forEach(r -> ranged.insert(Map.copyOf(r)));
        ranged.createIndex("age", true); // sorted

        System.out.println("== age = 30 (hash index, O(1)) ==");
        print(users.select(List.of(Condition.eq("age", 30))));

        System.out.println("== age = 30 AND city = 'NYC' (index then filter) ==");
        print(users.select(List.of(Condition.eq("age", 30), Condition.eq("city", "NYC"))));

        System.out.println("== age > 25 (sorted index, range) ==");
        print(ranged.select(List.of(Condition.gt("age", 25))));

        System.out.println("== update Sam's age 30 -> 31 (index stays consistent) ==");
        users.update(List.of(Condition.eq("name", "Sam")), Map.of("age", 31));
        System.out.println("age = 30 now:");
        print(users.select(List.of(Condition.eq("age", 30))));
        System.out.println("age = 31 now:");
        print(users.select(List.of(Condition.eq("age", 31))));

        System.out.println("== delete city = 'LA' ==");
        int removed = users.delete(List.of(Condition.eq("city", "LA")));
        System.out.println("deleted rows: " + removed);
        System.out.println("all NYC:");
        print(users.select(List.of(Condition.eq("city", "NYC"))));
    }

    private static void print(List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows)
            System.out.printf("  id=%s name=%s age=%s city=%s%n",
                    r.get("id"), r.get("name"), r.get("age"), r.get("city"));
        if (rows.isEmpty())
            System.out.println("  (no rows)");
    }
}
