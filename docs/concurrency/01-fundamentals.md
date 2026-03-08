# Java Concurrency — Part 1: Foundations
## Sections 1–5: Introduction · Threads · Thread Safety · Synchronization · Volatile

---

## 1. Introduction to Concurrency

### 1.1 What Is Concurrency?

**Concurrency** is the ability of a program to deal with multiple tasks that overlap in time. The word "deal with" is intentional — concurrency does not require both tasks to be physically running at the same instant. It only requires that the system makes *progress on both* within an overlapping time window.

Think of a chef cooking multiple dishes. The pasta is on the stove boiling (waiting, no CPU needed). While it boils, the chef chops vegetables (executing). When the timer rings, the chef stirs the pasta and goes back to chopping. Both tasks are in progress simultaneously even though only one has the chef's attention at any given moment.

```
Timeline:
Task A: [====running====][--waiting--][==running==][--waiting--][=running=]
Task B:            [=running=][----waiting----][=========running=========]
```

This interleaving is the essence of concurrency. The OS switches between tasks fast enough that they appear to progress together.

**Concurrency vs Parallelism** are often confused but they describe different things:

- **Concurrency** is a *design property* of a system — it is structured to handle multiple things at once. It says nothing about whether they run simultaneously.
- **Parallelism** is a *runtime property* — multiple computations are literally executing at the same physical instant on multiple cores.

Parallelism requires multiple CPUs. Concurrency does not. A single-core system can be highly concurrent (many tasks interleaved) but never parallel.

```
Concurrency on 1 core (interleaving, NOT simultaneous):
Core 0: [--A--][--B--][--A--][--B--][--A--]

Parallelism on 2 cores (physically simultaneous):
Core 0: [--A--][--A--][--A--]
Core 1: [--B--][--B--][--B--]
```

**Multithreading** is Java's mechanism for achieving concurrency (and potentially parallelism on multi-core hardware). A thread is an independently-scheduled unit of execution within a process. When you create two threads, the OS can run them on separate cores (parallelism) or time-slice them on one core (concurrency without parallelism).

The key insight: **parallelism is a subset of concurrency**. All parallel programs are concurrent. Not all concurrent programs are parallel.

### 1.2 Why Concurrency Is Hard

Concurrency is not inherently difficult — the difficulty comes from **shared mutable state**. When multiple tasks can read and write the same data, the order of operations matters. And since threads are scheduled by the OS non-deterministically, you cannot predict that order.

This non-determinism makes bugs:
- Hard to reproduce (the bug only appears when threads interleave in a specific unfortunate way)
- Hard to debug (the act of attaching a debugger changes thread scheduling)
- Hard to test (tests may pass 999 times and fail the 1000th)

The rest of this document is really about answering one question: *how do we write programs where shared state is accessed safely despite unpredictable scheduling?*

### 1.3 Why Concurrency Matters in Production Systems

Modern hardware has 8, 16, 32 CPU cores. A single-threaded program uses one core and leaves the rest idle. For CPU-bound work, concurrency is a multiplier on throughput. But even for I/O-bound work (which is most backend work), concurrency is critical because threads can do useful work while others wait on disk, network, or database.

Real examples:

**Web Server (Tomcat/Jetty):** Each HTTP request is assigned to a thread from a pool. Without concurrency, Request 2 must wait for Request 1 to complete — including its database query time. With a thread pool, Request 1's thread blocks on its DB query, and the scheduler gives the CPU to Request 2's thread.

```
Without concurrency:           With concurrency (thread pool):
Req 1: [====DB====][response]  Thread-1: [====DB====][response]
Req 2: ........[====DB====]    Thread-2: [====DB====][response]   ← runs in parallel
                                         ↑ starts at same time
```

**Database Connection Pool (HikariCP):** A fixed pool of 10 connections is shared among 200 threads. Concurrency control ensures no two threads use the same connection simultaneously. Without it, two threads sending queries on the same connection would corrupt both responses.

**Kafka Consumer:** A consumer group has N consumers, each reading from a separate partition. Each consumer is a thread. They read and process events in parallel, achieving N× throughput vs a single consumer.

---

## 2. Java Thread Fundamentals

### 2.1 What Is a Thread?

A **process** is an isolated instance of a running program. The OS gives each process its own address space — its own heap, its own code segment, its own file descriptor table. Processes are isolated from each other by design. One process crashing does not crash another.

A **thread** is a unit of execution *within* a process. All threads in a process share the same heap, the same static variables, and the same open file handles. Each thread has its own:
- **Stack** — local variables, method call frames
- **Program counter** — which instruction to execute next
- **Register state** — CPU register values at the current point of execution

This sharing is what makes threads efficient (no IPC needed) and dangerous (uncoordinated access to shared state causes bugs).

```
JVM Process
├── Heap (shared by ALL threads)
│   ├── Object instances
│   └── Static fields
│
├── Thread-1
│   ├── Stack (private)
│   └── Program Counter (private)
│
├── Thread-2
│   ├── Stack (private)
│   └── Program Counter (private)
│
└── Thread-3 (GC thread, JVM internal)
    ├── Stack (private)
    └── Program Counter (private)
```

When you write `int x = 5;` inside a method, `x` lives on the stack — private to that thread. When you write `this.count++`, `count` is a field on a heap-allocated object — visible to every thread that holds a reference to that object.

### 2.2 Thread Lifecycle

A Java thread moves through well-defined states defined in `java.lang.Thread.State`:

```
         new Thread(r)
NEW ──────────────────────► RUNNABLE
                                │  ▲
               OS dispatches    │  │  OS preempts (timeslice expires)
                                ▼  │
                            RUNNING
                           /       \
           wait()/join()  /         \  synchronized block
           sleep(n)      /           \  (lock not available)
                        ▼             ▼
              WAITING/TIMED_WAITING  BLOCKED
                        │
              notify()/interrupt()/timeout
                        │
                        └──────────────► RUNNABLE
                                              │
                                    run() returns or throws
                                              │
                                         TERMINATED
```

Understanding each state matters for debugging:

| State | What it means | How to get out |
|---|---|---|
| `NEW` | Thread object created, `start()` not called | Call `start()` |
| `RUNNABLE` | Eligible to run; may or may not be on CPU right now | OS schedules it → RUNNING |
| `BLOCKED` | Waiting to enter a `synchronized` block; another thread holds the lock | Lock holder exits synchronized block |
| `WAITING` | Indefinitely waiting — called `wait()`, `join()` with no timeout, or `LockSupport.park()` | Another thread calls `notify()` / `notifyAll()`, or the joined thread terminates |
| `TIMED_WAITING` | Waiting with a deadline — called `sleep(n)`, `wait(n)`, `join(n)` | Timeout expires, or woken early |
| `TERMINATED` | `run()` returned or threw an uncaught exception | Cannot restart; create a new Thread |

`BLOCKED` and `WAITING` are often confused. `BLOCKED` specifically means waiting to acquire a **monitor lock** from `synchronized`. `WAITING` means the thread voluntarily gave up the CPU with `wait()` or `join()` — it is not trying to acquire a lock; it is waiting for a signal.

When you read a thread dump (e.g., from `jstack` or a profiler), these states tell you exactly what every thread is doing. A pile of `BLOCKED` threads means lock contention. A pile of `WAITING` threads usually means threads are parked in a thread pool waiting for work.

### 2.3 Creating Threads

#### Method 1: Extending `Thread`

```java
public class DownloadTask extends Thread {
    private final String url;

    public DownloadTask(String url) {
        super("downloader-" + url); // name the thread — it shows in thread dumps
        this.url = url;
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + " downloading " + url);
        // actual download logic
    }
}

DownloadTask task = new DownloadTask("https://example.com/file.zip");
task.start(); // creates a new OS thread and calls run() on it
```

**Critical mistake:** Calling `task.run()` instead of `task.start()` does NOT create a new thread. It calls `run()` on the *calling thread*, blocking it until `run()` completes. Always call `start()`.

#### Method 2: Implementing `Runnable` (Preferred)

```java
public class LogProcessor implements Runnable {
    private final String logEntry;

    public LogProcessor(String logEntry) {
        this.logEntry = logEntry;
    }

    @Override
    public void run() {
        System.out.println("Processing: " + logEntry);
    }
}

Thread t = new Thread(new LogProcessor("ERROR: NullPointerException"));
t.start();

// Or with a lambda — Runnable is a functional interface
Thread t2 = new Thread(() -> System.out.println("Processing log entry"));
t2.start();
```

**Why prefer `Runnable` over extending `Thread`?**

Java allows only single inheritance. If your class extends `Thread`, it cannot extend anything else. More importantly, extending `Thread` couples the *task definition* (what to run) with the *execution mechanism* (how it runs — on which thread, from which pool). By implementing `Runnable`, the task is decoupled. You can submit the same `Runnable` to a thread pool, a `ScheduledExecutorService`, or a `ForkJoinPool` without changing the task at all.

#### Method 3: `Callable` and `Future`

`Runnable.run()` has two limitations: it returns `void` and cannot throw checked exceptions. `Callable<V>` removes both constraints:

```java
public class PriceCalculator implements Callable<Double> {
    private final String productId;

    public PriceCalculator(String productId) {
        this.productId = productId;
    }

    @Override
    public Double call() throws Exception {
        // simulate calling a pricing microservice
        Thread.sleep(100);
        return 42.99;
    }
}

ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Double> future = executor.submit(new PriceCalculator("SKU-123"));

// The main thread can do other work here — the calculation is running concurrently
System.out.println("Doing other work while price calculates...");

Double price = future.get(); // blocks until the Callable completes and returns its result
System.out.println("Price: " + price);
executor.shutdown();
```

`Future<V>` represents the *eventual result* of an asynchronous computation. `future.get()` either returns the result or throws:
- `InterruptedException` — the waiting thread was interrupted
- `ExecutionException` — the `Callable` itself threw an exception (wrapped here)
- `TimeoutException` — from `future.get(timeout, unit)` overload

### 2.4 Thread Control

**Joining a thread** means waiting for it to finish:

```java
Thread t = new Thread(() -> heavyComputation());
t.start();

// Do some other work on the main thread...
doOtherWork();

t.join(); // blocks the main thread until t finishes
System.out.println("t is done, result is ready");
```

`join()` uses the `WAITING` state. The calling thread waits until the target thread reaches `TERMINATED`. This establishes a happens-before relationship: everything the target thread did is visible to the calling thread after `join()` returns.

**Interrupting a thread** is Java's cooperative cancellation mechanism:

```java
Thread worker = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        doWork();
    }
    System.out.println("Interrupted, shutting down cleanly");
});

worker.start();
Thread.sleep(2000);
worker.interrupt(); // sets the interruption flag on 'worker'
```

`interrupt()` does not forcibly kill a thread. It sets a boolean flag in the thread. The thread must periodically check `isInterrupted()` and handle it. If the thread is currently in `wait()`, `sleep()`, or `join()`, the JVM throws `InterruptedException` in that thread, clearing the flag in the process.

**Good practice when catching `InterruptedException`:**

```java
// WRONG — swallowing the interrupt loses the cancellation signal
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // do nothing — the thread will not know it was interrupted
}

// CORRECT — restore the interrupt flag so callers can observe it
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // re-set the flag
    return; // or throw, or clean up
}
```

**Thread properties:**

```java
Thread t = new Thread(() -> {});

// Name your threads — they appear in thread dumps and logs
t.setName("payment-processor-1");

// Daemon threads: the JVM shuts down when only daemon threads remain
// Use for background housekeeping tasks that should not prevent JVM exit
t.setDaemon(true); // must be set before start()

// Uncaught exception handler — catches exceptions that escape run()
t.setUncaughtExceptionHandler((thread, throwable) -> {
    log.error("Thread {} died unexpectedly", thread.getName(), throwable);
    alertMonitoringSystem(throwable);
});
```

### 2.5 Thread Scheduling and Context Switching

The JVM does not schedule threads — the OS does. The OS uses a **preemptive scheduler**: it can suspend any running thread at any moment and give the CPU to another thread. This suspension is called a **context switch**.

During a context switch:
1. The OS saves the current thread's register state, program counter, and stack pointer into the thread's **Thread Control Block (TCB)**
2. The OS selects the next thread to run (based on priority, fairness policy, I/O readiness)
3. The OS loads the saved state from the new thread's TCB into CPU registers
4. Execution resumes where the new thread left off

A context switch costs roughly **1–10 microseconds**. The hidden cost is even larger: the new thread's data likely isn't in the CPU's L1/L2 cache, causing **cache misses** that stall the CPU waiting for main memory fetches.

This has a practical consequence: **creating thousands of threads is wasteful**. If you have 10,000 threads and 8 cores, the OS is doing 10,000 context switches to simulate 10,000 concurrent things. The overhead dominates. Thread pools solve this by reusing a small fixed set of threads for many tasks.

**Thread priority** (`Thread.MIN_PRIORITY=1` through `Thread.MAX_PRIORITY=10`) is a hint to the OS scheduler. On Linux, it maps to `nice` values. On Windows, it maps to Windows thread priorities. But it is only a hint — do not write code whose correctness depends on priority. Use it only for performance tuning.

---

## 3. Thread Safety

### 3.1 What Thread Safety Means

A class is **thread-safe** if it behaves correctly when accessed from multiple threads simultaneously, with no additional synchronization required from the caller. "Correctly" means: it maintains its invariants and produces the expected result regardless of how threads are interleaved by the scheduler.

Thread safety is hard to define precisely because it requires specifying what "correct" means for a given class. For a counter, correct means the count equals the exact number of `increment()` calls. For a bank account, correct means the balance equals initial + deposits - withdrawals.

The challenge: correctness must hold for *all possible interleavings*, not just the common ones. If a class works correctly 99.9% of the time but fails 0.1% of the time under unusual scheduling, it is not thread-safe.

### 3.2 Race Conditions

A **race condition** occurs when the program's correctness depends on the relative timing of operations across threads. The program "races" against the scheduler — sometimes it wins (threads interleave safely), sometimes it loses (they interleave in a way that corrupts state).

**Type 1: Read-Modify-Write**

The classic example is `count++`. This looks like one operation but is actually three:

```
1. READ:  load count from memory into CPU register
2. MODIFY: add 1 to the register value
3. WRITE: store the register value back to memory
```

If two threads execute these three steps concurrently, the scheduler can interleave them:

```
Thread A                        Thread B
READ  count → 0
                                READ  count → 0   ← reads stale value
MODIFY 0+1 = 1
                                MODIFY 0+1 = 1
WRITE count = 1
                                WRITE count = 1   ← overwrites Thread A's result

Expected: count = 2
Actual:   count = 1
```

Thread A's increment is permanently lost. This is called a **lost update**. With 1000 threads each calling `increment()` 1000 times, you expect `1,000,000` but may get `997,412` or `501,882` — it varies every run.

```java
// UNSAFE — do not use this in production
public class UnsafeCounter {
    private int count = 0;

    public void increment() {
        count++; // not atomic: read, add, write
    }

    public int getCount() {
        return count;
    }
}
```

**Type 2: Check-Then-Act**

```java
// UNSAFE — classic lazy initialization race
public class Cache {
    private ExpensiveObject instance = null;

    public ExpensiveObject getInstance() {
        if (instance == null) {         // Thread A checks: null → true
                                        // Thread B checks: null → true (before A writes)
            instance = new ExpensiveObject(); // Both threads create an instance!
        }
        return instance;
    }
}
```

Thread A checks `instance == null`, sees true. Before A creates the object, Thread B runs the check — also sees null. Both threads create `ExpensiveObject`. One overwrites the other. Callers may hold references to two different instances when they expected one.

Both types share the same structure: a thread reads state, assumes that state is still valid, then acts on that assumption — but another thread changes the state between the read and the act.

### 3.3 The Critical Section

A **critical section** is code that accesses shared mutable state and must execute atomically with respect to other threads. "Atomically" here means: from the perspective of other threads, either none of the critical section has happened or all of it has happened — never a partial result.

For `count++`, the critical section is the entire read-modify-write sequence. For the `getInstance()` check, the critical section is the check-and-create sequence. The fix is always the same: make the critical section execute under a mutual exclusion guarantee.

### 3.4 Shared Mutable State

The root cause of almost every concurrency bug is **shared mutable state**: state that is both shared between threads and mutable. Remove either property and the problem disappears:

**Make it not shared: Thread-Local State**

`ThreadLocal<T>` gives each thread its own independent copy of a variable:

```java
// Each thread gets its own SimpleDateFormat — no sharing, no contention
private static final ThreadLocal<SimpleDateFormat> dateFormat =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

public String formatDate(Date date) {
    return dateFormat.get().format(date); // thread-safe: using our own copy
}
```

`SimpleDateFormat` is not thread-safe. Rather than synchronizing every access, `ThreadLocal` gives each thread its own instance. No sharing → no race condition.

**Make it not mutable: Immutable Objects**

An immutable object cannot change state after construction. Any number of threads can read it simultaneously without coordination:

```java
// IMMUTABLE — thread-safe by design, zero synchronization needed
public final class Money {
    private final long amountInCents;
    private final String currency;

    public Money(long amountInCents, String currency) {
        if (amountInCents < 0) throw new IllegalArgumentException("negative amount");
        this.amountInCents = amountInCents;
        this.currency = Objects.requireNonNull(currency);
    }

    // No setters — state is frozen after construction

    public Money add(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Currency mismatch");
        return new Money(this.amountInCents + other.amountInCents, this.currency);
        // Returns a NEW object — this object is unchanged
    }

    public long getAmountInCents() { return amountInCents; }
    public String getCurrency() { return currency; }
}
```

Rules for a properly immutable class:
1. Declare the class `final` — prevents subclasses from adding mutable state
2. All fields must be `final` and `private`
3. No setter methods
4. If any field is a reference to a mutable object, return a defensive copy from getters
5. Complete all initialization in the constructor

**Make it not state: Stateless Objects**

If a class has no instance fields, it has no state to protect:

```java
// STATELESS — thread-safe, no synchronization needed
public class TaxCalculator {
    // no fields

    public double calculate(double price, double taxRate) {
        return price * taxRate; // operates only on method parameters (on the stack)
    }
}
```

Spring `@Service`, `@Repository`, and `@Controller` beans are singletons shared across all request threads. They are safe *only because* they should be stateless. If you add a mutable instance field to a Spring service, you break thread safety for all users of that service.

---

## 4. Synchronization in Java

### 4.1 The `synchronized` Keyword

`synchronized` is Java's built-in mechanism for mutual exclusion. It ensures that at most one thread executes a given block of code at a time. All other threads that try to enter the block are suspended in the `BLOCKED` state until the current thread exits the block.

The mechanism relies on **intrinsic locks** (also called **monitor locks**). Every Java object, without exception, has exactly one intrinsic lock associated with it. `synchronized` acquires this lock on entry and releases it on exit — even if an exception is thrown (the lock is released in a `finally`-like manner).

### 4.2 Synchronized Methods

```java
public class SafeCounter {
    private int count = 0;

    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
```

Marking a method `synchronized` acquires the lock on `this` — the instance the method is called on. The `synchronized` keyword on a method is syntactic sugar for:

```java
public void increment() {
    synchronized (this) {
        count++;
    }
}
```

For `static synchronized` methods, the lock is the **Class object** (`SafeCounter.class`), not any instance. Static and instance synchronized methods have *different* locks and do not mutually exclude each other.

```java
public static synchronized void staticIncrement() {
    // lock is on SafeCounter.class
    staticCount++;
}
```

### 4.3 Synchronized Blocks — Minimizing Critical Sections

Synchronizing an entire method is often too coarse. If a method does expensive computation that doesn't need the lock, synchronizing the whole method forces other threads to wait unnecessarily.

```java
// BAD — unnecessarily holds lock during I/O and computation
public synchronized void processOrder(Order order) {
    validate(order);              // pure computation, no shared state
    double price = calculate(order); // pure computation, no shared state
    orders.add(order);            // this line needs the lock
    sendConfirmationEmail(order); // I/O — slow! while holding lock
}

// GOOD — hold the lock only during the minimum necessary section
public void processOrder(Order order) {
    validate(order);
    double price = calculate(order);

    synchronized (orderLock) {    // lock only this critical section
        orders.add(order);
    }                             // lock released here, before the email is sent

    sendConfirmationEmail(order); // email sent without holding lock
}
```

The second version allows other threads to enter `processOrder` and acquire the lock for their `orders.add()` call while this thread is sending the email. More concurrency, less contention.

**Why use a dedicated lock object instead of `this`?**

Using `this` as the lock object exposes your lock to external code. Anyone with a reference to your object can `synchronized(yourObject)` and compete with your internal critical sections:

```java
// EXPOSED LOCK — any caller can do synchronized(counter) and cause problems
public class BadCounter {
    private int count = 0;
    public synchronized void increment() { count++; }
}

// External code inadvertently (or maliciously) contends with your lock:
BadCounter c = new BadCounter();
synchronized (c) {         // holds the same lock as increment()!
    Thread.sleep(10000);   // blocks increment() for 10 seconds
}
```

Using a `private final Object lock` prevents this:

```java
public class BetterCounter {
    private final Object lock = new Object();
    private int count = 0;

    public void increment() {
        synchronized (lock) { // lock is private — nobody outside can acquire it
            count++;
        }
    }
}
```

### 4.4 Reentrancy

Java's intrinsic locks are **reentrant**: a thread that already holds a lock can acquire it again without deadlocking. The JVM tracks a hold count per thread per lock. Each acquisition increments the count; each release decrements it. The lock is actually released only when the count drops to zero.

```java
public class Account {
    private double balance;
    private final Object lock = new Object();

    public synchronized void deposit(double amount) {
        // acquires lock on 'this', count = 1
        balance += amount;
        logTransaction("deposit", amount); // calls another synchronized method
    }

    public synchronized void logTransaction(String type, double amount) {
        // tries to acquire lock on 'this' — but THIS THREAD already holds it!
        // reentrant: count becomes 2, no deadlock
        System.out.printf("%s: %.2f, balance: %.2f%n", type, amount, balance);
    } // count drops back to 1
    // deposit() exits: count drops to 0, lock released
}
```

Without reentrancy, `deposit()` calling `logTransaction()` would deadlock: the thread would be waiting to acquire a lock it already holds.

### 4.5 The `wait` / `notify` Protocol

Sometimes a thread needs to wait for a *condition* to become true, not just for a lock to become available. For example, a consumer thread needs to wait until the buffer is non-empty. Busy-waiting (spinning in a loop checking the condition) wastes CPU. Java's `wait()` / `notify()` mechanism solves this.

`wait()`, `notify()`, and `notifyAll()` are methods on `Object` and must be called while holding the lock on that object.

```java
public class BoundedBuffer<T> {
    private final Queue<T> buffer = new LinkedList<>();
    private final int maxSize;

    public BoundedBuffer(int maxSize) {
        this.maxSize = maxSize;
    }

    // Called by producer threads
    public synchronized void put(T item) throws InterruptedException {
        while (buffer.size() == maxSize) {
            // Buffer is full. Atomically release lock and suspend this thread.
            // When woken, reacquire lock before continuing.
            wait();
        }
        buffer.add(item);
        notifyAll(); // wake up any consumer threads waiting for items
    }

    // Called by consumer threads
    public synchronized T take() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait(); // buffer is empty — wait for a producer to add something
        }
        T item = buffer.poll();
        notifyAll(); // wake up any producer threads waiting for space
        return item;
    }
}
```

**Key points about `wait()` / `notify()`:**

`wait()` does two things atomically: it releases the lock *and* suspends the thread. "Atomically" is critical here — if release and suspend were separate, a producer could `notifyAll()` between the consumer releasing the lock and the consumer going to sleep. The consumer would miss the notification and sleep forever.

When a thread is woken by `notify()` or `notifyAll()`, it does not immediately run. It enters the `BLOCKED` state, waiting to reacquire the lock. Only after reacquiring the lock does it return from `wait()`.

**Always use `while`, not `if`, around `wait()`:**

```java
// WRONG
if (buffer.isEmpty()) {
    wait();
}
// If spuriously woken or condition changed before lock reacquired, buffer could be empty again
T item = buffer.poll(); // NullPointerException or incorrect behavior

// CORRECT
while (buffer.isEmpty()) {
    wait(); // re-checks condition every time it wakes up
}
T item = buffer.poll(); // guaranteed non-empty here
```

The JVM specification allows **spurious wakeups** — a thread may return from `wait()` without being notified. The `while` loop revalidates the condition and goes back to sleep if it's not yet true.

**`notify()` vs `notifyAll()`:**

`notify()` wakes exactly one waiting thread (chosen arbitrarily). `notifyAll()` wakes all of them. Use `notifyAll()` when different threads wait for different conditions on the same lock. With `notify()`, you might wake a thread that cannot make progress (its condition is still false), and a thread that could make progress stays asleep.

### 4.6 Liveness Hazards

Synchronization can cause new problems when used incorrectly:

**Deadlock** occurs when two or more threads are each waiting for a lock held by another:

```java
// Thread A: holds lockA, waiting for lockB
// Thread B: holds lockB, waiting for lockA
// Both wait forever

Object lockA = new Object();
Object lockB = new Object();

// Thread 1
synchronized (lockA) {
    Thread.sleep(100); // gives Thread 2 time to acquire lockB
    synchronized (lockB) { // WAITS — Thread 2 holds lockB
        // ...
    }
}

// Thread 2
synchronized (lockB) {
    synchronized (lockA) { // WAITS — Thread 1 holds lockA
        // ...
    }
}
```

**Prevention:** always acquire multiple locks in the same global order. If Thread 1 and Thread 2 both always acquire `lockA` before `lockB`, deadlock cannot occur.

**Livelock** is a dynamic version: threads keep responding to each other and changing state but make no actual progress (like two people in a hallway who keep stepping aside in the same direction).

**Starvation** occurs when a thread is ready to run but is perpetually passed over by the scheduler in favor of higher-priority threads. It makes progress eventually, just very slowly. Unfair locks can cause this.

---

## 5. The Volatile Keyword

### 5.1 The Memory Visibility Problem

To understand `volatile`, you need to understand how modern CPUs handle memory. CPUs don't read directly from RAM — RAM is too slow (hundreds of nanoseconds). Instead, CPUs have multi-level caches: L1 (~1ns), L2 (~5ns), L3 (~20ns), and only then RAM (~100ns).

Each CPU core has its own L1 and L2 cache. When a core writes a value, it writes to its L1 cache first. That write may not reach main memory for some time. Meanwhile, another core reads the same memory address from its own L1 cache — and finds the **old, stale value**.

```
Core 0                          Core 1
┌─────────────────┐             ┌─────────────────┐
│  L1 Cache       │             │  L1 Cache       │
│  flag = true    │             │  flag = false ← stale value
└────────┬────────┘             └────────┬────────┘
         │                               │
         └───────────────┬───────────────┘
                    Main Memory
                    flag = ???   (Core 0's write may not be here yet)
```

This is the **memory visibility problem**: Thread A's write is not guaranteed to be visible to Thread B without explicit synchronization.

### 5.2 Instruction Reordering

The problem goes deeper than caching. The JIT compiler and the CPU are allowed to **reorder instructions** for performance, as long as the reordering is invisible to the *current thread*. In single-threaded code, if A doesn't depend on B, swapping their order is safe. In multithreaded code, another thread observing partial results of these reorderings sees inconsistency.

```java
class Publisher {
    private boolean ready = false;
    private int value = 0;

    public void publish() {
        value = 42;          // write 1
        ready = true;        // write 2
        // The CPU/JIT may execute write 2 BEFORE write 1 (they're independent)
    }

    public void consume() {
        if (ready) {          // sees ready = true
            System.out.println(value); // might print 0! value=42 write hasn't happened yet
        }
    }
}
```

From a single-threaded view, reordering `value = 42` and `ready = true` is harmless — the method produces the same result. But from another thread observing the writes, `ready` can appear `true` while `value` is still `0`. The publication is broken.

### 5.3 What `volatile` Guarantees

Declaring a field `volatile` provides two guarantees from the **Java Memory Model (JMM)**:

**Guarantee 1 — Visibility:** Every write to a `volatile` field is immediately written through to main memory. Every read of a `volatile` field reads from main memory, bypassing the CPU cache. This ensures all threads see the most recent value.

**Guarantee 2 — Ordering (happens-before):** All writes that happen *before* a volatile write are flushed and ordered before it. All reads that happen *after* a volatile read see everything up to and including that volatile write. In effect, a volatile write acts as a **full memory barrier** (store fence + load fence).

```java
class Publisher {
    private volatile boolean ready = false; // volatile!
    private int value = 0;

    public void publish() {
        value = 42;          // guaranteed to happen BEFORE the volatile write
        ready = true;        // volatile write — memory barrier here
                             // all preceding writes are flushed to main memory
    }

    public void consume() {
        if (ready) {         // volatile read — memory barrier here
                             // sees all writes that preceded the volatile write
            System.out.println(value); // guaranteed to print 42
        }
    }
}
```

The volatile write to `ready` acts as a fence: the CPU and JIT cannot move the `value = 42` write to after it. And the volatile read of `ready` acts as a fence: the CPU cannot read `value` from cache — it must see the latest value that was flushed by the writer.

### 5.4 The Stop-Flag Pattern

The most common correct use of `volatile` is a stop flag for background threads:

```java
public class BackgroundWorker implements Runnable {
    private volatile boolean running = true;

    public void stop() {
        running = false; // volatile write — immediately visible to worker thread
    }

    @Override
    public void run() {
        while (running) {  // volatile read — sees the write as soon as it happens
            doWork();
        }
        System.out.println("Worker stopped cleanly");
    }
}

BackgroundWorker worker = new BackgroundWorker();
Thread t = new Thread(worker, "background-worker");
t.start();

Thread.sleep(5000);
worker.stop(); // signal the worker to stop
t.join();      // wait for the worker to actually finish
```

Without `volatile`, the JIT compiler sees that `running` is never written in the `run()` loop, concludes it is a constant, and hoists the read out of the loop:

```java
// What the JIT effectively compiles to (without volatile):
boolean cached = running; // read once, outside the loop
while (cached) {          // cached is always true — infinite loop
    doWork();
}
```

`volatile` prevents this optimization by forbidding caching of the value in a register.

### 5.5 When Volatile Is Not Enough

`volatile` guarantees visibility of individual reads and writes. It does **not** guarantee atomicity of compound operations (read-modify-write or check-then-act). This is the most common mistake with `volatile`:

```java
// BROKEN — volatile does NOT fix the race condition
public class BrokenCounter {
    private volatile int count = 0;

    public void increment() {
        count++; // still three non-atomic steps: READ, ADD, WRITE
                 // volatile ensures each READ and WRITE is visible to other threads
                 // but the sequence of READ-then-WRITE is still not atomic
                 // Thread A and Thread B can both READ the same value
    }
}
```

The race condition from Section 3.2 still applies. `volatile` makes sure every thread sees the latest written value, but two threads can still read the same value, both add 1, and both write the same result.

**When volatile is the right tool:**
- Exactly one thread writes the variable, others only read
- The write is a single assignment, not computed from the current value
- Example: status flags, shutdown signals, configuration values updated by admin

**When volatile is NOT enough:**
- Multiple threads write the same variable (`count++`, `balance -= amount`)
- The new value depends on the current value (read-modify-write)
- Atomicity of multiple variables together is required
- For these cases, use `synchronized`, `AtomicInteger`, `AtomicReference`, or `java.util.concurrent.locks`

### 5.6 The Java Memory Model and Happens-Before

The **Java Memory Model (JMM)** formally defines when one thread's writes are guaranteed to be visible to another thread's reads. The answer is: when there is a **happens-before relationship** between the write and the read.

Happens-before is not about time — it is about *guarantees*. If action A happens-before action B, the JMM guarantees B can see all effects of A.

Built-in happens-before rules:

| Rule | What it means |
|---|---|
| **Program Order** | Each statement in a thread happens-before the next statement in that same thread |
| **Monitor Unlock** | `unlock` of a lock happens-before any subsequent `lock` of the same lock |
| **Volatile Write** | A write to a volatile field happens-before any subsequent read of that field |
| **Thread Start** | `thread.start()` happens-before any action in the started thread |
| **Thread Join** | All actions in thread T happen-before `T.join()` returns in the joining thread |
| **Transitivity** | If A hb B and B hb C, then A hb C |

Without a happens-before relationship, there is no guarantee. A write in Thread A and a read in Thread B without any synchronization between them have no happens-before — the read might see any value.

**Volatile piggyback** exploits transitivity to make non-volatile variables visible:

```java
int x = 0;
volatile boolean flag = false;

// Thread A
x = 1;          // (1) write x — happens-before (2) by program order
flag = true;    // (2) volatile write — happens-before (3) by volatile rule

// Thread B
if (flag) {     // (3) volatile read of flag
    // By transitivity: (1) hb (2) hb (3)
    // So (1) hb (3) — Thread B is guaranteed to see x = 1
    System.out.println(x); // always prints 1
}
```

By writing `flag` volatile, we pull along the visibility of `x`. This technique works only because the write to `x` *precedes* the volatile write to `flag` in Thread A, and the read of `x` *follows* the volatile read of `flag` in Thread B.

---

## Summary: Part 1

| Topic | Core Concept | Key Takeaway |
|---|---|---|
| Concurrency | Overlapping task progress | Not the same as parallelism; parallelism requires multiple cores |
| Threads | Unit of execution in a process | Share heap, isolated stack; name your threads; understand BLOCKED vs WAITING |
| Thread Safety | Correct behavior under all schedulings | Eliminate: sharing, mutability, or unsynchronized access |
| Race Conditions | Correctness depends on scheduling | Read-modify-write and check-then-act are the two common forms |
| Synchronized | Intrinsic lock; mutual exclusion | Reentrant; minimize critical section; use private lock objects; always `while` with `wait()` |
| Volatile | Visibility + ordering, not atomicity | One writer pattern; stop flags; understand happens-before before using |

**Continue reading:** [Part 2 — Atomic Variables, Locks, Thread Pools, CompletableFuture](./02-advanced-concurrency.md)
