# Scenario 02 — Saga / ETL: the PO Enrichment Pipeline

> **Resume bullet:** _"Designed an ETL pipeline using the Saga pattern for transactional rollback and concurrent SKU resolution, processing 50k+ rows per run with automated S3 uploads and retry support for failed records."_

Everything under **What you built** is your real system. Everything under **What you'd improve** is explicitly framed as a change you'd make — never claim it as built. Business context and numbers are illustrative; swap in your real ones.

---

## The scenario

> A retail supply-chain platform. Nightly, an upstream ERP drops PO activity into S3 as two files — `POCreate` and `POCancel`. One scheduled Lambda picks them up, enriches every line item against an internal SKU service, updates four DynamoDB tables, and publishes four files that a warehouse-management process consumes the next morning.
>
> If the job half-finishes, the WMS reads partial data and creates real picking errors. **That's why there's a saga.**

Lead with that last line. It beats "we used the saga pattern."

| | |
|---|---|
| Volume | ~50k PO line items/day (~42k creates, ~8k cancels) |
| Files | 2 in, 4 out (~40 MB) |
| DynamoDB tables written | 4 |
| SKU calls | ~12k unique, ~76% in-run cache hit |
| Lambda | 3008 MB, 15 min timeout, reserved concurrency 1 |
| Run duration | ~6 min typical |
| Rejects | ~0.5% (~250/day) |
| Carryover failures | 100–300/day |
| Schedule | 02:00 daily; WMS consumes from 07:00 |

Two numbers earn their keep: **reserved concurrency 1** (runs can never overlap — this is what makes the rollback safe) and **6 min against a 15-min ceiling** (your headroom, and the answer to most scaling questions).

---

## The architecture

```
  EventBridge Scheduler (daily 02:00)
            ▼
  ┌────────────────────────────────────────────────┐
  │  ONE Lambda — in-process saga orchestrator     │
  └────────────────────────────────────────────────┘
   │
   ├─▶ 1. Read POCreate + POCancel from S3        ── read only
   ├─▶ 2. Load carryover failures from DynamoDB   ── read only
   ├─▶ 3. Enrich via SKU API (bounded pool + cache)── read only
   │        └── invalid → rejects table
   ├─▶ 4. Snapshot current state of 4 tables      ── read only
   │
   ├─▶ 5. Write new metadata → 4 DynamoDB tables       ⚠ MUTATES
   └─▶ 6. Upload 4 files → S3 target folder            ⚠ MUTATES
            ▼
      WMS consumer (07:00) — processes and deletes;
                             archives on its own failure
```

Compensations, in reverse: delete uploaded files → restore the DynamoDB snapshot.

**Worth saying out loud:** steps 1–4 are read-only and need no compensation. Only two steps mutate anything, so that's all the saga has to protect.

---

## The 40-second pitch

> "A single scheduled Lambda, once a night, with the saga orchestration written in-process — a sequence of steps, each with a compensating action, unwound in reverse on failure.
>
> It reads the day's PO create and cancel files, pulls in records that failed on previous runs for another attempt, then enriches each line item against an internal SKU service — cached and with bounded concurrency, since that service is shared. Records missing required fields go to a rejects table rather than being dropped.
>
> Then the mutating half: snapshot the four DynamoDB tables, write the new state, and upload four output files to S3. If either fails, it compensates — restore the snapshot, delete the files."

Stop there and let them pick a thread.

---

## The probes

**Choreography or orchestration?**
> "Orchestration. The sequence is fixed and single-owner, so there's nothing to decouple. Compensations have to run in reverse order and something has to know that order — in choreography that knowledge scatters across participants and no single place tells you where the saga is. And when this fails at 2am I want one place to look."

*Counter, if pushed:* choreography if the steps were owned by different teams and had to evolve independently — at the cost of no component knowing the transaction's state.

**Why not Step Functions?**
> "Step Functions caps the payload between states at 256 KB. My enriched dataset is ~100 MB in memory, so every step boundary becomes a serialize-to-S3 and read-back round trip — more code, more failure modes, for a job that fits in one process at 6 minutes against a 15-minute ceiling. Inside one Lambda the data just flows between steps.
>
> What I gave up is orchestrator durability — I'll come to that."

**So what happens if the Lambda dies mid-saga?** ← *the hard one*
> "Then nothing runs the compensations. The orchestration state lives in process memory and dies with it. That's a real gap and I didn't solve it in code — I'd rather say what actually bounded the risk.
>
> The blast radius is small. It's a daily batch with a five-hour gap before the consumer runs, so there's time for a human to re-run it. The failure is loud — the run either succeeds or the alarm fires. And the worst realistic outcome is partially-updated DynamoDB tables with no files published, which a re-run fixes.
>
> If I were hardening it: persist saga state to DynamoDB at each step so the next run detects an incomplete saga and recovers before starting, or move to Step Functions and get that for free."

Concede it cleanly, explain what bounded the risk, then say what you'd do. That's a stronger answer than a mitigation you didn't build.

**What if a compensation fails?**
> "Compensations are idempotent — restoring a known value twice is the same as once, deleting an already-deleted object is a no-op — and retried with backoff. If they still fail, it's a business incident, not a technical retry: alert with the run ID and a human decides whether to complete forward or restore manually. Any design claiming compensations never fail is hiding something."

**What consistency do you get?**
> "ACD, not ACID — no isolation. Between the DynamoDB write and the file upload the system is in a state no single transaction would produce. It's not observable here because nothing else reads those tables in that window."

Also be precise: *"A compensation isn't a rollback — the original already committed. It's a new forward transaction that semantically negates it."*

**Why not just retry forward instead of rolling back?**
> "Considered it. If every step is idempotent, retry-forward is often better than unwinding work you'll redo tomorrow. I chose rollback because the consumer would otherwise see partial data and a warehouse acting on a half-batch is expensive. If the output were append-only I'd have gone forward-only and skipped the compensation machinery."

**50k rows — why is that hard?**
> "It isn't, by volume. It's hard because it's all-or-nothing across two storage systems, inside a 15-minute process, with a hard downstream deadline."

---

## Three weaknesses to own

Raise these yourself. Finding the flaw in your own design is one of the strongest signals available.

**1. Snapshot-restore has a lost-update race.** If anything writes to those items between your snapshot and your rollback, the compensation destroys that write.

> "It's only correct because the saga is the sole writer in its window — nightly batch, reserved concurrency 1, no other process touches those tables. That's an assumption the design depends on, not something true by accident. If a second writer appeared I'd move to conditional writes on a version attribute, and if the version no longer matches, *stop and alert* rather than clobber — at that point automated rollback is destroying real data."

Knowing when *not* to auto-rollback is the judgement being tested.

**2. Deleting published files is a race.** If the consumer picks up file 1 before compensation deletes files 2–4, a partial batch is already downstream and a delete can't recall it. "The target folder is normally empty" is a timing assumption, not a guarantee.

> *Improvement:* upload to a `staging/` prefix, promote to `target/`, then write a `_SUCCESS` manifest **last**. The consumer ignores the folder until the manifest exists. Publishing becomes atomic from its point of view — before the manifest nothing is visible, after it everything is. Same pattern Hadoop and Spark use, for this exact reason.

This is your best answer to *"what would you do differently?"*

**3. The carryover retry loop has no exit.** A deterministically-failing record retries every night forever, the batch creeps toward the 15-minute cliff, and real new failures get buried in a growing pile so the alert becomes noise.

> *Improvement:* an `attempt_count`, and after 3 tries the record moves to permanent quarantine and drops out of the retry set. The alert then reports two numbers — transient failures still retrying, and newly quarantined records needing a human.

---

## Worth mentioning if it fits

- **`TransactWriteItems`** — DynamoDB gives real ACID transactions across tables (100 items, 4 MB, one region). Where the writes fit, those four table writes are genuinely atomic, and the saga shrinks to just the DynamoDB↔S3 boundary. *A saga should only span the boundaries a real transaction can't reach.*
- **Bounded concurrency** — "concurrent SKU resolution" on your resume means a promise pool capped at ~30, not `Promise.all` over 12k calls. Unbounded would be a self-inflicted DoS on a shared service.
- **Record-count reconciliation** — `input == published + rejected + carried forward`. Fails the run on any imbalance. This is the answer to "how did you know it worked."
- **Timeout headroom as a metric** — not just success/failure, but what percentage of the 15 minutes you consumed, tracked over time. On a single-Lambda design it's your most important early warning.
- **Circuit breaker on the SKU service** — if it's degraded, 12k calls hammer a struggling system and fail the batch anyway. Fail fast, retry the run in an hour.

---

## Trap table

| Trap | Answer |
|---|---|
| "Why not Step Functions?" | 256 KB inter-state limit vs a 100 MB in-memory dataset. Then volunteer the durability tradeoff. |
| "What if the Lambda dies mid-saga?" | Concede it. Five-hour recovery window, loud failure, re-run fixes it. Saga log or Step Functions is the hardening. |
| "What happens at 15 minutes?" | ~6 min today. Sustained growth past ~60% is the trigger to move to Step Functions with chunked checkpointing. |
| "Snapshot restore — what if someone else wrote?" | Name the single-writer invariant. Version-guarded conditional writes if it breaks. |
| "You delete files on failure — what if the consumer read one?" | Concede the race, present staging + manifest. |
| "Do you need a saga for four DynamoDB writes?" | Not if they fit `TransactWriteItems`. The saga only spans DynamoDB↔S3. |
| "How does this scale 10x?" | Single-Lambda breaks first, and 15 min is a cliff not a slowdown. Step Functions + Map over chunks + per-chunk checkpointing. Past that, it shouldn't be a daily batch at all. |
| "What broke in production?" | The carryover poison-pill. Honest, and it has a clean fix. |

---

## Related

- `../02-architecture-patterns.md` — saga theory, idempotency, retry semantics
- `../01-aws-serverless.md` — Lambda, Step Functions, S3, DynamoDB
- `../09-project-deep-dives.md` — STAR version of this bullet
