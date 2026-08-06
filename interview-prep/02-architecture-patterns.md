# Architecture Patterns — Microservices, EDA, REST, Saga, CDC, Reliability

This is the heart of your resume. Expect the deepest drilling here.

---

## 1. Microservices

### Definition worth giving
Independently deployable services organized around business capabilities, each owning its data, communicating over the network. The unit of value is **independent deployability**, not "small."

### Why (and the honest costs)
| Benefit | Cost you must acknowledge |
|---|---|
| Independent deploy/scale | Distributed system failure modes (partial failure, latency, retries) |
| Team autonomy (Conway's law) | Cross-service changes need coordination and versioning |
| Fault isolation | No cross-service transactions → sagas, eventual consistency |
| Tech heterogeneity | Operational surface: CI/CD, monitoring, tracing per service |

**Say this**: "Microservices trade a local complexity problem for a distributed one. It pays off when teams need independent release cadence or when parts of the system have very different scaling profiles — our ingestion pipeline and our catalog APIs did."

### Decomposition — how you drew boundaries (your "service boundaries" bullet)
- **Business capability / DDD bounded context**: Products, Taxonomy, Inventory are separate contexts with their own ubiquitous language. "Product" in Inventory means SKU + stock; in Taxonomy it means classification node.
- **Heuristics**: data ownership (one writer per table), transactional cohesion (what must change atomically stays together), rate of change, team ownership, and different scaling/availability needs.
- **Anti-patterns**: entity services ("UserService" that everyone calls for everything), shared database across services, chatty synchronous chains, and the **distributed monolith** (services that must be deployed together).

### Data management
- **Database per service.** Cross-service queries then need: API composition (join in the caller — fine for low fan-out), **CQRS read model** (materialize a joined view fed by events), or data duplication with events (each service keeps a local read-only replica of what it needs).
- **No shared tables** — the moment two services write the same table you've lost independent deployability and schema evolution.

### Communication
- **Synchronous** (REST/gRPC): simple, immediate consistency of read, but couples availability (`A` down ⇒ `B` degraded) and multiplies latency (`p99` compounds along chains).
- **Asynchronous** (events/commands over a broker): decoupled availability, natural buffering, but eventual consistency and harder debugging.
- Rule of thumb: **queries sync, state changes async** where you can tolerate lag.

### Resilience patterns (name them, know the failure they fix)
| Pattern | Fixes |
|---|---|
| Timeout | Unbounded waits consuming threads/connections |
| Retry + exponential backoff + **jitter** | Transient faults; jitter prevents synchronized retry storms |
| Circuit breaker (closed→open→half-open) | Hammering a downed dependency; fails fast, allows recovery |
| Bulkhead | One slow dependency exhausting the whole pool; isolate thread/conn pools per dependency |
| Rate limit / load shedding | Protecting yourself from upstream bursts; shed low-priority work early |
| Fallback / cached response | Degrade gracefully instead of erroring |
| Idempotency | Making retries safe |
| Dead letter + quarantine | Poison messages blocking a pipeline |

**Retry budget**: cap retries globally (e.g. retries ≤ 10% of requests) so a dependency's brown-out doesn't get amplified 3× by every caller in the chain — retries at multiple layers multiply (3 layers × 3 retries = 27× load).

### Contract evolution
- **Backwards-compatible changes only** in place: add optional fields, never remove/rename/retype, never tighten validation on existing fields.
- Breaking change ⇒ new version (`/v2`, or a new `detail-type` for events) and a deprecation window with usage metrics showing when the old one is dead.
- **Consumer-driven contract tests** (Pact) or schema registry compatibility checks in CI — this is how you make "owning API contracts" concrete.
- **Tolerant reader**: consumers ignore unknown fields, don't validate what they don't use.

---

## 2. Event-Driven Architecture

### The four styles (classic question: "what kind of event-driven?")
1. **Event notification** — thin event ("OrderCreated {orderId}"), consumers call back for details. Low coupling to payload shape, more chatter.
2. **Event-carrying state transfer** — event carries the data consumers need; they keep local copies. No callbacks, higher payload/coupling to schema, enables read replicas per service.
3. **Event sourcing** — the event log *is* the source of truth; state is a fold over events. Gives audit and time-travel; costs: replay complexity, versioning old events (upcasting), snapshotting.
4. **CQRS** — separate write model from read model(s), usually kept in sync by events. Use when read and write shapes/scales genuinely diverge; not required for every event-driven system.

Your ingestion pipeline is (2) with archival; your CDC work is (1)/(2) hybrid.

### Commands vs Events
- **Command**: imperative, directed at one handler, may be rejected (`ReserveInventory`).
- **Event**: past tense, fact, broadcast, cannot be rejected (`InventoryReserved`).
Naming events in past tense isn't pedantry — it prevents producers from encoding expectations about consumers.

### Choreography vs Orchestration
| | Choreography | Orchestration |
|---|---|---|
| Control | Each service reacts to events | One coordinator drives steps |
| Coupling | Low, but flow is implicit | Coordinator knows all participants |
| Visibility | Hard — flow exists only in logs/traces | Explicit, inspectable state machine |
| Change | Add consumer without touching others | Change one place |
| Best for | Simple fan-out, few steps | Multi-step business transactions with compensation (your Saga ETL) |

Good answer: "Choreography for notification-style fan-out; orchestration once a workflow has ordering, compensation, and needs to be debuggable. We used an orchestrator for the ETL saga because someone has to answer 'where did run #412 stop and what was rolled back?'"

### Delivery semantics
- **At-most-once**: fire and forget, can lose.
- **At-least-once**: the practical default (SQS, EventBridge, RabbitMQ, Kafka consumers) — duplicates possible.
- **Exactly-once**: not achievable end-to-end across systems; you achieve **effectively-once** = at-least-once delivery + idempotent consumers (+ transactional writes where available).

### Idempotency — implementation menu
1. **Natural**: `INSERT … ON CONFLICT (natural_key) DO UPDATE` / `PUT` semantics.
2. **Idempotency key store**: `messageId`/business key + status in DynamoDB (with TTL) or a unique index; write the key **in the same transaction** as the effect, or use a conditional write before doing the effect and mark complete after.
3. **Versioning**: store `version`/`event_time`; apply only if newer — also fixes out-of-order.
4. **Idempotent side effects**: for external calls (Stripe, email), pass the provider's idempotency key or record "already sent" before/after with a dedupe table.

Edge case to mention: a crash *between* the effect and marking the key done. Solutions: make the marker part of the same DB transaction, or make the effect naturally idempotent so a repeat is harmless.

### Ordering
- Guarantee ordering only where the domain needs it, keyed by the entity (`storeId`, `sku`).
- Mechanisms: FIFO groups, Kafka/Kinesis partition keys, or **version guards** to make out-of-order harmless.
- Out-of-order handling patterns: last-writer-wins with version, buffering with a reorder window, or sequence-gap detection with a fetch-on-gap.

### Big payloads → Claim check
Event carries a pointer (`s3://bucket/key`) plus enough metadata for routing/filtering; consumer fetches the body. Needed because EventBridge/SQS cap at 256 KB — directly applicable to your XML records.

### Transactional Outbox (know this even if you didn't use it — it's the standard answer)
**Problem — dual write:** writing to the DB and then publishing to a broker isn't atomic. Crash in between ⇒ state changed but no event (or event without state).

**Outbox:**
1. In the same DB transaction as the business write, insert a row into an `outbox` table.
2. A relay (poller or **log-based CDC on the outbox table**) reads new rows and publishes to the broker, marking them sent.
3. Consumers dedupe by the outbox row's ID (at-least-once).

**Inbox pattern** is the mirror image on the consumer side: record processed message IDs transactionally to guarantee idempotency.

### Schema evolution for events
- Additive, optional fields only; never repurpose a field's meaning.
- Version in `detail-type` (`OrderShipped.v2`) or a `schemaVersion` attribute.
- Compatibility modes: **backward** (new consumer reads old events), **forward** (old consumer reads new events), **full**. Choose backward+forward for long-retention buses.
- Keep the old shape flowing until consumer metrics prove nobody reads it.

### Backpressure & flow control
Queue depth is your shock absorber, but unbounded backlogs turn into unbounded latency. Controls: consumer autoscaling on queue depth/age, reserved concurrency to protect downstream DBs, batching, load shedding of low-priority events, and **age-based alerting** rather than depth alone.

---

## 3. REST API design

- **Resources & verbs**: nouns in paths, verbs from HTTP. `POST /shipments`, `GET /shipments/{id}`, `PATCH` for partial, `PUT` for full replace (idempotent), `DELETE` idempotent.
- **Status codes with meaning**: `201` + `Location`; `202 Accepted` for async work + a status resource; `204` for empty success; `400` malformed vs `422` semantically invalid; `409` conflict; `412` precondition failed (optimistic concurrency); `429` + `Retry-After`.
- **Idempotency for POST**: accept an `Idempotency-Key` header, store key→response, return the stored response on repeat. (Stripe's model — good to cite since you integrated Stripe.)
- **Pagination**: offset/limit is simple but degrades (`OFFSET 100000` scans and discards) and skips/duplicates rows under concurrent writes. **Keyset/cursor pagination** (`WHERE (created_at, id) < (:ts, :id) ORDER BY created_at DESC, id DESC LIMIT 50`) is O(1) with the right index — this is the concrete answer to "query optimization for high-volume reads."
- **Filtering/search**: predicate-style query params (`?filter[status]=ACTIVE&filter[updatedAt][gte]=…`) — connect this to your Backstage predicate-based search contribution: a structured, composable filter grammar beats ad-hoc params because it's parseable, validatable, and pushdown-able to the store.
- **Versioning**: URI (`/v1`) is bluntest and most operable; header/media-type versioning is purer but harder to debug and cache. Whatever you choose, version the *contract*, not every endpoint independently.
- **Caching & concurrency**: `ETag` + `If-None-Match` → `304`; `If-Match` → `412` for lost-update prevention; `Cache-Control` with `max-age`/`stale-while-revalidate`.
- **Errors**: RFC 7807 `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`) plus a stable machine-readable `code` and a `traceId` for support.
- **Async job pattern**: `POST /imports` → `202` + `Location: /imports/{id}` → client polls or receives a webhook. The right answer whenever work exceeds a gateway timeout (29s on API Gateway).
- **Bulk endpoints**: accept arrays with per-item results (`207`-style multi-status semantics) so one bad row doesn't fail 5,000 good ones — mirrors partial batch failure in SQS.
- **Contract-first**: OpenAPI as the source of truth, generated clients/servers, schema validated in CI, breaking-change linting.
- **REST vs gRPC vs GraphQL**: REST for public/simple/cacheable; gRPC for internal high-throughput, strict contracts, streaming, low latency; GraphQL when clients need flexible aggregation across many resources (cost: query complexity control, caching difficulty, N+1 on resolvers).
- **Security**: authn (OAuth2/JWT/mTLS) + **authorization per object** — OWASP API #1 is BOLA (broken object-level authorization: `/orders/{id}` where you forget to check ownership). Also input validation, rate limits per client, no sensitive data in URLs/logs.

---

## 4. Saga Pattern (your ETL bullet)

### Problem
A business transaction spans multiple services/resources. 2PC is off the table: it blocks, it needs XA support across heterogeneous stores, and coordinator failure holds locks — unacceptable across HTTP/queue boundaries and unavailable for most cloud services.

### Definition
A saga is a sequence of **local transactions**, each publishing an event/command that triggers the next. If step *k* fails, the saga runs **compensating transactions** for steps *k−1 … 1* in reverse.

### Compensation is semantic, not `ROLLBACK`
You cannot un-commit; you issue a business-meaningful inverse: release a reservation, issue a credit note, mark a row `VOIDED`, delete an uploaded file. Design rules:
- Compensations must be **idempotent** (they will be retried).
- Compensations must be **retriable forever** — they can't themselves fail permanently, or you need manual intervention with an alert.
- Classify steps: **compensatable** (can be undone) → **pivot** (the point of no return; after it, only forward) → **retriable** (must eventually succeed). Order your saga so the pivot is as late as possible.

### Isolation problem: sagas are ACD, not ACID
Intermediate states are visible to others → anomalies (dirty reads, lost updates). **Countermeasures** (Richardson's list — quoting these lands well):
- **Semantic lock**: mark records `PENDING`/`IN_PROGRESS` so others skip or wait.
- **Commutative updates**: use `+= / -=` deltas so ordering matters less (credit/debit rather than set-absolute).
- **Pessimistic view**: reorder steps so the risky visible state is minimized.
- **Reread value**: re-read and verify unchanged before writing (optimistic check).
- **Version file**: record operations and reorder/replay them if they arrive out of order.
- **By value**: route low-risk requests through saga, high-risk ones through a stricter (2PC/manual) path.

### Orchestration vs Choreography saga
- **Orchestrated** (what you should describe): a coordinator persists saga state (`saga_instances` + `saga_steps` tables or a Step Functions execution), issues commands, handles replies, and drives compensation. Recoverable after crash by reading state and resuming.
- **Choreographed**: services react to each other's events; no central state — cheaper for 2–3 steps, opaque beyond that.

### Implementation checklist for your ETL saga
1. **Run record**: `etl_runs(id, status, started_at, source_file, counters)`.
2. **Step log**: `etl_run_steps(run_id, step, status, attempt, error, compensated_at)` — this *is* the saga log; it makes runs resumable and auditable.
3. **Idempotent steps** keyed by `(run_id, step, row_key)`.
4. **Concurrent SKU resolution**: bounded parallelism (worker pool / `Promise.allSettled` with a concurrency limiter), per-SKU locking to avoid two workers resolving the same SKU — `SELECT … FOR UPDATE SKIP LOCKED` or an advisory lock or a unique constraint + conflict handling.
5. **Retries**: per-row retry with backoff; row-level failures collected rather than aborting the run (partial success), then written to a rejects file in S3 with reason codes.
6. **Compensation**: for rows already committed downstream when a later step fails — reverse writes, delete uploaded S3 objects (or write a tombstone), mark run `ROLLED_BACK`.
7. **Observability**: counters (`rows_in`, `rows_ok`, `rows_failed`, `rows_compensated`), duration per step, alert on runs stuck in `IN_PROGRESS` past an SLA.

### Likely follow-ups
- *"What if a compensation fails?"* → retry with backoff, then park the saga in `NEEDS_MANUAL_INTERVENTION`, alert, and expose an admin endpoint to resume/force-complete. Never silently drop.
- *"How do you prevent double-processing after a crash mid-run?"* → step log + idempotency keys; on restart, resume from the last completed step, and every write is upsert-shaped.
- *"Why not just one big DB transaction?"* → the work spans HTTP calls and S3; holding a DB transaction across network I/O for 50k rows would hold locks for minutes, blow up connection pools, and still can't roll back S3 or a remote API.

---

## 5. Change Data Capture (your 35%-latency bullet)

### What CDC is
Capturing row-level changes from a database and delivering them as a stream of change events, instead of consumers polling for "what changed since X."

### Three implementations
| Approach | How | Pros | Cons |
|---|---|---|---|
| **Query-based (polling)** | `WHERE updated_at > :last` on a schedule | Trivial, no infra | Misses deletes, misses intra-poll intermediate states, load on primary, latency = poll interval, clock/`updated_at` skew |
| **Trigger-based** | DB triggers write to an audit/change table | Captures deletes, in-transaction consistency | Write amplification, trigger maintenance, hurts write latency |
| **Log-based** | Read WAL/binlog/oplog | Lowest overhead on the DB, complete + ordered, captures deletes | Operationally heavier (replication slots, connectors), exposes internal schema |

### Log-based specifics
- **Postgres**: logical replication — `wal_level=logical`, a **publication** defines tables, a **replication slot** tracks consumer position, output plugin (`pgoutput`, `wal2json`) decodes. `REPLICA IDENTITY FULL` if you need before-images. **Danger**: an inactive slot pins WAL and can fill the disk — monitor `pg_replication_slots.confirmed_flush_lsn` lag.
- **MySQL**: row-based binlog + GTIDs.
- **MongoDB**: change streams over the oplog, with resume tokens.
- **DynamoDB**: Streams (24h) → Lambda/Pipes.
- **AWS-native path**: DMS (full load + CDC) → Kinesis/MSK/S3; or Debezium on MSK Connect.

### Snapshot + stream
Any CDC rollout needs an initial **snapshot** (consistent full copy) then switch to streaming from the snapshot's LSN/position, or **incremental snapshotting** (Debezium's watermark-based approach) to avoid long locks.

### Semantics to speak to
- At-least-once → consumers idempotent, keyed by PK + LSN/version.
- Ordering is per table/PK (partition by PK to preserve it).
- **Deletes** arrive as delete events (and tombstones in compacted topics).
- **Schema changes** flow through too — consumers need tolerant readers and a plan for column drops.

### CDC vs Outbox (a favourite senior question)
Raw CDC leaks your **internal schema** as a public contract — rename a column and every consumer breaks. **Outbox** publishes deliberate *domain events* written transactionally by the owning service. The best of both: **CDC on the outbox table** — atomic with the business write and a curated contract.

### How to narrate your bullet honestly
"Services were polling each other on a fixed interval to detect changes across 500+ locations, which meant constant load for mostly-unchanged data and a worst-case staleness equal to the poll interval. We replaced it with change events published on write and delivered via SQS, so consumers update on change rather than on schedule. End-to-end sync latency dropped ~35% (measured as p95 from source commit time to consumer applied time), and read load on the source DB fell because the periodic scans went away."
Be ready to say **which flavour** it was — if it was application-emitted change events, call it "outbox-style / application-level CDC," not log-based. Interviewers respect the precision far more than the buzzword.

---

## 6. Reliability & LLD toolkit ("retry semantics, fault tolerance, data contracts")

### Retries done right
- Retry **only** transient/retriable errors (timeouts, 429, 5xx, connection resets, `ProvisionedThroughputExceeded`) — never `400/422` validation failures or business rejections.
- **Exponential backoff with full jitter**: `sleep = random(0, min(cap, base * 2^attempt))`. Jitter is the part people forget; without it, retries synchronize into waves.
- Cap attempts *and* total elapsed time; propagate a **deadline** so a retry doesn't outlive the caller's patience.
- Retries require idempotency — say the two in the same breath.
- Beware **retry amplification** across layers; prefer retrying at one layer (usually the closest to the failure) plus a queue for the rest.

### Timeouts
Every network call gets a connect and a read timeout, derived from your latency SLO (e.g. p99 downstream 200 ms ⇒ timeout 1 s, not 30 s). Default HTTP client timeouts (often infinite) are a top production incident cause.

### Circuit breaker
Closed → (failure rate/slow-call threshold breached) → Open (fail fast, no calls) → after cooldown → Half-open (limited probes) → Closed or Open again. Pair with a fallback and with bulkheads so one dependency can't eat all threads/connections.

### Failure isolation checklist for a pipeline
- Poison message → DLQ + alarm + redrive.
- Partial batch failure → report per-item failures.
- Downstream outage → queue absorbs; alert on **age**, not just depth.
- Data corruption → validate at the edge, quarantine rejects with reason codes, never crash-loop on them.
- Duplicate → idempotency key.
- Out-of-order → version guard.
- Silent stall → heartbeat/freshness metric ("time since last successful record"), because zero errors and zero throughput looks healthy on error dashboards.

### Data contracts (make this concrete — it's a resume phrase)
A data contract specifies: **schema** (JSON Schema/Avro/XSD), **semantics** (field meanings, units, timezone, nullability), **ownership** (producing team, on-call), **compatibility policy** (backward/forward), **delivery guarantees** (at-least-once, ordering key), **freshness SLA** (p95 lag), **volume expectations**, and **PII classification/retention**. Enforcement points: producer-side validation in CI, edge validation on ingest (API Gateway models / XSD validation), and contract tests between producer and consumer.

### Consistency vocabulary
CAP (under partition: consistency or availability), **PACELC** (else: latency vs consistency), strong vs eventual vs causal vs read-your-writes, monotonic reads. Use PACELC when discussing Aurora replicas: "we accept replica lag (EL: latency over consistency) for catalog reads, but inventory decrement reads go to the writer."

---

## Rapid-fire Q&A

**Q: When would you *not* use microservices?**
Small team, single deploy cadence, unclear domain boundaries, or an early product where the boundaries will move. A modular monolith with clean module interfaces gets most of the design benefit with none of the distributed cost, and can be split later along the seams that proved stable.

**Q: Saga vs 2PC?**
2PC gives atomicity and isolation but blocks on coordinator failure, requires XA-capable participants, and doesn't exist for S3/HTTP APIs. Sagas give availability and heterogeneity at the cost of isolation, which you patch with semantic locks and versioning.

**Q: How do you handle a consumer that must not process an event twice, but the side effect is an external email?**
Record intent transactionally (outbox/idempotency row with `SENT`/`PENDING`), send with the provider's idempotency key, mark sent. Worst case you send twice only if the provider lacks idempotency and you crash at the exact wrong moment — then choose "at most once" (mark before sending) if a missed email is safer than a duplicate, and make that trade-off explicit with the business.

**Q: Your event schema needs a breaking change. Rollout plan?**
Emit both shapes (v1 and v2) for a deprecation window, or make v2 additive and let tolerant readers ignore extras. Track per-consumer usage metrics on the old detail-type; when they hit zero and the window expires, stop emitting v1. Never break and coordinate a big-bang cutover across teams.

**Q: How do you test an event-driven system?**
Unit tests on handlers with fixture events; contract tests against the schema registry; component tests with LocalStack/Testcontainers (SQS, Postgres); replay of recorded production events into a staging bus; chaos on the failure paths (force DLQ, force duplicate, force out-of-order) — the failure paths are the ones that never get tested and always fire at 3 a.m.
