# Backstage/CNCF, Design Patterns, System Design Fundamentals, Trap Questions

---

## 1. Backstage (your CNCF open-source contribution)

### What Backstage is
An open-source developer portal framework (created at Spotify, now a **CNCF** project). It's not a product you install and use — it's a platform you build your internal portal on, composed of plugins.

### Core pillars
1. **Software Catalog** — the inventory of everything: services, APIs, resources, and who owns them.
2. **Software Templates (Scaffolder)** — golden-path project creation with parameterized templates and actions.
3. **TechDocs** — docs-as-code (Markdown in the repo → rendered in the portal via MkDocs).
4. **Search** — unified search over catalog entities, docs, and plugin data.
5. **Plugins** — everything else (CI/CD views, Kubernetes, cost, incidents).

### Catalog model (know the entity kinds)
`Component` (a service/website/library), `API` (an interface a component exposes/consumes), `Resource` (a DB, bucket, queue), `System` (a group of components/resources), `Domain`, `Group`/`User` (ownership), `Location` (where to find more entity descriptors).
Entities are declared in **`catalog-info.yaml`** in each repo, discovered by **entity providers**, refined by **processors**, and referenced by **entity refs** (`kind:namespace/name`). Relations (`ownedBy`, `providesApi`, `dependsOn`) form the graph that makes the catalog useful.

### Architecture
- **Frontend**: React app composed of plugins, each with routes, extensions, and an API registry for cross-plugin services.
- **Backend**: originally an Express-based backend with plugin routers; the **new backend system** uses a declarative plugin/module/service model (`createBackendPlugin`, dependency injection, extension points).
- **Catalog client**: the typed client that any plugin (frontend or backend) uses to query the catalog API — filters, pagination, entity refs. This is what you contributed to.
- **Search**: a search backend with **collators** (pull documents from a source — e.g. the catalog collator turns entities into indexable documents), **decorators** (enrich documents), and pluggable **search engines** (in-memory Lunr, Postgres, Elasticsearch).

### Your contribution — "predicate-based search API & catalog client"
Be ready to explain, in this order:
1. **The problem**: consumers needed to express structured filters (field-level predicates, combinations) rather than only free-text or fixed query params; without it, callers over-fetched and filtered client-side.
2. **The design**: a predicate/filter grammar (field, operator, value; AND/OR composition), how it maps onto the storage/search engine query, and how it degrades safely for engines with fewer capabilities.
3. **API compatibility**: the parameters had to be additive so existing callers were unaffected — the same backward-compatibility discipline you apply to production contracts.
4. **Client ergonomics**: the catalog client method signature, typing, pagination behaviour, and error handling.
5. **Process**: how you engaged maintainers (issue/RFC first?), review iterations, tests, changeset/changelog, and what you learned about designing an API for consumers you'll never meet.

**Likely questions**: "Why predicates instead of just more query params?" (composability, validatability, engine pushdown, avoids param explosion). "How do you prevent expensive queries?" (limit operators, enforce pagination, index-backed fields only, depth/complexity caps). "What was the hardest review feedback?" (have a real answer — this is a rare, high-signal story most candidates don't have; use it).

### Talking about CNCF/OSS process
Contribution flow: issue → discussion/RFC for anything non-trivial → PR with tests and docs → **DCO sign-off**/CLA → maintainer review cycles → changeset for release notes → semver-aware release. Emphasize the discipline: small PRs, no breaking changes, documented behaviour, tests that read as specification.

---

## 2. OOP & design patterns (your resume mentions design patterns explicitly)

### SOLID — one line each, with the smell it fixes
- **S**ingle Responsibility — a class changes for one reason; fixes "god service."
- **O**pen/Closed — extend without modifying; fixes the growing `switch` on type.
- **L**iskov Substitution — subtypes must honour the base contract; fixes surprising overrides that throw or tighten preconditions.
- **I**nterface Segregation — many small interfaces; fixes fat interfaces forcing empty implementations.
- **D**ependency Inversion — depend on abstractions; fixes untestable code wired to concrete infrastructure.

### Patterns worth having a real usage story for
| Pattern | Where it shows up in your work |
|---|---|
| **Strategy** | Per-source validation/parsing strategies in ingestion |
| **Factory / Abstract Factory** | Creating handlers per event type / per integration |
| **Builder** | Complex query/DTO construction (predicate builders!) |
| **Adapter / Anti-corruption layer** | Cal.com, Directus, Stripe wrappers |
| **Decorator** | Nest interceptors, retry/caching wrappers |
| **Observer / Pub-Sub** | Event-driven consumers |
| **Command** | Queue messages as commands; saga steps |
| **Template Method** | Base ETL step with hooks per source |
| **Chain of Responsibility** | Middleware/guard/pipe chains |
| **Repository / Unit of Work** | JPA repositories + persistence context |
| **Circuit Breaker / Bulkhead / Retry** | Resilience layer around dependencies |
| **Singleton** | DI-managed beans/providers (note: framework-managed, not `static` global) |

Anti-patterns to name: anemic domain model, service locator, god object, premature abstraction, and "pattern-driven design" (choosing a pattern before the problem).

### Concurrency vocabulary
Race condition, critical section, mutex/semaphore, deadlock (and the four Coffman conditions), livelock, starvation, optimistic vs pessimistic concurrency, atomicity, idempotency, thread pools and queue saturation, immutability as a concurrency strategy, and the event-loop model as an alternative to threads.

---

## 3. System design fundamentals (the generic round)

- **Scaling**: vertical vs horizontal; stateless services + externalized state; sharding/partitioning; **consistent hashing** (why it beats modulo when nodes change).
- **Load balancing**: L4 vs L7, algorithms (round robin, least connections, hashing), health checks, connection draining, sticky sessions (and why to avoid them).
- **Caching layers**: client → CDN → gateway → application → database buffer pool; cache-aside vs read-through/write-through/write-behind; invalidation; hit-ratio reasoning.
- **CAP / PACELC**; consistency models (strong, eventual, causal, read-your-writes, monotonic reads); quorum (`R + W > N`); leader election; split brain.
- **Reliability math**: series vs parallel availability (5 dependencies at 99.9% each ⇒ ~99.5% combined — the argument for async decoupling and graceful degradation).
- **Rate limiting & backpressure**: token bucket, sliding window, per-tenant quotas, load shedding, admission control.
- **Idempotency & exactly-once** (the recurring theme of your resume).
- **Data structures for scale**: bloom filters (dedupe/precheck), LSM trees vs B-trees (write- vs read-optimized stores), inverted indexes (search), HyperLogLog (approximate counts).
- **Multi-tenancy**: shared schema with tenant ID (cheap, needs strict filtering) vs schema-per-tenant vs DB-per-tenant (isolation, cost, migration overhead).
- **Security basics**: least privilege IAM, secrets management, encryption in transit/at rest, input validation, OWASP Top 10 + API Top 10, audit logging, PII minimization and retention.
- **Cost as a design constraint**: per-request vs per-hour pricing, egress and NAT costs, storage class lifecycles, log/metric retention — a senior differentiator most candidates ignore.

### A repeatable system-design answer structure
1. Clarify functional requirements and **non-functional** ones (scale, latency SLO, consistency, retention, compliance).
2. Estimate: QPS (avg and peak), payload size, storage growth/year, read:write ratio.
3. Sketch the high-level components and data flow.
4. Define the data model and the access patterns.
5. Walk the critical path end-to-end, then the failure paths (this is where your event-driven experience shines).
6. Address scaling bottlenecks, then observability, then cost.
7. State trade-offs explicitly and name what you'd do differently at 10× and 100×.

---

## 4. Trap questions your resume specifically invites

| Trap | What they're testing | Your line |
|---|---|---|
| "You wrote CDC — which log did you read?" | Buzzword vs practice | Be precise about the flavour you used, and know log-based CDC properly |
| "50k events/day is ~0.6/sec. Why serverless?" | Whether you inflate difficulty | Talk about **burstiness**, isolation, and ops cost, not raw throughput |
| "How is 99.9% measured?" | Numbers you can't defend | SLI definition + budget math + where failures land (DLQ) |
| "1M+ records — over what period?" | Vague metrics | Have the timeframe, peak rate, and file sizes |
| "35% latency cut — from what to what?" | Made-up percentages | Baseline, method of measurement, comparable windows |
| "Saga: show me a compensation you wrote" | Pattern name vs implementation | One concrete step + its inverse + the failure path |
| "Six microservices for one product — was that justified?" | Judgment, not resume padding | Give the boundary rationale, and admit where a modular monolith would have done |
| "You listed Kubernetes and ArgoCD — did you operate them or use them?" | Honesty about depth | State your actual level: "I owned service manifests, probes, HPA, and Argo apps; cluster upgrades were platform-owned" |
| "Which of these tools would you drop from your resume?" | Self-awareness | Pick the one you used least, say what depth you have, and what you'd need a week to relearn |

**Rule**: for every tool on the resume, be ready to place yourself honestly on: *used it in production and debugged it* / *used it, happy path* / *read and prototyped*. Interviewers forgive limited depth; they don't forgive discovering you bluffed.

---

## 5. Final-week checklist

- [ ] Draw all three flagship systems (ingestion pipeline, saga ETL, CDC replacement) from memory in under 3 minutes each.
- [ ] Write, from memory: an SQS consumer's failure path; an idempotent upsert; a keyset pagination query; a PromQL error-ratio; a saga step + compensation.
- [ ] Have exact numbers for every metric on the resume (or soften the resume wording to what you can defend).
- [ ] Two mentoring stories, one disagreement story, one incident story, one "I was wrong" story.
- [ ] One sentence each on: why EventBridge vs SNS, SQS vs RabbitMQ, saga vs 2PC, CDC vs outbox, Lambda vs Fargate, sessions vs JWT, Postgres vs Mongo.
- [ ] Questions **for them**: on-call model, how services are boundaried, deploy frequency and rollback story, what breaks most often, how SLOs are set, what the last incident taught them.
