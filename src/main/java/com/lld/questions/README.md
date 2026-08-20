# LLD / Machine-Coding Question Bank

Ordered **medium → hard**. Each entry names the core data-structure/concurrency challenge being tested — that's what an interviewer is actually grading, not the domain story. `✅` = solved in [`solutions/`](solutions/) (code + README).

## Medium

| # | Question | What it's really testing |
|---|---|---|
| 1 | **Parking Lot System** ✅ | Strategy pattern for pricing/spot-assignment, OOP modeling of vehicle/spot types — [`solutions/parkingsystem/`](solutions/parkingsystem/) |
| 2 | **Elevator System** ✅ | State machine (idle/moving/door), request-scheduling algorithm (SCAN-like) — [`solutions/elevatorsystem/`](solutions/elevatorsystem/) |
| 3 | **Library Management System** ✅ | Plain OOP modeling — books, members, holds, fines; not algorithmically hard — [`solutions/librarymanagement/`](solutions/librarymanagement/) |
| 4 | **Vending Machine** ✅ | State machine design (idle → selecting → dispensing → returning change) — [`solutions/vendingmachine/`](solutions/vendingmachine/) |
| 5 | **ATM Machine** ✅ | State machine + simple transaction rollback on failure — [`solutions/atmmachine/`](solutions/atmmachine/) |
| 6 | **Tic-Tac-Toe / Connect Four** | Clean board abstraction + win-condition checking, extensible board size |
| 7 | **LRU Cache** | `HashMap` + doubly linked list, O(1) get/put — classic but always asked |
| 8 | **Splitwise (Expense Sharing)** | Graph/ledger modeling, debt simplification algorithm |
| 9 | **Logging Framework** ✅ | Builder/strategy pattern for sinks + levels, extensibility over performance — [`solutions/logger/`](solutions/logger/) |
| 10 | **Hotel/Meeting Room Booking** | Interval scheduling, overlap detection (sweep line or interval tree) |

## Hard

| # | Question | What it's really testing |
|---|---|---|
| 11 | **In-Memory SQL Database (with Indexing)** ✅ | Index consistency, query planner, strategy pattern across index types — [`solutions/inmemorysql/`](solutions/inmemorysql/1.in-memory-sql-database.md) |
| 12 | **In-Memory MongoDB (Document Store)** | Recursive document matcher, dynamic schema, nested-field indexing |
| 13 | **Rate Limiter (token bucket / sliding window)** | Per-key state + concurrency-safe counters, distributed variant with Redis |
| 14 | **Distributed Cache (Redis-lite)** | Eviction policy + TTL expiry + thread-safety, all composed cleanly |
| 15 | **Movie/Event Ticket Booking (BookMyShow-style)** | Seat-locking under concurrency, double-booking prevention, hold-then-confirm flow |
| 16 | **Ride-Hailing Matching (Uber/Ola)** | Geo-indexing (geohash/quadtree), nearest-driver matching at scale |
| 17 | **Stock Exchange / Order Matching Engine** | Price-time priority order book, two heaps (bid/ask), matching loop correctness |
| 18 | **Distributed Job Scheduler** | Priority queue + delayed execution + retry/backoff, idempotent re-runs |
| 19 | **Chess Engine (move validation)** | Polymorphic piece movement rules, check/checkmate detection, board state |
| 20 | **Pub-Sub / Message Broker (Kafka-lite)** | Topic-partition modeling, consumer offsets, at-least-once delivery semantics |
| 21 | **In-Memory File System** | Tree structure (dir/file nodes), path resolution, recursive size/search ops |
| 22 | **Search Autocomplete** | Trie with weighted/frequency-ranked suggestions, prefix traversal |
| 23 | **Distributed Lock Manager** | Lease/TTL-based locks, fencing tokens, handling crashed lock holders |
| 24 | **Notification System (multi-channel)** | Strategy pattern per channel, retry + dedup + rate-limit-per-user |
| 25 | **Thread-Safe LRU Cache** | Same as #7 but under concurrent access — lock granularity, `ConcurrentHashMap` + segment locks vs single lock |

## DSA-Heavy (data-structure-first)

LLD rounds where the OOP modeling is thin and the *crux is one data structure or algorithm*. The interviewer grades whether you reach for the right structure and hit the target complexity — "design" here is picking the DS, wiring it cleanly, and keeping it consistent under mutation.

**All of these are sized for a 60–90 min round** — one core structure, ~40–120 lines, buildable end-to-end with a `main()` demo. Time estimates assume you *name the DS first, then code*. If you can't finish the crux in ~45 min, it doesn't belong in this round.

| # | Question | Core DS / algorithm | Target complexity | Build |
|---|---|---|---|---|
| 26 | **LRU Cache** | `HashMap` + doubly linked list | O(1) get/put | ~40 min |
| 27 | **Insert/Delete/GetRandom O(1)** | array + `HashMap<val,index>`, swap-with-last on delete | O(1) all | ~30 min |
| 28 | **Min/Max Stack** | value stack + monotonic auxiliary stack | O(1) push/pop/min | ~20 min |
| 29 | **Time-Based Key-Value Store** | `HashMap<key, List<(ts,val)>>` + binary search | O(log n) get | ~30 min |
| 30 | **Median from a Data Stream** | two heaps (max-heap low / min-heap high), balanced | O(log n) add, O(1) median | ~30 min |
| 31 | **Circular / Ring Buffer (bounded queue)** | fixed array + head/tail modular indices | O(1) enqueue/dequeue | ~25 min |
| 32 | **Tic-Tac-Toe with O(1) win check** | per-row/col/diagonal running counters | O(1) per move | ~30 min |
| 33 | **My Calendar (no double-book)** | `TreeMap<start,end>` + `floor`/`ceiling` overlap check | O(log n) book | ~40 min |
| 34 | **Text Editor Undo/Redo** | two stacks (or command pattern) | O(1) undo/redo step | ~40 min |
| 35 | **Trie Autocomplete** | trie nodes + DFS, top-k by frequency | O(prefix) traversal | ~45 min |
| 36 | **LFU Cache** | `HashMap` + freq→DLL buckets, min-freq pointer | O(1) get/put | ~60 min |
| 37 | **In-Memory File System** | tree of dir/file nodes, path split + recursive ops | O(path depth) | ~60 min |

Each is a clean **Tier 1 = core DS / Tier 2 = the O(1)-or-O(log n) trick / Tier 3 = one extension** build, same as #11/#12.

**Too heavy for a 60–90 min round — know the approach, don't build from scratch.** These come up as *follow-up discussion* or HLD, not as the thing you code start-to-finish:

- **Skip list from scratch** — probabilistic levels + pointer surgery eats the whole clock; in a round just say "`TreeMap` gives me O(log n) ordered ops, skip list is the lock-free alternative."
- **Consistent hashing ring w/ virtual nodes** — that's an HLD/sharding topic; a `TreeMap` + `ceilingKey` sketch is enough to *explain*, not a full machine-coding build.
- **Bloom filter with tuned false-positive rate** — the bit-math and k-hash derivation is a whiteboard discussion, not runnable-in-45-min.
- **Order-statistics / leaderboard top-K at scale** — mention `TreeMap` or bucketed counts; a balanced order-statistics tree from scratch is out of scope.

Overlap note: #26 (LRU), #35 (autocomplete), #37 (file system) also appear in the "system" Hard table (#7/#25, #22, #21) — same crux, framed here as the pure DS exercise.

---

## How to run a 60–90 min LLD round

The clock is the real enemy. This split keeps you out of the two classic failure modes — coding before agreeing on scope, and polishing entities while the crux goes unwritten:

1. **Requirements & scope cut (5–10 min).** Restate the problem, list 4–6 functional requirements, and explicitly say what you're *deferring* ("I'll skip payments and multi-floor for now, add if time permits"). Interviewers grade the scope cut itself.
2. **Core entities + relationships (5–10 min).** Name the classes and enums, sketch relationships verbally or as a rough diagram. Don't write full class bodies yet.
3. **Interfaces at the extension points (5 min).** Wherever the interviewer will say "now add a new X" — pricing rules, log sinks, notification channels, spot matchers — put an interface/strategy there *before* being asked. This is the single highest-signal move in the round.
4. **Code the crux first (30–45 min).** Implement the one thing the problem is really testing (middle column of the tables above) end-to-end before filling in getters, validation, or secondary flows. A working `rentBook()` with an anemic `Member` beats a perfect entity model with an unwritten crux.
5. **Demo + walk through a failure case (5–10 min).** Run a `main()` that exercises the happy path, then narrate one failure path (double-booking attempt, dispense failure, concurrent access) even if you don't code it.

**SDE-2 vs SSE grading, roughly:** SDE-2 = clean OOP, right data structures, working code, sensible names. SSE adds: concurrency awareness (where are the race conditions, what would you lock), justifying *against* patterns ("a factory is overkill here because…"), and driving the requirements conversation instead of waiting to be steered. If you're targeting SSE, practice saying the thread-safety plan out loud for every problem even when single-threaded code is accepted.

**Common LLD follow-ups to pre-think for every problem:** "make it thread-safe" (lock granularity), "add a new type of X" (should already be an interface), "persist it" (repository boundary), "what would you test first."

---

Solved-solution write-ups follow the tiered format (Tier 1 = working baseline, Tier 2 = the headline feature, Tier 3 = stretch). Next candidates to solve: #12 (In-Memory MongoDB), #15 (Ticket Booking — highest-frequency hard question in Indian product-company loops), #13 (Rate Limiter).
