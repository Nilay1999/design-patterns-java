# Scenario 03 — Catalog Platform: boundaries, read scaling, CDC

> **Resume bullets covered (all one system):**
> - _"Built and owned Java Spring Boot microservices serving core domains (Products, Taxonomy, Inventory) as internal APIs for Node.js ETL pipelines, owning API contracts and query optimization for high-volume reads."_
> - _"Replaced redundant cross-service polling with a CDC pattern using async messaging (Node.js, SQS), cutting sync latency by 35% and reducing DB load across 500+ locations."_

**Connects to scenario 02:** the SKU service that the PO enrichment pipeline calls is the Products service here. One platform, not scattered projects.

---

## The scenario

> A retail distribution business, ~800 locations. Catalog data — SKUs, locations, suppliers — lives in a **single-table DynamoDB design**. The category catalog lives separately in **MySQL**. Three Spring Boot services front them as internal APIs, and a family of Node ETL pipelines consumes those APIs, including the nightly PO pipeline from scenario 02.
>
> The defining characteristic is the traffic shape: **creates and updates are rare, reads are constant.** Roughly 1500 reads per write. Nearly every design decision follows from that.
>
> Originally the pipelines polled the services on a schedule to find what had changed. That was a lot of load to discover that almost nothing had. We replaced it with change data capture.

| | |
|---|---|
| Locations | ~800 |
| SKUs | ~200k |
| Suppliers | ~20k |
| Taxonomy nodes | ~40k (MySQL) |
| DynamoDB items | ~1.2M (entities + access-pattern duplicates) |
| Writes | ~2k/day |
| Reads | ~3M/day, peak ~800 RPS in batch windows |
| **Read:write ratio** | **~1500:1** |
| Total catalog size | ~500 MB — small enough to cache in full |
| p95 end-to-end sync | 40s → 26s (**the 35%**) |

**Lead with the ratio.** "Roughly 1500 reads per write, and the whole catalog is about 500 MB" tells the interviewer in one sentence why everything downstream looks the way it does.

---

## Why two different databases

Polyglot persistence is only defensible if you can say why. Here you can:

> "The entities split cleanly by access shape. **SKUs, locations and suppliers have no relationships between them** — nothing joins. Every access is a lookup by a known key. That's a key-value workload, and putting it in a relational database means paying for a query planner and join machinery we never use.
>
> **Taxonomy is the opposite** — it's a hierarchy, and merchandising asks tree-shaped questions of it: everything under a subtree, path to root, reparenting a branch. That's genuinely relational, so it stayed in MySQL with recursive CTEs.
>
> So: key-value access to DynamoDB, hierarchical and ad-hoc queries to MySQL."

The "no joins" observation is the strongest thing you have here — **it's what makes DynamoDB obviously correct rather than a fashionable choice.** Say it early.

---

## The single-table design

Access patterns first — always state these before the schema, because that's the order you actually design in:

1. Get SKU by ID
2. Get location by ID
3. Get supplier by ID
4. List SKUs for a supplier
5. List SKUs modified since timestamp *(the ETL delta query)*

```
PK              SK              GSI1PK          GSI1SK
SKU#00123       META            SUP#0900        SKU#00123
LOC#0042        META            —               —
SUP#0900        META            —               —
```

- Generic `PK`/`SK` attributes with type prefixes, so one table holds heterogeneous entities
- **GSI overloading** — `GSI1` serves "SKUs by supplier" without a second table
- Everything is a `Query` or `GetItem`. No `Scan` in any production path.

### The honest take — have this ready

Single-table design is contested, and an interviewer who knows DynamoDB may probe whether you're pattern-matching:

> "I'd push back on my own design here. The main argument for single-table is fetching related items in one query through item collections — and these entities have **no relationships**, so that benefit doesn't apply. What we actually got was operational: one table to provision, monitor, back up and alarm on, and one consistent access layer.
>
> Multi-table would have been equally correct and arguably simpler to reason about. It was a deliberate choice, not a requirement, and I wouldn't defend it as the only option."

Most candidates defend single-table as self-evidently better. Knowing *when its main benefit doesn't apply* is the stronger signal.

---

## Architecture

```
   Catalog team           Merchandising          Supply chain
   ┌──────────┐          ┌────────────┐        ┌─────────────┐
   │ Products │          │  Taxonomy  │        │  Inventory  │  Spring Boot
   └────┬─────┘          └──────┬─────┘        └──────┬──────┘
        │                       │                     │
        └───────┬───────────────┘                     │
                │                                     │
     ┌──────────▼──────────┐          ┌───────────────▼─────────┐
     │  MySQL              │          │  DynamoDB single table  │
     │  taxonomy-catalog   │          │  SKU / LOC / SUP        │
     │  40k nodes          │          │  ~1.2M items            │
     └──────────┬──────────┘          └───────────────┬─────────┘
                │                                     │
        outbox table                         DynamoDB Streams
        (same transaction)                   (native, ordered per key)
                │                                     │
         ┌──────▼──────┐                       ┌──────▼──────┐
         │ relay       │                       │ Lambda      │
         └──────┬──────┘                       └──────┬──────┘
                └──────────────┬─────────────────────┘
                               ▼
                             SQS ────▶ Node ETL pipelines (incl. scenario 02)
                               └─────▶ Redis cache invalidation
```

---

## The 40-second pitch

> "Three Spring Boot services over two stores. SKUs, locations and suppliers sit in a single-table DynamoDB design — they have no relationships, every access is a key lookup, so there's nothing a relational database would buy us. The category catalog is in MySQL because taxonomy is a genuine hierarchy and merchandising asks tree-shaped questions of it.
>
> The traffic is roughly 1500 reads per write — the catalog barely changes but gets read constantly. So the read path is aggressively cached, and because the whole catalog is only about 500 MB, we cache effectively all of it rather than relying on TTL churn.
>
> The original integration had ETL pipelines polling on a schedule to find changes. We replaced it with CDC — DynamoDB Streams on one side, transactional outbox on the MySQL side, both landing on SQS. Consumers react to changes instead of hunting for them, and the same events invalidate the cache. End-to-end p95 dropped about 35%."

---

## Part A — service boundaries and API contracts

**Boundaries:**
> "Split by rate of change, ownership, and access shape rather than by noun. Taxonomy is owned by merchandising, changes weekly, and is hierarchical — different team, different cadence, different database. Products and Inventory are read-mostly key lookups owned by different teams over the same underlying table."

**Expect the shared-database challenge:**
> *"Two services sharing one DynamoDB table — isn't that the shared-database anti-pattern?"*
>
> "Yes, and it's a real coupling. What made it tolerable: it's read-mostly, so there's no write contention between them, and one group owned all three services so a schema change didn't need cross-team negotiation. The boundary we actually enforced was the API — no consumer touches the table directly. If those services had gone to separate teams, splitting the table would have been the first thing I'd do."

Concede the coupling, name what contained it, name the trigger to undo it.

**Contracts:**
- Additive changes in place; breaking changes get a new URI version, at most two live at once
- OpenAPI spec as the contract, clients generated from it
- Consumer-driven contract tests in CI, so a breaking change fails the *producer's* build
- Request metrics tagged by version and consumer, so retiring `/v1` is a data question rather than an email thread

*What counts as breaking:* removing or renaming a field, narrowing a type, making an optional field required, changing pagination semantics, changing an enum's meaning. Adding an optional field is safe **only if consumers ignore unknown fields**.

---

## Part B — read scaling

The whole section follows from the ratio: **rare writes, constant reads, small dataset.** That's close to the ideal caching profile, and you should say so.

**1. Cache the entire catalog, don't TTL-guess.**
> "200k SKUs at a couple of KB each is a few hundred megabytes — the whole catalog fits in Redis. So rather than a short TTL and hoping, we cached it in full and invalidated precisely on CDC events. With writes at ~2k/day, invalidation is rare, and hit rate sits well above 95%. A TTL-based cache would have been re-fetching unchanged data constantly for no reason."

This is the strongest point in the section: **at this write rate, event-driven invalidation makes near-total caching viable.** It also ties Part B to Part C.

**2. Eventually consistent reads.** DynamoDB's default. Half the read capacity cost of strongly consistent, and with writes this rare the staleness window is nearly always irrelevant. Reads that genuinely need read-after-write go strongly consistent explicitly — a small, listed set.

**3. No `Scan` in any production path.** The ETL originally wanted "all SKUs", which is a 200k-item scan. Two fixes: `BatchGetItem` for known key sets, and for bulk consumption, feed the pipelines via CDC so they maintain their own local copy and never scan at all. *That's the same change as Part C, arrived at from the read side.*

**4. Partition key distribution.** SKU ID as the partition key gives ~200k distinct keys and spreads evenly. The risk would have been keying on something low-cardinality like location — 800 keys with heavily skewed traffic is a hot-partition problem waiting to happen.

**5. Capacity mode.** With a spiky read profile — near-idle, then ~800 RPS during batch windows — on-demand avoids sizing for peak and paying for idle. Provisioned with auto-scaling is the alternative, but auto-scaling reacts in minutes and these bursts arrive in seconds.

**6. Taxonomy: load the tree, don't query it.**
> "40k nodes changing weekly. Rather than recursive CTEs on every request, the service loads the whole tree into memory at startup and rebuilds it on a CDC event. Tree traversal becomes a pointer walk instead of a database round trip. Same principle as the Redis decision — when data is small and near-static, the right optimization is not to query it repeatedly."

If pushed on MySQL tree modelling: adjacency list is what's there; closure table or materialized path are the alternatives that make subtree queries cheap at the cost of write complexity — a bad trade when the tree is fully cached anyway.

---

## Part C — CDC

**Why polling had to go:**
> "Every consumer polled every service on a schedule. Given writes were about 2k a day, essentially every poll returned data that hadn't changed. Constant load, plus freshness capped at the poll interval no matter how fast everything else ran."

**Two stores, two mechanisms — and this is the interesting part:**

> "The DynamoDB side needed no pattern at all. **DynamoDB Streams is native change capture** — an ordered, exactly-once-per-item change log, guaranteed ordered per partition key, retained 24 hours. A Lambda consumes it and publishes to SQS. There's no dual-write problem to solve because the stream *is* the commit log.
>
> MySQL has no equivalent, so that side uses the **transactional outbox** — the service writes a domain event to an outbox table in the same transaction as the state change, and a relay publishes it."

Being able to say *"one side needed the outbox and the other didn't, for this specific reason"* is much better than applying one pattern uniformly.

**Say the dual-write problem out loud — they're listening for it:**
> "The reason it's *transactional* outbox is dual writes. Commit to MySQL and then publish to SQS as two separate operations and either can fail independently — a state change nobody hears about, or an event for a change that rolled back. There's no distributed transaction across MySQL and SQS. Putting the event insert in the same local transaction makes it atomic. Publishing becomes a separate retryable step, and at-least-once is fine because consumers are idempotent."

**Spring specifics worth having:**
- The outbox insert shares the `@Transactional` boundary with the state change — same transaction, not `REQUIRES_NEW`
- **The self-invocation trap:** `@Transactional` works through proxies, so calling an annotated method from another method in the same class silently bypasses it
- The relay is idempotent — a crash after publishing but before marking the row causes redelivery, absorbed by the version guard

**Why not Debezium on the MySQL binlog?** Know it as a real alternative: no application changes and it catches everything, but it emits *row diffs*, coupling consumers to your physical schema, and Kafka Connect is real operational weight. The outbox emits domain events instead, so the schema can be refactored without breaking consumers.

**Ordering:**
> "Only per-entity ordering mattered, never global. DynamoDB Streams already guarantees order per partition key. For the MySQL side we carried the source row's version on every event and consumers ignore anything older than what they've applied. Last-write-wins with a version guard — that handles ordering and duplicate delivery in one move, without paying for FIFO queues."

**Initial snapshot plus ongoing stream:**
> "Turn CDC on and a new consumer has nothing. We recorded the stream position first, then bulk-exported current state, then streamed from that position. Changes during the export appear in both, and that overlap is exactly why consumers must be idempotent — upsert by key with the version guard makes replaying it harmless. Idempotency isn't a nicety here, it's what makes bootstrapping tractable."

**Consumer down for six hours:**
> "Different answers per store, and the asymmetry matters.
>
> SQS retains up to 14 days, so anything already published is a backlog, not data loss. At ~2k writes a day, six hours is a few hundred events — the consumer drains that in seconds.
>
> **DynamoDB Streams only retains 24 hours**, and that's the real constraint. If the Lambda that reads the stream is broken for longer than that, those changes are gone and the only recovery is a fresh snapshot. So the alarm that matters is stream iterator age, and it needs to fire with hours of headroom, not minutes.
>
> On the MySQL side the failure is quieter and worse: if the *relay* dies, services keep committing happily, the queue looks perfectly healthy, and outbox rows just pile up unpublished. Age of the oldest unpublished row is the metric people forget.
>
> And past a threshold, replaying deltas is slower than re-snapshotting — with a 500 MB catalog, a full refresh takes minutes, so that's a genuinely cheap fallback here."

The 24-hour Streams retention is a specific, real constraint that separates people who've run this from people who've read about it.

**Defending the 35%:**
> "p95 end-to-end from source commit to visible downstream, measured over a week before and after. The poll interval was one component; the pipelines' own processing dominated the rest, which is why removing a 30-second poll didn't yield a 90% cut. 40 seconds to 26."

---

## Part D — when the CDC consumer itself fails

> *"The whole CDC interface is one Lambda reading DynamoDB Streams. If that Lambda is down, is an alert your only option?"*

**No — and saying "we alert on it" is the weakest available answer.** An alarm is detection, not a solution: it still needs a human awake inside a 24-hour window. Five real answers, strongest first.

### 1. Shrink the consumer so there's almost nothing to break *(the real fix)*

First, be precise about what "the Lambda is down" means. The event source mapping is a **managed poller run by AWS** — it isn't your infrastructure and it doesn't crash. What actually happens is your *function* fails: a bad deploy, a bug, or a downstream dependency being unavailable. And on an ordered stream, a failing batch **blocks its shard** — everything behind it stalls, which is why iterator age climbs toward the 24-hour cliff.

So the architectural fix is to make the function incapable of failing for business reasons:

> "The stream consumer should do exactly one thing — read the record and write it to SQS. No transformation, no enrichment, no external calls, no business logic. Then the only way it fails is SQS being unavailable, which is about as unlikely as anything in AWS gets.
>
> All the actual processing lives in a second consumer reading from SQS. **That moves the durability boundary from 24 hours to 14 days.** A bug in the transform logic now stalls a queue you have two weeks to fix, instead of a stream you have one day to fix."

That's the answer. Push the durable buffer as close to the stream as possible, and keep everything fallible on the far side of it.

If your CDC Lambda currently does transform-and-publish in one function, **this is the change to describe** — and it's a great "what would you do differently" answer.

### 2. Configure the event source mapping so it can't block indefinitely

Most teams leave these at defaults, and the defaults are what create the 24-hour cliff. On a stream ESM:

| Setting | Why |
|---|---|
| `FunctionResponseTypes: ReportBatchItemFailures` | Partial batch response — one poison record doesn't fail the other 99 |
| `BisectBatchOnFunctionError: true` | Splits a failing batch to isolate the bad record |
| `MaximumRetryAttempts` | Default is unlimited — retries until the record expires. Cap it |
| `MaximumRecordAgeInSeconds` | Discard records older than N so a poison record can't stall the shard forever |
| `DestinationConfig.OnFailure` → SQS | Failed batches go to a DLQ instead of blocking |
| `ParallelizationFactor` (up to 10) | More concurrency per shard, faster catch-up after a backlog |

One detail worth knowing precisely, because it catches people out:

> "For stream sources the on-failure destination receives **metadata about the failed batch — shard ID and sequence numbers — not the records themselves.** So the DLQ tells you what to go re-read, and you can only act on it while those records are still inside the 24-hour window. It's a pointer, not a copy. That's a real limitation and it's another argument for getting the data into SQS as early as possible."

### 3. If 24 hours genuinely isn't enough, change the constraint

DynamoDB can feed **Kinesis Data Streams** instead of (or alongside) DynamoDB Streams, with retention configurable up to **365 days**. That removes the hard deadline outright.

The tradeoff, and you should state it:

> "DynamoDB Streams gives exactly-once delivery, ordered per partition key. Kinesis Data Streams for DynamoDB explicitly does *not* — records can arrive out of order and duplicated. So you're trading ordering guarantees for retention. That's an acceptable trade only if consumers already carry a version guard and are idempotent, which ours were — which is exactly why that discipline pays for itself twice."

### 4. Make re-snapshot a tested path, not an improvised one

If you do blow the window, recovery has to be boring:

> "**DynamoDB Export to S3** is built for this — a point-in-time export that consumes zero read capacity and doesn't touch the table's performance. It needs PITR enabled, which gives a 35-day window. With a 500 MB catalog the export and reload takes minutes.
>
> The important part is that it's a documented, periodically-rehearsed runbook rather than something invented at 2am. A recovery path you've never executed isn't a recovery path."

### 5. Alarms — but the right ones, including the blind spot

Detection still matters; just make it the last layer rather than the only one.

- **`IteratorAge` on the ESM** — the key metric. Warn at ~1 hour, page at ~4. Alarming at 20 hours out of a 24-hour budget leaves no room to act.
- **Lambda `Errors` and `Throttles`** — throttling is a common silent cause; reserved concurrency set too low on a stream consumer starves it.
- **The blind spot: a disabled event source mapping.** If someone disables the ESM, or an IaC change drops it, **there is no CloudWatch metric for it.** Iterator age stops being emitted rather than going up, so a metric alarm sees nothing and stays green. Catch it with an EventBridge rule on the CloudTrail `UpdateEventSourceMapping` / `DeleteEventSourceMapping` events, or a scheduled canary that calls the API and asserts state is `Enabled`.
- **An end-to-end heartbeat, which beats all of the above.** Write a synthetic item to the table every few minutes and assert it appears downstream within a threshold. It tests the entire path rather than individual components, and it catches the worst failure mode — a consumer that is running, healthy, emitting no errors, and silently dropping records.

> "If I could only have one signal, it'd be the synthetic heartbeat. Component metrics tell you a part is unhealthy; the heartbeat tells you the *system* stopped working, which is the thing you actually care about."

### The 30-second version

> "The Lambda being down isn't really the risk — the ESM is managed by AWS. The risk is my function failing on a batch and blocking the shard, which is what walks iterator age toward the 24-hour cliff. So: keep the stream consumer trivial and have it do nothing but write to SQS, which moves the durability boundary from 24 hours to 14 days; configure the ESM with partial batch responses, bisect-on-error, a record age limit and a failure destination so a poison record can't stall a shard; alarm on iterator age with hours of headroom, plus a synthetic heartbeat end-to-end; and keep a rehearsed re-snapshot via DynamoDB Export to S3 for when all of that fails anyway. If the 24-hour window still isn't enough, Kinesis Data Streams for DynamoDB extends retention to a year, at the cost of ordering guarantees."

---

## Weaknesses to own

**Two services share one DynamoDB table.** Real coupling, tolerable because reads dominate and one group owned both. Splitting it is the first move if ownership diverges.

**Single-table design wasn't strictly necessary.** Its main benefit — item collections for related entities — doesn't apply when nothing is related. The win was operational, and that's worth being straight about.

**DynamoDB Streams' 24-hour retention is a hard deadline.** A broken consumer over a long weekend means a full re-snapshot. The fix is architectural, not an alarm — see Part D.

**The CDC consumer did too much.** A stream consumer that transforms *and* publishes has a large failure surface sitting in front of a 24-hour window. Shrinking it to "write to SQS and nothing else" is the change I'd make first.

**The outbox relay is a single point of failure that fails silently.** Everything looks healthy while data quietly goes stale downstream.

**Event schemas had no contract tests initially.** The REST contracts had version tests in CI; the events didn't, despite being just as much a public interface. Worth naming as something you'd tighten.

---

## Trap table

| Trap | Answer |
|---|---|
| "Why DynamoDB and not a relational DB?" | No joins between SKU/location/supplier; every access is a key lookup. Taxonomy *is* relational, so it stayed in MySQL. |
| "Why single-table?" | Operational simplicity. Concede its main benefit doesn't apply without relationships, and that multi-table would have been fine. |
| "Two services on one table — anti-pattern?" | Yes, and here's what contained it, and here's the trigger to split it. |
| "How do you avoid the dual-write problem?" | Transactional outbox for MySQL. DynamoDB Streams needs none — the stream *is* the log. |
| "Why not Debezium?" | Row diffs couple consumers to physical schema; outbox emits domain events. Know it as real, not a strawman. |
| "How do you guarantee ordering?" | Streams order per partition key natively; version guard with last-write-wins on the MySQL side. |
| "Consumer down 6 hours?" | SQS fine. **Streams retain only 24h** — that's the real limit. Outbox lag is the silent one. |
| "Where did 35% come from?" | p95 end-to-end, a week either side; poll interval was one component of several. |
| "How did you optimize reads?" | Cache the whole catalog (500 MB) with event-driven invalidation, eventually-consistent reads, no Scan, tree held in memory. |
| "What's the hot-partition risk?" | SKU ID as PK gives 200k well-spread keys. Keying on location — 800 skewed keys — would have been the mistake. |

---

## Related

- `02-saga-etl-po-pipeline.md` — the ETL consuming these services
- `../02-architecture-patterns.md` — CDC, outbox, idempotency
- `../03-databases.md` — DynamoDB modelling, indexing, MySQL
- `../04-frameworks-runtimes.md` — Spring, `@Transactional` internals
