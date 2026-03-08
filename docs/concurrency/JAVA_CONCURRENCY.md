# Java Concurrency — Master Guide

A comprehensive reference for software engineers mastering concurrent programming in Java for production systems.

> **Difficulty:** Beginner → Advanced → Expert
> **Audience:** Software engineers building production JVM services
> **Format:** 4-part series, ~20,000 words, with code examples, diagrams, and real-world use cases

---

## Document Structure

### [Part 1 — Foundations](./docs/concurrency/01-fundamentals.md)
**Sections 1–5**

| # | Topic | Key Concepts |
|---|---|---|
| 1 | Introduction to Concurrency | Concurrency vs Parallelism vs Multithreading; why it matters |
| 2 | Java Thread Fundamentals | Process vs Thread; lifecycle; Thread, Runnable, Callable |
| 3 | Thread Safety | Race conditions; critical sections; immutability |
| 4 | Synchronization | `synchronized`; intrinsic locks; monitors; wait/notify |
| 5 | Volatile Keyword | Memory visibility; CPU cache effects; happens-before |

---

### [Part 2 — Advanced Mechanisms](./docs/concurrency/02-advanced-concurrency.md)
**Sections 6–10**

| # | Topic | Key Concepts |
|---|---|---|
| 6 | Java Memory Model (JMM) | Working vs main memory; atomicity; instruction reordering; final fields |
| 7 | Atomic Variables | AtomicInteger; CAS internals; LongAdder; high-throughput counters |
| 8 | Locks Framework | ReentrantLock; ReadWriteLock; StampedLock; fairness; tryLock |
| 9 | Thread Pools & Executors | ThreadPoolExecutor internals; pool sizing; rejection policies |
| 10 | Future & CompletableFuture | Async pipelines; chaining; combining; API aggregation example |

---

### [Part 3 — Collections, Utilities & Deadlocks](./docs/concurrency/03-collections-and-patterns.md)
**Sections 11–15**

| # | Topic | Key Concepts |
|---|---|---|
| 11 | Concurrent Collections | ConcurrentHashMap; CopyOnWriteArrayList; BlockingQueue variants |
| 12 | Producer-Consumer Pattern | BlockingQueue pipeline; poison pill shutdown |
| 13 | Synchronization Utilities | CountDownLatch; CyclicBarrier; Semaphore; Phaser; Exchanger |
| 14 | Fork/Join Framework | Work stealing; RecursiveTask; parallel merge sort |
| 15 | Deadlocks | Coffman conditions; lock ordering; tryLock prevention; detection |

---

### [Part 4 — Patterns, Performance & Production](./docs/concurrency/04-production-and-patterns.md)
**Sections 16–20**

| # | Topic | Key Concepts |
|---|---|---|
| 16 | Concurrency Design Patterns | Immutable object; thread confinement; ThreadLocal; active object |
| 17 | Performance Considerations | Lock contention; false sharing; context switching; Amdahl's law |
| 18 | Debugging Concurrency Issues | Thread dumps; race condition patterns; JFR; stress testing |
| 19 | Real Production Use Cases | High-throughput API; event processor; web crawler; streaming pipeline |
| 20 | Best Practices | Golden rules; tool selection guide; thread pool checklist |

---

## Quick Reference: Choosing the Right Tool

| Problem | Best Tool |
|---|---|
| Simple counter, high contention | `LongAdder` |
| Atomic state transitions | `AtomicReference.compareAndSet()` |
| Thread-safe map | `ConcurrentHashMap` |
| Read-heavy list (rare writes) | `CopyOnWriteArrayList` |
| Producer-consumer queue | `LinkedBlockingQueue` (bounded!) |
| Limit concurrent resource access | `Semaphore` |
| Wait for N completions (one-time) | `CountDownLatch` |
| All threads sync at point (repeating) | `CyclicBarrier` |
| Async task composition | `CompletableFuture` + dedicated executor |
| CPU-bound recursive tasks | `ForkJoinPool` |
| Per-thread mutable state | `ThreadLocal` (with `remove()` in finally!) |
| Multiple readers / few writers | `StampedLock` (optimistic read) |
| Flexible multi-phase sync | `Phaser` |
| Simple mutual exclusion | `ReentrantLock` or `synchronized` |
| Background periodic task | `ScheduledExecutorService` |

---

## The 10 Golden Rules

1. The safest shared state is **no shared state** — design for immutability first
2. **Document** who owns each lock and what state it protects
3. **Synchronize consistently** — reads and writes, same lock
4. Never call **alien methods** (callbacks) while holding a lock
5. Always release locks in **`finally` blocks**
6. Never **swallow `InterruptedException`** — restore the interrupt flag
7. **Name threads**, bound queues, handle rejections in all thread pools
8. **Test with stress tests** — unit tests miss race conditions
9. **Profile before optimizing** — measure lock contention before adding locks
10. Enforce **lock ordering globally** — never nest locks without documented order
