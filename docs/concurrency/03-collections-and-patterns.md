# Java Concurrency — Part 3: Collections, Utilities & Deadlocks
## Sections 11–15: Concurrent Collections · Producer-Consumer · Synchronization Utils · Fork-Join · Deadlocks

---

## 11. Concurrent Collections

### 11.1 Why Not Just Use synchronized Collections?

`Collections.synchronizedMap(map)` wraps every method in `synchronized(this)`. This means:
- Only one thread can access the map at a time — even two *readers* block each other
- Compound operations (check-then-act) are still unsafe without external locking
- Very high contention under load

`java.util.concurrent` provides purpose-built thread-safe collections with much better performance.

### 11.2 ConcurrentHashMap

The most important concurrent collection. Uses **lock striping** (Java 7) and **CAS + synchronized on individual bins** (Java 8+) to allow highly concurrent access.

```
Java 8 ConcurrentHashMap internals:
┌─────────────────────────────────────────────┐
│              Array of bins (nodes)          │
│  [bin-0][bin-1][bin-2]...[bin-n]            │
│     │                                       │
│  Each bin is the head of a linked list      │
│  (or a red-black tree when bin size > 8)   │
│                                             │
│  Locking: synchronized on the FIRST node   │
│  of each bin — different bins lock          │
│  independently (high concurrency)           │
└─────────────────────────────────────────────┘
```

**Default concurrency level:** Effectively the number of bins (16 initially, grows as map resizes). 16 threads can write to different bins simultaneously without contention.

```java
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    // Thread-safe individual operations
    public void addSession(String token, Session session) {
        sessions.put(token, session);
    }

    // Atomic check-and-insert (no external sync needed)
    public Session getOrCreate(String token) {
        return sessions.computeIfAbsent(token, k -> new Session(k)); // atomic!
    }

    // Atomic update
    public void updateLastAccess(String token) {
        sessions.computeIfPresent(token, (k, session) -> {
            session.setLastAccess(System.currentTimeMillis());
            return session;
        });
    }

    // Atomic remove if condition met
    public void expireIfOld(String token, long maxAgeMs) {
        sessions.computeIfPresent(token, (k, session) -> {
            long age = System.currentTimeMillis() - session.getLastAccess();
            return age > maxAgeMs ? null : session; // returning null removes the entry!
        });
    }

    // Thread-safe bulk operations (Java 8)
    public long countActiveSessions() {
        return sessions.reduceValuesToLong(
            1,                              // parallelism threshold
            session -> session.isActive() ? 1L : 0L,
            0L,
            Long::sum
        );
    }
}
```

**ConcurrentHashMap vs synchronizedMap:**

| Operation | synchronizedMap | ConcurrentHashMap |
|---|---|---|
| `get()` under contention | Blocks all other gets | Lockless (volatile read) |
| `put()` under contention | Blocks everything | Locks only affected bin |
| `size()` | Accurate | Approximate (summing bin counters) |
| Iteration | Must hold lock (prevent ConcurrentModificationException) | Weakly consistent — safe without lock |
| Null keys/values | Allowed | NOT allowed |
| Compound operations | Unsafe without external lock | Atomic via compute methods |

**Important limitation:** `ConcurrentHashMap` does NOT support null keys or null values. If you need null support, use `Collections.synchronizedMap(new HashMap<>())` with external locking.

### 11.3 CopyOnWriteArrayList

On every write (add/remove/set), a **new copy** of the underlying array is created. Reads access the current snapshot without locking.

```
Read (no lock):
Thread A reads → [1, 2, 3, 4]  ← snapshot at time of read

Write (full copy):
Thread B adds 5:
  old array → [1, 2, 3, 4]
  new array → [1, 2, 3, 4, 5]
  reference atomically updated to new array

Thread A continues reading [1, 2, 3, 4] — it doesn't see 5 (that's OK, it has a consistent snapshot)
```

```java
import java.util.concurrent.CopyOnWriteArrayList;

// Perfect for: event listener registries (few writes, many reads)
public class EventBus {
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(EventListener listener) {
        listeners.add(listener); // creates new array copy — expensive but rare
    }

    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(Event event) {
        // Iteration is SAFE and LOCK-FREE — iterates over the snapshot at publish time
        // Even if subscribers add/remove during iteration, this loop is unaffected
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
```

**When to use CopyOnWriteArrayList:**
- Reads vastly outnumber writes (10:1 or more)
- List is small (copy cost is proportional to size)
- Iteration must not throw `ConcurrentModificationException`

**When NOT to use:**
- Frequent writes — O(n) copy cost on every write
- Large lists — memory pressure from constant copying

### 11.4 BlockingQueue Implementations

`BlockingQueue` is the backbone of producer-consumer pipelines. It blocks the producer when full and the consumer when empty.

```java
BlockingQueue<Task> queue;

// ArrayBlockingQueue — bounded, backed by array, single lock
queue = new ArrayBlockingQueue<>(1000);

// LinkedBlockingQueue — optionally bounded, two locks (head/tail)
// Higher throughput than ArrayBlockingQueue under high contention
queue = new LinkedBlockingQueue<>(10000); // bounded
queue = new LinkedBlockingQueue<>(); // unbounded — dangerous in production!

// PriorityBlockingQueue — unbounded, orders by priority
queue = new PriorityBlockingQueue<>(100, Comparator.comparing(Task::getPriority));

// DelayQueue — elements only available after delay expires
// Perfect for: retry with delay, scheduled task execution
queue = new DelayQueue<>();

// SynchronousQueue — no buffer at all; producer hands off directly to consumer
// Used in: Executors.newCachedThreadPool() — each thread is handed work directly
queue = new SynchronousQueue<>();
```

**BlockingQueue API:**

| Method | On Full Queue | On Empty Queue |
|---|---|---|
| `put(e)` | Blocks until space available | N/A |
| `offer(e)` | Returns `false` immediately | N/A |
| `offer(e, t, unit)` | Blocks up to timeout | N/A |
| `take()` | N/A | Blocks until element available |
| `poll()` | N/A | Returns `null` immediately |
| `poll(t, unit)` | N/A | Blocks up to timeout |
| `peek()` | N/A | Returns `null` immediately (no remove) |

### 11.5 ConcurrentLinkedQueue

Non-blocking, lock-free queue based on CAS. Useful when you don't need blocking semantics.

```java
import java.util.concurrent.ConcurrentLinkedQueue;

// Work queue for a custom thread pool
ConcurrentLinkedQueue<Task> workQueue = new ConcurrentLinkedQueue<>();

// Thread-safe operations, no blocking
workQueue.offer(new Task("email-notification")); // never blocks (unbounded)
Task task = workQueue.poll(); // returns null if empty, never blocks
```

**Choose between:**
- `BlockingQueue` → when producers/consumers need to coordinate (wait for data/space)
- `ConcurrentLinkedQueue` → when polling in a loop with other logic; or non-blocking drain

---

## 12. Producer-Consumer Pattern

### 12.1 The Classic Problem

Producers generate work items. Consumers process them. The challenge:
- Producers must not overwhelm consumers (backpressure)
- Consumers must not spin-wait when no work is available
- The queue between them must be thread-safe

### 12.2 Full Production Example: Log Pipeline

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogPipeline {
    private final BlockingQueue<LogEvent> queue = new LinkedBlockingQueue<>(10_000);
    private final ExecutorService producers = Executors.newFixedThreadPool(4,
        r -> new Thread(r, "log-producer"));
    private final ExecutorService consumers = Executors.newFixedThreadPool(2,
        r -> new Thread(r, "log-consumer"));
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Producer: application threads call this
    public void log(LogEvent event) throws InterruptedException {
        if (!queue.offer(event, 100, TimeUnit.MILLISECONDS)) {
            // Queue is full — apply back-pressure
            metrics.increment("log.dropped");
        }
    }

    // Consumer: reads from queue and writes to storage
    private void consume() {
        while (running.get() || !queue.isEmpty()) {
            try {
                LogEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    writeToDisk(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void start() {
        consumers.submit(this::consume);
        consumers.submit(this::consume); // two consumer threads
    }

    public void stop() throws InterruptedException {
        running.set(false);
        consumers.shutdown();
        consumers.awaitTermination(10, TimeUnit.SECONDS);
    }
}
```

### 12.3 Poison Pill — Graceful Shutdown Signal

A common pattern for stopping consumers: send a special "poison pill" sentinel value.

```java
public class Pipeline {
    private static final Task POISON_PILL = new Task("__STOP__");
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private final int numConsumers = 4;

    public void stop() {
        // Send one poison pill per consumer thread
        for (int i = 0; i < numConsumers; i++) {
            queue.put(POISON_PILL);
        }
    }

    private void consume() {
        while (true) {
            Task task = queue.take();
            if (task == POISON_PILL) { // reference equality — same instance
                return; // exit the consumer
            }
            process(task);
        }
    }
}
```

---

## 13. Synchronization Utilities

### 13.1 CountDownLatch

A one-time gate. Initialized with a count. Threads call `countDown()` to decrement; other threads `await()` until count reaches zero. **Cannot be reset.**

```java
import java.util.concurrent.CountDownLatch;

// Use case: wait for all services to initialize before accepting traffic
public class ApplicationStartup {
    private final CountDownLatch readyLatch = new CountDownLatch(3); // 3 services

    public void startAll() throws InterruptedException {
        new Thread(() -> {
            initializeDatabase();
            readyLatch.countDown(); // service 1 ready
        }).start();

        new Thread(() -> {
            initializeCacheCluster();
            readyLatch.countDown(); // service 2 ready
        }).start();

        new Thread(() -> {
            initializeMessageBroker();
            readyLatch.countDown(); // service 3 ready
        }).start();

        readyLatch.await(); // main thread waits here until all 3 are done
        System.out.println("All services ready — starting HTTP server");
        startHttpServer();
    }
}
```

**Another use case: Fan-out fan-in**

```java
// Dispatch 100 tasks in parallel, wait for all to complete
int numTasks = 100;
CountDownLatch latch = new CountDownLatch(numTasks);

for (int i = 0; i < numTasks; i++) {
    final int taskId = i;
    executor.submit(() -> {
        try {
            processChunk(taskId);
        } finally {
            latch.countDown(); // always count down, even on failure
        }
    });
}
latch.await(5, TimeUnit.MINUTES); // wait with timeout
```

### 13.2 CyclicBarrier

A reusable synchronization point. All threads wait at the barrier until all have arrived, then all proceed. **Can be reset and reused.**

```java
import java.util.concurrent.CyclicBarrier;

// Use case: parallel batch processing with synchronization points
public class ParallelProcessor {
    private final int numWorkers = 4;
    private final CyclicBarrier barrier = new CyclicBarrier(numWorkers, () -> {
        // Barrier action runs when all threads arrive — on one of the participant threads
        System.out.println("All workers finished phase — merging results");
        mergeResults();
    });

    private void workerTask(int workerId) {
        for (int phase = 0; phase < 3; phase++) {
            processPhase(workerId, phase);
            try {
                barrier.await(); // wait for all workers to finish this phase
                // All workers proceed together to the next phase
            } catch (InterruptedException | BrokenBarrierException e) {
                return; // handle abort
            }
        }
    }

    public void run() throws InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            final int id = i;
            exec.submit(() -> workerTask(id));
        }
        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.HOURS);
    }
}
```

**CountDownLatch vs CyclicBarrier:**

| Aspect | CountDownLatch | CyclicBarrier |
|---|---|---|
| Reusable | No | Yes (reset()) |
| Who decrements | Anyone (multiple `countDown()` callers) | Each participant calls `await()` once |
| What happens at 0 | Waiting threads released | All participants continue; barrier action runs |
| Main use | One waits for many | Many wait for each other |

### 13.3 Semaphore

Controls how many threads can access a resource simultaneously. Think of it as a pool of permits.

```java
import java.util.concurrent.Semaphore;

// Use case: limit concurrent connections to an external API
public class ExternalApiClient {
    private final Semaphore permits = new Semaphore(10, true); // max 10 concurrent calls, fair

    public String callApi(String endpoint) throws InterruptedException {
        permits.acquire(); // blocks if 10 calls already in progress
        try {
            return httpClient.get(endpoint);
        } finally {
            permits.release(); // always release, even on exception
        }
    }

    // tryAcquire — non-blocking rate limiting
    public Optional<String> tryCallApi(String endpoint) {
        if (permits.tryAcquire()) {
            try {
                return Optional.of(httpClient.get(endpoint));
            } finally {
                permits.release();
            }
        }
        return Optional.empty(); // too many concurrent calls
    }
}
```

**Real production use case — database connection pool:**

```java
// Simplified connection pool using Semaphore
public class ConnectionPool {
    private final Semaphore semaphore;
    private final BlockingQueue<Connection> available;

    public ConnectionPool(int poolSize) {
        semaphore = new Semaphore(poolSize, true);
        available = new LinkedBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            available.offer(createConnection());
        }
    }

    public Connection acquire(long timeout, TimeUnit unit) throws Exception {
        if (!semaphore.tryAcquire(timeout, unit)) {
            throw new TimeoutException("No connection available within " + timeout + unit);
        }
        return available.poll(); // will have an item since semaphore tracks availability
    }

    public void release(Connection conn) {
        available.offer(conn);
        semaphore.release();
    }
}
```

### 13.4 Phaser

A flexible, reusable multi-phase barrier. More powerful than CyclicBarrier: threads can dynamically register/deregister, and you have fine-grained control per phase.

```java
import java.util.concurrent.Phaser;

public class MultiPhaseProcessor {
    private final Phaser phaser = new Phaser(1); // register main thread

    public void runWorker(int id) {
        phaser.register(); // dynamically register this worker
        try {
            for (int phase = 0; phase < 5; phase++) {
                doWork(id, phase);
                phaser.arriveAndAwaitAdvance(); // synchronize at each phase
            }
        } finally {
            phaser.arriveAndDeregister(); // clean up when done
        }
    }

    public void run() {
        for (int i = 0; i < 10; i++) {
            final int id = i;
            new Thread(() -> runWorker(id)).start();
        }
        phaser.arriveAndDeregister(); // deregister main thread
    }
}
```

### 13.5 Exchanger

Two threads swap objects at a synchronization point. Useful for double-buffering patterns.

```java
import java.util.concurrent.Exchanger;
import java.util.ArrayList;
import java.util.List;

public class DoubleBufferPipeline {
    private final Exchanger<List<Data>> exchanger = new Exchanger<>();

    // Producer fills a buffer, then exchanges it for the empty one from consumer
    private void produce() throws InterruptedException {
        List<Data> buffer = new ArrayList<>();
        while (true) {
            for (int i = 0; i < 100; i++) {
                buffer.add(generate());
            }
            buffer = exchanger.exchange(buffer); // hand off full buffer, get empty buffer
            buffer.clear();
        }
    }

    // Consumer processes the full buffer, sends back empty one
    private void consume() throws InterruptedException {
        List<Data> buffer = new ArrayList<>();
        while (true) {
            buffer = exchanger.exchange(buffer); // get full buffer, send empty one
            process(buffer);
        }
    }
}
```

---

## 14. Fork/Join Framework

### 14.1 Work Stealing

Traditional thread pools have one shared queue. Under uneven load, some threads sit idle while others have long queues. **Work stealing** gives each thread its own deque (double-ended queue). Idle threads "steal" tasks from the back of busy threads' deques.

```
Thread 1 deque: [task1, task2, task3, task4]
                  ↑ pushes/pops here          ← task5 stolen by Thread 2

Thread 2 deque: [task5]  ← stolen from Thread 1
Thread 3 deque: []       ← idle, looking to steal
```

### 14.2 RecursiveTask and RecursiveAction

```java
import java.util.concurrent.*;

// RecursiveTask<T> — returns a result
public class ParallelMergeSort extends RecursiveTask<int[]> {
    private static final int THRESHOLD = 1000; // below this, sort sequentially
    private final int[] array;

    public ParallelMergeSort(int[] array) {
        this.array = array;
    }

    @Override
    protected int[] compute() {
        if (array.length <= THRESHOLD) {
            // Base case: small enough to sort directly
            Arrays.sort(array);
            return array;
        }

        // Divide
        int mid = array.length / 2;
        int[] left = Arrays.copyOfRange(array, 0, mid);
        int[] right = Arrays.copyOfRange(array, mid, array.length);

        // Fork — schedule left and right as subtasks
        ParallelMergeSort leftTask = new ParallelMergeSort(left);
        ParallelMergeSort rightTask = new ParallelMergeSort(right);

        leftTask.fork();  // schedule left asynchronously
        int[] sortedRight = rightTask.compute(); // compute right in this thread (optimization)
        int[] sortedLeft = leftTask.join();  // wait for left result

        // Combine
        return merge(sortedLeft, sortedRight);
    }

    private int[] merge(int[] left, int[] right) {
        int[] result = new int[left.length + right.length];
        int i = 0, j = 0, k = 0;
        while (i < left.length && j < right.length) {
            result[k++] = left[i] <= right[j] ? left[i++] : right[j++];
        }
        while (i < left.length) result[k++] = left[i++];
        while (j < right.length) result[k++] = right[j++];
        return result;
    }
}

// Usage
ForkJoinPool pool = new ForkJoinPool(); // uses availableProcessors() threads by default
int[] data = generateRandomArray(1_000_000);
int[] sorted = pool.invoke(new ParallelMergeSort(data));
```

### 14.3 Large Data Processing Example

```java
// RecursiveAction — no return value; useful for parallel updates in-place
public class ParallelStatistics extends RecursiveAction {
    private static final int THRESHOLD = 10_000;
    private final double[] data;
    private final int start, end;
    private double sum = 0;

    public ParallelStatistics(double[] data, int start, int end) {
        this.data = data;
        this.start = start;
        this.end = end;
    }

    @Override
    protected void compute() {
        if (end - start <= THRESHOLD) {
            for (int i = start; i < end; i++) {
                sum += data[i];
            }
            return;
        }

        int mid = (start + end) / 2;
        ParallelStatistics left = new ParallelStatistics(data, start, mid);
        ParallelStatistics right = new ParallelStatistics(data, mid, end);

        invokeAll(left, right); // fork both and join — cleaner than fork/join pattern

        sum = left.sum + right.sum;
    }

    public double getSum() { return sum; }
}

// Usage
ForkJoinPool pool = ForkJoinPool.commonPool();
double[] bigDataset = new double[100_000_000];
ParallelStatistics task = new ParallelStatistics(bigDataset, 0, bigDataset.length);
pool.invoke(task);
System.out.println("Total: " + task.getSum());
```

### 14.4 When to Use Fork/Join

**Good fit:**
- Recursively decomposable problems (sorting, searching, matrix operations)
- CPU-bound computation (no blocking I/O in tasks)
- Large datasets where parallel processing helps

**Bad fit:**
- I/O-bound tasks (blocking calls stall ForkJoin threads — use CompletableFuture + IO pool)
- Tasks that can't be subdivided
- Small tasks where fork overhead exceeds computation time

**Parallel Streams use ForkJoinPool.commonPool() internally:**
```java
// This runs on ForkJoinPool.commonPool()
long count = Stream.of(largeList)
    .parallel()
    .filter(this::isValid)
    .count();

// For custom thread count, submit to your own pool
ForkJoinPool customPool = new ForkJoinPool(8);
long count = customPool.submit(() ->
    largeList.parallelStream().filter(this::isValid).count()
).get();
```

---

## 15. Deadlocks

### 15.1 What Is a Deadlock?

A deadlock occurs when two or more threads are each waiting for a lock held by another thread in the group — creating a circular dependency that never resolves.

```
Thread A holds Lock 1, waits for Lock 2
Thread B holds Lock 2, waits for Lock 1

    Thread A ──holds──► Lock 1
       │                   ▲
    waiting               waiting
       │                   │
       ▼                   │
    Lock 2 ◄──holds── Thread B
```

Both threads are BLOCKED forever. Neither can proceed.

### 15.2 The Four Conditions for Deadlock (Coffman Conditions)

All four must hold simultaneously for a deadlock to occur:

1. **Mutual Exclusion** — at least one resource is non-shareable (held by one thread at a time)
2. **Hold and Wait** — a thread holds a resource while waiting to acquire another
3. **No Preemption** — resources cannot be forcibly taken away; a thread must release voluntarily
4. **Circular Wait** — a cycle exists in the wait-for graph (A waits for B waits for C waits for A)

Breaking any one of these four conditions prevents deadlock.

### 15.3 Classic Deadlock Example

```java
// DEADLOCK EXAMPLE — do not use in production
public class DeadlockExample {
    private final Object lockA = new Object();
    private final Object lockB = new Object();

    public void methodA() {
        synchronized(lockA) {           // Thread 1 acquires lockA
            System.out.println("Thread 1: holding lockA");
            synchronized(lockB) {       // Thread 1 tries to acquire lockB — BLOCKED
                System.out.println("Thread 1: holding lockA and lockB");
            }
        }
    }

    public void methodB() {
        synchronized(lockB) {           // Thread 2 acquires lockB
            System.out.println("Thread 2: holding lockB");
            synchronized(lockA) {       // Thread 2 tries to acquire lockA — BLOCKED
                System.out.println("Thread 2: holding lockA and lockB");
            }
        }
    }
}

// Thread 1 calls methodA(); Thread 2 calls methodB() simultaneously → DEADLOCK
```

### 15.4 Deadlock Prevention Strategies

#### Strategy 1: Lock Ordering (Break Circular Wait)

Always acquire locks in the same global order:

```java
// SAFE — both threads acquire locks in the same order
private final Object lockA = new Object();
private final Object lockB = new Object();

// Use System.identityHashCode or a canonical ordering
public void transfer(Account from, Account to, double amount) {
    Account first = System.identityHashCode(from) < System.identityHashCode(to) ? from : to;
    Account second = first == from ? to : from;

    synchronized(first) {
        synchronized(second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

#### Strategy 2: tryLock with Timeout (Break Hold and Wait)

```java
// DEADLOCK-SAFE — using tryLock with timeout
public void transferSafe(ReentrantLock lockA, ReentrantLock lockB, double amount)
        throws InterruptedException {
    while (true) {
        if (lockA.tryLock(50, TimeUnit.MILLISECONDS)) {
            try {
                if (lockB.tryLock(50, TimeUnit.MILLISECONDS)) {
                    try {
                        performTransfer(amount);
                        return; // success
                    } finally {
                        lockB.unlock();
                    }
                }
            } finally {
                lockA.unlock();
            }
        }
        // Failed to acquire both — back off and retry
        Thread.sleep(ThreadLocalRandom.current().nextInt(10)); // random backoff avoids livelock
    }
}
```

#### Strategy 3: Avoid Nested Locks

```java
// BAD — nested locks create deadlock potential
public void process() {
    synchronized(lockA) {
        synchronized(lockB) { // nested — risky
            doWork();
        }
    }
}

// BETTER — flatten to one lock or restructure to avoid nesting
public void process() {
    Object data = readUnderLock();    // acquire/release lockA only
    processData(data);                // no lock
    writeUnderLock(data);             // acquire/release lockB only
}
```

#### Strategy 4: Lock Hierarchies (Structural)

Define lock levels. A thread holding a level-2 lock can only acquire level-3 or higher locks — never lower. This prevents circular waits structurally.

### 15.5 Detecting Deadlocks at Runtime

**Using thread dumps (jstack):**

```bash
jstack <pid>
# Look for "Found one Java-level deadlock" section

# Example output:
Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x000070c8 (object 0x00000007d6ba4148, a java.lang.Object),
  which is held by "Thread-0"
"Thread-0":
  waiting to lock monitor 0x000070d8 (object 0x00000007d6ba4158, a java.lang.Object),
  which is held by "Thread-1"
```

**Detecting programmatically:**

```java
import java.lang.management.*;

public class DeadlockDetector {
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    public void detectAndLog() {
        long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
        if (deadlockedIds != null && deadlockedIds.length > 0) {
            ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedIds, true, true);
            StringBuilder sb = new StringBuilder("DEADLOCK DETECTED:\n");
            for (ThreadInfo info : infos) {
                sb.append(info.toString());
            }
            log.error(sb.toString());
            alertOncall("Deadlock detected in JVM!");
        }
    }

    // Schedule this to run periodically
    public void scheduleChecks() {
        ScheduledExecutorService checker = Executors.newSingleThreadScheduledExecutor();
        checker.scheduleAtFixedRate(this::detectAndLog, 0, 30, TimeUnit.SECONDS);
    }
}
```

### 15.6 Livelock and Starvation

**Livelock:** threads are not blocked but keep responding to each other and making no progress.
```
Thread A sees Thread B, backs off.
Thread B sees Thread A, backs off.
Both back off simultaneously, try again — cycle repeats forever.
```
Fix: Introduce randomized backoff (random sleep before retry).

**Starvation:** a thread never gets CPU time because higher-priority threads or an unfair scheduler always preempt it.
```
Thread A (high priority) keeps preempting Thread B
Thread B starves — never runs
```
Fix: Use fair locks (`new ReentrantLock(true)`); avoid extreme priority differences; use bounded queues.

---

## Summary: Part 3

| Topic | Key Takeaway |
|---|---|
| ConcurrentHashMap | Fine-grained locking; use compute methods for atomic compound ops |
| CopyOnWriteArrayList | Lock-free reads; expensive writes; for infrequently updated lists |
| BlockingQueue | Heart of producer-consumer; bound it in production |
| CountDownLatch | One-time gate; wait for N events; not reusable |
| CyclicBarrier | Reusable; all participants sync at barrier; barrier action |
| Semaphore | Resource pool; rate limiting; connection pool |
| ForkJoin | CPU-bound recursive tasks; work-stealing reduces idle threads |
| Deadlock | Break circular wait via lock ordering or tryLock with timeout |

**Continue reading:** [Part 4 — Concurrency Patterns, Performance, Debugging, Production Systems & Best Practices](./04-production-and-patterns.md)
