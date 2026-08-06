# Architecture Patterns — Microservices, Event-Driven Architecture, REST, Saga, Change Data Capture, Reliability

This is the heart of your resume, so expect the deepest questioning here. An interviewer will not be satisfied with naming a pattern; they want to know what problem it solves, what it costs you, and what you would do instead.

For every pattern in this file you will find:

1. **What it is** — explained in plain words, with the problem it exists to solve.
2. **Every part explained** — each concept, rule, and variation, spelled out rather than listed as a keyword.
3. **What it costs** — the honest downside, because naming the cost is what separates a senior answer from a junior one.
4. **How it behaves as the system grows** — where the pattern stops working and what you do next.

At the end there is a section on how these patterns combine into real architectures, a section that pulls all the scaling discussion together, and a rapid-fire question and answer section.

---

## Table of contents

1. [Microservices](#1-microservices)
2. [Event-Driven Architecture](#2-event-driven-architecture)
3. [REST API design](#3-rest-api-design)
4. [Saga pattern](#4-saga-pattern)
5. [Change Data Capture](#5-change-data-capture)
6. [Reliability toolkit](#6-reliability-toolkit)
7. [How the patterns combine](#7-how-the-patterns-combine)
8. [Scaling, put together in one place](#8-scaling-put-together-in-one-place)
9. [Rapid-fire questions and answers](#9-rapid-fire-questions-and-answers)

---

## 1. Microservices

### What it is

Microservices means building a system as a set of independently deployable services, each organised around a business capability, each owning its own data, and communicating over the network.

The word "micro" is misleading and you should say so. **The unit of value is independent deployability, not smallness.** A service that is 200 lines long but cannot be released without three other services being released at the same time gives you none of the benefit and all of the cost. A service that is 20,000 lines long but can be deployed on its own on a Tuesday afternoon gives you the full benefit. Size is a consequence of drawing the boundary in the right place, not a goal.

### Why teams do it, and what it actually costs

| What you gain | What you pay for it |
|---|---|
| Independent deployment and independent scaling | Every call that used to be a function call is now a network call, with latency, partial failure, and retries |
| Team autonomy — a team can ship without coordinating with everyone else | Any change that crosses a service boundary now needs versioning and a coordinated rollout |
| Fault isolation — one service failing does not take the whole system down | You lose database transactions across services, so you need sagas and you have to live with eventual consistency |
| Freedom to use different technologies per service | The operational surface multiplies: every service needs its own pipeline, monitoring, alerting, tracing, and on-call story |
| Smaller, more understandable codebases | The system as a whole becomes harder to understand, because the behaviour now lives between the services rather than inside any one of them |

**The sentence to say in an interview:** "Microservices trade a local complexity problem for a distributed one. That trade pays off when teams genuinely need independent release cadence, or when different parts of the system have very different scaling profiles. Our ingestion pipeline and our catalogue API were a good example — the pipeline needed to scale hard and briefly, the API needed steady low latency, and putting them in one deployable meant we were always sizing for the wrong one."

That framing works because it does not pretend the pattern is free, and it names a concrete reason rather than "it is more scalable."

### Drawing service boundaries

This is the part that actually determines whether the architecture works, and it is where interviewers dig.

**Start from business capability, not from technical layer.** A boundary around "Products", "Taxonomy", and "Inventory" works. A boundary around "the database layer", "the API layer", and "the validation layer" does not, because every feature change touches all three, so you have to deploy all three together — which is the distributed monolith.

**Use bounded contexts from domain-driven design.** The key insight is that the same word means different things in different parts of the business, and that difference is where the boundary belongs. "Product" in the Inventory context means a stock-keeping unit with a quantity and a location. "Product" in the Taxonomy context means a node in a classification tree with parents and attributes. Trying to build one shared Product model that serves both produces an object with forty fields where each consumer uses eight of them, and every change breaks somebody. Two models, each correct in its own context, is the right answer.

**The practical heuristics for where to cut:**

- **Data ownership.** Exactly one service writes each table. If two services write the same table, they are one service that has been split incorrectly.
- **Transactional cohesion.** Things that must change atomically belong together. If splitting two operations forces you into a saga, ask first whether they should have been split at all — a saga is a real cost you are choosing to pay.
- **Rate of change.** Code that changes together should live together. If two areas are always modified in the same pull request, the boundary between them is imaginary.
- **Team ownership.** Conway's law says your architecture will end up mirroring your communication structure whether you plan for it or not. Better to plan for it: one service should have one clear owning team.
- **Different scaling or availability needs.** A component that needs to scale to 100 times normal load for ten minutes a day is a strong candidate to be its own service, because otherwise you pay for that capacity everywhere.

**The anti-patterns, and why each one hurts:**

| Anti-pattern | What it looks like | Why it hurts |
|---|---|---|
| **Entity service** | `UserService` that every other service calls for anything to do with users | It has no business capability of its own, so it becomes a shared bottleneck and a coordination point. Every feature needs a change in it. |
| **Shared database** | Two services reading and writing the same tables | You have lost independent deployability instantly. Neither team can change the schema. The database is now the coupling you were trying to remove. |
| **Chatty synchronous chains** | Service A calls B, which calls C, which calls D, to serve one request | Availability multiplies downwards (four services at 99.9 percent each gives roughly 99.6 percent overall) and latency compounds — the p99 of the chain is far worse than the p99 of any link. |
| **Distributed monolith** | Services that must be deployed together in a particular order | All the operational cost of microservices, none of the benefit. This is the most common failure mode, and it usually comes from boundaries drawn along technical layers rather than business capabilities. |

### Data management

**Database per service** is the rule, and it is the rule that makes everything else possible. Once each service owns its own store, it can change its schema, choose its storage technology, and scale its data layer independently.

The obvious objection is: how do you query across services when you can no longer join? Three answers, and you should know when each applies:

| Approach | How it works | When to use it | What it costs |
|---|---|---|---|
| **API composition** | The caller queries several services and joins the results in memory. | Low fan-out, small result sets, a page that shows one order with its customer. | Latency adds up, and it does not work for filtering or sorting across services — you cannot ask "all orders from customers in Germany" without pulling everything. |
| **CQRS read model** | A separate read-optimised store is built by consuming events from the owning services, holding a pre-joined view. | Complex queries across several services, dashboards, search. | Eventual consistency, plus a whole extra store to build, populate, backfill, and keep correct. |
| **Data duplication through events** | Each service keeps a local read-only copy of the small amount of other services' data that it needs. | A service needs a customer's country on every request and cannot afford a network call for it. | The copy is stale by however long replication takes, and you must handle the initial backfill and repair when it drifts. |

The rule underneath all three: **the owning service is still the only writer.** Copies are read-only and rebuilt from events. If a second service starts writing to the copy, you are back to a shared database with extra steps.

### How services talk to each other

**Synchronous communication** (REST or gRPC) means the caller waits for a reply.

- Good: simple to reason about, you get an immediate answer, errors come back to the caller who can decide what to do.
- Bad: it couples availability. If B is down, A is degraded, whether or not A really needed B to answer right now. It also couples latency — every hop adds to the response time, and the tail latency compounds along a chain.

**Asynchronous communication** (events or commands through a broker) means the sender publishes and moves on.

- Good: availability is decoupled, because the broker holds the message while the consumer is down. It buffers naturally, so a spike becomes a backlog rather than a failure. Adding a new consumer does not touch the producer.
- Bad: everything is eventually consistent, so the user may not see their change reflected everywhere immediately. Debugging is harder because there is no single call stack — you need correlation identifiers and distributed tracing from day one, not added later.

**The rule of thumb worth quoting:** queries synchronous, state changes asynchronous, wherever the business can tolerate a short lag. A user asking "what is my balance" needs an answer now. A user's order triggering an inventory update, a notification, and an analytics record does not need all three to happen before the response returns.

**The important nuance:** "asynchronous" does not have to mean the user waits. The pattern is to do the minimum synchronously — validate, write the authoritative record, return 201 — and publish an event for everything else. The user gets a fast, correct response, and the rest of the system catches up in milliseconds.

### Resilience patterns, each with the failure it fixes

Naming these is easy. Knowing exactly which failure each one prevents is what gets you the offer.

| Pattern | The failure it prevents | How it works |
|---|---|---|
| **Timeout** | A slow dependency consuming every thread and connection you have, so your service dies even though it is healthy | Every network call gets a connect timeout and a read timeout. There is no such thing as a call that is allowed to take forever. |
| **Retry with exponential backoff and jitter** | Losing a request to a transient blip that would have succeeded a moment later | Retry only errors that might succeed on a second attempt. Wait longer between each attempt. Randomise the wait so that a thousand callers who all failed at the same moment do not all retry at the same moment. |
| **Circuit breaker** | Continuing to hammer a dependency that is already down, which both wastes your resources and stops the dependency from recovering | Count failures. When the failure rate crosses a threshold, open the circuit and fail immediately without calling. After a cooldown, half-open and let a few probe requests through. If they succeed, close; if not, open again. |
| **Bulkhead** | One slow dependency exhausting a shared pool and taking down calls to every other dependency | Give each dependency its own thread pool or connection pool, with its own limit. A slow dependency can then only exhaust its own bulkhead. Named after ship compartments — one floods, the ship stays up. |
| **Rate limiting and load shedding** | Being overwhelmed by an upstream burst and failing everything, including the important work | Cap how much you accept. When over capacity, reject cheaply and early with 429 rather than accepting work you will fail slowly. Shed low-priority work first so the important work still gets through. |
| **Fallback and cached response** | Returning an error when a slightly stale or reduced answer would have been fine | Serve the last known good value, or a sensible default, when the dependency is unavailable. Degrade rather than fail. |
| **Idempotency** | Retries causing duplicate charges, duplicate rows, duplicate emails | Make repeating an operation produce the same result as doing it once. This is what makes every other retry-based pattern safe. |
| **Dead letter queue and quarantine** | One unprocessable message blocking a pipeline forever | After a fixed number of failures, move the message aside, alert a human, and keep processing everything else. |

**Retry budget** is the concept people miss and interviewers love. Retries at multiple layers multiply. If the client retries three times, the gateway retries three times, and the service retries three times, a single user request can become 27 requests to the struggling dependency. So a dependency having a bad five minutes gets hit with 27 times its normal load exactly when it can least handle it — this is how a small blip becomes a full outage.

Two fixes: **retry at exactly one layer**, usually the one closest to the failure, and let the others pass the error through. And cap retries as a proportion of traffic — for example, allow retries to be at most 10 percent of total requests, and stop retrying beyond that. Saying "we set a retry budget so a brown-out could not be amplified" is a strong, specific answer.

### Contract evolution

Once a service is used by others, its interface is a contract, and you cannot break it unilaterally.

**Changes that are always safe:**

- Adding a new optional field to a response.
- Adding a new optional field to a request, with a sensible default.
- Adding a new endpoint or a new event type.
- Relaxing validation on an existing field.

**Changes that are always breaking:**

- Removing or renaming a field.
- Changing a field's type, including string to number, or a single value to an array.
- Making an optional field required.
- Tightening validation on an existing field — this is the one people forget, because it looks harmless in the code but rejects requests that used to succeed.
- Changing the meaning of an existing field while keeping its name and type. This is the worst kind, because nothing fails loudly; it just produces wrong answers.

**How to make a breaking change safely:** publish a new version alongside the old one (`/v2` for an API, or a new `detail-type` such as `OrderShipped.v2` for events). Run both. Instrument the old one so you can see who is still using it. When usage reaches zero and the deprecation window has passed, remove it. Never coordinate a big-bang cutover across teams — it will be scheduled three times and fail twice.

**Two practices that make this concrete rather than aspirational:**

- **Consumer-driven contract tests** (Pact, or schema-registry compatibility checks in continuous integration). The consumer publishes what it actually depends on; the producer's build fails if a change would break it. This turns "please do not break us" into an automated check.
- **Tolerant reader.** Consumers ignore fields they do not recognise and do not validate fields they do not use. This single discipline makes almost all additive changes non-events, and its absence is why some organisations cannot change anything.

### How microservices scale

**What gets better as you grow:** you can scale each service according to its own load. The ingestion pipeline scales on queue depth, the API scales on request rate, the reporting service scales on nothing because it runs at midnight. In a monolith all three are sized by whichever needs the most.

**What gets worse as you grow, and it is not what people expect.** The technical scaling is usually fine; the **organisational and operational** scaling is what bites:

- **The service map becomes unknowable.** At 15 services a person can hold the map in their head. At 150 they cannot, and nobody can answer "what breaks if I change this."
- **Cross-cutting changes get expensive.** Adding a new required field to a shared concept touches every service. What was a one-line change in a monolith is now a twelve-team programme of work.
- **Latency compounds along chains.** Each hop adds its own p99. A request crossing five services has a tail latency far worse than any individual service's, because the slowest hop varies each time.
- **Availability multiplies downwards.** Five services in a synchronous chain, each at 99.9 percent, gives about 99.5 percent overall — roughly four hours of downtime a year that no single service is responsible for.

**The fixes, which are the mature answer:**

- **Replace synchronous chains with events** wherever the business tolerates lag. This breaks the availability multiplication, because a consumer being down no longer fails the caller.
- **Cache aggressively** at boundaries so the same question is not asked repeatedly down the chain.
- **Provide a platform**, so a new service comes with pipeline, logging, tracing, and dashboards by default rather than being reinvented. This is exactly the problem a developer portal such as Backstage exists to solve, which connects directly to your own work.
- **Merge services back together** when a boundary proves wrong. This is allowed, it is a sign of maturity rather than failure, and saying it out loud in an interview shows you are not dogmatic.

---

## 2. Event-Driven Architecture

### What it is

An event-driven architecture is one where services communicate by publishing facts about things that have happened, rather than by calling each other directly. The producer publishes and does not know who is listening. Consumers subscribe and react.

The structural benefit is that **adding a consumer requires no change to the producer.** In a request-response architecture, every new thing that needs to happen when an order is placed means editing the order service. In an event-driven architecture, the new consumer subscribes and the order service never finds out. That is the whole value proposition, and it compounds as the number of things-that-must-happen grows.

### The four styles — the classic opening question

When an interviewer asks "what kind of event-driven?", they are checking whether you know these four are different things.

**1. Event notification.** The event is thin: `OrderCreated { orderId: "O-9" }`. The consumer calls back to the producer for the details it needs.

- Good: the payload is tiny, and consumers are not coupled to the producer's data shape. The producer can change its internal model freely.
- Bad: every event causes a callback, so you have re-introduced synchronous coupling and a load spike on the producer. It is also possible for the callback to return data that has changed since the event was emitted, which produces subtle bugs.

**2. Event-carrying state transfer.** The event carries the data consumers need: `OrderCreated { orderId, customerId, items[], total }`. Consumers keep their own local copies.

- Good: no callbacks, so consumers keep working when the producer is down. Each service builds exactly the read model it needs.
- Bad: larger payloads, and consumers are now coupled to the event schema, so schema evolution matters a great deal. You also have several copies of the data that can drift.

**3. Event sourcing.** The event log **is** the source of truth. Current state is not stored; it is computed by folding all events for an entity.

- Good: a perfect audit trail for free, the ability to reconstruct state at any past moment, and the ability to build a brand new read model by replaying history.
- Bad: this is a genuinely large commitment. You need snapshots so you do not replay a million events to load one entity. You need upcasting to handle events written in an old shape years ago. Queries become hard, so you almost always need CQRS alongside it. Do not claim you did event sourcing if you only published events — interviewers ask about snapshotting and upcasting and the difference shows immediately.

**4. Command Query Responsibility Segregation (CQRS).** The write model and the read model are separate, usually kept in sync by events.

- Good: you can shape and scale reads completely independently of writes. A normalised write model and a denormalised, pre-joined read model, each optimal for its job.
- Bad: eventual consistency between them, which means a user can write and then not see their own change. You need a plan for that — read from the write model straight after a write, or return the expected new state from the write itself.

**Be precise about which one you did.** An ingestion pipeline that publishes records with their data and archives them is style 2 with archival. Change events that carry the changed fields are a hybrid of 1 and 2. Neither is event sourcing. Saying "we published events" and letting the interviewer assume event sourcing is a trap you will fall into on the follow-up.

### Commands against events

This distinction sounds pedantic and is not.

| | Command | Event |
|---|---|---|
| Grammar | Imperative: `ReserveInventory` | Past tense: `InventoryReserved` |
| Audience | Directed at exactly one handler | Broadcast to whoever cares |
| Can it be refused? | Yes — the handler may reject it | No — it already happened |
| Who knows about whom | The sender knows the receiver | The producer does not know the consumers |
| Coupling | Higher | Lower |

**Why the past tense matters:** naming an event `SendConfirmationEmail` rather than `OrderPlaced` smuggles the producer's expectation about consumers into the event itself. Now the producer implicitly knows there is an email service, and when a second consumer appears the name is wrong. Past-tense naming forces the producer to describe what happened in its own domain and stay out of the consumer's business. It is a small discipline that keeps the coupling low over years.

Both have their place. Commands over a queue are perfectly good for "do this specific work," and that is what most task queues actually carry. The mistake is calling a command an event, or building a fan-out bus for something that has exactly one legitimate handler.

### Choreography against orchestration

| | Choreography | Orchestration |
|---|---|---|
| Who controls the flow | Nobody. Each service reacts to events and emits its own. | A coordinator drives each step in order. |
| Coupling | Low — services only know about event types | The coordinator knows every participant |
| Visibility | Poor. The flow exists only implicitly, across logs and traces. Nobody can point at it. | Explicit. The flow is a state machine you can read and inspect. |
| Adding a step | Add a consumer, touch nothing else | Change the coordinator |
| Debugging | Hard. Answering "where did this get stuck?" needs distributed tracing and patience. | Easy. Look at the execution and see which step failed. |
| Failure handling | Each service handles its own; compensation is scattered | Compensation lives in one place |
| Best for | Notification-style fan-out, few steps, no ordering requirement | Multi-step business transactions with ordering, compensation, and an audit requirement |

**The answer that lands:** "Choreography for notification-style fan-out — an order was placed, and four services each do their own independent thing. Orchestration as soon as the workflow has ordering, compensation, or a need to be debugged. We used an orchestrator for the ETL saga because somebody eventually has to answer 'where did run 412 stop, and what got rolled back?' and with pure choreography the only honest answer is 'let me read the logs for an hour.'"

The rule underneath: **choreography scales the organisation, orchestration scales the operator.** Choreography lets teams add behaviour without coordinating; orchestration lets an on-call engineer understand what happened at 3 in the morning. Pick based on which one you need more of, and note that most systems need both in different places.

### Delivery semantics

| Semantic | What it means | Reality |
|---|---|---|
| **At-most-once** | Every message is delivered zero or one times. Never duplicated, may be lost. | Fire and forget. Only acceptable when losing a message genuinely does not matter — metrics samples, some telemetry. |
| **At-least-once** | Every message is delivered one or more times. Never lost, may be duplicated. | The practical default everywhere: SQS, EventBridge, Kafka consumers, RabbitMQ. Assume it always. |
| **Exactly-once** | Every message is delivered exactly once. | Not achievable end-to-end across independent systems. Kafka offers exactly-once semantics **within Kafka**, between its own topics; the moment your consumer writes to an external database or calls an external API, that guarantee no longer covers the thing you care about. |

**What you actually build is "effectively once":** at-least-once delivery plus idempotent consumers. Say it that way. A candidate who claims exactly-once gets probed until it falls apart; a candidate who says "at-least-once delivery with idempotent processing, which is effectively once" has already answered the follow-up.

The reason exactly-once is impossible is worth being able to explain: the consumer must both do the work and record that it did the work. Those are two operations. If they are not in the same transaction, a crash can land between them. If the work is in an external system, there is no shared transaction to put them in. That is the whole argument.

### Idempotency — the implementation menu

**1. Natural idempotency.** Write the operation so that repeating it changes nothing.

```sql
INSERT INTO shipments (id, status, updated_at)
VALUES (:id, :status, :ts)
ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;
```

This is the cheapest and most robust option. No extra table, no extra failure mode. Reach for it first, and design your schema so that it is available — which mostly means having a stable natural key from the source system rather than only a generated one.

**2. An idempotency key store.** A table keyed on the message identifier or a business idempotency key, holding a status.

The naive version has a bug. If you do the work and then record the key, a crash in between means the work is repeated. If you record the key and then do the work, a crash in between means the work never happens at all and the key says it did.

Two correct versions:

- **Write the key in the same database transaction as the effect.** If the effect is a database write, this is easy and completely solves it. This is the preferred answer whenever it is possible.
- **Two-phase with a status.** Write the key with status `IN_PROGRESS` conditionally (fail if it exists and is recent). Do the work. Update to `COMPLETE`. If a later attempt finds `IN_PROGRESS` that is older than a timeout, it may retry, so the effect still needs to be either idempotent or safe to repeat. This is what you use when the effect is external.

**3. Version guard.** Store a `version` or `event_time` on the row and apply an incoming change only if it is newer.

```sql
UPDATE inventory SET qty = :qty, version = :version
WHERE sku = :sku AND version < :version;
```

This handles duplicates **and** out-of-order delivery at the same time, which no other option on this list does. When you can get a monotonic version from the source, this is often the strongest choice.

**4. Idempotent external side effects.** For calls to third parties, pass the provider's own idempotency key — Stripe, for example, accepts an `Idempotency-Key` header and returns the original response for a repeat. When the provider offers this, use it; it moves the problem to someone who has already solved it.

**The edge case to raise before they ask:** a crash between performing the effect and marking the key done. Your answer should be one of three things: put the marker in the same transaction as the effect; make the effect naturally idempotent so a repeat is harmless; or, if neither is possible and duplicates are worse than misses, mark before the effect and accept at-most-once — but say explicitly that this is a business trade-off you would confirm with the business rather than decide alone.

### Ordering

**The first thing to say: only guarantee ordering where the domain actually needs it.** Ordering is expensive — it limits parallelism, it constrains your partitioning, and it makes recovery harder. Most events do not need it. Find the specific entity for which order matters and scope the guarantee to that entity.

**Mechanisms:**

- **FIFO queues with a message group** — ordering within the group, parallelism across groups. The group key is the design decision.
- **Kafka or Kinesis partition key** — ordering within a partition. Same idea, same trade-off, same hot-key risk.
- **Version guards** — do not guarantee ordering at all; make out-of-order arrival harmless instead. Usually the cheapest and most robust option.

**Handling out-of-order events when you cannot prevent it:**

| Technique | How it works | When to use |
|---|---|---|
| **Last writer wins with a version** | Apply only if the incoming version is newer than the stored one, and drop older ones silently. | Almost always the right default. Simple, no state, no waiting. |
| **Reorder buffer** | Hold events briefly and release them in sequence order, with a timeout for gaps. | When downstream genuinely cannot handle out-of-order and the window is short. Adds latency and memory. |
| **Gap detection and fetch** | Track sequence numbers, and when you detect a missing one, fetch the current state from the source instead of waiting. | Good when the source can answer "what is the current state of X" cheaply. Self-healing. |

### Big payloads — the claim-check pattern

Message systems have a size limit: 256 KB for EventBridge and SQS, 1 MB by default for Kafka. Real business documents exceed this routinely.

The pattern: write the body to object storage, and put a **pointer plus enough metadata for routing** in the event.

```json
{
  "detail-type": "ShipmentDocumentReceived",
  "detail": {
    "shipmentId": "S-1",
    "source": "partnerA",
    "contentType": "application/xml",
    "sizeBytes": 4823910,
    "s3Key": "raw/dt=2026-08-06/source=partnerA/S-1.xml"
  }
}
```

**The important design detail:** put enough into the event that rules can filter on it and consumers can decide whether they care, without fetching the body. If a consumer has to download a five megabyte file to discover the event is not for it, you have not really used the pattern.

**The consequences to mention:** the object must be written **before** the event is published, or a fast consumer will get a 404. The object's lifecycle must be at least as long as the message's retention plus your replay window, or a replayed event points at a deleted object. And access control now has two places to get right — the message and the bucket.

### The transactional outbox — know this even if you did not use it

**The problem, called dual write.** Your handler writes to the database and then publishes to the broker. These are two systems, so there is no transaction spanning both. If the process crashes between them, you have changed the state without telling anyone, or you have told everyone about a state change that got rolled back. Both are corruption, and both happen in production more often than people expect.

Reversing the order does not help; it just changes which failure you get.

**The outbox pattern:**

1. In the **same database transaction** as the business write, insert a row into an `outbox` table containing the event you intend to publish.
2. A separate relay reads unsent outbox rows and publishes them to the broker, then marks them sent. The relay is either a poller (`SELECT ... WHERE sent_at IS NULL ... FOR UPDATE SKIP LOCKED`) or log-based change data capture on the outbox table.
3. Consumers deduplicate on the outbox row identifier, because the relay is at-least-once — it can crash after publishing and before marking sent.

Now the two things that must be atomic — the state change and the intent to publish — are in the same transaction, and everything after that is retryable.

**The inbox pattern** is the mirror image on the consumer side: record the processed message identifier in the same transaction as the effect. That gives you exactly the idempotency guarantee described earlier, done correctly.

**The costs, which you should name:** the outbox table needs cleaning up or it grows without limit. The relay adds a component and a small amount of latency. Ordering across the outbox needs care if you rely on it. And the polling version adds load to the database that log-based capture avoids.

### Schema evolution for events

Events are harder than APIs, for two reasons. First, you cannot see who your consumers are, because they subscribed without telling you. Second, events may sit in an archive for a year and be replayed against code you have since rewritten.

**The rules:**

- Add optional fields only. Never remove, rename, or retype.
- Never repurpose an existing field's meaning. Adding a field is cheap; a field whose meaning silently changed is a bug that takes months to find.
- Version explicitly when you must break: a new `detail-type` such as `OrderShipped.v2`, or a `schemaVersion` attribute inside the payload. Prefer the new type name, because it lets consumers subscribe to the version they understand rather than parsing to find out.

**Compatibility modes, which you should be able to define:**

| Mode | Meaning | You need it when |
|---|---|---|
| **Backward** | A new consumer can read old events. | Always, if you have an archive or any retention. Consumers get deployed against history. |
| **Forward** | An old consumer can read new events. | Always, in practice, because you cannot deploy every consumer at the same moment as the producer. |
| **Full** | Both. | Choose this for any long-retention bus. It restricts you to adding optional fields and removing optional fields, which is exactly the discipline you want. |

**The deprecation process:** emit both shapes during a transition window. Instrument consumption of the old shape per consumer. When it hits zero and the window has expired, stop. Do not schedule a coordinated cutover; it never happens on the date it was promised.

### Backpressure and flow control

A queue is a shock absorber, but an unbounded backlog is just unbounded latency wearing a disguise. A system where the queue always has three million messages in it is not working; it is failing slowly.

The controls, and what each one is for:

| Control | What it does | When to use |
|---|---|---|
| **Consumer auto-scaling on queue age** | Add consumers when the backlog is getting old. | The default. Scale on **age of the oldest message**, not depth, because depth without a drain rate tells you nothing. |
| **Concurrency limits on consumers** | Stop the consumer fleet from overwhelming the database behind it. | Any time the consumer writes to a store with a hard connection or throughput limit. This is deliberate: you are matching a fast component to a slow one. |
| **Batching** | Process many messages per unit of work. | Almost every asynchronous pipeline. It trades latency for a large throughput and cost gain. |
| **Load shedding by priority** | Drop or defer low-value work when saturated so high-value work still completes. | Systems with a genuine priority difference. Requires separate queues per priority — you cannot shed selectively from one queue. |
| **Age-based alerting** | Alert on how far behind you are, in time. | Always. This is the metric that maps to a user-visible promise: "changes appear within two minutes." |

**The thing to say:** "Depth alone is a bad signal. A queue with a million messages draining in thirty seconds is healthy; a queue with two hundred messages that has not moved in an hour is broken. Age of the oldest message is the true service-level indicator, because it is the one that maps to what we promised users."

### How event-driven architectures scale

**What scales beautifully:** adding consumers. Each new consumer gets its own queue, its own retry behaviour, its own scaling, and its own failure isolation. The producer is unaffected. This is the reason the architecture exists, and it scales organisationally as well as technically — new teams can build without asking anyone's permission.

**Where it stops scaling and what you do:**

| Problem at scale | Symptom | Fix |
|---|---|---|
| **Fan-out multiplication** | One event matching many rules becomes many deliveries and many invocations, and the bill grows faster than the traffic. | Filter at the bus rather than in consumers. Consolidate consumers that always run together. Model the multiplication before assuming it is cheap. |
| **Event storms** | One event triggers a consumer that emits an event that triggers another consumer, and the volume amplifies. In the worst case it forms a cycle. | Trace event lineage with a correlation identifier and a hop count. Reject events past a maximum hop count. Watch for cycles deliberately, because they are silent until they are not. |
| **Schema drift** | Consumers break at random because a producer changed something and nobody knew who was listening. | Schema registry with compatibility checks in continuous integration, plus consumer-driven contract tests. |
| **Nobody understands the flow** | An incident takes hours because the flow is implicit and nobody can draw it. | Distributed tracing with a correlation identifier propagated through every event. Orchestration for the flows that matter. A service catalogue that records who consumes what. |
| **A hot key** | One entity's ordering key concentrates traffic on one partition or group. | A composite key to spread the load, or narrow the ordering requirement so the natural key is finer-grained. |
| **Replay overload** | Replaying a day of events overwhelms the live consumers and takes down the production path. | Replay into a dedicated rule and a dedicated consumer fleet, with its own rate limit. Never replay into the live path at full speed. |

**The scaling insight worth stating:** event-driven systems scale throughput extremely well and scale **comprehension** extremely badly. The engineering investment that keeps them working at size is not more infrastructure — it is tracing, correlation identifiers, schema contracts, and documentation of who consumes what.

---

## 3. REST API design

### Resources and verbs

Paths name **things** (nouns); HTTP methods say what you are doing to them.

```
POST   /shipments          create
GET    /shipments/{id}     read one
GET    /shipments          read many, with filters
PATCH  /shipments/{id}     partial update
PUT    /shipments/{id}     full replace
DELETE /shipments/{id}     remove
```

The property that matters most here is **idempotency**, and it is a property of the method, not a nicety:

| Method | Idempotent? | Safe (no side effects)? | Consequence |
|---|---|---|---|
| `GET` | Yes | Yes | Can be cached, retried, and prefetched freely. |
| `PUT` | Yes | No | Sending it twice leaves the same state, so a client can retry after a timeout without checking. |
| `DELETE` | Yes | No | Deleting twice is fine. The second call should return 204 or 404, not an error. |
| `PATCH` | Not necessarily | No | `{"qty": 5}` is idempotent; `{"op": "increment", "by": 1}` is not. Know which one you built. |
| `POST` | No | No | This is why `POST` needs an explicit idempotency key — see below. |

This matters because **a client that times out does not know whether the request succeeded.** With an idempotent method it can simply retry. With `POST` it cannot, unless you give it a way.

### Status codes that carry meaning

| Code | Use it for | The distinction people get wrong |
|---|---|---|
| `200 OK` | Successful request with a body | — |
| `201 Created` | Resource created; include a `Location` header pointing at it | Do not return 200 for a creation. The `Location` header is the part that gets forgotten. |
| `202 Accepted` | You accepted the work but have not done it yet; include a `Location` for a status resource | The correct answer for any job that exceeds a gateway timeout. |
| `204 No Content` | Success with nothing to return | Use for `DELETE` and for updates where the client does not need the body. |
| `400 Bad Request` | The request is malformed — bad JSON, wrong types | **Malformed**, not merely wrong. |
| `422 Unprocessable Entity` | Well-formed but semantically invalid — a valid date that is in the past when it must be in the future | This is a business rule failure, and separating it from 400 tells the client whether to fix its serialisation or its input. |
| `401 Unauthorized` | Not authenticated — we do not know who you are | Badly named in the specification; it means unauthenticated. |
| `403 Forbidden` | Authenticated but not allowed | We know who you are and the answer is still no. |
| `404 Not Found` | Does not exist, or you are not allowed to know it exists | Deliberately returning 404 instead of 403 hides the existence of resources from people who should not know about them. |
| `409 Conflict` | The request conflicts with current state — duplicate creation, or a state machine violation | — |
| `412 Precondition Failed` | An `If-Match` condition did not hold | This is optimistic concurrency control, and it is how you prevent lost updates. |
| `429 Too Many Requests` | Rate limited; include `Retry-After` | Without `Retry-After` the client guesses, and it guesses badly. |
| `503 Service Unavailable` | Temporarily down or overloaded; include `Retry-After` | Signals "retry later" clearly, unlike a 500 which signals "something is broken." |

### Idempotency for POST

`POST` is not idempotent, so a client that retries after a timeout can create two orders. The standard solution, popularised by Stripe:

1. The client generates a unique key and sends it in an `Idempotency-Key` header.
2. The server, in one transaction, records the key and performs the work.
3. If the same key arrives again, the server returns the **stored original response** without redoing anything.
4. Keys expire after a window, typically 24 hours.

Two details worth mentioning because they show you have implemented it rather than read about it: store the response body and status code, not just a flag, so the retry gets an identical answer. And handle the concurrent case — two requests with the same key arriving at once — with a unique constraint, so the second one either waits or returns a 409 rather than both proceeding.

### Pagination

**Offset and limit** is the obvious approach and it is wrong at scale, for two separate reasons:

```sql
SELECT * FROM shipments ORDER BY created_at DESC LIMIT 50 OFFSET 100000;
```

- **It gets slower the deeper you go.** The database must produce and discard 100,000 rows before returning the 50 you want. Page 1 is fast, page 2000 times out.
- **It is incorrect under concurrent writes.** If a row is inserted while the client is paging, every subsequent page shifts by one, so the client sees a duplicate row. If a row is deleted, the client silently skips one. This is a real data-loss bug in exports and it is very hard to notice.

**Keyset (cursor) pagination** fixes both:

```sql
SELECT * FROM shipments
WHERE (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 50;
```

- Constant time for every page, given an index on `(created_at, id)`, because the database seeks straight to the position rather than counting.
- Stable under concurrent writes, because the cursor is anchored to a row rather than to a count.
- The tie-breaker on `id` is essential. Without it, rows sharing a `created_at` value are ordered arbitrarily and can be skipped or repeated at a page boundary.

Encode the cursor as an opaque token rather than exposing the raw values, so you can change the underlying ordering later without breaking clients.

This is the concrete answer to "how do you optimise queries for high-volume reads", and it is much better than talking about indexes in the abstract.

### Filtering and search

Ad-hoc query parameters (`?status=ACTIVE&createdAfter=...&nameContains=...`) work until there are thirty of them, and then nothing can validate, document, or optimise them.

A **structured predicate grammar** is better:

```
?filter[status]=ACTIVE&filter[updatedAt][gte]=2026-01-01&filter[region][in]=EU,US
```

Why this is better, and this is the argument to make:

- It is **parseable** into a small internal query object rather than a pile of if-statements.
- It is **validatable** — you can declare which fields are filterable and which operators each supports, and reject everything else. This is also a security control, because it stops arbitrary column access.
- It is **pushdown-able** to the storage layer. Because the filter is structured, you can translate it into SQL, into an OpenSearch query, or into a DynamoDB filter, rather than fetching rows and filtering in memory.
- It is **extensible** without breaking anything, since adding a new field or operator is additive.

This connects directly to predicate-based search work: the value is not the syntax, it is that a structured grammar can be validated and pushed down to the store, and an ad-hoc one cannot.

### Versioning

| Approach | Looks like | Good | Bad |
|---|---|---|---|
| **URI versioning** | `/v1/shipments` | Obvious, easy to route, easy to cache, easy to debug from a log line | Purists object that the resource has not changed, only its representation |
| **Header or media-type versioning** | `Accept: application/vnd.company.v2+json` | Conceptually cleaner; the URL identifies the resource, the header identifies the representation | Harder to test from a browser or curl, easy for a proxy to strip, and you must set `Vary` correctly or caches will serve the wrong version |

Choose URI versioning unless you have a strong reason not to; operability beats purity here. Whichever you choose, **version the contract as a whole, not each endpoint independently** — a client that has to track `/v1/orders` alongside `/v3/customers` alongside `/v2/products` has a worse experience than one version bump for everything.

### Caching and concurrency control

- **`ETag` and `If-None-Match`.** The server returns an entity tag with the response. The client sends it back on the next request, and the server responds `304 Not Modified` with no body when nothing changed. This saves bandwidth and serialisation, and it works through intermediate caches.
- **`If-Match` and `412`.** The client sends the tag it last saw on an update. If the resource has changed since, the server returns `412 Precondition Failed`. This is **optimistic concurrency control** over HTTP, and it prevents the lost-update problem where two clients read, both modify, and the second silently overwrites the first.
- **`Cache-Control`.** `max-age` for how long a response is fresh, `stale-while-revalidate` to serve a slightly stale response while fetching a new one in the background, `private` for anything user-specific so a shared cache never stores it. Getting `private` wrong is a data-leak bug, not a performance bug.

### Errors

Use RFC 7807 `application/problem+json`, so every error in the system has the same shape:

```json
{
  "type": "https://api.example.com/errors/insufficient-inventory",
  "title": "Insufficient inventory",
  "status": 409,
  "detail": "SKU-123 has 2 units available, 5 requested",
  "instance": "/orders/O-9",
  "code": "INVENTORY_INSUFFICIENT",
  "traceId": "1-5f8a-b2c3d4e5"
}
```

The two fields to insist on beyond the standard:

- **A stable machine-readable `code`.** Clients must branch on something, and if you do not give them a code they will parse your English message, and then you can never reword it.
- **A `traceId`.** When a user reports a problem, this is the single piece of information that turns a support ticket into a two-minute investigation. Include it on every error, and consider including it on success too.

### The asynchronous job pattern

Whenever the work can exceed the gateway timeout (29 seconds on API Gateway), do not try to make it fit:

```
POST /imports              → 202 Accepted, Location: /imports/{id}
GET  /imports/{id}         → { status: "RUNNING", processed: 4200, total: 100000 }
GET  /imports/{id}         → { status: "COMPLETED", resultUrl: "https://..." }
```

The client polls, or you send a webhook, or you push over WebSocket. Return progress in the status response, because "RUNNING" with no numbers is indistinguishable from "stuck" and you will be asked about it. This is the same shape as the API Gateway to SQS pattern from the AWS notes: accept fast, process at your own pace.

### Bulk endpoints

When a client sends 5,000 rows, one bad row must not fail the other 4,999:

```json
{
  "results": [
    { "index": 0, "status": 201, "id": "S-1" },
    { "index": 1, "status": 422, "code": "INVALID_SKU", "detail": "SKU-999 not found" },
    { "index": 2, "status": 201, "id": "S-3" }
  ]
}
```

Return `207 Multi-Status`, or `200` with per-item results. This is exactly the same idea as partial batch failure in SQS, and pointing out that the two are the same pattern at different layers is a good thing to say — both exist because all-or-nothing processing of a large batch multiplies the cost of a single bad record.

### Contract-first development

Write the OpenAPI specification first and treat it as the source of truth. Generate server stubs and client libraries from it. Validate requests and responses against it in tests. Run a breaking-change linter in continuous integration that compares the new specification against the previous one and fails the build on an incompatible change.

The reason this matters more than it sounds: without it, the contract is whatever the code happens to do today, and it changes accidentally. With it, changing the contract is a deliberate, reviewed act. That is what "owning API contracts" actually means in practice.

### REST against gRPC against GraphQL

| | REST | gRPC | GraphQL |
|---|---|---|---|
| Format | JSON over HTTP | Protocol Buffers over HTTP/2 | JSON over HTTP, single endpoint |
| Contract | OpenAPI, optional | `.proto`, mandatory and enforced | Schema, mandatory and enforced |
| Performance | Fine | Best — binary, multiplexed, streaming | Fine, but resolver work can be heavy |
| Caching | Easy, standard HTTP caching | Hard, you build it | Hard, POST-based and query-specific |
| Streaming | Server-sent events or WebSocket | Built in, both directions | Subscriptions |
| Browser support | Native | Needs a proxy layer | Native |
| Best for | Public APIs, simple integration, anything that benefits from HTTP caching | Internal service-to-service, high throughput, low latency, strict contracts | Clients that need flexible aggregation across many resources, especially mobile with varying screens |

**The costs to name when discussing GraphQL**, because interviewers check whether you only know the marketing: query complexity control is mandatory, since a client can otherwise write a deeply nested query that costs you an enormous amount of work; caching is genuinely hard because every query is different; and the N+1 problem in resolvers is nearly guaranteed unless you use batching such as DataLoader.

### Security

- **Authentication**: OAuth 2.0 and OpenID Connect with JWTs for users, mutual TLS or signed requests for service-to-service. Validate the token's signature, issuer, audience, and expiry — all four. Checking only the signature is a common and serious bug.
- **Authorisation per object**, and this is the one that matters most. The top item in the OWASP API Security list is **broken object-level authorisation**: an endpoint `/orders/{id}` that checks the caller is logged in but never checks the order belongs to them. Every single object access needs an ownership or permission check, and the check belongs in a place that is impossible to forget rather than repeated in every handler.
- **Input validation at the edge**, against a schema, before the request reaches business logic.
- **Rate limits per client**, so one caller cannot consume everyone else's capacity.
- **Never put sensitive data in URLs**, because they end up in access logs, browser history, and referrer headers. Tokens and identifiers of sensitive resources go in headers or bodies.
- **Never log secrets or personal data**, and enforce that with a redaction layer in your logging setup rather than with a code-review convention.

### How REST APIs scale

**The layers, from cheapest to most expensive:**

1. **Client-side caching** with `ETag` and `Cache-Control`. A request that is never made is the cheapest request.
2. **Content delivery network or gateway caching** for public, non-personalised responses.
3. **Application-level caching** (Redis) for expensive computed results.
4. **Read replicas** for read-heavy workloads, with a plan for replication lag.
5. **More application instances**, which is the easy one and works only if the application is stateless.
6. **Sharding the database**, which is the expensive one you defer for as long as possible.

**The API-design decisions that determine whether you can scale at all:**

- **Keyset pagination**, because offset pagination stops working at depth and you cannot retrofit it without breaking clients.
- **Statelessness.** No session state in the process. Anything in memory belongs in Redis or the token. This is what makes horizontal scaling possible at all.
- **Asynchronous jobs for long work**, so slow requests do not occupy connections and threads.
- **Bulk endpoints**, because 5,000 individual requests cost far more than one request with 5,000 items, in network overhead, authentication, and per-request work.
- **Field selection** (`?fields=id,status`), so clients that need two fields do not force you to serialise forty.
- **Rate limits**, both to protect you and to make client behaviour predictable enough to plan capacity for.

---

## 4. Saga pattern

### The problem

A business transaction spans several services or several resources, each with its own store. You need all of it to happen, or none of it, but there is no shared transaction.

**Why two-phase commit is not the answer**, and you should be able to say this without being prompted:

- It **blocks**. Between prepare and commit, participants hold locks. If the coordinator dies at that moment, those locks are held until somebody intervenes manually. In a system with real traffic, that is an outage.
- It needs **XA support in every participant**. Your database might have it. S3 does not. A third-party HTTP API does not. A message broker mostly does not. So the pattern is unavailable for exactly the boundaries you have.
- It **couples availability**. The transaction succeeds only if every participant is up at the same moment. With five participants that is a much lower number than any of them individually.

### The definition

A saga is a sequence of **local transactions**. Each one commits in its own service, and each one triggers the next. If step *k* fails, the saga runs **compensating transactions** for steps *k−1* down to *1*, in reverse order, to undo what was already done.

The trade is explicit: you give up isolation and atomicity as guarantees provided by infrastructure, and you rebuild something like them in your domain logic, in exchange for availability and the ability to span heterogeneous systems.

### Compensation is semantic, not a rollback

You cannot un-commit a committed transaction. What you do instead is perform a business-meaningful inverse action:

| Forward action | Compensation |
|---|---|
| Reserve inventory | Release the reservation |
| Charge a card | Issue a refund or a credit note |
| Create a shipment record | Mark it `VOIDED` (usually not delete — the audit trail matters) |
| Upload a file to S3 | Delete it, or write a tombstone marker |
| Send an email | Nothing. You cannot un-send an email. |

That last row is the important one. **Some actions cannot be compensated**, and identifying them is the core design work of a saga.

**Three rules for compensations:**

1. **They must be idempotent**, because they will be retried. A refund that runs twice must not refund twice.
2. **They must be retriable until they succeed.** A compensation that fails permanently leaves the system in an inconsistent state with nobody responsible for fixing it. If it truly cannot succeed, it must raise an alert and park for a human, never fail silently.
3. **They must not depend on state that a later step might have changed.** Compensating by "set the quantity back to what it was" breaks if someone else has changed it in between. Compensate with a delta (`add back the 5 we took`) rather than an absolute value wherever you can.

**Classify every step**, because this ordering decision is what makes a saga workable:

- **Compensatable steps** — can be undone. Put these first.
- **The pivot step** — the point of no return. After this, going backwards is impossible or unacceptable, so the saga can only go forwards. Charging a customer is often the pivot.
- **Retriable steps** — after the pivot, everything must eventually succeed. These must be designed so that they cannot fail permanently: no validation that could reject, no dependency that might be gone.

**Order your saga so the pivot is as late as possible.** Every step before the pivot is one you can still back out of cleanly. This single piece of ordering advice is the most practical thing in the whole pattern.

### The isolation problem — sagas are ACD, not ACID

The letter you lose is **I** for isolation. Because each step commits independently, the intermediate state is visible to everyone else while the saga is still running. That produces real anomalies:

- **Dirty reads** — another transaction sees the half-finished state and acts on it.
- **Lost updates** — another transaction modifies the same row between two saga steps, and the compensation overwrites their change.

The countermeasures (this list comes from Chris Richardson, and quoting the names lands well):

| Countermeasure | What it does | Example |
|---|---|---|
| **Semantic lock** | Mark records with a `PENDING` or `IN_PROGRESS` status so other operations skip them or wait. This is an application-level lock replacing the database lock you gave up. | An order in `PENDING_PAYMENT` cannot be modified by another process. |
| **Commutative updates** | Design updates so that ordering does not matter, using deltas instead of absolute values. | `quantity -= 5` and `quantity += 5` commute; `quantity = 10` and `quantity = 15` do not. |
| **Pessimistic view** | Reorder the steps so the risky, visible intermediate state exists for as short a time as possible, or does not exist at all. | Debit before credit, so an intermediate state shows less money rather than more — a state that cannot be exploited. |
| **Reread value** | Re-read the record and confirm it is unchanged before writing. Effectively optimistic concurrency inside the saga. | Check the version before applying the compensation; if it changed, escalate instead of overwriting. |
| **Version file** | Record the operations that arrived and reorder or replay them, so out-of-order arrival is handled rather than prevented. | Log operations against an entity and apply them in the intended order. |
| **By value** | Route requests down different paths based on risk: low-risk ones through the saga, high-risk ones through a stricter path. | Small refunds are automatic; refunds over a threshold go through a two-phase or manual process. |

### Orchestrated against choreographed sagas

**Orchestrated** — a coordinator holds the saga state, issues commands, receives replies, and drives compensation.

- The coordinator persists its state, so it can be recovered after a crash by reading where it got to and resuming.
- The whole flow lives in one place, so it can be read, tested, and drawn on a whiteboard.
- The cost is a component that knows about every participant, which is a form of coupling. That is usually a price worth paying for a business transaction.

**Choreographed** — each service reacts to the previous service's event and emits its own. No central state.

- Cheaper for two or three steps, and no extra component.
- Beyond that it becomes opaque. Nobody can answer where a run stopped. Compensation logic is scattered across services, so nobody owns the "undo" as a whole.

**The recommendation:** choreograph two or three steps with no compensation; orchestrate anything with compensation, ordering, or an audit requirement.

### Implementation checklist for an ETL saga

This is the concrete version, and being able to describe the tables is what makes the answer credible.

**1. The run record.**

```sql
etl_runs (
  id, status, source_file, started_at, finished_at,
  rows_in, rows_ok, rows_failed, rows_compensated
)
```

Status moves through `RUNNING`, `COMPLETED`, `FAILED`, `COMPENSATING`, `ROLLED_BACK`, `NEEDS_MANUAL_INTERVENTION`.

**2. The step log. This is the saga log**, and it is what makes runs resumable and auditable.

```sql
etl_run_steps (
  run_id, step_name, status, attempt, started_at, finished_at,
  error, compensated_at
)
```

On restart after a crash, you read this table, see which step was in flight, and resume from there rather than from the beginning.

**3. Idempotent steps**, keyed on `(run_id, step_name, row_key)`. Every write is an upsert. This is what makes resumption safe — re-running a step that partially completed does no harm.

**4. Bounded parallelism with per-key locking.** Processing 50,000 rows one at a time is too slow; processing them all at once destroys the database. Use a worker pool with a fixed concurrency limit.

For the case where two workers might process the same key at the same time, three options:

- `SELECT ... FOR UPDATE SKIP LOCKED` — each worker claims a distinct set of rows, and workers never block each other. The best option for a work-queue table.
- **Advisory locks** — a lightweight named lock keyed on the identifier, when there is no row to lock yet.
- **A unique constraint plus conflict handling** — let both proceed and let the database reject the second. Simplest, and often enough.

**5. Row-level retries with partial success.** A single bad row should not abort a 50,000-row run. Retry individual rows with backoff, collect failures rather than aborting, and write the rejects to S3 with reason codes so someone can fix and resubmit them.

**6. Compensation.** When a later step fails after earlier rows were already committed downstream, reverse those writes, delete or tombstone uploaded objects, and mark the run `ROLLED_BACK`. Note that partial success and compensation are different policies — decide deliberately whether a run is all-or-nothing or best-effort, because that is a business decision rather than a technical one.

**7. Observability.** Counters for `rows_in`, `rows_ok`, `rows_failed`, `rows_compensated`; duration per step; and an alert on any run stuck in `RUNNING` past its expected duration. That last alarm is the one that catches the silent failures, which are the ones that hurt.

### How sagas scale

**What scales well:** the steps themselves. Each step is a normal service doing normal work, and each can scale on its own.

**What does not scale, and what you do about it:**

| Problem | Why it happens | Fix |
|---|---|---|
| **Saga state store becomes hot** | Every step reads and writes the saga instance record, so a high-volume saga hammers one table. | Partition by saga identifier. Or use Step Functions, where AWS owns that store. Or keep the saga state in the aggregate itself rather than in a separate table. |
| **Long-running sagas hold semantic locks** | A saga that takes minutes leaves records marked `PENDING` for minutes, blocking other work. | Shorten the saga. Move slow steps after the pivot. Set lock timeouts with automatic release, and monitor for locks that outlive their timeout. |
| **Compensation storms** | A downstream outage fails thousands of in-flight sagas at once, and every one starts compensating simultaneously against the same already-struggling dependency. | Rate-limit compensation. Use a circuit breaker so sagas fail fast and queue for later rather than all compensating at once. Consider pausing new sagas while compensating existing ones. |
| **Step count grows** | Sagas with twenty steps have twenty compensations and twenty times the failure surface. | This is usually a boundary problem, not a saga problem. A saga that touches eight services is telling you the services were split wrongly. |

**The scaling question interviewers actually ask:** "what happens when 10,000 sagas are running at once?" The answer covers three things: the saga state store is the shared bottleneck and needs partitioning; semantic locks mean contention, so lock scope and duration matter more than throughput; and compensation must be rate-limited, because the failure mode is a thundering herd of undo operations aimed at the dependency that just failed.

---

## 5. Change Data Capture

### What it is

Change Data Capture means capturing row-level changes from a database — inserts, updates, deletes — and delivering them as a stream of change events, instead of consumers repeatedly asking "what has changed since I last looked?"

The shift is from **pull on a schedule** to **push on change**. That single shift removes constant load for data that mostly has not changed, and it removes the staleness window that a polling interval necessarily creates.

### The three implementations

| Approach | How it works | Advantages | Disadvantages |
|---|---|---|---|
| **Query-based (polling)** | Run `WHERE updated_at > :last_seen` on a schedule. | Trivial to build. No extra infrastructure. Works on any database. | **Misses deletes entirely** — a deleted row simply stops appearing, and nothing tells you. **Misses intermediate states** — if a row changes three times between polls you see only the final value. Constant load on the primary, most of it returning nothing. Latency equals the poll interval. Depends on `updated_at` being set correctly everywhere, which one forgotten code path breaks silently. |
| **Trigger-based** | Database triggers write every change to an audit or change table, which a consumer reads. | Captures deletes. Runs inside the original transaction, so it is exactly consistent with the change. Works on any database with triggers. | Write amplification — every write becomes two. It adds latency to the original transaction, on the critical path. Triggers are code living in the database, easy to forget and hard to review. The change table needs its own cleanup. |
| **Log-based** | Read the database's own replication log: the write-ahead log in PostgreSQL, the binary log in MySQL, the oplog in MongoDB. | Lowest possible overhead on the database, because the log is being written anyway. Complete and correctly ordered. Captures deletes and before-images. No application changes at all. | Operationally the heaviest: replication slots, connectors, and a new failure mode to monitor. And it exposes your **internal schema** as the event contract, which is the serious problem discussed below. |

### Log-based specifics, by database

**PostgreSQL** uses logical replication:

- `wal_level = logical` must be set, and it requires a restart.
- A **publication** declares which tables are included.
- A **replication slot** tracks how far the consumer has read, and guarantees the server retains the log until then.
- An **output plugin** (`pgoutput` built in, or `wal2json`) decodes the log into a readable format.
- `REPLICA IDENTITY FULL` on a table makes the log include the full previous row, not just the primary key, which you need if consumers want before-images. It costs more log volume.

**The danger you must mention, because it is the classic PostgreSQL CDC incident:** an inactive replication slot pins the write-ahead log. The server cannot delete log segments the slot has not confirmed, so if your connector goes down over a weekend, the log grows until it **fills the disk and takes down the primary database**. Monitor replication lag on `pg_replication_slots` and alarm on it. Drop slots that are genuinely abandoned. This single failure mode has caused a lot of production outages and knowing about it signals real experience.

**MySQL** uses row-based binary logging with global transaction identifiers, which make position tracking survive a failover.

**MongoDB** uses change streams over the oplog, with resume tokens so a consumer can restart from where it stopped.

**DynamoDB** has Streams, with a 24-hour retention window, consumed by Lambda or EventBridge Pipes.

**The AWS-native paths**: DMS does a full load followed by ongoing change capture into Kinesis, MSK, or S3. Debezium on MSK Connect is the open-source route and is more capable but more to operate.

### Snapshot plus stream

Every CDC rollout has the same bootstrapping problem: the log only contains changes from now onwards, but the consumer needs the existing data too.

The standard sequence: take a **consistent snapshot** of the current table, note the log position at which the snapshot was taken, then start streaming from exactly that position. Done correctly, no change is lost and no change is applied twice — and where a change does overlap, the consumer's idempotency handles it.

The problem is that a naive snapshot locks the table for the duration, which is unacceptable on a large busy table. **Incremental snapshotting** (Debezium's watermark-based approach) solves this by snapshotting in chunks while streaming continues, using watermark events in the log to work out, for each chunk, whether the streamed version or the snapshot version is newer. Being able to describe why incremental snapshotting exists is a genuinely strong signal.

### The semantics you must speak to

- **At-least-once delivery.** Consumers must be idempotent, keyed on primary key plus log sequence number or version. The version guard is the natural fit here, since the log sequence number is monotonic by construction.
- **Ordering is per table and per primary key.** Partition by primary key to preserve it. Ordering across tables is not guaranteed, which matters if a consumer needs a parent row to exist before its children — handle it by tolerating the gap and retrying, not by assuming order.
- **Deletes arrive as delete events**, and in a compacted topic also as tombstones (a message with a key and a null value) that tell the compaction process to remove the key entirely.
- **Schema changes flow through too.** A new column appears in the events. A dropped column disappears. Consumers must be tolerant readers, and you need a policy for column drops agreed in advance, because that is the change that breaks consumers.

### CDC against outbox — a favourite senior question

This is the question that separates people who have used CDC from people who have read about it.

**Raw CDC on your business tables leaks your internal schema as a public contract.** The moment another team consumes `customers` change events, you cannot rename a column, split a table, or change a representation without breaking them. You have accidentally made your database schema a published API, and you did it without a review, a version, or a deprecation policy. Every future refactor now needs a cross-team migration.

**The outbox pattern publishes deliberate domain events**, written transactionally by the owning service. The event is a designed contract. The internal schema stays private and free to change.

**The best of both: run CDC on the outbox table.** You get the atomicity of the transactional write, no polling load, low latency from the log, and a curated contract that you designed on purpose. This is the answer to give, and the reasoning matters more than the conclusion.

The one case where raw CDC is right: replicating into a data warehouse or analytics store owned by the same team, where the consumer and the schema are governed together and there is no cross-team contract being created by accident.

### How to describe your latency improvement honestly

"Services were polling each other on a fixed interval to detect changes across more than 500 locations. That meant constant load for data that mostly had not changed, and a worst-case staleness equal to the poll interval. We replaced it with change events published on write and delivered through SQS, so consumers update when something changes rather than on a schedule. End-to-end synchronisation latency dropped by about 35 percent, measured as p95 from source commit time to consumer applied time, and read load on the source database fell because the periodic scans went away."

**Then be precise about the flavour.** If the application emitted the change events itself, call it "outbox-style, application-level change capture", not log-based CDC. Interviewers respect that precision far more than the buzzword, and claiming log-based when it was not falls apart immediately on a question about replication slots.

### How CDC scales

**Where it stops scaling:**

| Problem | Symptom | Fix |
|---|---|---|
| **Replication slot lag** | The write-ahead log grows because the consumer cannot keep up, and eventually fills the disk. | Alarm on slot lag long before it is critical. Scale consumers. Consider a separate slot per consumer group so one slow consumer cannot pin the log for everyone. |
| **A single stream for a whole database** | Every table's changes flow through one ordered pipe, and one high-volume table starves the rest. | Route per table to separate topics or streams. Partition by primary key within each. |
| **A hot key** | One entity changes far more often than the rest and saturates its partition. | Same fix as anywhere else: a composite key, or narrowing the ordering requirement. |
| **A bulk update** | Somebody runs `UPDATE orders SET status = 'X'` across ten million rows and the pipeline receives ten million change events at once. | This is the CDC failure mode people do not anticipate. Rate-limit the consumer, buffer in a queue, and have a documented procedure for large administrative updates — ideally do them in batches with a pause. |
| **Consumer lag after a schema change** | A new column appears and consumers start failing on every message. | Tolerant readers, a schema registry with compatibility checks, and a rule that schema changes are announced. |

**The metric that matters:** end-to-end lag, measured from source commit time to consumer applied time. Not connector lag, not queue depth. The number that maps to the promise you made to users.

---

## 6. Reliability toolkit

### Retries done right

**Retry only errors that might succeed next time.** Timeouts, connection resets, 429, 5xx, and throughput-exceeded errors are retriable. A 400 or 422 validation failure is not — the request will be just as invalid on the fourth attempt, and retrying it wastes capacity and delays the error the caller needs to see. A 409 conflict is usually not retriable either, because the state genuinely conflicts.

**Exponential backoff with full jitter:**

```
sleep = random_between(0, min(cap, base * 2^attempt))
```

The jitter is the part people leave out, and it is the part that matters. Without it, a thousand clients that all failed at the same moment all wait exactly the same time and all retry at the same moment — so the dependency that was recovering gets hit by a synchronised wave and falls over again. **Full jitter**, where the wait is a random value between zero and the backoff bound, spreads the retries out and is what AWS recommends.

**Cap two separate things:** the number of attempts, and the total elapsed time. An operation that retries five times with a 30-second backoff has taken over two minutes, and the caller gave up long ago.

**Propagate a deadline.** Pass the remaining time budget down the call chain, and have each layer refuse work it cannot finish in time. Without this, a service happily starts a 10-second operation for a caller who is going to time out in 2 seconds — pure wasted capacity, at exactly the moment capacity is scarce.

**Retries require idempotency.** Say the two words in the same breath every time. A retry of a non-idempotent operation is a duplicate, not a recovery.

**Beware retry amplification**, as described in the microservices section: retries at three layers multiply to 27 times the load. Retry at one layer, and let a queue handle the rest.

### Timeouts

Every network call gets a connect timeout and a read timeout. There are no exceptions.

Derive them from your latency objective, not from a comfortable-looking round number. If a downstream call has a p99 of 200 milliseconds, a 1-second timeout is generous; a 30-second timeout means you will hold a thread for 30 seconds during an incident, and your whole service will fall over because of somebody else's problem.

**Default HTTP client timeouts are frequently infinite**, and this is one of the most common causes of production incidents. Java's `HttpURLConnection`, many HTTP libraries, and plenty of database drivers default to waiting forever. Audit them. Set them explicitly. Write the value down in a comment with the reasoning, so nobody removes it later thinking it is arbitrary.

**The chain rule:** a caller's timeout must be shorter than its caller's timeout. Otherwise the outer caller gives up while the inner work continues, consuming resources for a response nobody will read.

### Circuit breaker

```
CLOSED  ── failure rate or slow-call rate crosses threshold ──▶  OPEN
   ▲                                                              │
   │                                                       cooldown expires
   │                                                              ▼
   └──── probes succeed ────  HALF-OPEN  ──── probes fail ────▶  OPEN
```

- **Closed** — normal operation, calls pass through, failures are counted.
- **Open** — calls fail immediately without touching the dependency. This protects you (no threads held waiting) and it protects the dependency (it gets a chance to recover instead of being hammered while it restarts).
- **Half-open** — after a cooldown, allow a small number of probe calls. If they succeed, close. If not, open again and wait longer.

**The settings that matter:** the failure rate threshold (a percentage, not a raw count, so it works at any traffic level), the sliding window over which it is measured, the **slow-call rate** threshold (a dependency that answers in 30 seconds is as damaging as one that errors, and only counting errors misses it), the cooldown duration, and the number of permitted probes in half-open.

**Always pair it with a fallback**, otherwise an open circuit just turns a slow failure into a fast one. A cached value, a default, or a partial response is what turns the circuit breaker into graceful degradation. And pair it with bulkheads, so one dependency's failures cannot exhaust the thread pool that every other dependency shares.

### Failure isolation checklist for a pipeline

Go through this list for any pipeline you design, and having it memorised makes you sound like someone who has run one:

| Failure | Control |
|---|---|
| **Poison message** | Dead letter queue, an alarm on its depth, and a redrive procedure. |
| **Partial batch failure** | Report per-item failures so nine good records are not reprocessed because of one bad one. |
| **Downstream outage** | The queue absorbs it. Alert on **age**, not depth. |
| **Data corruption** | Validate at the edge, quarantine rejects with reason codes, never crash-loop on bad data. |
| **Duplicate** | Idempotency key. |
| **Out-of-order** | Version guard. |
| **Silent stall** | A freshness metric — time since the last successfully processed record — because zero errors and zero throughput looks perfectly healthy on an error dashboard. This is the one everybody forgets and it is the one that costs a whole day of data. |
| **Slow poison** | A latency alarm per step, so a step that has degraded from 100 milliseconds to 8 seconds is caught before the backlog becomes unrecoverable. |
| **Runaway cost** | A billing alarm and a cap on consumer concurrency, so an infinite retry loop is expensive for an hour rather than for a weekend. |

### Data contracts — making a resume phrase concrete

"Data contracts" sounds vague unless you can list what is actually in one. A data contract specifies:

| Element | What it pins down |
|---|---|
| **Schema** | The structure and types — JSON Schema, Avro, Protobuf, or XSD. |
| **Semantics** | What the fields mean. Units (grams or kilograms), timezone (UTC or local), nullability and what null means (unknown, or not applicable), enumerated values and what happens when a new one appears. This section prevents more bugs than the schema does. |
| **Ownership** | The producing team, and who is on call for it. A contract with no owner is a wish. |
| **Compatibility policy** | Backward, forward, or full, and what the process is for a breaking change. |
| **Delivery guarantees** | At-least-once or at-most-once, the ordering key if any, and the retention period. |
| **Freshness service level** | The p95 lag the producer commits to, so consumers can build on it. |
| **Volume expectations** | Typical and peak rate, so consumers can size for it and detect when reality diverges. |
| **Classification and retention** | Which fields are personal data, how long it may be kept, and how deletion requests propagate. |

**Where it is enforced**, because a contract nobody checks is documentation:

1. **Producer-side validation in continuous integration** — the build fails if the emitted shape does not match the declared schema.
2. **Edge validation on ingest** — API Gateway request models, or XSD validation on incoming files, so bad data never enters the system.
3. **Consumer-driven contract tests** — the consumer declares what it depends on and the producer's build fails if it would break it.
4. **Runtime monitoring** — the count of records that failed validation, per source, so contract violations become a visible metric rather than a support ticket.

### Consistency vocabulary

Use these terms precisely, because vague use of them is a common tell.

- **CAP** — during a **network partition**, you must choose between consistency and availability. The point everyone misses is the "during a partition" part. CAP says nothing about normal operation, and quoting it as a general trade-off is wrong.
- **PACELC** — the more useful extension. **If** there is a **P**artition, choose **A**vailability or **C**onsistency; **E**lse (in normal operation), choose **L**atency or **C**onsistency. This is the one to use, because most systems spend almost all of their time in the "else" branch.
- **Strong consistency** — every read sees the latest write.
- **Eventual consistency** — reads converge to the latest write, eventually, with no bound stated.
- **Causal consistency** — operations that are causally related are seen in order by everyone; unrelated ones may be seen in any order.
- **Read-your-writes** — a client always sees its own writes, even if others do not yet. Usually the property users actually notice, and often the only one you need to guarantee.
- **Monotonic reads** — a client never sees the clock go backwards; having seen a value, it never sees an older one.

**How to use it in an interview:** "We are PACELC EL for the catalogue reads — we accept replica lag in exchange for latency, because a product description being two hundred milliseconds stale harms nobody. But the inventory decrement reads go to the writer, because there we need read-your-writes and the cost of being wrong is overselling."

That sentence demonstrates the vocabulary and, more importantly, demonstrates that you apply it per use case rather than per system.

---

## 7. How the patterns combine

Knowing patterns individually gets you through the first half of the interview. Explaining how they fit together is what gets the offer.

### Microservices plus event-driven architecture — why they arrive together

Microservices create a problem: you have split the data, so you cannot join, and you cannot use a transaction. Events are the answer to both. Each service publishes what happened; other services build the local views they need and react to what concerns them.

**This is why the two patterns are almost always found together.** Microservices without events tend to degenerate into synchronous call chains, which reintroduce the coupling the split was meant to remove — with added network latency. When someone describes microservices communicating only over REST, the follow-up question to expect is about availability multiplication down the chain.

### Outbox plus CDC plus event-driven architecture — the reliable publish path

The three fit into one story:

1. The service writes its business change and an outbox row **in one transaction**. Atomicity solved.
2. Log-based CDC reads the outbox table and publishes to the bus. Low latency, no polling load, and a curated contract rather than a leaked schema.
3. Consumers deduplicate on the outbox row identifier. At-least-once handled.

This is the standard reliable publishing path, and being able to state it as three steps with the reason for each is a complete answer to "how do you make sure the event is published if the database write succeeds?"

### Saga plus orchestration plus idempotency — the reliable workflow path

A saga needs an orchestrator to be debuggable. An orchestrator retries steps. Retried steps must be idempotent. Compensations are also retried, so they must be idempotent too.

The chain of reasoning is: **distributed transaction → saga → compensation → retries → idempotency**. Each step forces the next. Walking an interviewer through that chain shows the patterns are connected in your head rather than memorised as a list.

### CQRS plus event-driven architecture plus API design

The read model is built from events, and the API serves it. The consequences you must handle at the API layer:

- **Read-your-writes.** After a write, the read model may not have caught up. Options: return the new state directly from the write response; read from the write model for a short window after a write; or include a version in the response and have the client wait for the read model to reach it.
- **Freshness visibility.** Expose how stale the read model is, either as a header or in the response, so clients can make their own decision rather than assuming it is current.
- **Rebuilds.** Because the read model is derived, you can throw it away and rebuild it from events. That is a genuine operational superpower, and it is only available if you kept the events — which is the argument for event retention and archiving.

### Claim check plus asynchronous jobs plus S3 lifecycle

A large document arrives. It goes to S3. The API returns 202 with a status URL. An event carrying the S3 key flows through the pipeline. Consumers fetch the body only if they need it. A lifecycle rule moves the object to cheaper storage after 30 days and expires it after seven years.

Every one of those decisions follows from one fact: **the payload is bigger than the message limit.** Being able to trace the whole design back to a single constraint is exactly the kind of reasoning interviewers are testing for.

### Resilience patterns as a stack

They compose in a specific order, and the order matters:

```
Request
  → Rate limit        (do not accept more than you can handle)
  → Bulkhead          (isolate this dependency's resources)
  → Circuit breaker   (fail fast if the dependency is known to be down)
  → Timeout           (bound this individual attempt)
  → Retry with jitter (recover from transient failure)
  → Fallback          (degrade gracefully if everything above failed)
```

**Why this order:** the rate limit is outermost because rejecting work you cannot do is cheaper than any other option. The bulkhead is next so that whatever happens inside cannot escape into other dependencies' resources. The circuit breaker is above the timeout so an open circuit costs nothing rather than costing a timeout. The retry wraps the timeout, so each attempt is individually bounded. The fallback is innermost-last, as the final answer when nothing worked.

Getting this order wrong produces subtle problems — for example, a retry outside a circuit breaker means the retries themselves trip the breaker, which is sometimes what you want and usually not.

---

## 8. Scaling, put together in one place

### What each pattern costs as the system grows

| Pattern | Scales well | Stops scaling when | What you do about it |
|---|---|---|---|
| **Microservices** | Independent deploy and scale per service | The service map exceeds what anyone can understand; cross-cutting changes touch everything | A platform and a service catalogue; events instead of synchronous chains; merge services whose boundary proved wrong |
| **Synchronous chains** | Small depth | Depth grows — latency compounds, availability multiplies downwards | Replace hops with events; cache at the boundary; flatten the chain |
| **Event-driven architecture** | Adding consumers is free for the producer | Fan-out multiplies cost; nobody understands the flow; event storms | Filter at the bus; tracing with correlation identifiers; schema registry; hop-count limits |
| **Event sourcing** | Audit and rebuild capability | Loading an entity means replaying a huge number of events | Snapshots, plus CQRS read models so queries never touch the event log |
| **CQRS** | Reads and writes scale independently | The read model falls behind and rebuilds take hours | Partition the read model; parallel rebuild; expose the lag so consumers can react |
| **Saga** | Steps scale individually | Saga state store becomes hot; semantic locks contend; compensation storms | Partition saga state; shorten lock duration; rate-limit compensation |
| **CDC** | Very low overhead on the source | Replication slot lag; one stream for a whole database; a bulk update floods the pipeline | Per-table streams; alarm on slot lag; a documented procedure for bulk updates |
| **REST APIs** | Horizontally, if stateless | Offset pagination at depth; long requests holding connections; chatty clients | Keyset pagination; asynchronous jobs; bulk endpoints; caching layers |
| **Queues** | Effectively unlimited buffering | The backlog never drains, so latency is unbounded | Scale on age not depth; concurrency limits; load shedding by priority |

### The scaling principles that cut across all of them

**1. Every synchronous hop you remove improves both latency and availability.**
Five services at 99.9 percent in a chain gives about 99.5 percent. Replace two of those hops with events and the remaining chain is both shorter and less coupled. This is the highest-leverage change available in most distributed systems, and it is usually cheaper than any infrastructure work.

**2. Buffer between components that scale at different speeds.**
A queue between a fast producer and a slow consumer converts an outage into a delay. This is the same principle as putting SQS between EventBridge and Lambda, as returning 202 from an API, and as the outbox between a transaction and a broker. Whenever you see a fast thing feeding a slow thing directly, that is where the next incident comes from.

**3. Push filtering and validation as early as possible.**
Rejected at the gateway costs nothing. Rejected in the consumer costs an invocation, a database connection, and a retry cycle. Every layer earlier you can move a rejection is capacity you did not have to buy.

**4. Make ordering requirements as narrow as possible.**
Ordering is the main thing that limits parallelism. Ordering across a whole stream means one consumer. Ordering per entity means as many consumers as there are entities. Every time you can narrow the ordering key, you have multiplied your available parallelism for free — and often you can remove the requirement entirely with a version guard.

**5. Idempotency is what makes everything else safe.**
Retries, at-least-once delivery, replay, saga compensation, CDC redelivery, and dead letter queue redrive all depend on it. It is not an optimisation; it is the property that makes the other patterns usable. If a system is not idempotent, none of the recovery mechanisms above can be used, and the only response to a failure is a manual investigation.

**6. Comprehension is the scarcest resource at scale.**
Technical throughput can usually be bought. Understanding cannot. The investments that keep large distributed systems working are correlation identifiers propagated everywhere, distributed tracing, a catalogue of who consumes what, explicit contracts, and orchestration for the flows that matter. Say this at the end of a system design discussion — it is the observation that distinguishes someone who has operated a large system from someone who has only designed one.

---

## 9. Rapid-fire questions and answers

**Q: When would you not use microservices?**
A small team, a single deploy cadence, unclear domain boundaries, or an early product where the boundaries will certainly move. A modular monolith with clean module interfaces and enforced dependency rules gets you most of the design benefit — separation of concerns, clear ownership, testability — with none of the distributed cost. And it can be split later along whichever seams proved stable, which is much easier than merging services that were split wrongly. The failure mode I would actively avoid is splitting before the domain is understood, because you end up with a distributed monolith, which is the worst of both.

**Q: Saga against two-phase commit?**
Two-phase commit gives real atomicity and isolation, but it blocks while holding locks if the coordinator dies, it requires every participant to support XA, and it simply does not exist for S3 or an HTTP API. Sagas give availability and work across heterogeneous systems, at the cost of isolation — which you patch with semantic locks, commutative updates, and version guards. In a cloud architecture the choice is usually made for you, because two-phase commit is not available across the boundaries you have.

**Q: A consumer must not process an event twice, but the side effect is an external email. How?**
Record the intent transactionally: an idempotency row with status `PENDING` written in the same transaction as the business change. Then send, using the provider's idempotency key if it has one, and mark `SENT`. The remaining gap is a crash between sending and marking, which can only cause a duplicate if the provider has no idempotency support. If a duplicate email is worse than a missing one, mark before sending and accept at-most-once. That is a business trade-off, not a technical one, and I would make it explicit with the business rather than pick silently.

**Q: Your event schema needs a breaking change. What is the rollout plan?**
Emit both shapes for a deprecation window — a new `detail-type` such as `OrderShipped.v2` alongside the existing one. If the change can be made additive instead, do that and rely on tolerant readers, because that avoids the whole exercise. Track per-consumer usage of the old shape as a metric. When it reaches zero and the window has expired, stop emitting it. I would not coordinate a big-bang cutover across teams; it gets scheduled, moved twice, and someone is always missed.

**Q: How do you test an event-driven system?**
Unit tests on handlers with fixture events, which is the easy part. Contract tests against the schema registry so a producer cannot break a consumer. Component tests with LocalStack or Testcontainers, running real SQS and real PostgreSQL, because in-memory fakes hide exactly the behaviour that matters — visibility timeouts, redelivery, transaction semantics. Replay of recorded production events into a staging bus, which finds the data shapes you never imagined. And deliberate chaos on the failure paths: force a message into the dead letter queue, force a duplicate, force out-of-order delivery. Those failure paths are the ones that never get tested and always fire at 3 in the morning.

**Q: Choreography or orchestration for a five-step order flow with payment?**
Orchestration. Payment means compensation, compensation means somebody has to own the undo, and five steps means somebody will eventually need to answer "where did this order stop and what was refunded?" With choreography that answer requires reading logs across five services. I would keep choreography for the genuinely independent fan-out around it — analytics, notifications, search indexing — because those need no ordering and no compensation, and letting them subscribe independently means new consumers cost the order service nothing.

**Q: A downstream service is timing out and your queue is backing up. Walk me through it.**
First, confirm the shape: is the age of the oldest message rising, and is the error rate on the downstream call rising with it? If yes, the circuit breaker should already be open and we should be failing fast rather than holding threads — if it is not, that is the first bug. The queue is doing its job by absorbing the backlog, so nothing is lost yet, and I would check the retention window against the current drain rate to see how much time we actually have. I would not scale consumers up, because that hammers a dependency that is already struggling and usually makes it worse. Once the dependency recovers, I would drain deliberately with a concurrency limit rather than letting the full consumer fleet hit it at once, because a recovering service being hit by a full backlog at maximum concurrency is how a recovery turns back into an outage.

**Q: How do you decide between at-least-once with idempotency and at-most-once?**
By asking which failure the business prefers, and this is genuinely their decision rather than mine. A duplicate order confirmation email is mildly annoying; a missing one is a support ticket, so at-least-once wins. A duplicate payment is a serious incident; a missing one is retryable by the customer, so at-most-once might win if the provider offers no idempotency key — though in practice payment providers do offer one, so you get to have both. The engineering answer is to make idempotency possible so the question does not arise; the honest answer when it cannot be made idempotent is to name the trade-off explicitly and let the business choose.
