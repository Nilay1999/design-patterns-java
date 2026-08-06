# Project Deep Dives — Every Resume Bullet, Drilled

Format per bullet: **the claim → the architecture you should be able to draw → the story (STAR) → drill-down Q&A → number defense → the trap.**

Fill the bracketed placeholders with your real specifics *before* the interview — the structure is right, only you know the exact values.

---

## Elevation Services — Senior Software Engineer (Sep 2023 – present)

### Bullet 1 — "Event-driven ingestion pipeline processing 1M+ XML records through EventBridge, SQS, and Lambda… schema validation, S3 archival, Aurora metadata persistence for auditability"

**Draw this:**
```
Partner/Source ──▶ S3 (raw/dt=…/source=…)  ──(S3 Notification)──▶ Splitter (Lambda/Fargate)
                                                                     │ streams XML, emits per-record events
                                                                     ▼
                                                            EventBridge custom bus
                                                    ┌──────────── rules by source/detail-type ───────────┐
                                                    ▼                                                   ▼
                                              SQS (shipments)                                   SQS (orders)
                                                    ▼                                                   ▼
                                          Lambda consumer (validate → transform → upsert)      Lambda consumer
                                                    │                    │                             │
                                              Aurora (metadata)     S3 (archive + rejects)         DLQ + alarm
```

**STAR:**
- **S**: Partner shipment/order data arrived as large XML files; the legacy path was a monolithic batch job that failed as a unit, gave no per-record visibility, and couldn't be audited or replayed.
- **T**: Ingest at scale with per-record fault isolation, provable auditability, and no data loss.
- **A**: Landed raw files in S3 with partitioned prefixes; streamed and split them into per-record events; routed by `source`/`detail-type` on an EventBridge bus so new consumers could subscribe without touching the producer; buffered per consumer with SQS so a slow or broken consumer couldn't drop events; validated each record against a schema before persisting; upserted metadata into Aurora keyed by the natural business ID (idempotent); archived the raw payload in S3 with lifecycle rules; sent invalid records to a rejects location with reason codes and failures to a DLQ with alerting.
- **R**: [1M+ records processed], per-record failure isolation instead of whole-batch failure, replayable from S3/archive, and an audit trail linking every stored record back to its raw source object.

**Drill-downs:**
- *Why EventBridge and not SQS directly?* Routing and fan-out: one publisher, several consumers with different filters, without the producer knowing them. SQS alone would have meant the producer publishing to N queues (coupling) or a fan-out Lambda (custom glue).
- *XML can be megabytes; SQS caps at 256 KB.* Claim check: the event carries the S3 key plus routing metadata; the consumer fetches the body. Same reason the bus payload stays small.
- *Where does schema validation happen?* At the boundary, before any persistence: XSD/JSON Schema validation on the record, with structured reject reasons. Invalid data never enters Aurora, and rejects are reportable back to the producing partner.
- *Duplicates?* At-least-once everywhere, so consumers upsert on a natural key and/or check an idempotency key; a redelivered record produces the same final state.
- *Ordering?* Not required per record for ingestion; where a later update could overwrite a newer one we guard with a version/timestamp comparison. If per-entity ordering had been required, FIFO with `MessageGroupId` = entity ID.
- *How do you know nothing was lost?* Reconciliation: records parsed from the source file vs persisted + rejected + DLQ. That count is the auditability claim, not just "we log."
- *Aurora connection storms from Lambda?* Reserved concurrency on the consumer (and/or RDS Proxy), batched writes, short transactions.
- *How would you reprocess a bad day?* Re-drive the DLQ, replay from the EventBridge archive, or re-run the splitter over the S3 prefix for that date — all three exist because the raw data is retained.

**Number defense:** know the timeframe (1M+ over what — a launch/backfill/monthly volume?), the file size distribution, the peak per-hour rate, and the average vs peak concurrency.

**Trap:** interviewers love asking "why not just Step Functions Distributed Map?" — the good answer acknowledges it's purpose-built for exactly this (S3-driven, 10k parallel children, tolerated failure %), and explains your choice on ownership/latency/cost grounds rather than pretending it doesn't exist.

---

### Bullet 2 — "Serverless event-driven pipelines… owning LLD for data contracts, retry semantics, and fault tolerance, processing 50k+ events daily at 99.9% reliability"

**What "owning the LLD" must mean concretely:**
- **Data contracts**: documented schema per event type (fields, types, required-ness, units, timezone), version policy (additive-only; breaking = new `detail-type`), ownership and on-call per producer, delivery guarantee (at-least-once), ordering key, freshness SLA, PII classification and retention.
- **Retry semantics**: classification of retriable vs terminal errors; exponential backoff with jitter; attempt caps; visibility timeout tuned to worst-case processing time; partial batch failure reporting; DLQ with `maxReceiveCount`; documented redrive procedure.
- **Fault tolerance**: idempotent consumers, timeouts on every external call, reserved concurrency to protect the database, poison-message quarantine, alerting on age-of-oldest-message and DLQ depth, and a runbook per alert.

**SLO framing (see `07-observability.md`):** the SLI was records successfully persisted / records received; 99.9% at 50k/day ≈ 50 failed records/day budget, all of them landing in the DLQ/rejects with a reason. Freshness (p95 event-time → applied-time) was tracked alongside, because a pipeline can be 100% "successful" and hours stale.

**Drill-downs:**
- *50k/day is under 1/sec average — where's the difficulty?* Bursts. Say the peak: files arrive in windows, so the pipeline sees [X]/minute for [Y] minutes. Serverless absorbs the burst; the DB is the thing that needs protecting.
- *What broke in production?* Have one: a poison record type that failed validation and consumed retries until DLQ; a visibility timeout shorter than a slow downstream call causing duplicate processing; a downstream outage filling the queue. Explain detection → fix → prevention (alert added, timeout adjusted, idempotency hardened).
- *What was the hardest contract negotiation?* A producer wanting to rename/repurpose a field. Answer: additive change + deprecation window + usage metrics.

---

### Bullet 3 — "Java Spring Boot microservices (Products, Taxonomy, Inventory) as internal APIs for Node.js ETL pipelines… API contracts, ORM transaction boundaries, query optimization for high-volume reads"

**Boundaries story:** Products (catalog master data), Taxonomy (classification hierarchy), Inventory (stock by location) are distinct bounded contexts — different write patterns, different consistency needs, different change cadence. Each owns its schema; nothing else writes to its tables.

**API contracts:** OpenAPI-first, additive evolution, versioned where breaking, standard error shape, pagination and filtering conventions shared across services, contract tests in CI so a Node consumer's expectations break the build rather than production.

**Transaction boundaries (expect deep JPA/Spring questions — see `04-frameworks-runtimes.md`):**
- `@Transactional` at the service/use-case layer, never in controllers or repositories.
- No network I/O (S3, HTTP, queue publish) inside a transaction; events published after commit.
- `readOnly = true` on query paths.
- Propagation understood (`REQUIRES_NEW` for audit writes that must survive a rollback).
- Optimistic locking (`@Version`) on concurrently-updated entities like inventory rows.

**High-volume reads — what you actually did:**
1. Killed N+1 with entity graphs/join fetch and DTO projections.
2. Keyset pagination for large listings instead of `OFFSET`.
3. Composite/covering indexes derived from `pg_stat_statements` top queries, verified with `EXPLAIN (ANALYZE, BUFFERS)`.
4. Batch endpoints (`GET /products?ids=…` or POST-with-filter) so the ETL fetched 500 SKUs in one round trip instead of 500 calls.
5. Cache for hot, slow-changing reads (taxonomy tree) with event-driven invalidation.
6. HikariCP pool sizing + short transactions to stop pool exhaustion under ETL bursts.

**Drill-downs:**
- *Why internal REST rather than sharing the DB?* Ownership and evolvability: shared tables mean any schema change is a cross-team deployment, and there's no place to enforce invariants.
- *Chatty ETL over HTTP is slow — how did you handle it?* Batch endpoints, gzip, keep-alive connections, pagination with a stable sort, and pushing filters server-side so the ETL never downloads what it discards.
- *What if Products is down when the ETL runs?* Retry with backoff, circuit breaker, and idempotent resume from the last checkpoint rather than restarting the run.

---

### Bullet 4 — "Replaced redundant cross-service polling with a CDC pattern using async messaging (Node.js, SQS), cutting sync latency by 35% and reducing DB load across 500+ locations"

**Before:** consumers polled on a fixed interval to detect changes → constant query load proportional to `locations × poll frequency`, mostly returning unchanged rows; worst-case staleness = the poll interval; scaling locations made it worse linearly.

**After:** the owning service emits change events on write (outbox-style / application-level CDC), delivered via SQS to consumers that apply the delta. Consumers update on change, not on schedule.

**Drill-downs:**
- *Log-based CDC or application-emitted?* Be precise. If you emitted events from the application, say "outbox-style/application-level change events" and explain why: no replication-slot ops burden, a curated domain contract instead of leaking internal columns. Then show you know log-based CDC (WAL/Debezium) and its tradeoffs — see `02-architecture-patterns.md`.
- *How did you avoid the dual-write problem?* Either the outbox table written in the same transaction as the business change, or (honestly) "we published after commit and accepted a small window, mitigated by a reconciliation sweep" — that honest version plus knowing the correct pattern beats a shaky claim.
- *Ordering and duplicates?* Consumers are idempotent (upsert by key + version guard); ordering enforced per entity where required by group/partition key, otherwise a version comparison makes out-of-order harmless.
- *What if a consumer is down for two hours?* SQS holds the backlog (up to 14 days); consumer catches up on restart; alerting on age-of-oldest-message tells us before staleness becomes a business problem. A periodic full reconciliation catches anything permanently missed.
- *Where did 35% come from?* Define the metric: p95 (or mean) of source-commit-time → consumer-applied-time, measured before and after over comparable windows. Also quantify the load drop: previous polls/day vs event volume/day.

**Trap:** "CDC" is a loaded term; if the interviewer is a data engineer, they'll assume Debezium/WAL. Pre-empt it in one sentence: "we used change *events* at the application boundary rather than log-based CDC, for these reasons…"

---

### Bullet 5 — "ETL pipeline using the Saga pattern for transactional rollback and concurrent SKU resolution, processing 50k+ rows per run with automated S3 uploads and retry support for failed records"

**Draw this:**
```
Trigger (schedule/file arrival)
   ▼
Run orchestrator ──▶ etl_runs / etl_run_steps  (saga log: status, attempt, error, compensated_at)
   │  step 1: extract + parse rows            → compensate: nothing (read-only)
   │  step 2: resolve SKUs (concurrent, bounded pool, per-SKU lock)  → compensate: release reservations
   │  step 3: persist to target (batched upserts)                    → compensate: reverse/mark VOID
   │  step 4: upload result file to S3                               → compensate: delete/tombstone object
   ▼
Rejects file (S3) + retry queue for failed rows + metrics per step
```

**Why saga:** the run spans a database, HTTP calls to internal services, and S3 — no single transaction can cover that, and holding a DB transaction across 50k rows of network work would hold locks for minutes and exhaust the pool. So: local transactions per step + compensations.

**Concurrent SKU resolution — the part they'll dig into:**
- Bounded concurrency (worker pool of N) rather than unbounded parallelism, so downstream services and the DB pool aren't overwhelmed.
- Per-SKU exclusivity so two workers never resolve the same SKU: `SELECT … FOR UPDATE SKIP LOCKED` on a staging table, or an advisory lock keyed by SKU hash, or a unique constraint that turns a race into a harmless conflict.
- Per-row failure isolation: a failed row is recorded and retried, it does not abort the run (`Promise.allSettled`-style semantics).
- Backpressure: batch size tuned so memory stays flat while streaming 50k rows (never load the whole set into an array).

**Retries and resumability:** each row keyed by `(run_id, row_key)`; steps idempotent so a crashed run resumes from the step log instead of restarting; failed rows go to a retry path with backoff and a cap, then to a rejects file with reason codes for the business to review.

**Drill-downs:**
- *What exactly gets compensated?* Give a concrete example: rows already written to the target when step 4 fails get marked reversed/voided, and any uploaded artifact is removed so downstream systems never see a partial run.
- *What if a compensation fails?* Retry with backoff, then park the run as `NEEDS_MANUAL_INTERVENTION` with an alert; never silently continue.
- *Isolation problem?* Sagas are ACD — other readers can see intermediate state. Mitigation: semantic lock (`IN_PROGRESS` status filtered out of consumer queries) and only publishing "run complete" once the saga finished.
- *Why not Step Functions?* Fair question — answer on ownership/cost/latency and mention that its `Catch` → compensation model is exactly this pattern, made durable and visual.
- *50k rows per run — how long, and what's the bottleneck?* Have the number ([X] minutes), the bottleneck ([downstream API rate limit / DB writes]), and what you'd do for 10× ([partitioned parallel runs / Distributed Map / bulk COPY]).

---

### Bullet 6 — "Mentored junior engineers through code and design reviews, and drove technical direction on service boundaries and data contracts"

Prepare **two concrete stories** (behavioral rounds are scored, and vague answers here cost senior offers):
1. **Mentoring**: a specific engineer, what they struggled with (e.g. transactions spanning HTTP calls, or untestable services), what you changed in how you reviewed (pairing on the first PR, review comments phrased as questions, a small design doc template), and the outcome (they shipped X independently within Y weeks).
2. **Technical direction with disagreement**: a boundary/contract call where you and someone else disagreed, how you resolved it (spike, data, a written trade-off doc, deferring the decision behind an interface), and — ideally — one where you were wrong and changed course. Senior signal is "how you decide," not "how often you win."

Also be ready for: how you keep reviews from becoming bottlenecks, how you handle a PR that's the wrong design (talk before comments), and what you standardized (contract templates, definition of done, runbooks).

---

## Kevit.io — Software Developer (Nov 2022 – Sep 2023)

### Bullet 7 — "Migrated auth from basic JWT to Ory Kratos across 6 NestJS microservices in an NX monorepo on AWS EKS with RabbitMQ"

Detail lives in `08-auth-integrations.md` (Kratos) and `06-devops-k8s.md` (NX/EKS). What to have ready here:
- **Why**: no revocation, no recovery/verification flows, no MFA, auth logic duplicated in 6 services.
- **Where validation moved**: to the edge (gateway/Oathkeeper or a shared Nest guard in a monorepo lib), so services consumed a verified identity rather than each parsing tokens. In a monorepo, the shared auth lib + shared DTO lib is the real win — one implementation, six consumers, enforced by module boundary lint rules.
- **Zero-downtime cutover**: dual acceptance window (old JWT + Kratos session) → migrate identities with hashes preserved → flip default → expire old tokens → remove old code path. Feature-flagged, with rollback.
- **Verification**: login success rate, session error rate, synthetic login canary, support ticket volume.
- **Drill-downs**: what happened to users mid-session at cutover (grace window); how you tested (staging with imported prod-shaped data, contract tests per service); what you'd do differently (start with the shared auth lib before touching flows; migrate one low-risk service first).

### Bullet 8 — "Stripe membership payment integration end-to-end, including webhook handling, subscription lifecycle for 15k+ users"

Full detail in `08-auth-integrations.md`. Have ready: the signature-verification-on-raw-body bug, `event.id` dedupe table, out-of-order handling by re-fetching the subscription, the reconciliation job, `past_due` grace-period behaviour, and how entitlements were checked at request time (cached status, refreshed by webhooks).
- *Likely question*: "A customer says they paid but has no access. Debug it." → check the Stripe dashboard for the payment/subscription state → check your processed-events table for the corresponding `event.id` → check DLQ/error logs for the handler → re-fetch and reapply entitlement (idempotent repair endpoint) → then ask why the automated reconciliation didn't catch it.

### Bullet 9 — "Integrated Cal.com scheduling and Directus CMS APIs, adding dedicated microservices with isolated RDS databases"

Have ready: anti-corruption layer, webhook handling with retries/idempotency, timezone/DST correctness, double-booking prevention with a DB constraint or lock, and the isolated-RDS trade-off (blast radius/schema ownership vs cost and no cross-DB joins). See `08-auth-integrations.md`.

### Bullet 10 — "Owned full user data migration from the legacy backend… validating data integrity across 25k+ user records"

**Strategy options and when each fits:**
| Strategy | Fits when |
|---|---|
| Big-bang (downtime window) | Small dataset, tolerant business, simplest correctness |
| Backfill + dual-write | No downtime allowed; needs write path in both systems and a reconciliation sweep |
| Backfill + CDC/change events tail | Large dataset, continuous writes; snapshot then apply the delta stream |

**Execution checklist to describe:** idempotent, re-runnable scripts keyed by legacy ID; batching with throttling so the source DB isn't hammered; a mapping table (legacy ID ↔ new ID); dry-run mode with a diff report; validation (row counts, unique keys, field-level checksums, sampled deep comparison); a rollback plan (keep the legacy system readable and the cutover behind a flag); and a post-cutover reconciliation run.
- *Likely question*: "How did you handle records that failed validation?" → quarantined with reasons, reported to the business owner for a decision, never silently dropped or coerced.

---

## Inexture — Software Developer (Jun 2021 – Jul 2022)

### Bullet 11 — "Backend APIs and business logic with NestJS/PostgreSQL; NestJS microservices with RabbitMQ, Redis, AWS S3, AWS SES"

- **RabbitMQ**: which exchange type and why; manual acks + prefetch; DLX + retry queue with TTL backoff (`05-messaging.md`).
- **Redis**: what you cached, TTL policy, invalidation strategy, and whether you handled stampedes; or if it was used for sessions/rate limiting/locks, describe the exact key design (`03-databases.md`).
- **S3**: presigned URLs for direct upload (keeps large files off your API), key layout, lifecycle.
- **SES**: sandbox vs production access, verified identities, **SPF/DKIM/DMARC** for deliverability, bounce/complaint handling via SNS (suppression list — required to protect sender reputation), templates, and send-rate limits. Email is a favourite "did they really run this in prod?" probe.

### Bullet 12 — "Internal resource management tool end-to-end (MongoDB, Express.js) — schema design, business logic, CI/CD, deployment"

The "end-to-end ownership" story: the domain model (embed vs reference decisions and why), indexes for the main queries, auth/roles, CI/CD pipeline, deployment target, and what you'd change now (probably: validation at the edge, structured logging, and Postgres if the data turned out relational — a good self-aware answer).

---

## Cross-cutting questions you'll get regardless of bullet

**"Walk me through your most complex system."** — Pick the ingestion pipeline. 60–90 seconds: problem → constraints → design → one hard trade-off → outcome with a number. Then stop and let them drill.

**"What would you design differently today?"** — Strong answers: enforce contracts with a schema registry from day one; use the transactional outbox instead of publish-after-commit; adopt Distributed Map/Step Functions for the batch splitting; invest in freshness SLIs earlier because success-rate alone hid staleness; make the ETL resumable from the start rather than retrofitting the step log.

**"What's the biggest production incident you owned?"** — Structure: detection (which alert, how fast) → impact (records/users/duration) → mitigation (what stopped the bleeding) → root cause → permanent fix → the guardrail that now prevents recurrence. Blameless tone, specific numbers, no hero narrative.

**"How do you scale this 100×?"** — Name where it breaks *first*, in order: DB connections/write throughput → Lambda concurrency and account limits → per-partition ordering constraints → third-party API rate limits → cost. Then the fixes: batching, bulk loads (COPY), partitioning/sharding, moving sustained load from Lambda to Fargate consumers, streaming with Kinesis/Kafka if replay and ordering at volume become requirements.

**"What are you weakest at?"** — Answer honestly with something real and current (e.g. deep Kafka/stream processing operations, or large-scale data engineering), plus what you're doing about it. Avoid fake weaknesses; senior interviewers use this to test calibration.
