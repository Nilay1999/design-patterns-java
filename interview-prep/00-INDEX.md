# Resume Interview Prep — Master Index

**Profile:** Backend SWE, 5 yrs — Java Spring Boot, Node.js/NestJS, event-driven AWS.
**Scope of this pack:** every architecture pattern, tool, service, and concept named on the resume, in interview depth. Programming languages themselves are deliberately excluded.

---

## Files

| File | Covers |
|---|---|
| `01-aws-serverless.md` | Lambda, EventBridge, SQS, Kinesis, Step Functions, API Gateway, ECS Fargate, S3, CloudWatch, Aurora/RDS |
| `02-architecture-patterns.md` | Microservices, Event-Driven Architecture, REST API design, Saga, CDC, Outbox, idempotency, retries, fault tolerance, data contracts, LLD |
| `03-databases.md` | PostgreSQL, MySQL, Aurora, MongoDB, DynamoDB, Redis, indexing, transactions, query optimization |
| `04-frameworks-runtimes.md` | Spring Boot, Hibernate/JPA, Node.js runtime, NestJS, Express |
| `05-messaging.md` | RabbitMQ deep dive, SQS vs RabbitMQ vs Kafka/Kinesis, delivery semantics |
| `06-devops-k8s.md` | Docker, Kubernetes/EKS, ArgoCD/GitOps, GitLab CI/CD, NX monorepo |
| `07-observability.md` | Prometheus, Grafana, Datadog, CloudWatch, SLO/error budgets, tracing |
| `08-auth-integrations.md` | JWT, sessions, OAuth2/OIDC, Ory Kratos, Stripe, Cal.com, Directus |
| `09-project-deep-dives.md` | Every resume bullet as STAR + drill-down Q&A + number defense |
| `10-backstage-and-fundamentals.md` | Backstage/CNCF, design patterns, system design fundamentals, trap questions |
| `scenarios/` | One resume bullet per file, built out as a **concrete tellable story**: business context, defensible numbers, the diagram to draw, the 60-second pitch, and the traps |

### Scenarios

`09-project-deep-dives.md` covers *what to say*. The `scenarios/` files go one level deeper — a full fabricated-but-consistent business context per bullet, so the story holds up under 20 minutes of drilling rather than 2.

| File | Resume bullet |
|---|---|
| `scenarios/02-saga-etl-po-pipeline.md` | Saga / ETL — PO enrichment, compensating transactions, single-Lambda orchestrator |
| `scenarios/03-catalog-inventory-cdc.md` | Products/Taxonomy/Inventory services, service boundaries, API versioning, read scaling, CDC + outbox |

These share one fabricated business context — a retail distribution platform — so the bullets read as one system rather than unrelated projects. The SKU service the PO pipeline calls in `02` is the Products service in `03`.

---

## The 8 questions every resume bullet must survive

Interviewers rarely ask "what is SQS." They ask about **your** bullet and then drill. For each line on your resume, have answers ready for:

1. **What exactly did you build?** (boxes, arrows, data flow, who calls whom)
2. **Why this design over the obvious alternative?** (the alternative is always: cron polling, a monolith, a direct DB call, Kafka)
3. **What were the failure modes and how did you handle them?** (retry, DLQ, partial failure, duplicate, out-of-order)
4. **How do you know it worked?** (metric, alarm, dashboard, load test — this is where "99.9%" gets audited)
5. **Where did the number come from?** (1M records, 50k events, 35%, 500+ locations, 15k users, 25k records)
6. **What was hard / what broke in production?** (a real war story beats any theory answer)
7. **What would you do differently now?** (shows growth, not defensiveness)
8. **How does it scale 10x/100x?** (where does it break first — DB connections, Lambda concurrency, shard count, cost)

---

## Resume line → topics you must own

| Resume claim | Must be able to discuss |
|---|---|
| Event-driven ingestion, 1M+ XML records, EventBridge + SQS + Lambda | EventBridge rules/patterns/archive, SQS visibility timeout + DLQ + partial batch response, Lambda ESM scaling, 256KB size limits → claim-check via S3, XML/XSD validation, S3 archival + lifecycle, Aurora writes from Lambda + RDS Proxy |
| Serverless pipelines, LLD for data contracts, retry semantics, fault tolerance, 50k+ events/day at 99.9% | Schema versioning & compatibility, exponential backoff + jitter, idempotency keys, DLQ + redrive, SLO math & error budget, timeouts, poison messages |
| Java Spring Boot microservices (Products, Taxonomy, Inventory) as internal APIs | Bounded contexts, API contracts & versioning, `@Transactional` boundaries + propagation, Hibernate N+1/fetch strategy, pagination for high-volume reads, connection pool sizing, index/EXPLAIN work |
| CDC pattern replacing cross-service polling, −35% latency, 500+ locations | CDC flavors (log-based vs outbox vs polling), transactional outbox, dual-write problem, ordering & dedupe, SQS consumer design, how latency was measured |
| ETL with Saga, 50k+ rows/run, concurrent SKU resolution, S3 uploads, retries | Saga orchestration vs choreography, compensating transactions, ACD not ACID + countermeasures, concurrency control (`SELECT … FOR UPDATE SKIP LOCKED`, optimistic version), batch/bulk DB writes, checkpointing/resume |
| Mentoring, service boundaries, data contracts | DDD, Conway's law, consumer-driven contracts, review culture, how you disagreed and resolved it |
| Ory Kratos migration, 6 NestJS services, NX monorepo, EKS, RabbitMQ | Sessions vs JWT, identity schemas, password hash import, zero-downtime migration & rollback, Nest microservice transports, NX affected builds, K8s deploys |
| Stripe membership payments, 15k+ users | PaymentIntent/Subscription lifecycle, webhook signature + idempotency + ordering, dunning, reconciliation job, PCI scope |
| Cal.com + Directus microservices, isolated RDS | Third-party integration patterns, anti-corruption layer, timezone/DST correctness, DB-per-service tradeoffs |
| 25k user data migration | Migration strategy (big-bang vs dual-write vs backfill+CDC), validation/reconciliation, rollback, idempotent re-runs |
| RabbitMQ, Redis, S3, SES at Inexture | Exchanges/DLX/prefetch/acks, cache-aside + stampede, email deliverability basics, bounce/complaint handling |
| Backstage OSS (predicate-based search API + catalog client) | Backstage catalog model, plugin/backend architecture, search collators, API design for filters, working with maintainers |
| 550+ DSA, OOP design patterns | SOLID, GoF patterns you actually used, complexity reasoning, concurrency primitives |

---

## Study order (highest ROI first)

1. **`09-project-deep-dives.md`** — your own stories. Interviewers start here.
2. **`02-architecture-patterns.md`** — Saga, CDC, EDA, idempotency. This is the core of the resume's identity.
3. **`01-aws-serverless.md`** — the AWS list is long and specific; specifics get tested.
4. **`03-databases.md`** — every backend loop has a DB round.
5. **`05-messaging.md`** + **`07-observability.md`** — "how did you know it was reliable?"
6. **`04-frameworks-runtimes.md`** — Spring/Hibernate/Nest internals for the framework round.
7. **`06-devops-k8s.md`**, **`08-auth-integrations.md`**, **`10-backstage-and-fundamentals.md`** — breadth rounds.

---

## Rules of engagement in the interview

- **Never inflate.** If a bullet was partly team work, say "I owned X, my teammate owned Y." Senior interviewers reward precise ownership and punish vague "we built."
- **Lead with the constraint, not the tech.** "We needed ordering per store and at-most-a-minute freshness, so…" beats "we used SQS FIFO."
- **Quantify the alternative you rejected.** "Polling every 30s across 500 locations was ~1.4M queries/day for mostly unchanged rows."
- **Have one failure story per major system.** Duplicate processing, a DLQ storm, a bad deploy, a lock timeout — with the fix.
- **Draw.** Ask for a whiteboard/screen share and draw the pipeline before talking. It structures the entire answer.
