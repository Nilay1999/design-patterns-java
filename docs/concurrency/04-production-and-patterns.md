# Java Concurrency — Part 4: Patterns, Performance & Production
## Sections 16–20: Design Patterns · Performance · Debugging · Production Systems · Best Practices

---

## 16. Concurrency Design Patterns

### 16.1 Thread Pool Pattern

**Problem:** Creating a new thread per task is expensive and unbounded.
**Solution:** Maintain a pool of pre-created threads that reuse themselves across tasks.

```
Task queue:  [T1][T2][T3][T4][T5]...
                   ↓
Thread pool: [Worker-1][Worker-2][Worker-3][Worker-4]
                   ↓
Results:     Future<R1>, Future<R2>...
```

Already covered in depth in Section 9. The key pattern insight:

```java
// The pattern: separate task definition from execution
interface Task { void execute(); }

class ThreadPool {
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();

    public ThreadPool(int size) {
        for (int i = 0; i < size; i++) {
            Thread worker = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Task task = queue.take(); // blocks until task available
                        task.execute();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "pool-worker-" + i);
            worker.start();
            workers.add(worker);
        }
    }

    public void submit(Task task) throws InterruptedException {
        queue.put(task);
    }
}
```

### 16.2 Immutable Object Pattern

**Problem:** Shared mutable state requires synchronization.
**Solution:** Make objects immutable — no synchronization needed.

```java
// MUTABLE — needs synchronization everywhere it's shared
public class UserProfile {
    private String name;
    private String email;
    public void setName(String name) { this.name = name; }
    // ... thread-unsafe
}

// IMMUTABLE — safely shared across any number of threads
public final class UserProfile {
    private final String name;
    private final String email;
    private final List<String> roles; // defensive copy!

    public UserProfile(String name, String email, List<String> roles) {
        this.name = name;
        this.email = email;
        this.roles = List.copyOf(roles); // unmodifiable copy — Java 10+
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; } // safe — unmodifiable

    // "Modification" creates a new instance — original unchanged
    public UserProfile withEmail(String newEmail) {
        return new UserProfile(this.name, newEmail, this.roles);
    }
}
```

**Production example:** JDK's `String`, `Integer`, `LocalDate`, `URI`, `InetAddress` are all immutable. Java records (Java 16+) are a language-level enforcement of immutability:

```java
// Java Record — automatically immutable, final, with accessors and equals/hashCode
public record Money(long amountInCents, String currency) {
    // compact canonical constructor for validation
    public Money {
        if (amountInCents < 0) throw new IllegalArgumentException("Amount must be non-negative");
        Objects.requireNonNull(currency, "Currency must not be null");
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Money(this.amountInCents + other.amountInCents, this.currency);
    }
}
```

### 16.3 Thread Confinement Pattern

**Problem:** You need mutable state but don't want synchronization overhead.
**Solution:** Confine mutable state to a single thread — no other thread can access it.

**Stack confinement:** Local variables in a method are always thread-confined (each thread has its own stack).

```java
public List<Order> processOrders(List<Order> input) {
    List<Order> result = new ArrayList<>(); // confined to this stack frame — safe!
    for (Order order : input) {
        if (order.isValid()) {
            result.add(enrichOrder(order));
        }
    }
    return result; // once returned, the caller decides what to do with it
}
```

**ThreadLocal confinement:** Give each thread its own instance of a mutable object.

```java
// Each thread gets its own DateFormatter — avoids the thread-safety issues of SimpleDateFormat
public class DateUtils {
    private static final ThreadLocal<SimpleDateFormat> formatter = ThreadLocal.withInitial(
        () -> new SimpleDateFormat("yyyy-MM-dd")
    );

    public static String format(Date date) {
        return formatter.get().format(date); // each thread uses its own instance
    }
}

// More realistic use: per-thread database connections, request context
public class RequestContext {
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    public static void setCurrentUser(String userId) {
        currentUserId.set(userId);
    }

    public static String getCurrentUser() {
        return currentUserId.get();
    }

    // CRITICAL: Always clean up ThreadLocals in finally blocks!
    // Failure to do so causes memory leaks in thread pools (the thread never dies)
    public static void clear() {
        currentUserId.remove();
    }
}

// Usage in a servlet filter
public class AuthFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String userId = extractUserId(req);
        RequestContext.setCurrentUser(userId);
        try {
            chain.doFilter(req, res);
        } finally {
            RequestContext.clear(); // ALWAYS clean up — thread goes back to pool!
        }
    }
}
```

**ThreadLocal memory leak:** A common production bug. Thread pool threads are never destroyed — their ThreadLocal values persist forever if not removed. Each new request that doesn't clean up grows the leak.

### 16.4 Monitor Object Pattern

**Problem:** Need to protect shared state and coordinate between threads.
**Solution:** Encapsulate state in an object; all access through synchronized methods.

```java
// The object IS the monitor — all coordination inside it
public class BoundedCounter {
    private final int max;
    private int current = 0;

    public BoundedCounter(int max) {
        this.max = max;
    }

    public synchronized void increment() throws InterruptedException {
        while (current >= max) {
            wait();
        }
        current++;
        notifyAll();
    }

    public synchronized void decrement() throws InterruptedException {
        while (current <= 0) {
            wait();
        }
        current--;
        notifyAll();
    }

    public synchronized int getCurrent() {
        return current;
    }
}
```

### 16.5 Active Object Pattern

**Problem:** Decouple method execution from method invocation — make calls asynchronous without changing the caller.

```java
// Interface — looks synchronous to the caller
public interface OrderService {
    Future<Order> placeOrder(OrderRequest request);
}

// Active Object — calls are queued and executed asynchronously
public class ActiveOrderService implements OrderService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
        r -> new Thread(r, "order-service")
    );

    @Override
    public Future<Order> placeOrder(OrderRequest request) {
        return executor.submit(() -> {
            // Runs on the order-service thread, not the caller's thread
            validate(request);
            Order order = persistToDatabase(request);
            notifyFulfillment(order);
            return order;
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
```

### 16.6 Read-Write Lock Pattern (Beyond `ReadWriteLock`)

Pattern for data structures where reads are cheap but writes require full rebuild:

```java
public class ConfigStore {
    private volatile Map<String, String> config = Map.of(); // immutable snapshot
    private final ReentrantLock writeLock = new ReentrantLock();

    // Reads: no lock at all — just read the volatile reference (immutable map)
    public String get(String key) {
        return config.get(key); // volatile read — safe, no lock needed
    }

    // Writes: lock, copy, modify, replace reference
    public void update(Map<String, String> updates) {
        writeLock.lock();
        try {
            Map<String, String> newConfig = new HashMap<>(config);
            newConfig.putAll(updates);
            config = Map.copyOf(newConfig); // volatile write — immediately visible
        } finally {
            writeLock.unlock();
        }
    }
}
```

This pattern — volatile reference to an immutable object — gives **zero-overhead reads** with correct visibility.

---

## 17. Performance Considerations

### 17.1 Lock Contention

**Definition:** Multiple threads competing for the same lock simultaneously.

```
Low contention:                High contention:
Thread A: [lock][work][unlock]  Thread A: [lock][--waiting--][lock][work][unlock]
Thread B:              [lock]   Thread B: [----waiting----][lock][work][unlock]
Thread C:                       Thread C: [--------waiting--------][lock][work][unlock]
```

**Measuring contention:** If threads spend more time waiting for locks than doing work, you have a contention problem.

```bash
# JVM flag to monitor contention
-XX:+PrintConcurrentLocks

# Or programmatically
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
bean.setThreadContentionMonitoringEnabled(true);
ThreadInfo[] infos = bean.getThreadInfo(bean.getAllThreadIds());
for (ThreadInfo info : infos) {
    System.out.println(info.getThreadName() + " blocked count: " + info.getBlockedCount()
        + " wait time: " + info.getBlockedTime() + "ms");
}
```

**Reducing contention:**
- Reduce lock hold time (do expensive work outside the lock)
- Use lock striping (ConcurrentHashMap approach)
- Replace locking with CAS (AtomicInteger, LongAdder)
- Use thread-local caches for read-heavy access patterns
- Partition work by key so threads rarely need the same lock

### 17.2 False Sharing

**Definition:** Two threads modify different variables that happen to live on the same CPU cache line (typically 64 bytes). When one thread writes, it invalidates the entire cache line — forcing the other thread to reload it from main memory.

```
Cache line (64 bytes):
[counter1 (8 bytes)][counter2 (8 bytes)][counter3 (8 bytes)]...[padding]

Thread A updates counter1 → invalidates the entire cache line
Thread B reads counter2 → must reload from main memory (cache miss!)

Even though A and B touched DIFFERENT variables, they fight over the same cache line.
```

**Detection:** High cache miss rate; performance degrades as you add more threads despite no logical contention.

**Fix: Padding**

```java
// Without padding — counter1 and counter2 share a cache line
class FalseSharingExample {
    volatile long counter1;
    volatile long counter2; // on same cache line as counter1!
}

// With padding — force each counter onto its own cache line
class PaddedCounter {
    volatile long counter;
    long p1, p2, p3, p4, p5, p6, p7; // 7 longs = 56 bytes padding (56 + 8 = 64 bytes)
}

// Java 8+ annotation (compiler hint — not guaranteed)
@Contended // JVM flag required: -XX:-RestrictContended
class AnnotatedCounter {
    @Contended
    volatile long counter;
}
```

**LongAdder solves false sharing:** It uses padded cells internally — each thread updates its own cache-line-isolated cell.

### 17.3 Context Switching Cost

Context switching between threads costs:
- **Direct cost:** ~1–10 microseconds to save/restore thread state
- **Indirect cost:** CPU cache warm-up for the newly scheduled thread (cold cache → L3 misses)

**When context switching becomes a problem:**
- Thousands of threads — OS scheduler overhead dominates
- Lock contention causes constant BLOCKED → RUNNABLE transitions
- Fine-grained locking inside a hot path

**Measuring:**
```bash
# Linux — context switches per second
vmstat 1
# Look at 'cs' column

# Per-thread context switches
/proc/<pid>/status | grep ctxt
```

**Reducing context switching:**
- Fewer threads (thread pool, not thread-per-request)
- Larger work units per task (reduce task creation overhead)
- Spin-waiting for very short critical sections (avoids kernel-level blocking)
- Use `LockSupport.parkNanos()` for fine-grained waiting

### 17.4 Thread Starvation

**Definition:** A thread can't access a resource it needs because other threads monopolize it.

**Causes:**
- Priority inversion (low-priority thread holds a lock needed by high-priority thread)
- Unfair scheduling with non-fair locks
- Long-running tasks starving the thread pool

```java
// Starvation example: all threads grab long tasks, short tasks never run
ThreadPoolExecutor pool = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>());

// Short tasks submitted but starved by long tasks
for (int i = 0; i < 1000; i++) pool.submit(longTask);     // fills pool
for (int i = 0; i < 100; i++) pool.submit(shortUrgentTask); // starved!

// Fix: Use separate pools for different task priorities
ThreadPoolExecutor highPriorityPool = new ThreadPoolExecutor(2, 2, ...);
ThreadPoolExecutor normalPool = new ThreadPoolExecutor(4, 4, ...);
```

### 17.5 CPU Utilization and Amdahl's Law

**Amdahl's Law:** The maximum speedup from parallelization is limited by the sequential fraction.

```
Speedup = 1 / (S + (1-S)/N)
Where:
  S = fraction of work that is sequential
  N = number of processors

If S = 20% (0.2) and N = 8 processors:
Speedup = 1 / (0.2 + 0.8/8) = 1 / (0.2 + 0.1) = 1/0.3 = 3.3x

Even with infinite processors, max speedup = 1/0.2 = 5x
```

**Implication:** Profile before parallelizing. If 50% of your code is inherently sequential, you can never achieve more than 2x speedup no matter how many threads you add.

---

## 18. Debugging Concurrency Issues

### 18.1 Thread Dumps

A thread dump captures the state of every thread at a moment in time. Essential for diagnosing:
- Deadlocks
- Thread pool exhaustion
- Stuck threads (waiting on I/O, locks)

```bash
# Generate thread dump
jstack <pid> > threaddump.txt

# Or kill -3 on Linux (prints to stdout/log)
kill -3 <pid>

# Or via JMX/jconsole (GUI)
jconsole <pid>
```

**Reading a thread dump:**

```
"http-nio-8080-exec-5" #42 daemon prio=5 os_prio=0 tid=0x00007f cpu=100ms elapsed=10s
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.example.OrderService.placeOrder(OrderService.java:45)
        - waiting to lock <0x0000000712345678> (a com.example.Order)
        at com.example.CheckoutController.checkout(CheckoutController.java:78)
```

**What to look for:**
- Many threads in `BLOCKED` state on the same lock → contention
- `"Found one Java-level deadlock:"` → deadlock (JVM detected it for you)
- Many threads in `WAITING` on `java.util.concurrent.locks.AbstractQueuedSynchronizer.parkAndCheckInterrupt` → lock waiters
- Pool threads all `BLOCKED` → pool exhaustion or deadlock

### 18.2 Common Race Condition Patterns

**Check-then-act:**
```java
// RACE CONDITION
if (!map.containsKey(key)) {     // check
    map.put(key, newValue);      // act — another thread may have inserted between check and act!
}

// FIX — atomic compound operation
map.putIfAbsent(key, newValue);  // ConcurrentHashMap
// or
map.computeIfAbsent(key, k -> computeValue(k));
```

**Read-modify-write:**
```java
// RACE CONDITION
int old = counter.get();
counter.set(old + 1);  // another thread may have changed counter between get and set

// FIX
counter.incrementAndGet();  // AtomicInteger — atomic CAS loop
```

**Lazy initialization:**
```java
// RACE CONDITION — two threads may both create the instance
if (instance == null) {        // check
    instance = new MyClass();  // init — two threads can both pass the check!
}

// FIX — double-checked locking (requires volatile!)
private volatile MyClass instance; // volatile is REQUIRED here

public MyClass getInstance() {
    if (instance == null) {          // first check (no lock)
        synchronized(this) {
            if (instance == null) {  // second check (with lock)
                instance = new MyClass(); // volatile write — visible immediately
            }
        }
    }
    return instance;
}

// Even better — class-loader guarantee (Initialization-on-Demand Holder)
public class Singleton {
    private Singleton() {}
    private static class Holder {
        static final Singleton INSTANCE = new Singleton(); // class loading is thread-safe
    }
    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

### 18.3 Tools for Concurrency Debugging

| Tool | Purpose |
|---|---|
| `jstack` | Thread dumps — deadlock and stuck thread detection |
| `jconsole` / `jvisualvm` | GUI — live thread states, memory, CPU |
| Java Flight Recorder (JFR) | Low-overhead production profiling — lock events, thread samples |
| `async-profiler` | Flamegraphs — CPU and lock profiling |
| ThreadSanitizer (TSan) | Race condition detection (for JVM: experimental) |
| FindBugs/SpotBugs | Static analysis — finds common concurrency bugs |
| IntelliJ IDEA Thread Sanitizer | IDE-level detection of threading issues |

**Using Java Flight Recorder for lock profiling:**

```bash
# Start recording
jcmd <pid> JFR.start duration=60s filename=recording.jfr settings=profile

# Analyze with JDK Mission Control
jmc recording.jfr
# Look at: Lock Instances, Monitor Inflation, Thread Latency
```

### 18.4 Reproducing Race Conditions

Race conditions are timing-dependent and hard to reproduce reliably. Techniques:

1. **Stress testing:** Run many threads and iterations (`java.util.concurrent.CyclicBarrier` to synchronize start)
2. **Thread sleep injection:** Inject `Thread.sleep(1)` at suspected race points to amplify timing issues
3. **Loop tests:** Run the suspected code in a tight loop with multiple threads
4. **Partial failure logging:** Log intermediate states to spot inconsistencies

```java
// Stress test harness for a supposed thread-safe class
@Test
public void testCounterThreadSafety() throws InterruptedException {
    SafeCounter counter = new SafeCounter();
    int numThreads = 100;
    int incrementsPerThread = 1000;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(numThreads);

    for (int i = 0; i < numThreads; i++) {
        new Thread(() -> {
            try {
                start.await(); // all threads ready before starting
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }).start();
    }

    start.countDown(); // release all threads simultaneously
    done.await();

    assertEquals(numThreads * incrementsPerThread, counter.getCount());
}
```

---

## 19. Real Production Use Cases

### 19.1 High-Throughput REST API

**Scenario:** An e-commerce API handles 50,000 requests per second. Each request reads from a cache, queries a database, calls pricing service.

```
Architecture:
Request → Tomcat thread pool (200 threads) → Spring Controllers
              → Cache lookup (ConcurrentHashMap, ~10μs)
              → DB query (HikariCP pool, 20 connections, ~5ms each)
              → Pricing service call (HTTP client pool, async, ~50ms)
              → Response assembly
```

**Concurrency design:**

```java
@Service
public class ProductApiService {
    // Read-heavy cache — ConcurrentHashMap or Caffeine cache
    private final Cache<String, Product> productCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

    // I/O thread pool — sized for async HTTP calls (not CPU cores)
    private final ExecutorService ioPool = new ThreadPoolExecutor(
        20, 200, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(5000),
        r -> new Thread(r, "io-worker"),
        new ThreadPoolExecutor.CallerRunsPolicy() // back-pressure on caller
    );

    public ProductResponse getProductDetail(String productId) {
        // 1. Cache lookup — no lock (ConcurrentHashMap)
        Product product = productCache.getIfPresent(productId);
        if (product != null) {
            return new ProductResponse(product, null, null); // cache hit
        }

        // 2. Parallel async fetches
        CompletableFuture<Product> productFuture =
            CompletableFuture.supplyAsync(() -> db.findProduct(productId), ioPool)
                .orTimeout(2, TimeUnit.SECONDS);

        CompletableFuture<Price> priceFuture =
            CompletableFuture.supplyAsync(() -> pricingService.getPrice(productId), ioPool)
                .orTimeout(1, TimeUnit.SECONDS)
                .exceptionally(ex -> Price.defaultPrice()); // graceful degradation

        CompletableFuture<List<Review>> reviewsFuture =
            CompletableFuture.supplyAsync(() -> reviewService.getTopReviews(productId, 5), ioPool)
                .orTimeout(1, TimeUnit.SECONDS)
                .exceptionally(ex -> Collections.emptyList());

        return CompletableFuture.allOf(productFuture, priceFuture, reviewsFuture)
            .thenApply(v -> {
                Product p = productFuture.join();
                productCache.put(productId, p); // populate cache
                return new ProductResponse(p, priceFuture.join(), reviewsFuture.join());
            })
            .join();
    }
}
```

**Key decisions:**
- Tomcat pool: 200 threads (I/O-bound, most spend time waiting for DB/HTTP)
- `CompletableFuture.allOf()` for parallel service calls (reduces latency from 3s to 1s)
- `orTimeout()` prevents cascading slow requests
- `exceptionally()` for resilient degradation

### 19.2 Event Processing System

**Scenario:** A Kafka-based event processor handles 1M events per minute. Events must be processed in order per user, but different users can be processed in parallel.

```
Kafka partition → ConsumerThread → partition by userId → UserQueue → WorkerThread
                                          │
                                          ├── Queue(user-123) → Worker-A
                                          ├── Queue(user-456) → Worker-B
                                          └── Queue(user-789) → Worker-C
```

**Implementation:**

```java
public class UserEventProcessor {
    private final int numWorkers = 16;
    // Each worker handles a subset of users — ordered processing per user guaranteed
    private final List<BlockingQueue<UserEvent>> workerQueues = new ArrayList<>();
    private final List<Thread> workers = new ArrayList<>();

    public UserEventProcessor() {
        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i;
            BlockingQueue<UserEvent> queue = new LinkedBlockingQueue<>(10_000);
            workerQueues.add(queue);
            Thread worker = new Thread(() -> processWorkerQueue(queue),
                "event-worker-" + workerId);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
    }

    // Route events: same userId always → same worker (preserves ordering)
    public void process(UserEvent event) throws InterruptedException {
        int workerIndex = Math.abs(event.getUserId().hashCode()) % numWorkers;
        BlockingQueue<UserEvent> queue = workerQueues.get(workerIndex);
        if (!queue.offer(event, 500, TimeUnit.MILLISECONDS)) {
            // Queue full — back-pressure to Kafka consumer (pause partition)
            pauseKafkaPartition(event.getPartition());
        }
    }

    private void processWorkerQueue(BlockingQueue<UserEvent> queue) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                UserEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    applyEvent(event); // state changes for this user — no contention
                    commitOffset(event); // Kafka offset commit
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

**Key insight:** By routing the same userId to the same worker, we achieve:
- Per-user ordering (no race conditions on user state)
- Cross-user parallelism (different users run in parallel)
- No locks inside `applyEvent()` — the worker is the only thread for that user

### 19.3 Web Crawler

**Scenario:** Crawl 10 million web pages in parallel, extract links, store content.

```
CrawlerManager
  ├── URL Frontier: PriorityBlockingQueue<URL>
  ├── Fetcher Pool: 50 I/O threads (HTTP downloading)
  ├── Parser Pool: 8 CPU threads (HTML parsing)
  ├── Visited Set: ConcurrentHashMap<String, Boolean>
  └── Storage: async writer to distributed storage
```

```java
public class WebCrawler {
    private final BlockingQueue<CrawlTask> frontier = new PriorityBlockingQueue<>(10_000,
        Comparator.comparing(CrawlTask::getPriority).reversed());
    private final Set<String> visited = ConcurrentHashMap.newKeySet(); // concurrent set
    private final ExecutorService fetcherPool = new ThreadPoolExecutor(
        50, 50, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(5000),
        r -> new Thread(r, "fetcher"));
    private final ExecutorService parserPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> new Thread(r, "parser"));

    public void start(String seedUrl) throws InterruptedException {
        frontier.put(new CrawlTask(seedUrl, 1.0));

        while (true) {
            CrawlTask task = frontier.poll(5, TimeUnit.SECONDS);
            if (task == null) break; // no more tasks

            if (!visited.add(task.getUrl())) continue; // already visited — skip

            fetcherPool.submit(() -> {
                try {
                    String html = httpClient.get(task.getUrl());
                    parserPool.submit(() -> {
                        List<String> links = parseLinks(html, task.getUrl());
                        storeContent(task.getUrl(), html);
                        links.stream()
                            .filter(url -> !visited.contains(url)) // pre-filter
                            .forEach(url -> {
                                try {
                                    frontier.put(new CrawlTask(url, computePriority(url)));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                    });
                } catch (Exception e) {
                    log.warn("Failed to fetch {}: {}", task.getUrl(), e.getMessage());
                }
            });
        }
    }
}
```

### 19.4 Streaming Pipeline

**Scenario:** Real-time metrics pipeline — collect → aggregate → alert.

```java
public class MetricsPipeline {
    // Stage 1: Collection (many producers)
    private final BlockingQueue<Metric> collectionQueue = new LinkedBlockingQueue<>(100_000);

    // Stage 2: Aggregation (single consumer for accuracy, or partitioned)
    private final BlockingQueue<AggregatedMetric> aggregationQueue = new LinkedBlockingQueue<>(10_000);

    // Stage 3: Alert evaluation (parallel)
    private final ExecutorService alertPool = Executors.newFixedThreadPool(4);

    // Stage: Collection → Aggregation
    private void aggregationWorker() {
        Map<String, DoubleSummaryStatistics> window = new HashMap<>();
        long windowStart = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Metric m = collectionQueue.poll(100, TimeUnit.MILLISECONDS);
                if (m != null) {
                    window.computeIfAbsent(m.getName(), k -> new DoubleSummaryStatistics())
                          .accept(m.getValue());
                }

                // Flush window every 10 seconds
                long now = System.currentTimeMillis();
                if (now - windowStart >= 10_000) {
                    window.forEach((name, stats) ->
                        aggregationQueue.offer(new AggregatedMetric(name, stats, windowStart)));
                    window.clear();
                    windowStart = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Stage: Aggregation → Alert Evaluation (parallel)
    private void alertWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AggregatedMetric metric = aggregationQueue.take();
                alertPool.submit(() -> evaluateAlerts(metric));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

---

## 20. Best Practices for Production Systems

### 20.1 When NOT to Use Threads

Concurrency adds complexity. Before reaching for threads, ask:
- **Can the task be done sequentially?** If latency is acceptable and throughput is sufficient, sequential code is simpler, easier to test, and easier to debug.
- **Is the task CPU-bound with a single core?** Adding threads to a CPU-saturated core doesn't help — it adds overhead.
- **Is the task I/O-bound but using async I/O?** Non-blocking I/O (Netty, reactive programming) can handle thousands of connections with far fewer threads.

### 20.2 Rules for Thread-Safe Design

**Rule 1: Default to Immutability**
Make objects immutable unless there is a clear reason for mutability. Immutability is free — no locks, no CAS, no visibility issues.

**Rule 2: Confine State**
- Prefer stack confinement (local variables, method parameters)
- Use `ThreadLocal` for per-thread state (with careful cleanup)
- Use message passing instead of shared state (actors, queues)

**Rule 3: Document Synchronization Policy**
Every class should document its thread-safety guarantee:
```java
/**
 * Thread-safe. All public methods are safe to call from multiple threads.
 * Internally uses a single ReentrantLock for state protection.
 */
@ThreadSafe
public class OrderService { ... }

/**
 * NOT thread-safe. Instances must not be shared between threads.
 * Use ThreadLocal or create per-request instances.
 */
@NotThreadSafe
public class RequestParser { ... }
```

**Rule 4: Minimize Synchronized Scope**
```java
// BAD — holds lock while doing I/O
public synchronized String processAndLog(Data data) {
    String result = process(data);   // fast
    sendToMonitoring(result);        // slow I/O — holding lock unnecessarily!
    return result;
}

// GOOD — lock only the minimum
public String processAndLog(Data data) {
    String result;
    synchronized(this) {
        result = process(data);      // only protect state access
    }
    sendToMonitoring(result);        // I/O outside lock
    return result;
}
```

**Rule 5: Avoid Lock Nesting**
Each nested synchronized level doubles the risk of deadlock. Refactor to acquire one lock at a time, or use lock ordering if nesting is unavoidable.

**Rule 6: Prefer Higher-Level Abstractions**
```java
// Lower level — more error-prone
synchronized(lock) { ... }

// Higher level — prefer these
executor.submit(task);                    // thread pool
queue.put(item);                          // blocking queue
CompletableFuture.supplyAsync(task);     // async pipeline
ConcurrentHashMap.computeIfAbsent(k,f); // atomic compound op
```

### 20.3 Thread Pool Best Practices

```java
// DO: Name your threads — invaluable in thread dumps
ThreadFactory factory = r -> {
    Thread t = new Thread(r, "payment-processor-" + counter.incrementAndGet());
    t.setUncaughtExceptionHandler((thread, ex) -> log.error("Thread died: {}", thread.getName(), ex));
    return t;
};

// DO: Bound your queues — prevent unbounded memory growth
new LinkedBlockingQueue<>(10_000) // not new LinkedBlockingQueue<>() (unbounded!)

// DO: Set a sensible rejection policy
new ThreadPoolExecutor.CallerRunsPolicy() // back-pressure
// or custom policy that logs and throws with context

// DO: Monitor pool health
ThreadPoolExecutor pool = ...;
metrics.gauge("pool.active.threads", pool::getActiveCount);
metrics.gauge("pool.queue.size", () -> pool.getQueue().size());
metrics.gauge("pool.completed.tasks", pool::getCompletedTaskCount);

// DON'T: Create thread pools inside loops or per-request
// BAD
public void handleRequest(Request r) {
    ExecutorService exec = Executors.newFixedThreadPool(4); // created for EVERY request!
    exec.submit(() -> process(r));
    exec.shutdown(); // this blocks! and creates/destroys 4 threads per request
}

// GOOD: Inject the thread pool as a shared dependency
public class RequestHandler {
    private final ExecutorService ioPool; // injected from application context

    public void handleRequest(Request r) {
        ioPool.submit(() -> process(r));
    }
}
```

### 20.4 CompletableFuture Best Practices

```java
// DO: Always specify an executor for async tasks involving I/O
CompletableFuture.supplyAsync(() -> dbQuery(), dbExecutor); // not commonPool!

// DO: Use orTimeout() (Java 9+) or completeOnTimeout() to prevent hanging futures
future.orTimeout(5, TimeUnit.SECONDS); // completes exceptionally on timeout
future.completeOnTimeout(defaultValue, 5, TimeUnit.SECONDS); // completes normally on timeout

// DO: Handle exceptions explicitly — uncaught exceptions in CompletableFuture are silently swallowed
future
    .thenApply(this::process)
    .exceptionally(ex -> {
        log.error("Pipeline failed", ex);
        metrics.increment("pipeline.errors");
        return fallback;
    });

// DON'T: Call join() or get() inside a CompletableFuture stage — can deadlock commonPool
CompletableFuture.supplyAsync(() -> {
    return anotherFuture.join(); // BAD — may block commonPool thread
});

// DO: Compose instead
CompletableFuture.supplyAsync(() -> compute())
    .thenCompose(result -> anotherAsyncCall(result)); // no blocking
```

### 20.5 Quick Reference: Choosing the Right Tool

| Problem | Solution |
|---|---|
| Simple counter updated by many threads | `AtomicInteger` or `LongAdder` (high contention) |
| Shared map with concurrent reads/writes | `ConcurrentHashMap` |
| Event listener list (few writes, many reads) | `CopyOnWriteArrayList` |
| Producer-consumer pipeline | `LinkedBlockingQueue` (bounded) |
| Limiting concurrent access to resource | `Semaphore` |
| Wait for N tasks to complete | `CountDownLatch` |
| All threads sync at a point, repeat | `CyclicBarrier` |
| Async task with result | `CompletableFuture` + dedicated executor |
| CPU-bound recursive computation | `ForkJoinPool` |
| Mutable per-thread state | `ThreadLocal` (with `remove()` in finally!) |
| Complex state machine transitions | `AtomicReference` with CAS |
| Multiple readers, few writers | `StampedLock` (optimistic) or `ReadWriteLock` |
| Immutable shared config | `volatile` reference to immutable `Map.copyOf()` |
| Background periodic task | `ScheduledExecutorService` |

### 20.6 The Golden Rules of Java Concurrency

1. **The safest shared state is no shared state.** Design for immutability and message passing first.
2. **If state must be shared, always document who owns the lock and which lock protects what.**
3. **Synchronize consistently.** If you synchronize on `this` for writes, synchronize on `this` for reads too.
4. **Never call alien methods (user-supplied callbacks) while holding a lock** — they can call back into your code and deadlock.
5. **Always release locks in `finally` blocks.** One missed unlock = permanent lock → JVM restart required.
6. **Thread interruption is a cooperative contract.** Always check `Thread.interrupted()` or let `InterruptedException` propagate; never swallow it silently.
7. **Test concurrency with stress tests.** Unit tests on a single thread miss race conditions.
8. **Profile before optimizing.** Premature concurrency optimization is the root of many bugs. Measure first.
9. **Name your threads, bound your queues, handle rejections.** These three prevent 80% of thread pool incidents.
10. **Deadlock prevention beats detection.** Enforce lock ordering globally; never nest locks without a documented ordering.

---

## Summary: Complete Guide Index

| Part | Sections | Topics |
|---|---|---|
| [Part 1](./01-fundamentals.md) | 1–5 | Concurrency basics, threads, thread safety, synchronized, volatile |
| [Part 2](./02-advanced-concurrency.md) | 6–10 | JMM, AtomicInteger, Locks, Thread pools, CompletableFuture |
| [Part 3](./03-collections-and-patterns.md) | 11–15 | ConcurrentHashMap, BlockingQueue, Synchronizers, ForkJoin, Deadlocks |
| [Part 4](./04-production-and-patterns.md) | 16–20 | Design patterns, Performance, Debugging, Production systems, Best practices |
