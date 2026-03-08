# Java Concurrency — Part 2: Advanced Mechanisms
## Sections 6–10: JMM · Atomics · Locks · Thread Pools · CompletableFuture

---

## 6. Java Memory Model (JMM)

### 6.1 The Problem JMM Solves

Without a formal memory model, the behavior of concurrent Java programs on different hardware architectures (x86, ARM, SPARC) would be unpredictable. The JMM specifies *exactly* what memory visibility guarantees Java programmers can rely on.

### 6.2 Working Memory vs Main Memory

The JMM defines an abstract memory architecture:

```
┌─────────────────────────────────────────────────────┐
│                    Main Memory (Heap)               │
│   ┌────────────┐  ┌────────────┐  ┌────────────┐    │
│   │  Object A  │  │  Object B  │  │  int x=5   │    │
│   └────────────┘  └────────────┘  └────────────┘    │
└─────────────────────────────────────────────────────┘
         ▲                  ▲
         │                  │
┌────────┴───────┐  ┌───────┴────────┐
│  Thread 1      │  │  Thread 2      │
│  Working Mem   │  │  Working Mem   │
│  (CPU cache /  │  │  (CPU cache /  │
│   registers)   │  │   registers)   │
│  x = 5 (copy)  │  │  x = 5 (copy)  │
└────────────────┘  └────────────────┘
```

Each thread works on a local copy of variables. Without synchronization, changes in one thread's working memory may not propagate to main memory or to other threads' working memories.

### 6.3 Atomicity in the JMM

The JMM guarantees that reads and writes of the following types are **atomic** (indivisible):
- Reads and writes of `byte`, `char`, `short`, `int`, `float`, `boolean` (32-bit or smaller)
- Reads and writes of **object references**
- Reads and writes of `volatile long` and `volatile double` (otherwise 64-bit reads/writes may be non-atomic on 32-bit JVMs)

Note: `long` and `double` (64-bit) without `volatile` can be subject to **word tearing** on 32-bit JVMs — the two 32-bit halves can be written by different threads.

### 6.4 Instruction Reordering — Detailed

There are three sources of reordering:

1. **Compiler reordering** — JIT moves instructions for optimization
2. **CPU reordering** — Out-of-order execution units in modern CPUs
3. **Store buffer reordering** — CPU writes to a store buffer before flushing to cache

```java
// Original code order
a = 1;    // statement 1
b = 2;    // statement 2
c = a + b; // statement 3

// Compiler/CPU may execute as:
b = 2;    // reordered — safe for single-threaded, not for multi-threaded
a = 1;
c = a + b;
```

**Memory barriers (fences)** prevent specific types of reordering:
- `StoreStore` barrier: no store reordered before this point
- `LoadLoad` barrier: no load reordered before this point
- `StoreLoad` barrier: the most expensive — both stores and loads cannot pass this point
- `LoadStore` barrier: prevents loads from being reordered after stores

`volatile` inserts `StoreLoad` barriers — the most conservative (and expensive) of the four.

### 6.5 Final Fields and Safe Publication

`final` fields have a special JMM guarantee: once an object's constructor finishes, any thread that obtains a reference to that object sees the `final` fields as initialized — without synchronization.

```java
// Safe publication via final fields
public class SafePoint {
    public final int x;
    public final int y;

    public SafePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

// Thread A: constructs and publishes
SafePoint p = new SafePoint(3, 5);
sharedReference = p; // publish

// Thread B: reads (even without synchronization)
SafePoint local = sharedReference;
if (local != null) {
    // local.x and local.y are GUARANTEED to be visible
    // because they are final and the constructor completed
    System.out.println(local.x + ", " + local.y); // always 3, 5
}
```

**WARNING:** Without `final` (or other synchronization), even the constructor body can be reordered, making it possible for another thread to see partially-constructed objects.

---

## 7. Atomic Variables

### 7.1 The Problem with synchronized for Counters

Using `synchronized` for a simple counter is correct but expensive under high contention. Every increment requires:
- Lock acquisition (possible kernel call if contested)
- Memory barrier
- Lock release

### 7.2 Compare-And-Swap (CAS)

Modern CPUs provide a hardware instruction called **Compare-And-Swap (CAS)**:

```
CAS(memoryAddress, expectedValue, newValue):
  atomically:
    if (*memoryAddress == expectedValue):
        *memoryAddress = newValue
        return true
    else:
        return false
```

CAS is **lock-free** — it never blocks a thread. Instead, it retries when contended. This is the **optimistic locking** approach.

### 7.3 AtomicInteger

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet(); // uses CAS internally — no lock acquired
    }

    public int getCount() {
        return count.get();
    }

    // Example: atomic check-and-update
    public boolean setIfZero(int value) {
        return count.compareAndSet(0, value); // returns true if swap happened
    }
}
```

**CAS loop implementation (what incrementAndGet does internally):**
```java
// Conceptual implementation of incrementAndGet
public int incrementAndGet() {
    while (true) {
        int current = get();           // read current value
        int next = current + 1;       // compute new value
        if (compareAndSet(current, next)) { // if still 'current', set to 'next'
            return next;
        }
        // If CAS fails, another thread changed the value — retry
    }
}
```

Under low contention: CAS almost always succeeds on the first try — extremely fast.
Under high contention: Many threads spin retrying — can cause CPU waste (**ABA problem**, spinning).

### 7.4 All Atomic Classes

| Class | Use Case |
|---|---|
| `AtomicBoolean` | Atomic flag (stop signals, feature toggles) |
| `AtomicInteger` | Atomic int counter, ID generation |
| `AtomicLong` | Atomic long counter, high-volume metrics |
| `AtomicReference<V>` | Atomic object reference swaps |
| `AtomicIntegerArray` | Array of atomically updated ints |
| `AtomicLongArray` | Array of atomically updated longs |
| `AtomicReferenceArray<V>` | Array of atomically updated references |
| `AtomicIntegerFieldUpdater` | Atomically update a volatile int field of existing class |
| `LongAdder` | High-contention counter — much faster than AtomicLong under contention |
| `LongAccumulator` | High-contention accumulator with custom function |

### 7.5 AtomicReference — Atomic State Transitions

```java
import java.util.concurrent.atomic.AtomicReference;

public enum OrderState { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

public class Order {
    private final AtomicReference<OrderState> state =
        new AtomicReference<>(OrderState.PENDING);

    // Atomic state transition — only succeeds if current state is as expected
    public boolean confirm() {
        return state.compareAndSet(OrderState.PENDING, OrderState.CONFIRMED);
    }

    public boolean ship() {
        return state.compareAndSet(OrderState.CONFIRMED, OrderState.SHIPPED);
    }

    public boolean cancel() {
        // Can cancel from PENDING or CONFIRMED but not from SHIPPED
        OrderState current = state.get();
        if (current == OrderState.PENDING || current == OrderState.CONFIRMED) {
            return state.compareAndSet(current, OrderState.CANCELLED);
        }
        return false;
    }
}
```

### 7.6 LongAdder — High-Throughput Counters

`LongAdder` is designed for high-contention scenarios. Instead of a single cell, it maintains an array of cells — each thread updates its own cell, reducing CAS contention.

```
LongAdder internals (simplified):
┌──────────────────────────────────────────────────────┐
│  base cell + [cell-0][cell-1][cell-2][cell-3] ...   │
│                                                      │
│  Thread 1 → updates cell-0                          │
│  Thread 2 → updates cell-1                          │
│  Thread 3 → updates cell-2                          │
│  Thread 4 → updates cell-3                          │
│                                                      │
│  sum() = base + cell-0 + cell-1 + cell-2 + cell-3  │
└──────────────────────────────────────────────────────┘
```

```java
import java.util.concurrent.atomic.LongAdder;

// For high-frequency metrics (e.g., request counter)
public class Metrics {
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    public void recordRequest() { requestCount.increment(); }
    public void recordError() { errorCount.increment(); }

    public long getRequestCount() { return requestCount.sum(); } // sum() is eventually consistent
    public long getErrorCount() { return errorCount.sum(); }
}
```

**Performance comparison (high contention, 16 threads):**
- `synchronized int++`: ~50M ops/sec
- `AtomicLong.incrementAndGet()`: ~100M ops/sec
- `LongAdder.increment()`: ~500M ops/sec

Use `LongAdder` when you need a counter and don't need to read the current value as frequently as you update it.

---

## 8. Locks Framework (`java.util.concurrent.locks`)

### 8.1 Why Not Just Use `synchronized`?

`synchronized` has limitations:
- Cannot interrupt a thread waiting for a lock
- Cannot try to acquire a lock without blocking
- Cannot implement non-reentrant or fair locking
- No separate read and write locks

`java.util.concurrent.locks` solves all of these.

### 8.2 ReentrantLock

```java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BankAccount {
    private final Lock lock = new ReentrantLock();
    private double balance;

    public BankAccount(double initialBalance) {
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        lock.lock();
        try {
            balance += amount;
        } finally {
            lock.unlock(); // ALWAYS unlock in finally — even if exception thrown
        }
    }

    // tryLock — non-blocking acquisition attempt
    public boolean tryDeposit(double amount) {
        if (lock.tryLock()) { // returns immediately
            try {
                balance += amount;
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false; // couldn't acquire lock — try later
    }

    // tryLock with timeout
    public boolean tryDepositWithTimeout(double amount, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (lock.tryLock(timeout, unit)) {
            try {
                balance += amount;
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    // lockInterruptibly — respects thread interruption
    public void depositInterruptibly(double amount) throws InterruptedException {
        lock.lockInterruptibly(); // throws InterruptedException if interrupted while waiting
        try {
            balance += amount;
        } finally {
            lock.unlock();
        }
    }
}
```

### 8.3 Fairness Policy

```java
// Fair lock — threads acquire in FIFO order (waiting order)
Lock fairLock = new ReentrantLock(true);

// Unfair lock (default) — threads may "barge" in front of waiting threads
// This is faster because it avoids context switches, but can cause starvation
Lock unfairLock = new ReentrantLock(false); // default
```

| Policy | Throughput | Starvation Risk | Latency |
|---|---|---|---|
| Unfair (default) | Higher | Possible | Lower average |
| Fair | Lower | None (FIFO) | More predictable |

Production guideline: Use unfair locking (default) unless you have a specific starvation problem. Fair locking has 5–10x lower throughput under contention.

### 8.4 Condition Variables (Advanced wait/notify)

`ReentrantLock` can create multiple `Condition` objects — each is a separate wait queue. This is more powerful than `wait()/notifyAll()` which operates on a single queue.

```java
import java.util.concurrent.locks.*;

public class ProductionBuffer<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();   // producers wait here
    private final Condition notEmpty = lock.newCondition();  // consumers wait here
    private final Object[] items;
    private int putIndex, takeIndex, count;

    public ProductionBuffer(int capacity) {
        items = new Object[capacity];
    }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) {
                notFull.await(); // only wakes up producers — not all waiting threads!
            }
            items[putIndex] = item;
            putIndex = (putIndex + 1) % items.length;
            count++;
            notEmpty.signal(); // wake one consumer
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            T item = (T) items[takeIndex];
            takeIndex = (takeIndex + 1) % items.length;
            count--;
            notFull.signal(); // wake one producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

This is exactly how `ArrayBlockingQueue` is implemented in the JDK.

### 8.5 ReadWriteLock

Many data structures are read frequently but written infrequently. `ReadWriteLock` allows:
- **Multiple readers** simultaneously (when no writer holds the lock)
- **Exclusive write access** (blocks all readers and other writers)

```java
import java.util.concurrent.locks.*;

public class CachedUserService {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Map<String, User> cache = new HashMap<>();

    public User getUser(String id) {
        readLock.lock(); // multiple threads can hold readLock simultaneously
        try {
            return cache.get(id);
        } finally {
            readLock.unlock();
        }
    }

    public void updateUser(String id, User user) {
        writeLock.lock(); // exclusive — all readers and writers blocked
        try {
            cache.put(id, user);
        } finally {
            writeLock.unlock();
        }
    }
}
```

**When to use ReadWriteLock:**
- Read-to-write ratio is high (10:1 or more)
- Read operations take non-trivial time
- Cache, configuration store, reference data

**Pitfall:** Under write-heavy workloads, ReadWriteLock can be *slower* than a plain lock due to coordination overhead.

### 8.6 StampedLock (Java 8+)

`StampedLock` is a more advanced lock that adds an **optimistic read mode** — a reader doesn't acquire the lock at all; it just validates afterward that no write occurred.

```java
import java.util.concurrent.locks.StampedLock;

public class Point {
    private final StampedLock lock = new StampedLock();
    private double x, y;

    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // Optimistic read — try without acquiring lock
    public double distanceFromOrigin() {
        long stamp = lock.tryOptimisticRead(); // not a real lock — just a stamp
        double currentX = x, currentY = y;    // read values optimistically

        if (!lock.validate(stamp)) { // if a write happened since our stamp, retry
            stamp = lock.readLock(); // fall back to actual read lock
            try {
                currentX = x;
                currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
}
```

**StampedLock characteristics:**
- Not reentrant (unlike ReentrantLock — calling write lock when you hold read lock = deadlock)
- Optimistic reads are essentially free under low write contention
- Use when: frequent reads, rare writes, low-latency reads required

---

## 9. Thread Pools and Executors

### 9.1 Why Thread Pools?

Creating a new `Thread` for each task is expensive:
- Thread creation: ~1ms (OS allocates stack, registers kernel thread)
- Thread destruction: overhead on GC and OS
- Uncontrolled thread count leads to memory exhaustion (each thread stack: 512KB–2MB)

Thread pools solve this by:
- Pre-creating a set of worker threads
- Reusing threads across tasks
- Bounding the number of concurrent threads

### 9.2 The Executor Framework

```
Executor (submit tasks)
    └── ExecutorService (lifecycle management: shutdown, submit with result)
            └── ScheduledExecutorService (schedule tasks at intervals)
                    └── AbstractExecutorService
                            └── ThreadPoolExecutor (the real implementation)
                            └── ForkJoinPool (work-stealing)
```

### 9.3 ThreadPoolExecutor — The Core

All `Executors.*` factory methods internally create a `ThreadPoolExecutor`. Understanding its parameters is critical for production tuning.

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                              // corePoolSize: threads always kept alive
    8,                              // maximumPoolSize: max threads when queue is full
    60L, TimeUnit.SECONDS,          // keepAliveTime: idle threads above core survive this long
    new LinkedBlockingQueue<>(100), // workQueue: tasks wait here when all cores busy
    new ThreadFactory() {           // how to create threads (name them!)
        private final AtomicInteger id = new AtomicInteger(0);
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "payment-worker-" + id.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy when queue is full
);
```

**Thread lifecycle in ThreadPoolExecutor:**

```
Task arrives
    │
    ├─► If < corePoolSize threads running: create new thread (even if idle threads exist)
    │
    ├─► If >= corePoolSize threads running: put in workQueue
    │
    ├─► If workQueue is FULL and < maximumPoolSize threads: create new (non-core) thread
    │
    └─► If workQueue is FULL and >= maximumPoolSize threads: REJECTION POLICY
```

### 9.4 Rejection Policies

| Policy | Behavior | Use When |
|---|---|---|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` | Caller must handle overflow |
| `CallerRunsPolicy` | Caller thread executes the task | Natural back-pressure — slows the producer |
| `DiscardPolicy` | Silently drops the task | Fire-and-forget, non-critical tasks |
| `DiscardOldestPolicy` | Drops the oldest queued task, retries | Freshest data matters most (sensor data) |
| Custom | Implement `RejectedExecutionHandler` | Log, retry, send to DLQ |

```java
// Production: custom rejection policy with monitoring
executor.setRejectedExecutionHandler((r, exec) -> {
    metrics.increment("executor.rejections");
    log.warn("Task rejected, queue size: {}", exec.getQueue().size());
    // optionally: send to overflow queue, or throw with context
    throw new RejectedExecutionException("Thread pool overloaded");
});
```

### 9.5 Types of Executor Pools

```java
// Fixed thread pool — predictable resource usage
ExecutorService fixed = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors(), // common choice for CPU-bound tasks
    r -> new Thread(r, "cpu-worker")
);
// Backed by: LinkedBlockingQueue (unbounded!) — DANGER for production

// Cached thread pool — elastic, but unconstrained thread creation
ExecutorService cached = Executors.newCachedThreadPool();
// Backed by: SynchronousQueue — tasks are handed off directly to threads
// WARNING: Can create thousands of threads under load → OOM

// Single thread executor — serial execution, preserves task order
ExecutorService single = Executors.newSingleThreadExecutor(
    r -> new Thread(r, "event-processor")
);

// Scheduled executor — cron-like scheduling
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);
// Schedule with fixed delay (wait for completion, then delay)
scheduled.scheduleWithFixedDelay(() -> cleanUpExpiredSessions(), 0, 30, TimeUnit.SECONDS);
// Schedule at fixed rate (fire every N seconds regardless of duration)
scheduled.scheduleAtFixedRate(() -> reportMetrics(), 0, 10, TimeUnit.SECONDS);

// Work-stealing pool (ForkJoinPool under the hood)
ExecutorService workStealing = Executors.newWorkStealingPool();
```

**Production rule:** Never use `Executors.newFixedThreadPool()` or `Executors.newCachedThreadPool()` directly in production — the unbounded queue or unbounded threads are dangerous. Always create `ThreadPoolExecutor` directly with bounded queues.

### 9.6 Sizing Thread Pools

**For CPU-bound tasks (image processing, crypto, compression):**
```
threads = number of CPU cores (N)
// No benefit from more — extra threads just cause context switching
int cpuBound = Runtime.getRuntime().availableProcessors();
```

**For I/O-bound tasks (HTTP calls, DB queries):**
```
threads = N * (1 + wait_time / service_time)
// If average I/O wait = 900ms and service time = 100ms:
// threads = 8 * (1 + 9) = 80
// A core can serve 10 I/O tasks concurrently
```

**Little's Law for sizing:** `throughput = concurrency / latency`
- If latency = 100ms and you need 1000 req/sec: you need 100 concurrent threads

### 9.7 Graceful Shutdown

```java
// Production shutdown pattern
public void shutdown(ExecutorService executor) {
    executor.shutdown(); // stop accepting new tasks
    try {
        // Wait up to 30 seconds for running tasks to complete
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow(); // forcibly cancel remaining tasks
            // Wait again for tasks to respond to cancellation
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.error("Executor did not terminate cleanly");
            }
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt(); // restore interrupt flag
    }
}
```

---

## 10. Future and CompletableFuture

### 10.1 The Future Interface

`Future<V>` represents a computation that may not have completed yet.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

// Submit task — returns immediately
Future<UserProfile> profileFuture = executor.submit(() -> fetchUserProfile(userId));
Future<List<Order>> ordersFuture = executor.submit(() -> fetchOrders(userId));

// Do other work...
enrichRequestContext(request);

// Now block for results (if not yet complete)
UserProfile profile = profileFuture.get(); // blocks until done or throws
List<Order> orders = ordersFuture.get(5, TimeUnit.SECONDS); // timeout version
```

**Future limitations:**
- `get()` is blocking — you must wait
- Cannot chain or compose futures
- No callback mechanism
- No way to manually complete a future

### 10.2 CompletableFuture (Java 8+)

`CompletableFuture` solves all Future limitations. It is a *monad* for asynchronous computation.

#### Basic Async Execution

```java
CompletableFuture<UserProfile> future = CompletableFuture.supplyAsync(
    () -> fetchUserProfile(userId),   // task
    executor                          // always provide explicit executor!
);
// Using the common pool (default) is dangerous in production — shared with ForkJoinPool
```

#### Transforming Results

```java
CompletableFuture<String> greeting = CompletableFuture
    .supplyAsync(() -> fetchUser(userId))      // CompletableFuture<User>
    .thenApply(user -> user.getName())         // CompletableFuture<String> — transform result
    .thenApply(name -> "Hello, " + name);      // chain more transforms
```

#### Running Side Effects

```java
CompletableFuture.supplyAsync(() -> processOrder(orderId))
    .thenAccept(order -> sendConfirmationEmail(order)) // consumes result, returns void
    .thenRun(() -> metrics.increment("orders.processed")); // no result needed
```

#### Chaining Async Steps (flatMap)

```java
// thenCompose = flatMap — avoid nested CompletableFuture<CompletableFuture<T>>
CompletableFuture<PaymentResult> pipeline = CompletableFuture
    .supplyAsync(() -> validateOrder(orderId))
    .thenCompose(order -> processPayment(order))  // each step is async
    .thenCompose(payment -> fulfillOrder(payment));
```

#### Combining Two Futures

```java
// thenCombine — wait for BOTH, combine results
CompletableFuture<UserProfile> profileFuture = fetchProfileAsync(userId);
CompletableFuture<CreditScore> creditFuture = fetchCreditAsync(userId);

CompletableFuture<LoanDecision> decision = profileFuture.thenCombine(
    creditFuture,
    (profile, credit) -> makeLoanDecision(profile, credit)
);
```

#### Waiting for Any or All

```java
// allOf — wait for ALL to complete
CompletableFuture<Void> all = CompletableFuture.allOf(
    sendEmailAsync(user),
    sendSMSAsync(user),
    logAuditAsync(user)
);
all.join(); // blocks until all three complete

// anyOf — complete when FIRST finishes (useful for redundant calls)
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    queryPrimary(key),
    queryReplica1(key),
    queryReplica2(key)
);
String value = (String) fastest.join();
```

#### Error Handling

```java
CompletableFuture<UserProfile> resilient = CompletableFuture
    .supplyAsync(() -> fetchUserFromDB(userId))
    .exceptionally(ex -> {
        log.warn("DB fetch failed for {}: {}", userId, ex.getMessage());
        return UserProfile.anonymous(); // fallback
    });

// handle — receives both result (or null on exception) and exception (or null on success)
CompletableFuture<UserProfile> handled = CompletableFuture
    .supplyAsync(() -> fetchUserFromDB(userId))
    .handle((profile, ex) -> {
        if (ex != null) {
            metrics.increment("db.errors");
            return UserProfile.anonymous();
        }
        return profile;
    });

// whenComplete — side effect on completion (both success and failure)
CompletableFuture
    .supplyAsync(() -> callExternalAPI())
    .whenComplete((result, ex) -> {
        duration = System.currentTimeMillis() - start;
        metrics.record("api.call.duration", duration);
    });
```

### 10.3 Real-World Example: API Aggregation

A product detail page needs data from 3 microservices simultaneously:

```java
public class ProductPageService {
    private final ProductClient productClient;
    private final ReviewClient reviewClient;
    private final InventoryClient inventoryClient;
    private final ExecutorService ioExecutor;

    public ProductPage buildProductPage(String productId) {
        // Fire all 3 requests in parallel
        CompletableFuture<Product> productFuture = CompletableFuture
            .supplyAsync(() -> productClient.getProduct(productId), ioExecutor)
            .orTimeout(2, TimeUnit.SECONDS) // Java 9+ timeout
            .exceptionally(ex -> Product.unavailable(productId));

        CompletableFuture<List<Review>> reviewsFuture = CompletableFuture
            .supplyAsync(() -> reviewClient.getReviews(productId), ioExecutor)
            .orTimeout(1, TimeUnit.SECONDS)
            .exceptionally(ex -> Collections.emptyList()); // degrade gracefully

        CompletableFuture<InventoryStatus> inventoryFuture = CompletableFuture
            .supplyAsync(() -> inventoryClient.getStatus(productId), ioExecutor)
            .orTimeout(1, TimeUnit.SECONDS)
            .exceptionally(ex -> InventoryStatus.UNKNOWN);

        // Wait for all and combine — total time = max(p, r, i), not p+r+i
        return CompletableFuture.allOf(productFuture, reviewsFuture, inventoryFuture)
            .thenApply(v -> new ProductPage(
                productFuture.join(),     // join() is safe here — already complete
                reviewsFuture.join(),
                inventoryFuture.join()
            ))
            .join();
    }
}
```

**Sequential time (if done one-after-another):** 500ms + 300ms + 200ms = 1000ms
**Parallel time:** max(500ms, 300ms, 200ms) = 500ms — **2x faster**

### 10.4 Async with Thread Pool — Critical Production Rule

```java
// WRONG — uses ForkJoinPool.commonPool() shared with parallel streams
CompletableFuture.supplyAsync(() -> blockingDbCall());

// RIGHT — dedicated I/O thread pool, isolated from other work
ExecutorService ioPool = new ThreadPoolExecutor(10, 100, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(500),
    r -> new Thread(r, "io-async"));

CompletableFuture.supplyAsync(() -> blockingDbCall(), ioPool);
```

The common pool has threads = CPU cores. If you saturate it with blocking I/O calls, all parallel stream operations in the application stall.

---

## Summary: Part 2

| Topic | Key Takeaway |
|---|---|
| JMM | Defines visibility rules; always reason about happens-before |
| Atomics | Lock-free via CAS; use LongAdder for high-contention counters |
| ReentrantLock | More flexible than synchronized; always unlock in finally |
| ReadWriteLock | Multiple concurrent readers; one exclusive writer |
| StampedLock | Optimistic reads — nearly free under low write contention |
| Thread Pools | Never create unbounded pools; size by CPU/IO ratio |
| CompletableFuture | Compose async workflows; always provide explicit executor |

**Continue reading:** [Part 3 — Concurrent Collections, Synchronization Utilities, Fork/Join, Deadlocks](./03-collections-and-patterns.md)
