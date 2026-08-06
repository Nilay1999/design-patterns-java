# AWS — Serverless, Eventing, Compute, Storage

Everything here is on your resume: Lambda, Step Functions, ECS Fargate, EventBridge, SQS, Kinesis, API Gateway, CloudWatch, plus S3 and Aurora from the bullets.

---

## 1. EventBridge

### What it is
A serverless **event bus**: publishers `PutEvents` onto a bus, **rules** match events by content, and matched events are pushed to up to **5 targets** per rule. Publisher and consumer never know about each other — that's the decoupling win over calling a service directly.

### Event envelope
```json
{
  "version": "0",
  "id": "uuid",
  "detail-type": "ShipmentCreated",
  "source": "com.elevation.ingestion",
  "account": "123456789012",
  "time": "2026-08-06T10:00:00Z",
  "region": "us-east-1",
  "resources": [],
  "detail": { "shipmentId": "S-1", "orderId": "O-9", "s3Key": "raw/2026/08/06/S-1.xml" }
}
```
`source` + `detail-type` are your routing dimensions — design them like a topic taxonomy. `detail` is your payload and must be JSON (XML gets archived to S3 and referenced by key — **claim-check pattern**).

### Rules and event patterns
Patterns match the event's JSON structure — not a regex over a string:
```json
{
  "source": ["com.elevation.ingestion"],
  "detail-type": ["ShipmentCreated", "ShipmentUpdated"],
  "detail": {
    "region": ["us-east", "us-west"],
    "quantity": [{ "numeric": [">", 0] }],
    "sku": [{ "prefix": "SKU-" }],
    "status": [{ "anything-but": ["DRAFT"] }],
    "customerId": [{ "exists": true }]
  }
}
```
Comparators: `prefix`, `suffix`, `anything-but`, `numeric`, `exists`, `cidr`, `equals-ignore-case`, `wildcard`, and `$or`. Array fields match if **any** element matches.

### Buses
- **default** — receives AWS service events (EC2 state change, S3 via CloudTrail/EventBridge notifications, CodePipeline…).
- **custom** — your domain events. Best practice: one bus per bounded context or per environment, never mix prod/nonprod.
- **partner** — SaaS sources (Stripe/Datadog/Auth0 push directly).

### Delivery guarantees (say this precisely)
- **At-least-once**, asynchronous, **no ordering guarantee**.
- Retries with exponential backoff up to **24 hours** if a target fails.
- **DLQ per target** (an SQS queue) captures events that exhaust retries or can't be delivered (e.g. target deleted, permission error).
- Duplicates are possible → consumers must be idempotent.

### Archive & Replay
Archive stores matched events for a retention period; Replay re-emits them into the bus for a chosen time window and rule set. This is the answer to **"how would you reprocess a day of bad data?"** and it's a strong pairing with your auditability story. Caveat: replayed events go to the rules you select and arrive with a replay marker; downstream must be idempotent or targeted at a replay-specific rule.

### Schema Registry
Discovers event schemas (OpenAPI/JSONSchema) from traffic, versions them, and generates code bindings. Useful vocabulary for your **"schema validation and data contracts"** bullet: registry = discovery/versioning, but *enforcement* still happens in your producer/consumer code (EventBridge does not reject an event for failing a registry schema).

### EventBridge Pipes vs Bus vs Scheduler
- **Bus**: 1→N fan-out with content routing.
- **Pipes**: point-to-point source→(filter)→(enrich)→target with built-in polling for SQS/Kinesis/DynamoDB Streams/MSK. Replaces "Lambda that just moves and reshapes messages."
- **Scheduler**: cron/rate/one-time schedules at scale (millions of schedules, per-schedule targets), successor to CloudWatch Events scheduled rules.

### Limits worth quoting
- Event size **256 KB** (same as SQS) → big XML goes to S3, event carries the key.
- `PutEvents` batches up to 10 entries.
- Default PutEvents throughput in the thousands/sec (region-dependent, raisable).
- Rule count per bus in the low hundreds by default (raisable).

### EventBridge vs SNS vs SQS vs Kinesis (the table they want)
| | EventBridge | SNS | SQS | Kinesis |
|---|---|---|---|---|
| Model | Bus, content routing | Pub/sub topic | Queue (point-to-point) | Ordered log/stream |
| Consumers | Rules → targets | Subscribers | One logical consumer per queue | Many, independent offsets |
| Filtering | Rich, on full payload | Attribute + payload filter policies | n/a | n/a (consumer-side) |
| Ordering | None | FIFO topic option | FIFO queue option | Per partition key |
| Replay | Archive + replay | No | No (DLQ redrive only) | Yes, by retention window |
| Latency | ~sub-second, higher variance | Lowest | Low | Low |
| Throughput | High | Very high | Effectively unlimited (Std) | Provisioned by shard |
| Best for | Routing domain events across services | Fan-out notifications | Buffering & work distribution | High-volume ordered analytics/CDC |

**Interview answer for "why EventBridge and not SNS?"** — content-based routing on the whole payload, archive/replay for audit and reprocessing, many target types without writing glue, and a schema registry. SNS wins on raw throughput/latency and FIFO ordering; SQS is the buffer you put *behind* either one so consumers can fail and retry independently.

---

## 2. SQS

### Queue types
- **Standard**: nearly unlimited throughput, **at-least-once** delivery, **best-effort ordering**.
- **FIFO**: strict ordering **within a MessageGroupId**, exactly-once *processing* within a 5-minute dedup window (`MessageDeduplicationId` or content-based dedup). Base 300 TPS (3,000 with batching); high-throughput FIFO mode raises this substantially. Ordering is per group — so use `storeId`/`orderId` as group to get parallelism *and* ordering.

### The concepts that get tested
- **Visibility timeout**: after a receive, the message is hidden for N seconds (default 30s, max 12h). If the consumer doesn't delete it in time, it becomes visible again → **duplicate processing**. Rule: visibility timeout ≥ worst-case processing time; for Lambda event source mappings AWS recommends ~6× the function timeout. Long jobs should call `ChangeMessageVisibility` as a heartbeat.
- **Retention**: 4 days default, max 14. `ApproximateAgeOfOldestMessage` is your backlog SLI.
- **Message size**: 256 KB. Larger → **Extended Client Library** (payload in S3, pointer in the message) = claim-check.
- **Long polling** (`WaitTimeSeconds` up to 20s): fewer empty receives, lower cost, lower latency than tight short-poll loops.
- **DLQ + `maxReceiveCount`**: after N failed receives the message is moved to the DLQ. Then **DLQ redrive** (console/API) replays them back to the source queue once the bug is fixed. Always alarm on `ApproximateNumberOfMessagesVisible` for DLQs.
- **Delay queues / message timers**: up to 15 min delay — a cheap first-tier retry backoff.

### Lambda + SQS event source mapping (know this cold)
- Lambda service polls the queue with **5 concurrent pollers initially**, scaling up (roughly 60 more concurrent invocations per minute, up to 1,000 for standard queues; FIFO scales per active group).
- **Batch size** up to 10 (standard, no batch window) or up to 10,000 with a **batch window** up to 300s.
- **Partial batch failure**: enable `ReportBatchItemFailures` and return `batchItemFailures: [{itemIdentifier: messageId}]` — otherwise a single bad record forces the whole batch to be retried, multiplying duplicates. This is a great detail to volunteer for the XML pipeline bullet.
- Successful messages are deleted by the Lambda service; failures return to the queue and eventually hit the DLQ.
- **Backpressure hazard**: SQS-triggered Lambda can consume your whole account concurrency and starve other functions → set **reserved concurrency** on the pipeline function; also protects your Aurora connection count.

### Idempotency with SQS (mandatory follow-up)
Standard SQS can duplicate. Options:
1. Natural idempotency — `UPSERT … ON CONFLICT DO NOTHING/UPDATE` keyed by business ID.
2. Dedupe table (DynamoDB with TTL, or a unique index in Aurora) keyed by `messageId` or a domain idempotency key, written **conditionally**.
3. Version/sequence check — apply only if incoming `version > stored version` (also fixes out-of-order).

---

## 3. Lambda

### Execution lifecycle
1. **Init** — download code/image, start the runtime, run module-level (outside handler) code. This is the cold start.
2. **Invoke** — handler runs; the container (execution environment) is reused for subsequent invocations.
3. **Shutdown** — after idle, environment is frozen then destroyed. Extensions get a shutdown event.

**Practical consequence:** create DB clients, AWS SDK clients, and load config **outside** the handler so they're reused; but be careful with DB connections in Lambda (see RDS Proxy below).

### Knobs and limits
| Knob | Value |
|---|---|
| Memory | 128 MB – 10,240 MB; CPU scales with memory (~1 vCPU near 1,769 MB, up to ~6 vCPU) |
| Timeout | 15 minutes max |
| Payload | 6 MB sync request/response, 256 KB async |
| /tmp | 512 MB – 10 GB |
| Deployment | 50 MB zipped upload / 250 MB unzipped / 10 GB container image |
| Concurrency | 1,000 per account default (raisable); burst limits per region |

### Concurrency model
- **Reserved concurrency**: caps *and* guarantees a function's slice of account concurrency.
- **Provisioned concurrency**: pre-initialized environments — removes cold starts, costs money while idle.
- **SnapStart** (Java, and now other runtimes): snapshots the initialized JVM and restores it — the answer to "Java cold starts are terrible." Mention it since you write Spring Boot; caveat: uniqueness/randomness and network connections must be re-established via runtime hooks.
- Throttling returns `TooManyRequestsException` (429); async invocations are retried internally, sync callers must handle it.

### Invocation models & error behaviour
| Model | Examples | Retries |
|---|---|---|
| Synchronous | API Gateway, ALB, SDK `RequestResponse` | None by Lambda — caller retries |
| Asynchronous | EventBridge, S3 notifications, SNS | Internal queue; 2 retries with backoff, up to 6h event age; then **on-failure destination** or DLQ |
| Event source mapping (poll) | SQS, Kinesis, DynamoDB Streams, MSK | Batch retried per source semantics; Kinesis/DDB retry until success/expiry unless `bisectBatchOnFunctionError`/`maximumRetryAttempts` configured |

**Destinations vs DLQ:** destinations (onSuccess/onFailure → SQS/SNS/Lambda/EventBridge) carry the full invocation record including the response/error — richer than the legacy DLQ, which carries just the payload.

### Cold starts — how to talk about them
Causes: package size, runtime init, VPC ENI attachment (largely solved by Hyperplane shared ENIs), heavy DI frameworks (Spring). Fixes: smaller deps, lazy init of unused clients, provisioned concurrency for latency-critical paths, SnapStart for Java, keep Lambda off the synchronous user path when a container (Fargate) is a better fit.

### Lambda in VPC + Aurora — the classic trap
Each concurrent execution can open its own DB connection → 1,000 concurrent Lambdas will exhaust Aurora's `max_connections`. Fixes: **RDS Proxy** (connection pooling and multiplexing, IAM auth, failover-aware), reserved concurrency, batching writes, or moving heavy DB work to a Fargate consumer.

### Cost model
GB-seconds × duration + per-request fee (+ provisioned concurrency if used). Doubling memory can *lower* cost if it more than halves duration — use AWS Lambda Power Tuning for the sweet spot. Good, concrete thing to say you did.

---

## 4. Step Functions

### Standard vs Express
| | Standard | Express |
|---|---|---|
| Max duration | 1 year | 5 minutes |
| Execution semantics | **Exactly-once** | **At-least-once** |
| Pricing | Per state transition | Per invocation + duration × memory |
| History | Full, queryable in console/API | CloudWatch Logs only |
| Use for | Long ETL, human approval, sagas | High-volume short workflows, API backends (sync express) |

### Amazon States Language (ASL) building blocks
`Task` (do work), `Choice` (branch), `Parallel` (fixed branches), `Map` (per-item iteration), `Pass`, `Wait`, `Succeed`, `Fail`.

- **Inline Map**: up to 40 concurrent iterations, items from state input.
- **Distributed Map**: reads directly from **S3** (JSON/CSV/objects), up to **10,000 parallel child executions**, with batching, tolerated-failure thresholds, and result writing back to S3. This is *the* purpose-built tool for "process 1M XML records" and is worth naming even if you did it with SQS + Lambda — shows you know the alternative.

### Error handling
```json
"Retry": [{
  "ErrorEquals": ["States.TaskFailed", "Lambda.ServiceException"],
  "IntervalSeconds": 2, "MaxAttempts": 4, "BackoffRate": 2.0, "JitterStrategy": "FULL"
}],
"Catch": [{ "ErrorEquals": ["States.ALL"], "Next": "CompensateInventory", "ResultPath": "$.error" }]
```
Built-in error names: `States.ALL`, `States.TaskFailed`, `States.Timeout`, `States.Permissions`, `States.DataLimitExceeded`, plus custom errors thrown by your code.

### Service integrations
- **Optimized**: `.sync` patterns wait for completion (ECS RunTask, Glue, EMR, Batch, nested Step Functions).
- **AWS SDK integrations**: call ~200 services directly, no Lambda glue.
- **`.waitForTaskToken`**: pause until an external system/human calls `SendTaskSuccess/Failure` — the pattern for approval steps or third-party callbacks.

### Saga in Step Functions
Each forward step is a `Task`; each has a `Catch` routing to its **compensating** task, and compensations chain backwards. State machine = the saga log, durable and inspectable. If you implemented saga in application code instead, be ready to explain: "we owned the orchestrator in Node because compensation needed domain logic and we wanted the pipeline in one deployable — Step Functions would have given us durability and visual debugging for free, at the cost of state-transition pricing and 256 KB payload limits."

### Limits
- 256 KB payload between states → pass S3 references, not data.
- 25,000 events in a Standard execution history → long loops need Distributed Map or child executions.
- Express has no per-state history; you debug via logs.

---

## 5. Kinesis Data Streams

- **Shards**: unit of capacity — 1 MB/s or 1,000 records/s in; 2 MB/s out shared, or 2 MB/s **per consumer** with Enhanced Fan-Out (HTTP/2 push, ~70 ms latency).
- **Partition key** → MD5 hash → shard. Ordering is guaranteed **per partition key**.
- **Retention**: 24h default, extendable to 365 days → **replay by sequence number/timestamp** (SQS can't do this).
- **Capacity modes**: provisioned (you manage shards, resharding by split/merge) or **on-demand** (auto-scales, higher unit cost).
- **Consumers**: KCL (leases + checkpoints in a DynamoDB table) or Lambda ESM (checkpoint per batch, `bisectBatchOnFunctionError`, `maximumRetryAttempts`, `onFailure` destination).
- **Key metric: `IteratorAge`** — how far behind consumers are. Alarm on it; rising iterator age = a poison record or under-provisioned consumers, and unlike SQS a stuck record **blocks its whole shard**.
- **Hot shard / key skew**: a partition key like `storeId` where one store is 60% of traffic. Fix: composite key (`storeId#bucket`), or move ordering guarantees down to where they're actually needed.

**When to prefer Kinesis over SQS:** you need replay, multiple independent consumers of the same stream, or strict per-key ordering at high volume. **When SQS wins:** independent work items, elastic per-message parallelism, simple retries/DLQ, no capacity planning.

---

## 6. API Gateway

### Three flavours
- **REST API**: the full-featured one — request validation against JSON Schema models, VTL mapping templates, API keys + usage plans, WAF, caching, canary deployments, private endpoints.
- **HTTP API**: ~70% cheaper, lower latency, JWT authorizer built in, but fewer features (no built-in request validation, no caching, no usage plans in the same form).
- **WebSocket API**: `$connect`/`$disconnect`/custom routes, connection IDs, `@connections` callback API for server push.

### Auth options
IAM (SigV4), Cognito user pools, **Lambda authorizer** (TOKEN or REQUEST type, returns an IAM policy + context, cacheable by TTL to avoid invoking on every request), JWT authorizer (HTTP API), or mTLS on a custom domain.

### Throttling & quotas
Account-level default ~10,000 rps with a burst bucket; per-stage and per-method throttles; **usage plans + API keys** for per-client quotas. Excess → `429` with `Retry-After` semantics you should surface to clients.

### Integration types
Lambda **proxy** (whole request passed as event; you shape the response) vs non-proxy (VTL mapping in/out), HTTP/HTTP_PROXY, AWS service integration (e.g. straight to SQS — a great way to absorb bursts without a Lambda in front), and **VPC Link** to private ALB/NLB (useful for your ECS/EKS services).

### Other must-knows
- **29-second integration timeout** on REST APIs (long jobs → return `202 Accepted` with a status URL; poll or notify).
- Edge-optimized vs regional vs private endpoints.
- Stages + stage variables, canary release with traffic percentage.
- Request validation + models = enforcing your **data contract at the edge** — worth linking to your ingestion bullet.

---

## 7. ECS Fargate

- **Task definition** (image, cpu/memory, env, secrets from SSM/Secrets Manager, log config, IAM **task role** vs **execution role**) → **Task** (running instance) → **Service** (desired count, load balancer, rolling updates, health checks).
- **Fargate**: no EC2 hosts to patch/scale; per-second billing; `awsvpc` networking gives each task its own ENI and security group. CPU/memory come in fixed combinations (0.25–16 vCPU).
- **Scaling**: target tracking (CPU, memory, **ALB requests per target**) or step scaling; scale-in protection for long jobs. Task startup is ~30–60s (image pull matters) so scale earlier than you would with Lambda.
- **Deployments**: rolling with `minimumHealthyPercent`/`maximumPercent`, or blue/green via CodeDeploy (test listener, automatic rollback on alarms). Circuit breaker can auto-roll-back failed deployments.
- **Fargate Spot** for interruption-tolerant workloads (up to ~70% cheaper, 2-minute warning).
- **Service Connect / Cloud Map** for service discovery between tasks.

**Lambda vs Fargate vs EKS — the decision framework interviewers want:**
- Lambda: spiky/event-driven, <15 min, per-request scaling, pay-per-use, no infra. Costs balloon under sustained high throughput; cold starts on latency-critical paths.
- Fargate: long-running or steady load, big containers, gRPC/WebSocket, >15 min jobs, predictable cost at scale, no cluster ops.
- EKS: you need Kubernetes primitives, portability, service mesh, or a platform team already runs it (your Kevit stack).

---

## 8. S3 (archival + claim-check)

- **Consistency**: strong read-after-write for PUTs and LISTs since 2020 — no more "eventual consistency" caveat.
- **Storage classes & lifecycle**: Standard → Standard-IA (30d) → Glacier Instant/Flexible → Deep Archive. Lifecycle rules by prefix/tag automate this — exactly how you'd justify "S3 archival for auditability" at low cost.
- **Versioning + Object Lock (WORM)** — genuine audit/compliance answer: immutable retention, legal hold, protection against overwrite/delete.
- **Event notifications** → SQS/SNS/Lambda/EventBridge (prefix/suffix filters). Classic ingestion trigger.
- **Performance**: ~3,500 PUT/COPY/POST/DELETE and 5,500 GET per second **per prefix** — spread keys across prefixes (e.g. date + hash) for high-volume ingestion.
- **Multipart upload** for large objects (required >5 GB), presigned URLs for direct client upload/download without proxying through your API.
- **Security**: SSE-S3 vs SSE-KMS (audit trail, per-key permissions, throttling limits) vs SSE-C; bucket policies vs IAM vs ACLs; Block Public Access; VPC gateway endpoints to keep traffic off the internet.
- **Query in place**: S3 Select, Athena/Glue over archived data — how you'd answer "can you reprocess/inspect the raw XML from 6 months ago?"

**Key layout matters:** `raw/dt=2026-08-06/source=partnerA/<id>.xml` gives you lifecycle rules, Athena partitioning, and prefix parallelism in one scheme.

---

## 9. CloudWatch (see also `07-observability.md`)

- **Metrics**: namespace + dimensions + statistic; 1-minute standard, 1-second high-resolution custom metrics. Custom metrics are billed **per metric per month** — cardinality costs money.
- **EMF (Embedded Metric Format)**: write a specially-structured JSON log line and CloudWatch extracts metrics from it — cheap, high-cardinality-friendly instrumentation from Lambda.
- **Logs**: log groups (set retention! default is forever = a cost bug), **Logs Insights** query language, **subscription filters** → Kinesis/Lambda/OpenSearch for shipping logs elsewhere (e.g. into Datadog).
- **Alarms**: static thresholds, **anomaly detection** bands, **composite alarms** (suppress noise: alert only if error rate high AND deployment not in progress), `M out of N` datapoints, and `treatMissingData` (a queue with no data is not necessarily healthy).
- **X-Ray / ServiceLens**: distributed tracing across API Gateway → Lambda → SQS → Lambda → Aurora; sampling rules; annotations vs metadata for filterable trace search.

**The metrics you should name for your pipelines:** SQS `ApproximateAgeOfOldestMessage` and DLQ depth, Lambda `Errors`/`Throttles`/`Duration p99`/`ConcurrentExecutions`, EventBridge `FailedInvocations`/`ThrottledRules`, Kinesis `IteratorAge`, Aurora `DatabaseConnections`/`CPUUtilization`/replica lag, plus a business metric like `RecordsIngested` and `RecordsRejected` per source.

---

## 10. Aurora / RDS (your metadata store)

- **Architecture**: compute separated from a distributed storage layer that keeps **6 copies across 3 AZs**; writes ack on a **4/6 quorum**; reads need 3/6. Only redo log records cross the network (no double-write buffer / full page writes) — that's why Aurora outperforms stock MySQL/Postgres on write-heavy loads.
- **Replicas**: up to 15 readers sharing the same storage → replication lag typically in **milliseconds** (not the seconds you'd see with binlog/WAL shipping). Failover to a reader is usually ~30s or less; use the **reader endpoint** for scale-out reads and be explicit about read-after-write risk.
- **Aurora Serverless v2**: scales in fine-grained ACUs, good for spiky ETL windows; v1's abrupt pause/resume problems are gone.
- **Extras**: fast **clones** (copy-on-write, great for testing migrations against prod-sized data), **Backtrack** (MySQL, rewind in place), **Global Database** (cross-region, ~1s replication), Performance Insights for query-level diagnosis.
- **RDS Proxy**: pooling + multiplexing in front of Aurora, IAM auth, keeps failovers transparent — the standard fix for "Lambda exhausted my connections."

---

## Rapid-fire Q&A

**Q: EventBridge or SQS first in your pipeline?**
Bus first for routing/fan-out, queue behind each consumer for buffering and independent retry. The queue is what lets one slow consumer fall behind without dropping events or affecting others.

**Q: A message keeps failing. Walk me through what happens.**
Consumer throws → message becomes visible again after the visibility timeout → retried up to `maxReceiveCount` → moved to the DLQ → DLQ depth alarm pages → we inspect the payload, fix the bug or quarantine the record, then redrive the DLQ back to the source queue. With partial batch responses, only the failing records repeat; the rest are deleted.

**Q: How do you guarantee exactly-once?**
You don't — you get at-least-once delivery plus **idempotent processing**, which is "effectively once." Idempotency key (message ID or business key) + conditional write/upsert + version checks for ordering.

**Q: Order matters for a store's inventory updates. How?**
FIFO with `MessageGroupId = storeId` (ordering per store, parallelism across stores) or Kinesis with `partitionKey = storeId`. Alternative that avoids ordering entirely: carry a monotonic `version`/`updatedAt` and apply updates only if newer (last-writer-wins with a version guard).

**Q: Cost blew up on this pipeline. Where do you look?**
Lambda GB-s (over-provisioned memory or long DB waits), CloudWatch custom metrics + log ingestion/retention, NAT Gateway data processing, EventBridge per-million events, Kinesis shard-hours vs on-demand, S3 request counts and storage class, and Step Functions state transitions.

**Q: 1M XML records arrive at once. Design it.**
Land raw files in S3 (partitioned prefixes) → S3/EventBridge notification → splitter (Fargate task or Distributed Map) streams the file and emits per-record events → SQS with batching → Lambda validates against XSD/JSON Schema, upserts metadata into Aurora keyed by natural ID (idempotent), archives raw to S3 with lifecycle → invalid records to a rejects queue/bucket with reason codes. Controls: reserved concurrency to protect Aurora (or RDS Proxy), partial batch responses, DLQ + redrive, per-source counters so you can reconcile "records in = records stored + rejected."
