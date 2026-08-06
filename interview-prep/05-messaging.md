# Messaging — RabbitMQ, SQS, Kafka/Kinesis comparisons

---

## 1. RabbitMQ (AMQP 0-9-1)

### The model (draw this)
`Producer → Exchange → (binding, routing key) → Queue → Consumer`
A producer **never** publishes to a queue directly; it publishes to an **exchange**, which routes copies to bound queues. Connections are TCP; **channels** are lightweight multiplexed sessions inside a connection (one channel per thread/consumer; don't share channels across threads).

### Exchange types
| Type | Routing |
|---|---|
| **direct** | routing key equals binding key (`order.created` → that queue) |
| **topic** | pattern with `*` (one word) and `#` (zero+ words): `order.*.eu`, `audit.#` |
| **fanout** | broadcast to all bound queues, routing key ignored |
| **headers** | match on header attributes (`x-match: all/any`) |
| default (`""`) | direct exchange where the routing key is the queue name |

### Durability — three things must all be true
1. **Durable queue** (survives broker restart),
2. **Persistent message** (`delivery_mode=2`),
3. **Publisher confirms** (broker acks after it has taken responsibility).
Miss any one and you can lose messages. Transactions (`tx.select`) exist but are much slower than confirms — use confirms.
Also: `mandatory` flag + a return listener catches "published to an exchange that routed nowhere," and an **alternate exchange** catches unroutable messages instead of dropping them.

### Consumer side
- **Acknowledgement**: `autoAck=true` means "delivered = done" (fast, lossy). Use **manual ack** after successful processing; `nack`/`reject` with `requeue=false` to send to the DLX.
- **Prefetch (QoS)**: `basic.qos(prefetchCount=N)` limits unacked messages per consumer. `prefetch=1` gives fair dispatch for slow, variable work; higher values give throughput. Unlimited prefetch = one consumer hoards the queue and memory balloons.
- **Ordering**: FIFO per queue, but multiple consumers process concurrently → effective ordering is lost. Need strict ordering? One queue per key with **single active consumer**, or per-entity queues/consistent hashing exchange.

### Dead lettering and retries
A message is dead-lettered when: rejected with `requeue=false`, TTL expires, or the queue exceeds its max length. It goes to the configured **DLX** with `x-death` headers recording the count and reason.
Retry patterns:
- **TTL + DLX loop**: main queue → (reject) → retry queue with `x-message-ttl=30s` and DLX back to main → after N attempts (count `x-death`), route to a parked/failure queue.
- **Tiered backoff**: retry.5s, retry.30s, retry.5m queues.
- **Delayed message exchange plugin** for arbitrary per-message delays.
Always cap attempts and alarm on the parked queue — an infinite requeue loop is the classic RabbitMQ outage.

### Queue flavours
- **Classic queues** (single node; mirrored classic queues are deprecated).
- **Quorum queues** — Raft-replicated, the modern default for durability/HA; support delivery-limit (built-in poison-message handling); no message TTL priorities parity with classic (check features you rely on).
- **Streams** — append-only, replayable log (Kafka-like) with offset tracking; use when consumers need replay or multiple independent readers.
- **Lazy queues** (classic) push messages to disk to survive huge backlogs without memory pressure.

### Operations
- **Flow control**: broker throttles publishers when memory (`vm_memory_high_watermark`, default 0.4 of RAM) or disk-free alarms trip. A blocked publisher is often mis-diagnosed as "network hang."
- **Clustering**: metadata replicated across nodes; queue contents live on their home node unless quorum/mirrored. Network partitions need a handling strategy (`pause_minority` is the safe default).
- Heartbeats and connection recovery in clients; use `channelMax`/connection pooling sanely.
- Monitoring: queue depth, unacked count, consumer count/utilisation, publish vs deliver rates, DLQ depth, memory/disk alarms, node partitions.

### RabbitMQ vs SQS (you've used both — expect the comparison)
| | RabbitMQ | SQS |
|---|---|---|
| Ops | You run/patch/scale it (or CloudAMQP/MQ) | Fully managed, no capacity planning |
| Routing | Rich (topic/headers/DLX/priority/delay) | None — queue is the unit; routing via SNS/EventBridge upstream |
| Ordering | Per queue (lost with concurrent consumers) | Per MessageGroupId in FIFO |
| Latency | Sub-ms to low ms | Low ms, but polling-based |
| Protocol | AMQP, push to consumers | HTTPS long-poll |
| Retention | Until consumed (or TTL) | 14 days max |
| Throughput | Bounded by cluster resources | Effectively unlimited (standard) |
| Cost | Cluster cost regardless of traffic | Per request |

Say: "Rabbit when we needed rich routing, RPC-style request/response between Nest services, and low latency inside the cluster; SQS when we wanted zero ops, elastic scale, and native Lambda integration in the AWS pipeline."

---

## 2. SQS (recap — full details in `01-aws-serverless.md`)

Key exam points: visibility timeout ↔ duplicate processing, `maxReceiveCount` → DLQ → redrive, long polling, 256 KB limit → S3 claim check, FIFO groups for ordering, partial batch failure reporting with Lambda, reserved concurrency to protect downstream databases.

---

## 3. Kafka / Kinesis conceptual (why interviewers ask "why not Kafka?")

- **Log, not queue**: partitioned, append-only, retained for a time window regardless of consumption. Consumers track **offsets**; multiple consumer groups read independently; replay = reset the offset.
- **Partitions** are the unit of parallelism and ordering: ordering is guaranteed per partition, and a partition is consumed by at most one consumer within a group. More consumers than partitions = idle consumers.
- **Durability**: replication factor + `acks=all` + `min.insync.replicas`; ISR shrinkage is the thing that bites.
- **Exactly-once semantics** in Kafka: idempotent producer + transactions across produce/consume (only within Kafka; the moment you write to an external DB you're back to idempotent consumers).
- **Log compaction** keeps the latest value per key — the natural fit for CDC topics and state snapshots.
- **Kafka vs RabbitMQ**: Kafka for high-throughput streams, replay, multiple consumers, event sourcing, analytics; Rabbit for task distribution, complex routing, per-message TTL/priority/RPC.
- **Kafka vs Kinesis**: same shape (partitions=shards, offsets=sequence numbers). Kinesis is managed with tighter AWS integration but capacity-planned by shard and capped retention (365 days); Kafka/MSK gives more knobs, compaction, and ecosystem (Connect, Streams) at higher operational cost.

**Answer template for "why didn't you use Kafka?"** — "Our volume (tens of thousands of events/day, bursty) didn't justify a Kafka cluster's operational cost, and we didn't need replay-from-offset by consumers because EventBridge archive plus S3 raw archival covered reprocessing. If we'd needed multiple independent consumers replaying the same ordered stream at high volume, or stream processing/joins, Kafka would have been the right call."

---

## 4. Delivery semantics cheat sheet

| Guarantee | How you get it | Cost |
|---|---|---|
| At-most-once | auto-ack / fire-and-forget | data loss on crash |
| At-least-once | ack after processing + retries | duplicates → need idempotency |
| Effectively-once | at-least-once + idempotent consumer (dedupe store, conditional write, version guard) | extra state + write amplification |

**Sequencing rule**: ack/delete the message **after** the effect is durable, never before. If your effect is a DB write, prefer doing the dedupe insert in the same transaction.

---

## Rapid-fire Q&A

**Q: A consumer processes a message, then crashes before acking. What happens?**
The broker redelivers (Rabbit re-queues on channel close; SQS makes it visible again after the visibility timeout). The side effect already happened → duplicate. That's why the write must be idempotent or dedupe-guarded.

**Q: Queue is backing up. Diagnose.**
Compare arrival rate vs processing rate and look at consumer count and per-message latency: is a downstream dependency slow (DB, third-party), are consumers throttled (Lambda concurrency, prefetch too low), did a poison message stall a partition/FIFO group, did an autoscaler fail to scale, or did the producer legitimately spike? Short-term: scale consumers, raise prefetch/batch size, shed or divert low-priority work. Guardrail: alert on **age of oldest message**, not depth.

**Q: How do you drain a DLQ safely?**
Inspect a sample and classify: bug (fix code, then redrive), bad data (quarantine + report to producer), or transient (redrive immediately). Redrive at a controlled rate to avoid re-overwhelming downstream, and make sure the consumer is idempotent because some DLQ'd messages may have partially succeeded.

**Q: You need per-store ordering but 500 stores of parallelism.**
SQS FIFO with `MessageGroupId=storeId`, or Kafka/Kinesis with `partitionKey=storeId`, or RabbitMQ with a consistent-hash exchange into N queues each with a single active consumer. All three give ordering within a store and parallelism across stores.
