# Java Concurrency — Part 1: Foundations
## Sections 1–5: Introduction · Threads · Thread Safety · Synchronization · Volatile

---

## 1. Introduction to Concurrency

### 1.1 What Is Concurrency?

**Concurrency** is the ability of a system to deal with multiple tasks at the same time. It does not necessarily mean they execute simultaneously — it means the system makes *progress on multiple things* within an overlapping time window.

Think of a chef cooking multiple dishes. While the pasta boils (waiting), the chef chops vegetables (executing). Two tasks overlap in time — that is concurrency.

```
Timeline:
Task A: [====running====][--waiting--][==running==]
Task B:            [=running=][--waiting--][=========running=========]
```

### 1.2 Concurrency vs Parallelism vs Multithreading

| Concept | Definition | Requires multiple CPUs? | Example |
|---|---|---|---|
| **Concurrency** | Multiple tasks in progress simultaneously (interleaved) | No | Single-core OS switching tasks |
| **Parallelism** | Multiple tasks executing at the *exact same instant* | Yes | 4 CPU cores each running a thread |
| **Multithreading** | Using multiple threads within a single process | No (but helps) | Java web server with thread-per-request |

**Key insight:** Parallelism is a subset of concurrency. All parallel execution is concurrent, but not all concurrent execution is parallel.

```
Concurrency on 1 core (interleaving):
Core 0: [--A--][--B--][--A--][--B--][--A--]

Parallelism on 2 cores (truly simultaneous):
Core 0: [--A--][--A--][--A--]
Core 1: [--B--][--B--][--B--]
```

### 1.3 Why Concurrency Matters in Modern Systems

Modern hardware has multiple CPU cores that sit idle if only one thread runs. Applications that ignore concurrency leave performance on the table. More importantly:

- **Web servers** handle thousands of simultaneous HTTP requests
- **Database connection pools** serve multiple queries in parallel
- **Streaming pipelines** read, transform, and write data concurrently
- **Event processors** handle multiple event streams simultaneously
- **Background jobs** run without blocking the main request thread

### 1.4 Real-World Production Examples

**Web Server (Tomcat/Jetty):**
Each incoming HTTP request is handled by a dedicated thread from a pool. Without concurrency, requests would queue and users would wait for each other.

```
Request 1 ──► Thread-1 ──► Handler ──► DB Query ──► Response
Request 2 ──► Thread-2 ──► Handler ──► DB Query ──► Response
Request 3 ──► Thread-3 ──► Handler ──► DB Query ──► Response
```

**Database Connection Pool (HikariCP):**
A fixed set of database connections is shared among many threads. Concurrency control ensures no two threads use the same connection simultaneously.

**Event Processing (Kafka Consumer):**
Multiple consumer threads each read from different partitions, processing events in parallel, achieving horizontal throughput scaling.

**Streaming Pipeline (Apache Spark/Flink):**
Data flows through stages (read → filter → aggregate → write), with each stage potentially running on separate threads or processes.

---

## 2. Java Thread Fundamentals

### 2.1 Process vs Thread

| Aspect | Process | Thread |
|---|---|---|
| Memory | Separate address space | Shared heap within process |
| Creation cost | High (fork/exec) | Low (just a stack + registers) |
| Communication | IPC (pipes, sockets, shared memory) | Direct shared memory access |
| Crash isolation | Yes — one process crash doesn't kill others | No — one thread crash can kill the JVM |
| Context switch cost | High | Lower |

A JVM itself is a process. Every Java application runs at least one thread: the `main` thread. The JVM also runs background threads: GC thread, JIT compiler thread, finalizer thread, etc.

### 2.2 Thread Lifecycle

```
               start()
NEW ──────────────────────► RUNNABLE ◄──────────────────┐
                                │                        │
                    scheduler   │   wait()/sleep()/IO    │
                    dispatches  ▼   blocks               │
                           RUNNING ──────────────► BLOCKED/WAITING/TIMED_WAITING
                                │                        │
                    run()       │   notify()/interrupt() │
                    completes   ▼   /timeout             │
                          TERMINATED   ◄─────────────────┘
                                           (only if run() ends)
```

**Thread States (java.lang.Thread.State):**

| State | Description |
|---|---|
| `NEW` | Thread created but `start()` not called yet |
| `RUNNABLE` | Ready to run or currently running on CPU |
| `BLOCKED` | Waiting to acquire a monitor lock (synchronized) |
| `WAITING` | Indefinitely waiting — `wait()`, `join()`, `park()` |
| `TIMED_WAITING` | Waiting with timeout — `sleep(n)`, `wait(n)`, `join(n)` |
| `TERMINATED` | `run()` method has returned or threw an uncaught exception |

### 2.3 Creating Threads

#### Method 1: Extending Thread

```java
public class DownloadTask extends Thread {
    private final String url;

    public DownloadTask(String url) {
        super("downloader-" + url); // named threads help debugging
        this.url = url;
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + " downloading " + url);
        // ... actual download logic
    }
}

// Usage
DownloadTask task = new DownloadTask("https://example.com/file.zip");
task.start(); // do NOT call run() directly — that executes on the current thread!
```

**Pitfall:** Calling `task.run()` instead of `task.start()` executes the code on the *calling* thread, not a new thread. Always call `start()`.

#### Method 2: Implementing Runnable (Preferred)

```java
public class LogProcessor implements Runnable {
    private final String logEntry;

    public LogProcessor(String logEntry) {
        this.logEntry = logEntry;
    }

    @Override
    public void run() {
        // process the log entry
        System.out.println("Processing: " + logEntry);
    }
}

// Usage — with explicit Thread
Thread t = new Thread(new LogProcessor("ERROR: NullPointerException"));
t.start();

// Usage — with lambda (since Runnable is a functional interface)
Thread t2 = new Thread(() -> System.out.println("Processing log entry"));
t2.start();
```

**Why prefer Runnable over Thread extension?**
- Java only allows single inheritance. Extending `Thread` prevents extending other classes.
- Runnable separates the *task* from the *execution mechanism* — you can reuse the same Runnable with thread pools.

#### Method 3: Callable and Future

`Runnable.run()` returns void and cannot throw checked exceptions. `Callable<V>` solves both problems:

```java
import java.util.concurrent.*;

public class PriceCalculator implements Callable<Double> {
    private final String productId;

    public PriceCalculator(String productId) {
        this.productId = productId;
    }

    @Override
    public Double call() throws Exception {
        // simulate calling a pricing service
        Thread.sleep(100);
        return 42.99;
    }
}

// Usage
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Double> future = executor.submit(new PriceCalculator("SKU-123"));

// Do other work here while price is being calculated...

Double price = future.get(); // blocks until result is ready
System.out.println("Price: " + price);
executor.shutdown();
```

### 2.4 Thread Properties

```java
Thread t = new Thread(() -> {});

// Name — appears in thread dumps; always name your threads!
t.setName("payment-processor-1");

// Priority — hint to the OS scheduler (1=MIN, 5=NORM, 10=MAX)
// Do NOT rely on priority for correctness; only for performance hints
t.setPriority(Thread.NORM_PRIORITY);

// Daemon thread — JVM exits when only daemon threads remain
// Use for background housekeeping tasks (logging, monitoring)
t.setDaemon(true);

// Uncaught exception handler — global safety net
t.setUncaughtExceptionHandler((thread, throwable) -> {
    System.err.println(thread.getName() + " died: " + throwable.getMessage());
    // log to monitoring system
});
```

### 2.5 Thread Scheduling and Context Switching

The JVM delegates thread scheduling to the OS. The OS uses a preemptive scheduler — it can suspend a running thread at any moment and give the CPU to another thread. This is called a **context switch**.

**What happens during a context switch:**
1. The OS saves the current thread's registers, program counter, and stack pointer
2. The OS loads the next thread's saved state
3. Execution continues where the next thread left off

**Cost of context switching:**
- Typically 1–10 microseconds
- Cache invalidation: the CPU L1/L2 cache may not contain data for the new thread
- TLB misses: the memory translation lookaside buffer must be repopulated

**Practical implication:** Creating thousands of threads is wasteful. Thread pools amortize creation cost and limit context switching overhead.

---

## 3. Thread Safety

### 3.1 What Is Thread Safety?

A class is **thread-safe** if it behaves correctly when accessed from multiple threads simultaneously, regardless of scheduling or interleaving, without requiring additional synchronization from the caller.

"Correctly" means: it maintains its invariants and produces the correct result regardless of when threads are scheduled.

### 3.2 Race Conditions

A **race condition** occurs when the correctness of a computation depends on the relative timing or interleaving of multiple threads. The outcome "races" against thread scheduling.

**Classic example: Non-atomic increment**

```java
// THREAD UNSAFE — do NOT use in production
public class UnsafeCounter {
    private int count = 0;

    public void increment() {
        count++; // THIS IS NOT ATOMIC — it is 3 operations:
                 // 1. READ count from memory into register
                 // 2. ADD 1 to register
                 // 3. WRITE register back to memory
    }

    public int getCount() {
        return count;
    }
}
```

**What can go wrong with two threads:**

```
Thread A                        Thread B
READ  count → 0
                                READ  count → 0
ADD   1 → 1
                                ADD   1 → 1
WRITE count = 1
                                WRITE count = 1     ← Thread A's increment is LOST

Expected result: count = 2
Actual result:   count = 1
```

This is a **lost update** race condition. With 1000 threads each calling `increment()` 1000 times, you expect 1,000,000 but may get wildly different results every run.

### 3.3 Critical Section

A **critical section** is a block of code that accesses shared mutable state and must not be executed by more than one thread simultaneously.

In the counter example, the entire `count++` operation is a critical section. Only one thread should execute it at a time.

### 3.4 Shared Mutable State

The three dangerous words in concurrency: **shared**, **mutable**, **state**.

- **Shared:** multiple threads can access it
- **Mutable:** it can be changed
- **State:** it persists across calls

If any one of these three is removed, the problem goes away:
- Not shared (thread-local state) → safe
- Not mutable (immutable objects) → safe
- Not state (stateless computation) → safe

### 3.5 Immutability as a Concurrency Strategy

Immutable objects are inherently thread-safe — their state cannot change after construction, so no synchronization is needed.

```java
// IMMUTABLE — thread safe by design
public final class Money {
    private final long amountInCents;
    private final String currency;

    public Money(long amountInCents, String currency) {
        this.amountInCents = amountInCents;
        this.currency = currency;
    }

    // No setters — state cannot change

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(this.amountInCents + other.amountInCents, this.currency); // returns NEW object
    }

    public long getAmountInCents() { return amountInCents; }
    public String getCurrency() { return currency; }
}
```

Rules for immutability:
1. `final` class (prevents subclass from adding mutable state)
2. All fields `final` and `private`
3. No setters
4. If fields reference mutable objects, return defensive copies
5. Initialize fully in constructor

### 3.6 Stateless Objects Are Always Thread-Safe

```java
// STATELESS — thread safe
public class TaxCalculator {
    // no instance fields

    public double calculate(double price, double taxRate) {
        return price * taxRate; // operates only on method parameters
    }
}

// Any number of threads can call calculate() simultaneously — safe!
```

REST controllers in Spring are singletons but safe *only* because they're (should be) stateless. If you add mutable fields to a Spring `@Service` or `@Controller`, you break thread safety.

---

## 4. Synchronization in Java

### 4.1 The `synchronized` Keyword

`synchronized` is Java's built-in mutual exclusion mechanism. It ensures that only one thread at a time executes a synchronized block or method.

Every Java object has an associated **intrinsic lock** (also called a **monitor lock**). `synchronized` acquires this lock before entering the block and releases it when exiting (even if an exception is thrown).

### 4.2 Synchronized Methods

```java
public class SafeCounter {
    private int count = 0;

    // Acquires the lock on 'this' object before executing
    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}
```

**What happens internally:**
1. Thread A calls `increment()` → tries to acquire lock on `this`
2. Thread A acquires lock → executes `count++`
3. Thread B calls `increment()` → tries to acquire lock on `this` → BLOCKED
4. Thread A finishes → releases lock
5. Thread B acquires lock → executes `count++`

**Synchronized method is equivalent to:**
```java
public void increment() {
    synchronized(this) { // acquire lock on 'this'
        count++;
    } // release lock on 'this'
}
```

For `static synchronized` methods, the lock is on the **Class object**, not the instance:
```java
public static synchronized void staticIncrement() {
    // lock is on SafeCounter.class, not on 'this'
    staticCount++;
}
```

### 4.3 Synchronized Blocks

Synchronized blocks give finer-grained control — you choose the lock object and minimize the critical section size.

```java
public class OrderService {
    private final Object orderLock = new Object(); // dedicated lock object
    private final Object inventoryLock = new Object();
    private int orderCount = 0;
    private int inventoryCount = 100;

    public void placeOrder(int quantity) {
        // Lock only what needs protection — minimize contention
        synchronized(orderLock) {
            orderCount++;
        }

        synchronized(inventoryLock) {
            inventoryCount -= quantity;
        }
    }
}
```

**Why use a dedicated lock object instead of `this`?**
- Anyone with a reference to your object can also synchronize on it and accidentally contend with your internal locks.
- A private `final Object lock` is not accessible outside the class.

### 4.4 Intrinsic Locks and Reentrancy

Java's intrinsic locks are **reentrant** — a thread that holds a lock can acquire it again without deadlocking itself.

```java
public class ReentrantExample {
    public synchronized void methodA() {
        System.out.println("In A");
        methodB(); // Thread already holds 'this' lock — reentrant, no deadlock
    }

    public synchronized void methodB() {
        System.out.println("In B");
    }
}
```

The JVM tracks a lock count per thread. Each acquisition increments it, each release decrements it. The lock is released only when the count reaches 0.

### 4.5 The wait/notify Protocol

Synchronized blocks integrate with Java's condition mechanism via `wait()`, `notify()`, and `notifyAll()`:

```java
public class BoundedBuffer<T> {
    private final Queue<T> buffer = new LinkedList<>();
    private final int maxSize;

    public BoundedBuffer(int maxSize) {
        this.maxSize = maxSize;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (buffer.size() == maxSize) { // use WHILE, not if — guard against spurious wakeups
            wait(); // releases lock and suspends this thread
        }
        buffer.add(item);
        notifyAll(); // wake up threads waiting to consume
    }

    public synchronized T take() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait(); // releases lock and suspends this thread
        }
        T item = buffer.poll();
        notifyAll(); // wake up threads waiting to produce
        return item;
    }
}
```

**Rules for wait/notify:**
- Must be called from within a `synchronized` block on the same object
- `wait()` atomically releases the lock and suspends the thread
- When woken by `notify()`, the thread reacquires the lock before returning from `wait()`
- Always use `while` loop, not `if`, to recheck the condition (spurious wakeups exist)
- Prefer `notifyAll()` over `notify()` to avoid missed signals

### 4.6 Performance Impact of Synchronization

Synchronization has costs:
1. **Lock acquisition:** check, CAS, possible kernel call if contested
2. **Memory barriers:** flushes CPU cache, forces reads from main memory
3. **Contention:** threads blocking each other, context switches
4. **Lock convoy:** high contention causes threads to queue up

```java
// BAD — coarse-grained lock on entire method unnecessarily
public synchronized void processOrder(Order order) {
    validate(order);           // pure computation, needs no lock
    double price = calculate(order); // pure computation, needs no lock
    synchronized(this) {       // only this part needs the lock
        orders.add(order);
    }
    sendEmail(order);          // I/O, needs no lock
}

// GOOD — lock only the minimum critical section
public void processOrder(Order order) {
    validate(order);
    double price = calculate(order);
    synchronized(orderLock) {
        orders.add(order);
    }
    sendEmail(order);
}
```

**Lock contention causes:**
- Thread A holds lock, Thread B waits → context switch (expensive)
- More threads competing → more context switches → **throughput decreases**

---

## 5. The Volatile Keyword

### 5.1 The Memory Visibility Problem

Modern CPUs have multiple cache levels (L1, L2, L3). Each CPU core has its own L1/L2 cache. When Thread A writes a variable on Core 0, the write goes to Core 0's cache. Thread B on Core 1 may still read the **stale value** from its own cache.

```
Core 0                    Core 1
┌──────────────┐          ┌──────────────┐
│  L1 Cache    │          │  L1 Cache    │
│  flag = true │          │  flag = false│  ← stale!
└──────┬───────┘          └──────┬───────┘
       │                         │
       └────────────┬────────────┘
               Main Memory
               flag = true   ← Core 1 hasn't seen this yet
```

This is the **memory visibility problem**: a write by one thread is not guaranteed to be visible to other threads without synchronization.

### 5.2 Instruction Reordering

Both the JVM (JIT compiler) and CPU can **reorder instructions** for performance, as long as the reordering is invisible to a *single-threaded* program. In multithreaded programs, this breaks assumptions.

```java
// NOT volatile — the compiler/CPU may reorder these writes
class InitializationRace {
    private boolean initialized = false;
    private int value = 0;

    public void init() {
        value = 42;          // write 1
        initialized = true;  // write 2 — CPU may reorder this BEFORE write 1!
    }

    public void use() {
        if (initialized) {   // sees initialized = true
            use(value);      // but value might still be 0!
        }
    }
}
```

### 5.3 The `volatile` Keyword

Declaring a field `volatile` provides two guarantees:
1. **Visibility:** A write to a volatile variable is immediately visible to all threads
2. **Ordering:** Writes to volatile establish a happens-before relationship; prevents reordering across the volatile access

```java
class CorrectInitialization {
    private volatile boolean initialized = false;
    private int value = 0;

    public void init() {
        value = 42;          // guaranteed to happen BEFORE volatile write
        initialized = true;  // volatile write — acts as a memory barrier
    }

    public void use() {
        if (initialized) {   // volatile read — establishes happens-before
            use(value);      // guaranteed to see value = 42
        }
    }
}
```

### 5.4 Classic Volatile Use Case: Stop Flag

```java
public class BackgroundWorker implements Runnable {
    private volatile boolean running = true; // must be volatile!

    public void stop() {
        running = false; // write on caller's thread
    }

    @Override
    public void run() {
        while (running) { // read on worker thread — must see the write immediately
            doWork();
        }
        System.out.println("Worker stopped");
    }
}

// Usage
BackgroundWorker worker = new BackgroundWorker();
Thread t = new Thread(worker);
t.start();

Thread.sleep(5000); // let it run for 5 seconds
worker.stop(); // signals the worker to stop
```

Without `volatile`, the JIT compiler might cache `running` in a register and the worker thread *never* sees the update — running forever.

### 5.5 When Volatile Is NOT Enough

Volatile guarantees visibility but **not atomicity**. It does not protect compound actions (read-modify-write).

```java
// BROKEN — volatile does NOT fix this race condition
public class BrokenCounter {
    private volatile int count = 0;

    public void increment() {
        count++; // still 3 non-atomic operations: read, add, write
                 // volatile only ensures visibility of each individual read/write
                 // the sequence of read-then-write is still not atomic
    }
}
```

**Volatile is correct when:**
- Exactly one thread writes the variable (or writes are independent)
- Other threads only read, or write independently
- The variable is not part of a compound action (check-then-act, read-modify-write)

**Volatile is NOT enough when:**
- Multiple threads write the same variable based on its current value (`count++`)
- You need atomicity of multiple variables together (balance transfer)

### 5.6 Happens-Before Relationship

The Java Memory Model (JMM) defines **happens-before** as the guarantee that one action's effects are visible to another action. Key happens-before rules:

| Rule | Description |
|---|---|
| Program order | Each action in a thread happens-before later actions in that thread |
| Monitor lock | `unlock()` of a lock happens-before any subsequent `lock()` of that lock |
| Volatile write | A volatile write happens-before any subsequent volatile read of that field |
| Thread start | `Thread.start()` happens-before any action in the started thread |
| Thread join | All actions in a thread happen-before `Thread.join()` returns |
| Transitivity | If A hb B and B hb C, then A hb C |

```java
int x = 0;
volatile boolean flag = false;

// Thread A
x = 1;          // write to x (non-volatile)
flag = true;    // volatile write — creates happens-before edge

// Thread B
if (flag) {     // volatile read — sees the happens-before from Thread A's volatile write
    // x = 1 is GUARANTEED here because:
    // - write to x hb volatile write to flag (program order)
    // - volatile write to flag hb volatile read of flag
    // - by transitivity: write to x hb this point in Thread B
    System.out.println(x); // guaranteed to print 1
}
```

This is the **volatile piggyback** technique: by making `flag` volatile, we pull along the visibility of `x` even though `x` itself is not volatile.

---

## Summary: Part 1

| Topic | Key Takeaway |
|---|---|
| Concurrency vs Parallelism | Concurrency = overlapping progress; Parallelism = simultaneous execution |
| Thread creation | Prefer `Runnable`/`Callable`; always name your threads |
| Thread safety | Eliminate: shared state, mutable state, or unsynchronized access |
| Synchronized | Uses intrinsic locks; reentrant; minimize critical section size |
| Volatile | Visibility + ordering, NOT atomicity; one writer, multiple readers |

**Continue reading:** [Part 2 — JMM, Atomic Variables, Locks, Thread Pools, CompletableFuture](./02-advanced-concurrency.md)
