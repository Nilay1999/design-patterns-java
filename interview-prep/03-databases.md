# Databases — PostgreSQL, MySQL, MongoDB, DynamoDB, Redis

Your resume claims ORM transaction boundaries and query optimization for high-volume reads. That invites a hard DB round.

---

## 1. Relational fundamentals (PostgreSQL / MySQL / Aurora)

### ACID & isolation levels
| Level | Prevents | Still allows |
|---|---|---|
| Read Uncommitted | — | dirty reads (not in Postgres — it behaves as Read Committed) |
| **Read Committed** (PG default) | dirty reads | non-repeatable reads, phantoms |
| **Repeatable Read** (MySQL InnoDB default; PG = snapshot isolation) | non-repeatable reads (PG also phantoms) | write skew (PG RR), phantom in some engines |
| **Serializable** | everything | throughput cost; PG uses SSI → serialization failures you must **retry** |

**Write skew** is the anomaly to name: two transactions read overlapping data, each checks a constraint that holds, both write, and the combined result violates the invariant (classic: "at least one doctor on call"). Fix: `SERIALIZABLE`, or `SELECT … FOR UPDATE` on a common row, or a DB constraint.

### MVCC
- **Postgres**: each row version carries `xmin`/`xmax`; readers never block writers and vice versa. Dead tuples accumulate → **VACUUM** reclaims them; **autovacuum** tuning matters on high-churn tables; long-running transactions block cleanup and cause **bloat**; `XID wraparound` is the doomsday scenario. `TOAST` stores oversized column values out-of-line (relevant if you stored XML/JSONB payloads).
- **InnoDB**: undo logs + clustered primary key; secondary index lookups resolve via the PK ("bookmark lookup"); gap/next-key locks under REPEATABLE READ can cause surprising deadlocks on range updates.

### Indexes
- **B-tree** — default; supports `=`, ranges, `ORDER BY`, prefix of composite keys. **Composite index column order matters**: `(a, b)` serves `WHERE a=?`, `WHERE a=? AND b=?`, `ORDER BY a,b` — but not `WHERE b=?` alone.
- **Hash** — equality only, rarely worth it.
- **GIN** — inverted index for `jsonb`, arrays, full-text (`tsvector`), trigram search with `pg_trgm` (`ILIKE '%foo%'` acceleration). This is the index behind "predicate-based search."
- **GiST/SP-GiST** — geometry, ranges, nearest-neighbour.
- **BRIN** — huge append-only tables ordered by time; tiny index, great for `created_at` range scans on billions of rows.
- **Partial index** — `WHERE status = 'PENDING'`: small, hot, perfect for queue-like tables.
- **Expression index** — `lower(email)`, `(payload->>'sku')`.
- **Covering index** — `INCLUDE (col)` enables **index-only scans** (no heap fetch; needs a recent visibility map).
- Costs: every index slows writes and consumes cache. Find unused ones via `pg_stat_user_indexes.idx_scan = 0`.

### Reading a plan (say you do this — then be able to)
`EXPLAIN (ANALYZE, BUFFERS, VERBOSE)`. Look for:
- **Seq Scan** on a big table with a selective predicate → missing/unusable index (function on the column, type mismatch, leading wildcard).
- **Rows estimated vs actual** off by orders of magnitude → stale statistics (`ANALYZE`), correlated columns (`CREATE STATISTICS`), or a bad `n_distinct`.
- **Nested Loop** with a large outer side → probably wanted Hash Join; check `work_mem`.
- **Sort** spilling to disk (`external merge`) → raise `work_mem` per query or add an index that provides order.
- **Bitmap Heap Scan** with high `Rows Removed by Filter`/lossy pages → index isn't selective enough.
- Buffers: `shared read` vs `shared hit` tells you cache effectiveness.

### High-volume read optimization playbook (your bullet)
1. **Keyset pagination** instead of `OFFSET` (see `02` for the query shape).
2. Kill **N+1** — batch fetch (`WHERE id = ANY(:ids)`), join fetch/entity graphs in the ORM, DataLoader-style batching.
3. **Select only needed columns**; DTO/projection queries instead of hydrating full entities.
4. **Covering/partial indexes** for the top queries; verify with `pg_stat_statements` (total time, calls, mean time) rather than intuition.
5. **Caching** — Redis cache-aside for hot lookups, with TTL + jitter + explicit invalidation on write.
6. **Materialized views** or a CQRS read model for expensive aggregates; refresh concurrently or maintain incrementally via events.
7. **Partitioning** (declarative range by date, or list by tenant) — prunes scans and makes archiving a `DETACH` instead of a mass `DELETE`.
8. **Read replicas** for reporting/read-heavy endpoints; handle replica lag (route read-after-write to the writer, or use a session-sticky policy).
9. **Connection pooling** — PgBouncer (transaction mode) or RDS Proxy; pool size ≈ `cores × 2 + effective_spindles`, not "500 because we have 500 clients."
10. **Batch writes** — multi-row inserts, `COPY` for bulk, `INSERT … ON CONFLICT` upserts, chunked commits in ETL.

### Locking
- Row locks: `FOR UPDATE`, `FOR NO KEY UPDATE`, `FOR SHARE`.
- **`FOR UPDATE SKIP LOCKED`** — the DB-as-a-queue / concurrent-worker pattern (your concurrent SKU resolution): each worker grabs unlocked rows, no contention, no double-processing.
- **Advisory locks** (`pg_advisory_xact_lock(hashtext(key))`) — application-level mutex without a lock table; good for "one worker per SKU/tenant."
- **Deadlocks**: caused by inconsistent lock ordering; fix by always acquiring in a canonical order (e.g. sorted IDs), keeping transactions short, and retrying on `40P01`.
- `SET lock_timeout` / `statement_timeout` so a stuck migration or query can't wedge the system.

### Transaction boundaries (ORM-specific detail they'll probe)
- Boundary belongs in the **service/use-case layer**, not the repository and not the controller.
- Keep transactions **short**: never hold one across HTTP calls, S3 uploads, or queue publishes. If you must coordinate, use the outbox (write intent inside the tx, publish outside).
- Read-only transactions marked as such (`@Transactional(readOnly = true)`) — enables replica routing and skips dirty-checking.
- Nested calls: understand propagation (`REQUIRED` joins, `REQUIRES_NEW` suspends and opens a new physical tx — a common source of "why didn't my rollback roll back?").
- After-commit side effects: publish events on commit hooks (`TransactionSynchronization`/`AFTER_COMMIT`), never before.

### Schema migrations without downtime
**Expand → migrate → contract**: add nullable column → dual-write → backfill in batches (with sleep/throttle) → switch reads → drop old column in a later release. Watch out for locks: adding a column with a volatile default, adding an index (use `CREATE INDEX CONCURRENTLY`), or `ALTER TYPE` can take an `ACCESS EXCLUSIVE` lock and stall the app. Always set a short `lock_timeout` on migrations so they fail fast instead of queueing behind/blocking traffic. Tools: Flyway/Liquibase (Java), Prisma/TypeORM migrations (Node), gh-ost/pt-osc for MySQL.

---

## 2. MongoDB

- **Modeling**: embed for read-together, bounded, low-churn data; reference for unbounded or independently-updated data. Named patterns: **bucket** (time series), **subset** (hot fields inline, rest in a side collection), **extended reference** (denormalize a few fields to avoid joins), **computed** (precompute aggregates on write), **outlier** (special-case the whale documents).
- Limits: **16 MB per document**; arrays that grow forever are an anti-pattern (fragmentation, index bloat).
- **Indexes**: single-field, compound with the **ESR rule** (Equality → Sort → Range for field order), multikey (arrays), text, wildcard, TTL (auto-expiry), partial, unique. `explain("executionStats")` → look for `COLLSCAN`, `totalKeysExamined` vs `nReturned` ratio.
- **Replica sets**: one primary, N secondaries; elections via Raft-like protocol; oplog is the replication log (also what change streams read).
  - **Write concern**: `w:1` (primary only, can be lost on failover), `w:"majority"` (durable across failover), `j:true` (journaled to disk), `wtimeout`.
  - **Read concern**: `local`, `majority`, `linearizable`; **read preference**: `primary`, `primaryPreferred`, `secondary…` — reading from secondaries buys throughput and costs freshness.
  - Causal consistency sessions give read-your-writes across primary/secondary.
- **Sharding**: shard key is the single most consequential decision — it must have high cardinality, even distribution, and match query patterns. Ranged (range queries work, hotspot risk on monotonic keys like timestamps) vs hashed (even spread, no range targeting). Queries without the shard key are **scatter-gather**. Jumbo chunks and balancer behaviour are real ops pain.
- **Transactions**: multi-document ACID since 4.0 (replica sets) / 4.2 (sharded), but with time limits (default 60s) and performance cost — Mongo's model prefers designing so a single document is your transaction boundary.
- **Aggregation pipeline**: `$match` early (uses indexes), `$project` to shrink docs, `$group`, `$lookup` (left outer join — but no index on the joined side in some cases; watch performance), `$facet`, `allowDiskUse` for large sorts.

---

## 3. DynamoDB

- **Keys**: partition key (hash → physical partition) + optional sort key. Query = "PK equals X, SK operators"; anything else is a **Scan** (avoid). Item ≤ **400 KB**; all items sharing one partition key ≤ 10 GB (item collection limit when an LSI exists).
- **Capacity**: on-demand (pay per request, instant scaling) vs provisioned (RCU/WCU + autoscaling, cheaper for steady load). 1 WCU = 1 KB write/s; 1 RCU = one 4 KB strongly-consistent read/s (or two eventually-consistent). Burst + adaptive capacity smooth short spikes.
- **Modeling**: **access-pattern-first, single-table design** — list queries before schema. Overloaded keys (`PK=ORDER#123`, `SK=ITEM#1`), GSI overloading (`GSI1PK/GSI1SK` generic attributes), sparse indexes (index only items with a given attribute — e.g. only `status=PENDING`).
- **GSI vs LSI**: GSI = different PK, eventually consistent, own capacity, added anytime, 20 per table. LSI = same PK/different SK, strongly consistent reads possible, must be created with the table, shares the 10 GB item-collection limit.
- **Consistency & concurrency**: eventually consistent reads by default (strongly consistent option on the base table, never on GSIs). **Conditional writes** (`attribute_not_exists(pk)`) give idempotency; **optimistic locking** via a `version` attribute; **atomic counters** via `ADD`.
- **Transactions**: `TransactWriteItems` — up to 100 items (originally 25), all-or-nothing, ~2× cost, with `ClientRequestToken` for idempotency.
- **Streams** (24h, ordered per partition key) → Lambda/Pipes for CDC-style fan-out. **TTL** for expiry (great for idempotency keys). **PITR** (35 days) and on-demand backups. **Global tables** for multi-region (last-writer-wins conflict resolution).
- **Hot partitions**: write sharding (`PK = tenant#<0-9>`), avoid monotonic keys, spread with a suffix.
- **When not to use it**: ad-hoc queries, analytics, complex joins, or when access patterns aren't stable yet.

---

## 4. Redis

- **Types**: strings, hashes, lists, sets, sorted sets (leaderboards, delayed queues by score), bitmaps, HyperLogLog (approx cardinality), **streams** (append-only log with consumer groups), geo.
- **Threading**: single-threaded command execution (with I/O threads for network in 6+) — so **one O(N) command blocks everyone**. Never run `KEYS *`, big `DEL`, or huge `LRANGE` in prod; use `SCAN`, `UNLINK`, pagination.
- **Persistence**: RDB snapshots (fast restart, may lose minutes) vs AOF (append-only, `everysec` default, near-durable, larger/slower restore) — or both. Redis is **not** your system of record; replication is async so failover can lose writes.
- **Eviction**: `maxmemory` + policy (`allkeys-lru`, `allkeys-lfu`, `volatile-ttl`, `noeviction`). Know that `noeviction` turns a full cache into write errors — which is sometimes what you want for a queue/lock store.
- **Caching patterns**: cache-aside (read: miss→load→set with TTL; write: update DB then invalidate), read-through/write-through (via a library), write-behind (fast but risks loss).
  - **Stampede/dogpile protection**: per-key mutex (`SET lock NX PX`), early recomputation (probabilistic early expiry), or serve-stale-while-revalidate.
  - **TTL jitter** so a bulk-populated cache doesn't expire simultaneously.
  - **Negative caching** for "not found" to protect the DB from scans.
  - Invalidation: versioned key prefixes (`v3:product:123`) let you invalidate a whole class instantly.
- **Distributed locks**: `SET key token NX PX 30000`, release via Lua compare-and-delete (never delete someone else's lock), renew with a watchdog if work runs long. Redlock exists but is contested — for correctness-critical locks, prefer a DB constraint/fencing token.
- **Rate limiting**: fixed window (simple, bursty at boundaries), sliding window log (accurate, memory heavy), sliding window counter, **token bucket in Lua** (atomic, most used).
- **Pub/Sub vs Streams**: Pub/Sub is fire-and-forget (no persistence, no replay, subscriber offline = message lost). **Streams** give persistence, consumer groups, `XACK`, pending-entries list, `XAUTOCLAIM` for stuck messages — a real queue, when you don't want another broker.
- **Cluster**: 16,384 hash slots; multi-key ops must share a slot → **hash tags** `{userId}`; resharding moves slots. Sentinel provides HA for non-cluster setups. ElastiCache/MemoryDB: MemoryDB is durable (multi-AZ transaction log) if you need Redis as a primary store.

---

## Rapid-fire Q&A

**Q: Postgres vs MongoDB for a new service?**
Relational when data is highly relational, you need joins, multi-row transactions, or strong constraints (most business/catalog domains). Document when the aggregate is genuinely self-contained, schema varies per record, and you read/write whole documents. Postgres `jsonb` covers many "we need flexibility" cases without giving up SQL — so the bar for reaching for Mongo is "we need horizontal write scaling or a document-shaped access pattern," not "our schema might change."

**Q: You have a 2-second query on a 50M-row table. Process?**
Reproduce with `EXPLAIN (ANALYZE, BUFFERS)` → check estimates vs actuals → look for seq scans/sorts/spills → verify statistics and index usability (function/type mismatch on the predicate) → add or reshape an index (composite order, partial, covering) → if it's an aggregate, consider a materialized view or precomputed counter → confirm with `pg_stat_statements` that overall load dropped, not just the single query. Only then consider partitioning or a read replica.

**Q: Two ETL workers must not process the same SKU. How?**
`SELECT … FROM staging WHERE status='PENDING' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 100` inside a short transaction, mark rows `IN_PROGRESS` with a worker ID, commit, then process. Alternatively a unique constraint on `(sku, run_id)` and let the DB reject the duplicate, or a Postgres advisory lock keyed by SKU hash.

**Q: Cache and DB disagree. How did that happen and how do you prevent it?**
Classic race: reader loads old value, writer updates DB and invalidates, reader then writes its stale value into the cache. Mitigations: write-then-invalidate with short TTLs, delete-on-write rather than update-on-write, versioned keys, or invalidate via CDC/outbox events so the invalidation is tied to the committed write rather than the application's best effort.

**Q: How would you shard when a single Postgres stops keeping up?**
First exhaust the cheap wins: indexes, caching, read replicas, partitioning, archiving cold data, connection pooling, moving analytics off the OLTP box. Then shard by a natural tenant key (`storeId`/`customerId`) with a routing layer or Citus, accepting cross-shard queries become fan-out. Sharding is the last resort because it removes cross-shard transactions and joins.
