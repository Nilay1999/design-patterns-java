# AWS — Serverless, Eventing, Compute, Storage

This file covers the AWS services on your resume: EventBridge, SQS, Lambda, Step Functions, Kinesis, API Gateway, ECS Fargate, S3, CloudWatch, and Aurora.

For every service you get four things:

1. **What it is** — in plain words, with a comparison to something you already know.
2. **Every setting explained** — what it does, what value to pick, what breaks if you pick badly.
3. **How it scales** — what grows on its own, what you tune by hand, what fails first.
4. **How it fits with the other services** — the combinations that show up in real systems and interviews.

At the end there are complete architectures and a rapid-fire question section.

---

## Words used everywhere in this file

Read these once. They come up in every section.

| Word | Plain meaning |
|---|---|
| **Idempotent** | Running it twice gives the same result as running it once. Adding a row with a fixed id is idempotent. Adding £10 to a balance is not. |
| **At-least-once delivery** | The system promises your message arrives. It does not promise it arrives only once. You may get copies. |
| **Backpressure** | A slow part of the system telling a fast part to slow down. A queue filling up is backpressure. |
| **Claim-check pattern** | The message is too big, so you store the real data in S3 and put only the S3 file path in the message. The reader fetches the file when it needs it. Like a coat check ticket. |
| **Concurrency** | How many things are running at the same instant. Not the same as requests per second. |
| **Dead letter queue (DLQ)** | A second queue where messages go after they have failed too many times. A parking spot for broken messages. |
| **Poison message** | A single bad message that keeps failing and blocks or wastes work. |
| **Fan-out** | One event goes to many consumers. |
| **Throttle** | The service refuses your request because you asked for too much, too fast. Usually HTTP 429. |
| **Cold start** | The first run of a function on a new container. Slow, because it has to boot. |

---

## Table of contents

1. [EventBridge](#1-eventbridge)
2. [SQS](#2-sqs)
3. [Lambda](#3-lambda)
4. [Step Functions](#4-step-functions)
5. [Kinesis Data Streams](#5-kinesis-data-streams)
6. [API Gateway](#6-api-gateway)
7. [ECS Fargate](#7-ecs-fargate)
8. [S3](#8-s3)
9. [CloudWatch](#9-cloudwatch)
10. [Aurora and RDS](#10-aurora-and-rds)
11. [DynamoDB](#11-dynamodb)
12. [How the services combine](#12-how-the-services-combine)
13. [Scaling, all in one place](#13-scaling-all-in-one-place)
14. [Rapid-fire questions and answers](#14-rapid-fire-questions-and-answers)

---

## 1. EventBridge

### What it is

EventBridge is a **message router**.

Think of a company noticeboard. You pin up a note that says "shipment created". You do not know or care who reads it. Anyone who cares has already told the noticeboard "send me a copy of anything that says shipment created."

Mechanically:

1. Your service calls the `PutEvents` API to publish an event.
2. The bus checks every **rule** attached to it. A rule holds a pattern.
3. If the event matches the pattern, EventBridge sends a copy to that rule's **targets** (a Lambda, a queue, another service).

The point is that **the publisher does not know the consumers**. A second team wants to react to your event? They add their own rule. You do not change your code, do not redeploy, and never find out. That is the real reason to use a bus instead of calling another service directly.

EventBridge **pushes** to you. You never poll it.

### The event shape, field by field

Every event on the bus looks like this:

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

| Field | Set by | What it is |
|---|---|---|
| `version` | AWS | Always `"0"`. Ignore it. |
| `id` | AWS | Unique id for this event. Handy as a duplicate-detection key. |
| `detail-type` | You | The event name, like `ShipmentCreated`. Used for routing. |
| `source` | You | Who published it, like `com.elevation.ingestion`. Also used for routing. |
| `account` | AWS | Which AWS account published it. |
| `time` | You or AWS | When it happened. AWS fills in "now" if you leave it out. |
| `region` | AWS | Which region it was published in. |
| `resources` | You | Optional list of related ARNs. Usually empty for your own events. |
| `detail` | You | Your actual data. Must be JSON. |

A few notes on the tricky ones:

- **`id` and replay.** If you use the archive feature to re-publish old events, each replay produces a new delivery. So `id` alone is not enough for duplicate detection when replay is in play. Pair it with a business key like `orderId`.
- **`time`.** Set it yourself if the event describes something that happened earlier. Otherwise your timeline and audit trail will be wrong.
- **`source` and `detail-type` are the two fields you will be stuck with for years.** They are how every future consumer finds your events. A good scheme: `source = com.<company>.<team-or-domain>` and `detail-type = <Thing><WhatHappened>`, like `OrderPlaced`, `ShipmentCancelled`.

**The payload must be JSON, and it must be under 256 KB.** So if your data is XML, or a five megabyte document, do not put it in `detail`. Write the file to S3 and put the S3 key in `detail` instead. The consumer fetches the file when it needs it. This is the **claim-check pattern**, and it is the standard answer to "your payload is too big."

### Rules and patterns

A rule has three parts:

1. An **event pattern** — what to match.
2. A list of **targets** — where to send it.
3. Optional **input transformation** — how to reshape it first.

A pattern mirrors the shape of the event. At each leaf you write a list of allowed values.

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

Four rules for reading that:

- A field you **list** must be present and must match.
- A field you **do not list** is ignored. So `{"source": ["x"]}` matches every single event from source `x`.
- Values inside one array mean **or**. `["A", "B"]` means A or B.
- Different fields mean **and**. Source must match *and* detail-type must match.
- If the event's own field is an array, it matches when **any** element matches.

The comparators you can use:

| Comparator | Example | What it does |
|---|---|---|
| Exact | `["ACTIVE"]` | Matches that exact string or number. |
| `prefix` | `[{"prefix": "SKU-"}]` | String starts with this. |
| `suffix` | `[{"suffix": ".xml"}]` | String ends with this. |
| `anything-but` | `[{"anything-but": ["DRAFT"]}]` | Anything except these. |
| `numeric` | `[{"numeric": [">", 0, "<=", 100]}]` | Number comparison. Two bounds allowed. |
| `exists` | `[{"exists": true}]` | Field is present (or absent, with `false`). |
| `cidr` | `[{"cidr": "10.0.0.0/8"}]` | IP is inside this network range. |
| `equals-ignore-case` | `[{"equals-ignore-case": "active"}]` | Match ignoring capitals. |
| `wildcard` | `[{"wildcard": "raw/*/partnerA/*"}]` | Star matching inside a string. |
| `$or` | `{"$or": [ {...}, {...} ]}` | Either of two whole sub-patterns. |

**Why filter here instead of in your code?** An event that does not match never invokes your target. You pay nothing, you use no concurrency, and you never write the "not for me, return early" branch. Filtering at the bus is cheaper *and* simpler than filtering in the consumer.

### Input transformation

Before delivering, a rule can reshape the event.

You define two things: an **input path map** (pull pieces of the event into named variables) and an **input template** (build a new string or object from those variables).

Use it to send a plain text line to a Slack webhook, or a small trimmed object to a Lambda that does not want the whole envelope. It saves you writing a tiny Lambda whose only job is reformatting.

### The three bus types

| Bus | What lands on it | When to use it |
|---|---|---|
| **Default bus** | Every AWS service event in your account, automatically: EC2 state changes, ECS task changes, CodePipeline stages, S3 events, CloudTrail activity. | Reacting to infrastructure events. Do **not** publish your own business events here — they get lost in the noise. |
| **Custom bus** | Only what you publish. | Your business events. One bus per domain area. Always separate buses per environment. A test event must never reach production. |
| **Partner bus** | A vendor pushes straight to you: Stripe, Datadog, Auth0, Shopify. | Receiving third-party webhooks without running your own webhook endpoint or handling their signatures and retries. |

### Delivery guarantees — say this precisely

- Delivery is **at-least-once**. You will get duplicates. Your consumers must be idempotent. No setting turns this off.
- Delivery is **asynchronous**, and there is **no ordering guarantee**. Publish A then B, and B can arrive first.
- If you need ordering, EventBridge alone is not enough. Two fixes: put a FIFO queue or Kinesis stream in the path, or carry a version number in the event and ignore anything older than what you already have.
- If a target fails, EventBridge **retries with backoff for up to 24 hours**.
- Each target can have its own **dead letter queue** (an SQS queue). Events that run out of retries, or can never be delivered at all (target deleted, permission removed), land there instead of vanishing.

**Configure a dead letter queue on every target.** An event that silently disappears is the worst possible failure in an event-driven system, because nothing tells you it happened.

### Archive and replay

An **archive** keeps a copy of every event matching a pattern you choose, for a retention period you set (N days, or forever).

A **replay** takes a time window out of that archive and publishes those events again, delivered only to the rules you pick.

This is your answer to "a bug corrupted a day of data — how do you reprocess it?" You fix the consumer, then replay yesterday 09:00 to 17:00 into a rule pointing only at the fixed consumer.

Two things to know:

- Replayed events carry a replay marker, so a consumer can tell it is seeing a replay.
- Replay does not remove the need for idempotency. Replay is exactly when a non-idempotent consumer double-charges someone.

### Schema Registry

The Schema Registry watches the traffic on a bus, works out the JSON shape of the events, versions those shapes, and can generate typed classes for Java, Python, and TypeScript.

Be precise about what it does **not** do: **it does not reject bad events.** EventBridge will happily deliver an event that breaks your schema. The registry is for discovery, versioning, and code generation only.

Real enforcement has to happen in your producer (validate before publishing) or your consumer (validate on arrival, send failures to a rejects queue).

### Bus, Pipes, and Scheduler are three different products

| Product | Shape | What it replaces |
|---|---|---|
| **Bus** | One source, many rules, many targets. Routing and fan-out. | One service calling five others directly. |
| **Pipes** | Point to point. One source → optional filter → optional enrichment → one target. Pipes does the polling for you (SQS, Kinesis, DynamoDB Streams, MQ, MSK). | The Lambda whose only job was "read from queue, reshape, write elsewhere." |
| **Scheduler** | Cron, rate, or one-time schedules. Handles millions of individual schedules, each with its own target and payload. | Old CloudWatch scheduled rules, and homemade "table of future work plus a polling Lambda." |

Scheduler is genuinely useful and under-used. If you need "send a reminder 24 hours before the booking", create **one schedule per booking** instead of scanning a table every minute.

### Limits worth knowing

| Limit | Value | What it means for you |
|---|---|---|
| Event size | 256 KB | Same as SQS. Bigger goes to S3, event carries the key. |
| `PutEvents` batch | 10 entries per call | Batch your publishes. Failures are reported per entry, so retry only the failed ones. |
| `PutEvents` throughput | Thousands per second, raisable | Publishing is rarely the bottleneck. |
| Targets per rule | 5 | Need more consumers? Use more rules with the same pattern, or fan out through SNS. |
| Rules per bus | A few hundred, raisable | Fine for business events. Not fine for "one rule per customer" — use Scheduler or a routing table. |

### How EventBridge scales

**What scales on its own:** the bus, the rule matching, the delivery. Nothing to provision. No shards, no partitions, no capacity units. Publish throughput is a soft limit you raise with a support ticket.

**What actually limits you: the target.** If a rule points straight at a Lambda and 20,000 events land in one second, EventBridge tries to invoke that Lambda 20,000 times. The Lambda hits its concurrency ceiling, throttles, and EventBridge starts retrying. The bus is fine. Your consumer is on fire.

**The fix: put an SQS queue between the rule and the Lambda.** The rule fills the queue as fast as events arrive. The Lambda drains it as fast as it can cope. The queue absorbs the spike.

This is the single most important scaling pattern in EventBridge architectures, and interviewers look for it by name.

Three more scaling notes:

- Filter at the rule, not in code. A pattern that drops 90 percent of events cuts your consumer load by 90 percent for free.
- Watch two CloudWatch metrics: `ThrottledRules` (the bus is limiting you) and `FailedInvocations` (targets are rejecting deliveries). Alarm on both.
- Fan-out multiplies cost. One event matching five rules is five deliveries and five downstream invocations. Do that arithmetic before you assume the bill is small.

### EventBridge against SNS, SQS, and Kinesis

| | EventBridge | SNS | SQS | Kinesis |
|---|---|---|---|---|
| Model | Bus with content routing | Publish/subscribe topic | Queue, point to point | Ordered log |
| Consumers | Rules → targets | Subscribers | One consumer group per queue | Many, each at its own position |
| Filtering | Rich, on the whole payload | Attributes and payload | None | None, filter in code |
| Ordering | None | FIFO topic option | FIFO queue option | Guaranteed per partition key |
| Replay | Yes, archive and replay | No | Only DLQ redrive | Yes, within retention |
| Latency | Sub-second, variable | Lowest | Low | Low |
| Throughput | High | Very high | Effectively unlimited (standard) | Set by shard count |
| Scaling | Automatic | Automatic | Automatic | You provision shards |
| Best for | Routing business events | Broad notifications | Buffering work | Ordered high-volume analytics and change capture |

**"Why EventBridge and not SNS?"** EventBridge gives you routing on the whole payload rather than just message attributes, archive and replay, a big set of native targets so you write no glue code, and a schema registry. SNS wins on lowest latency, highest raw throughput, and FIFO ordering with fan-out.

SQS is not really the competitor to either. **It is the buffer you put behind one of them**, so each consumer can fail and retry on its own without affecting the others.

---

## 2. SQS

### What it is

SQS is a managed queue. Producers send messages. Consumers poll for messages, process them, then **delete** them. The delete is the acknowledgement.

If a consumer never deletes a message, the message comes back and someone else gets it. That is the whole safety model.

The value of a queue is not moving data around. It is **decoupling in time**: the producer can run at 10,000 messages per second while the consumer runs at 500, and nothing breaks. The backlog grows, then drains.

**A queue turns a traffic spike into a delay instead of an outage.** That sentence is the reason queues exist.

### Standard against FIFO

| | Standard queue | FIFO queue |
|---|---|---|
| Throughput | Effectively unlimited | 300 operations/sec per API action, 3,000 with batching. High-throughput mode raises this to tens of thousands. |
| Ordering | Best effort. Usually in order, no promise. | Strict order **within a message group**. |
| Duplicates | At-least-once. Duplicates happen. | Exactly-once processing within a 5-minute dedupe window. |
| Name | Anything | Must end in `.fifo` |
| Use when | Almost always. This is the default. | Only when order is needed for correctness. |

**The FIFO detail people get wrong:** ordering is per `MessageGroupId`, not per queue.

- Every message uses the same group id → you get one message processed at a time. Terrible throughput.
- Group id is `storeId` or `orderId` → strict order *within* each store, full parallelism *across* stores.

Picking the group id well is the entire design decision. Say that in an interview.

### Every SQS setting explained

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Visibility timeout** | 30 sec | 0 sec – 12 hours | How long a received message stays hidden from other consumers. |
| **Message retention** | 4 days | 1 min – 14 days | How long an undelivered message survives before SQS deletes it. |
| **Delivery delay** | 0 sec | 0 – 15 min | Every new message stays hidden this long before anyone can receive it. |
| **Max message size** | 256 KB | 1 KB – 256 KB | Bigger payloads need the Extended Client Library (body to S3, pointer in the message). |
| **Receive wait time** (long polling) | 0 sec | 0 – 20 sec | How long a receive call waits for a message before returning empty. |
| **Content-based dedupe** (FIFO only) | Off | On/off | SQS hashes the body to make the dedupe id for you. |
| **Redrive policy** | None | — | Points at a dead letter queue and sets `maxReceiveCount`. |
| **Redrive allow policy** | Allow all | — | Set on the DLQ. Controls which queues may use it. |
| **Encryption at rest** | SSE-SQS | SSE-SQS or SSE-KMS | Who owns the encryption key. |
| **Access policy** | Owner only | — | Who is allowed to send to this queue. |

Now the four that actually matter, in more detail.

**Visibility timeout.** A consumer receives a message. For the next 30 seconds (by default) nobody else can see it. If the consumer deletes it in time, it is gone forever. If not, it reappears and someone else picks it up — which means **duplicate processing**.

So: set it to at least your worst-case processing time. For Lambda consumers AWS suggests roughly six times the function timeout. For long jobs, call `ChangeMessageVisibility` every so often as a heartbeat to extend it while you work.

**Message retention.** After this period the message is deleted permanently, with no warning and no notification. Set your **dead letter queues to the maximum 14 days**, so you have time to notice and fix before evidence disappears.

**Delivery delay.** This is a cheap first-tier retry backoff. On failure, re-send the message with a 30-second delay instead of letting it retry immediately against a dependency that is still down.

**Receive wait time — always set this to 20.** With 0 (short polling) every receive call returns instantly even when the queue is empty, so you burn API calls and money spinning in a loop. With 20 (long polling) the call waits up to 20 seconds and returns the moment a message arrives. It is cheaper, quieter, **and actually lower latency** than a tight polling loop.

**Redrive policy.** After a message has been received `maxReceiveCount` times without being deleted, SQS moves it to the dead letter queue. Typical values are 3 to 5.

- Too low, and one transient blip sends good messages to the DLQ.
- Too high, and one bad message burns a lot of compute before you find it.

**Encryption.** SSE-SQS is free and AWS-managed. SSE-KMS gives you your own key, per-key permissions, and a CloudTrail audit trail — but it adds a KMS API call to every operation, which costs money and has its own throttling limits at high volume.

### Dead letter queues and redrive

A dead letter queue is just another SQS queue. Nothing special about it except how you use it.

The flow:

1. Consumer fails, so it never deletes the message.
2. Visibility timeout expires, message becomes visible again.
3. Repeat until receive count hits `maxReceiveCount`.
4. SQS moves the message to the dead letter queue.

Then **DLQ redrive** sends those messages back to the source queue once you have fixed the bug. Console or API. You can redrive everything, or at a limited rate.

**Put a CloudWatch alarm on `ApproximateNumberOfMessagesVisible` for every DLQ, with a threshold of 1.** A dead letter queue that nobody watches is a silent data-loss machine.

### Lambda plus SQS — know this cold

When you attach an SQS queue to a Lambda, SQS is not "triggering" Lambda. **The Lambda service runs its own fleet of pollers** that call `ReceiveMessage` for you and invoke your function with whatever they get.

That connection is called an **event source mapping**, and it has these settings:

| Setting | What it does |
|---|---|
| **Batch size** | How many messages arrive in one invocation. Up to 10 normally, up to 10,000 if you also set a batch window. |
| **Batch window** | Up to 300 seconds. How long the poller waits to fill the batch before invoking. |
| **Report batch item failures** | Lets your function say which individual messages failed. |
| **Maximum concurrency** | Caps how many concurrent invocations this queue can drive. |
| **Filter criteria** | Pattern matching on the message body, done before invoking. |

The two that matter most:

**Report batch item failures — turn this on for every batched consumer.** Your function returns:

```json
{ "batchItemFailures": [ { "itemIdentifier": "<messageId>" } ] }
```

and only those messages go back to the queue. **Without it, one bad message forces the entire batch to be retried.** With a batch of 10, that means nine good messages get processed a second time, and your duplicate rate multiplies.

**Maximum concurrency** caps this specific queue without reserving account-wide concurrency. It is newer and usually better than using reserved concurrency for the same job.

Two more things to know:

- Successful messages are deleted by the Lambda service automatically. You do not call delete.
- **Filtered-out messages are deleted, not preserved.** Filter criteria discards them. If you might want them later, do not filter here.

### How SQS scales

**What scales on its own:** everything, on standard queues. No capacity, no shards, no partitions. Zero to a very high message rate with no configuration. **SQS itself is essentially never your bottleneck.**

**How Lambda consumer scaling actually works.** The Lambda service starts with **5 concurrent pollers**. If the backlog keeps growing, it adds about **60 more concurrent invocations per minute**, up to 1,000 for a standard queue (or your account limit, if lower). For FIFO queues it scales up to the number of active message groups, because it cannot run two messages from one group at the same time.

So **an SQS-triggered Lambda ramps up over minutes, not instantly.** A million messages land in one second? You do not get a million Lambdas. You get 5, then 65, then 125, and so on.

Plan your latency expectations around that ramp. If a spike must be absorbed quickly, pre-warm with provisioned concurrency.

**The backpressure hazard you must mention in an interview.** An SQS-triggered Lambda will happily eat your **entire account concurrency** and starve every other function in the account, including the ones serving your API.

Two fixes:

1. **Maximum concurrency on the event source mapping** (preferred).
2. **Reserved concurrency on the function.** This has a bonus: it also caps how many database connections the pipeline can open, which protects Aurora.

**The metric that tells you if you are keeping up: `ApproximateAgeOfOldestMessage`.**

Queue depth alone lies to you. A deep queue that drains fast is healthy. A shallow queue that never drains is not. Age of the oldest message is the real health indicator. Alarm on it.

**Scaling FIFO queues.** Base limit is 300 operations per second per API action, or 3,000 with batches of 10. **High-throughput FIFO** raises this a lot by narrowing the dedupe scope from the whole queue down to each message group. If you still need more, the real fix is a better `MessageGroupId` that spreads work across more groups.

### Idempotency with SQS — the guaranteed follow-up question

Standard SQS is at-least-once, so duplicates will happen. There is no setting that fixes this. You fix it in the consumer. Three ways:

**1. Natural idempotency.** Write with `INSERT ... ON CONFLICT DO NOTHING` or `ON CONFLICT DO UPDATE`, keyed on a business id. The second copy overwrites with identical values, or does nothing.

Cheapest option. Try this first.

**2. A dedupe table.** A DynamoDB table with a TTL attribute, or a unique index in Aurora, keyed on `messageId` or a business idempotency key. Write it with a conditional put; if the write fails because the key exists, skip the work.

Use this when the work has side effects outside your database — calling a payment provider, sending an email.

**3. A version check.** Store a `version` or `updatedAt` on the row. Apply an incoming message only if its version is higher.

This fixes duplicates **and** out-of-order delivery at the same time, which is why it is often the best answer for standard queues.

---

## 3. Lambda

### What it is

Lambda runs your code when something happens. No servers to manage. You pay for the time it runs.

The mental model that explains everything else: **Lambda builds a small container (an "execution environment"), runs your startup code once, then reuses that container for many requests.**

Almost every surprising Lambda behaviour comes from knowing what gets reused and what does not.

### The three phases of a container's life

**1. Init.** AWS downloads your code, starts the language runtime, and runs everything at module level — imports, static blocks, anything outside your handler.

This is the **cold start**. You are not billed for it on zip packages, but you definitely feel the delay.

**2. Invoke.** Your handler runs. When it returns, the container is **frozen, not destroyed**. Background threads stop dead. The next request to reach this container thaws it and calls the handler again — a **warm start**. Your init code does not run again.

**3. Shutdown.** After a quiet period AWS destroys the container. Extensions get a shutdown event. Your function code does not get a reliable hook, so do not rely on cleanup running.

Two rules follow from this.

**Rule one: build your clients outside the handler.** Database clients, AWS SDK clients, HTTP connection pools, config loading — put them at module level so they are built once per container instead of once per request.

But be careful with **database connections** specifically, because every concurrent container holds its own. See the Aurora section for why that bites.

**Rule two: never rely on state surviving between requests, and never rely on it being cleared either.** `/tmp` contents, static variables, cached data — they may or may not still be there. Treat leftover state as a cache. Never as a source of truth.

### Every knob explained

| Knob | Range | What it controls |
|---|---|---|
| **Memory** | 128 MB – 10,240 MB | Memory *and CPU*. See below. |
| **Timeout** | 1 sec – 15 min | Hard ceiling on one invocation. |
| **Ephemeral storage** (`/tmp`) | 512 MB – 10,240 MB | Local scratch disk. |
| **Payload size** | 6 MB sync, 256 KB async | Max request and response size. |
| **Deployment package** | 50 MB zipped, 250 MB unzipped, 10 GB container image | How big your code can be. |
| **Layers** | 5, inside the 250 MB limit | Shared code across functions. |
| **Environment variables** | 4 KB total | Configuration. |
| **Concurrency** | 1,000 per account by default | How many can run at once. |
| **Architecture** | x86_64 or arm64 (Graviton) | Which chip. |
| **VPC config** | Subnets and security groups | Whether you can reach private resources. |

The ones with real consequences:

**Memory is the only performance dial you have.** CPU is handed out **in proportion to memory**. You get roughly one full vCPU at 1,769 MB, and up to about six vCPUs at the maximum. Network bandwidth scales with memory too.

So "my function is slow" is very often "my function is starved of CPU because I gave it too little memory" — even when it is nowhere near running out of memory.

**Timeout: 15 minutes is a hard ceiling.** If your work can exceed that, Lambda is the wrong compute. Use Fargate, or split the work with Step Functions.

**Payload: 6 MB synchronous.** This is what caps API Gateway responses that come from Lambda. Bigger responses need a presigned S3 URL instead.

**Package size affects cold start.** More code means more to download and more classes to load. Container images have their own layer caching and are usually right for large dependency trees.

**Environment variables are not for secrets.** Anyone who can read the function configuration can read them. Reference Secrets Manager or SSM Parameter Store instead.

**Graviton (arm64) is about 20 percent cheaper** and often faster for the same work. Switch unless a dependency has no ARM build.

**VPC config** is required to reach private resources like Aurora. It attaches a network interface. The old multi-second cold start penalty is largely gone thanks to shared Hyperplane interfaces, but mention it to show you know the history. Remember: **a Lambda in a VPC has no internet access** unless you add a NAT gateway or VPC endpoints.

### Concurrency, properly

**Concurrency is not requests per second.** It is how many invocations are in flight at one moment.

```
concurrency = requests per second × average duration in seconds
```

Work through it:

- 100 requests/sec at 200 ms each → **20** concurrent executions.
- The same 100 requests/sec at 2 seconds each → **200** concurrent executions.

So **making your function faster reduces concurrency use linearly.** A slow database call is not just a latency problem. It is a scaling problem.

Three kinds of concurrency setting:

| Setting | What it does | Cost | Use it for |
|---|---|---|---|
| **Unreserved** (default) | Draws from the shared account pool. | Pay per use | Most functions. |
| **Reserved concurrency** | Hard ceiling for this function, and it *reserves* that much capacity out of the account pool. | Free | Two jobs: stopping a pipeline function from starving everything else or exhausting database connections, and guaranteeing capacity for a critical function. Setting it to **0 is the emergency stop button**. |
| **Provisioned concurrency** | Pre-builds N containers and keeps them warm, so no cold start for those. | You pay whether used or not | Latency-critical synchronous routes behind API Gateway. Pair with Application Auto Scaling to raise it before a known busy window and lower it after. |

**Burst behaviour.** From cold, an account can add a burst of concurrent executions immediately (region-dependent, commonly 500 to 3,000), then grows by **500 more every 10 seconds** until it hits the account limit.

So Lambda scales very fast, but not instantly. A jump from 0 to 10,000 concurrent requests will throttle for the first few seconds.

**What happens when you throttle** depends on how you were invoked:

- **Synchronous** — the caller gets `TooManyRequestsException`, HTTP 429. The caller has to retry. Through API Gateway the client sees a 429 or 502.
- **Asynchronous** — Lambda retries internally for up to 6 hours, so throttles are usually invisible.
- **Event source mapping** — the poller backs off and retries. Nothing is lost, but the backlog grows.

**SnapStart matters to you because you write Java.** SnapStart takes a snapshot of the fully initialised container after Init, and restores from that snapshot on a cold start instead of re-running initialisation. For a Spring Boot function this can cut cold starts from several seconds to a few hundred milliseconds.

The catch: anything that must be unique per container (random seeds, generated ids) or that holds a live network connection must be rebuilt using the runtime hooks `beforeCheckpoint` and `afterRestore`. Otherwise every restored container shares the same "random" value and the same dead socket.

### Invocation models and what happens on error

| Model | Examples | Retries | Where failures go |
|---|---|---|---|
| **Synchronous** | API Gateway, ALB, SDK `RequestResponse` | None. Lambda does not retry at all. | Nowhere. The error goes back to the caller. |
| **Asynchronous** | EventBridge, S3 notifications, SNS | Lambda queues it internally and retries twice with backoff, up to 6 hours total event age. Both numbers configurable. | An **on-failure destination** (SQS, SNS, Lambda, EventBridge), or the older dead letter queue. |
| **Event source mapping** (polling) | SQS, Kinesis, DynamoDB Streams, MSK, MQ | Controlled by the source. SQS retries until `maxReceiveCount`. Kinesis retries the batch until it succeeds or the record expires. | SQS dead letter queue, or the `onFailure` destination for stream sources. |

**Destinations beat dead letter queues.** The old DLQ captures only the input payload. A **destination** captures the whole invocation record: request, response or error, request id, timestamps.

Use destinations for anything new. They also support an on-success destination, which is a neat way to chain async steps without a full workflow engine.

### Cold starts — how to talk about them

Causes, in the order they usually matter:

1. **Heavy dependency injection frameworks.** Spring scanning the classpath at startup is the single biggest cause of multi-second Java cold starts.
2. **Package size.** More code to download, more classes to load.
3. **Runtime.** Java and .NET start slower than Node, Python, or Go.
4. **VPC attachment.** Mostly solved now, but worth naming to show you know the history.

Fixes, in the order to try them:

1. Cut dependencies. Initialise lazily anything not used on every code path.
2. Use **SnapStart** for Java.
3. Use **provisioned concurrency**, but only on latency-critical synchronous routes, because it costs money while idle.
4. Move off Lambda entirely. For sustained user-facing traffic, a Fargate container has no cold start at all and often costs less.

The honest framing to give an interviewer: **cold starts matter on the synchronous user-facing path and barely matter on an async pipeline.** Do not spend money removing cold starts from a queue consumer nobody is waiting on.

### How Lambda scales

**What scales on its own:** the number of containers, up to your account limit — an initial burst, then 500 more every 10 seconds.

**What you tune by hand:**

- **Memory**, because it controls CPU, which controls duration, which controls concurrency use.
- **Reserved or maximum concurrency**, so one function cannot eat the account.
- **Provisioned concurrency**, when cold starts hurt real users.
- **Batch size**, when the source is a queue or stream. Ten messages per invocation uses one tenth the concurrency of one at a time.

**What breaks first as you scale up: almost never Lambda.** It is whatever Lambda talks to.

A thousand concurrent Lambdas means a thousand database connections, or a thousand calls per second to a third-party API with a rate limit, or a thousand writes to one DynamoDB partition.

**The concurrency limit is a protection mechanism for your downstream dependencies, not a limitation of Lambda.** Say exactly that in an interview — it shows you understand what the dial is for.

### Cost model

You pay for **gigabyte-seconds** (memory allocated × duration, billed per millisecond), plus a small per-request fee, plus provisioned concurrency if you use it.

**The counter-intuitive part: more memory can mean a smaller bill.**

Go from 512 MB to 1,024 MB and you double the cost per second — but you also double the CPU. If the function now finishes in less than half the time, you pay less overall *and* it is faster.

AWS Lambda Power Tuning is a Step Functions state machine that runs your function at several memory settings and plots the cost and speed curve. Running it and picking the best point is a very concrete thing to say you did.

**The other big lever: do not pay for waiting.** A function sitting idle on a slow HTTP call still bills you for full memory the whole time. Move long waits into Step Functions, which does not charge for waiting.

---

## 4. Step Functions

### What it is

Step Functions is a managed workflow engine. You describe a state machine in JSON (Amazon States Language). AWS runs it, tracks where each execution is, retries failed steps, and records a full history.

**Why use it instead of writing orchestration code?** Because the state lives outside your code and survives failures.

If your orchestrating Lambda dies halfway through a five-step process, you have to work out what already happened and clean it up by hand. If a Step Functions task fails, the workflow is still there, knows exactly which step failed, and follows the error path you defined.

### Standard against Express — your first decision

| | Standard | Express |
|---|---|---|
| Max duration | 1 year | 5 minutes |
| Guarantee | **Exactly-once** | **At-least-once** |
| Pricing | Per state transition | Per request, plus duration × memory |
| History | Full, stored and queryable | CloudWatch Logs only |
| Throughput | ~2,000 new executions/sec | Over 100,000 new executions/sec |
| Invocation | Async only | Async or sync |
| Use for | Long ETL, human approvals, sagas, anything you must audit | High-volume short workflows, API backends, per-event work |

**The pricing difference drives real design.** Standard bills per state transition. A 20-state workflow run a million times a day gets expensive fast. Express bills like Lambda, so it is far cheaper at high volume — but you give up exactly-once and the visual history.

A common pattern is to use both: a Standard workflow for the long overall job, calling Express child workflows for the high-volume per-item work.

### Every state type

| State | What it does |
|---|---|
| **Task** | Does actual work: invoke a Lambda, run an ECS task, call any AWS API, or wait for a callback. The only state that reaches outside. |
| **Choice** | Branches on the input. Compares strings, numbers, booleans, timestamps, and presence, combined with `And`, `Or`, `Not`. |
| **Parallel** | Runs a fixed set of branches at once. Output is an array, one element per branch. |
| **Map** | Runs the same steps once per item in an array. |
| **Pass** | Passes input through, optionally reshaping it or injecting fixed data. |
| **Wait** | Pauses for N seconds, or until a timestamp. |
| **Succeed** | Ends the execution successfully. |
| **Fail** | Ends it with an error name and cause you choose. |

Three details worth remembering:

- **Always give Choice a `Default` branch.** Input that matches nothing fails the whole execution.
- **Pass is great for stubbing a step** during development, and for restructuring data between steps.
- **You are not billed for Wait time.** This makes it dramatically cheaper than a Lambda calling `sleep`.

**Inline Map against Distributed Map** — this comes up constantly.

- **Inline Map** takes its items from the state input, so they must fit in the 256 KB payload limit. Runs up to **40 iterations at once**. Fine for tens or hundreds of items.
- **Distributed Map** reads its items straight from **S3** — a JSON array, a CSV, an S3 inventory report, or every object under a prefix. Runs up to **10,000 parallel child executions**. Supports batching items, a tolerated failure percentage, and writing combined results back to S3.

Distributed Map is purpose-built for "process one million records". Worth naming even if you actually solved the problem with SQS and Lambda, because it lets you compare:

- **Distributed Map** gives you progress tracking, failure tolerance, and result aggregation for free.
- **SQS plus Lambda** gives you finer retry per message, cheaper unit cost at very high volume, and no per-state-transition billing.

### Data flow inside a state — the part everyone gets wrong

Each state pushes data through a fixed sequence of filters:

1. **`InputPath`** — pick part of the state input to work with. Default is all of it.
2. **`Parameters`** — build the actual payload sent to the task. Mix constants with values pulled from the input using `.$` and JSONPath.
3. The task runs and produces a result.
4. **`ResultSelector`** — pick apart that raw result.
5. **`ResultPath`** — decide where the result goes relative to the original input.
6. **`OutputPath`** — pick what part of the combined thing goes to the next state.

**Step 5 is where the bugs are.**

- `ResultPath: "$.taskOutput"` → keeps your original input and adds the result under a new key.
- Omitting `ResultPath` → **replaces your input entirely with the result.**

Losing your input because you forgot `ResultPath` is the most common Step Functions bug there is.

### Error handling

```json
"Retry": [{
  "ErrorEquals": ["States.TaskFailed", "Lambda.ServiceException"],
  "IntervalSeconds": 2,
  "MaxAttempts": 4,
  "BackoffRate": 2.0,
  "MaxDelaySeconds": 60,
  "JitterStrategy": "FULL"
}],
"Catch": [{
  "ErrorEquals": ["States.ALL"],
  "Next": "CompensateInventory",
  "ResultPath": "$.error"
}]
```

| Field | What it controls |
|---|---|
| `ErrorEquals` | Which errors this rule covers. Rules are checked in order, so put specific errors before `States.ALL`. |
| `IntervalSeconds` | Wait before the first retry. |
| `MaxAttempts` | How many retries. **Zero means "match this error but do not retry"** — useful for excluding one error from a broader rule below. |
| `BackoffRate` | Multiplier each attempt. 2.0 gives 2, 4, 8, 16 seconds. |
| `MaxDelaySeconds` | Caps the backoff so it does not grow forever. |
| `JitterStrategy` | `FULL` randomises the wait. **Use it.** |
| `Catch` | Where to go when retries run out. |

Two things worth stressing.

**Why jitter matters.** Without it, a thousand executions that all failed at the same moment all retry at the same moment — and hammer the recovering dependency straight back down.

**Why `ResultPath: "$.error"` in Catch.** It keeps the original input and adds the error details. So your handler knows both what failed *and* what it was working on. Without it you get the error and lose the context.

Built-in error names: `States.ALL`, `States.TaskFailed`, `States.Timeout`, `States.Permissions`, `States.DataLimitExceeded`, `States.HeartbeatTimeout`, `States.Runtime`, plus any custom error your own code throws.

### Three kinds of service integration

| Kind | Behaviour | Example |
|---|---|---|
| **Request and response** (default) | Calls the service and moves straight on. Does not wait for the work to finish. | Publishing to SNS. |
| **Run a job** (`.sync`) | Calls the service and **waits for the job to finish**. Step Functions does the polling. | `ecs:runTask.sync`, Glue jobs, EMR steps, AWS Batch, nested Step Functions. This is how a 40-minute container becomes one step in a workflow. |
| **Wait for callback** (`.waitForTaskToken`) | Hands a task token to the target and pauses — possibly for up to a year — until something calls `SendTaskSuccess` or `SendTaskFailure` with that token. | Human approvals, third-party systems that call you back, anything needing an out-of-band signal. |

There are also **AWS SDK integrations**, letting a Task state call roughly 200 AWS services directly. This deletes an enormous amount of glue code: you no longer write a Lambda whose whole body is one `PutItem` call.

### The saga pattern

A **saga** is how you get transaction-like behaviour across services that share no transaction. Instead of rolling back, you run a **compensating action** to undo each step that already succeeded.

Book flight → book hotel → hotel fails → cancel the flight.

In Step Functions this maps directly:

- Each forward step is a Task.
- Each has a `Catch` pointing at its compensating task.
- The compensating tasks chain backwards, undoing earlier steps.

The state machine itself becomes the saga log — durable, inspectable, visual.

If you implemented a saga in application code instead, be ready to explain the trade-off honestly: *"We kept the orchestrator in our own service because the compensation logic needed domain knowledge and we wanted the pipeline in one deployable. Step Functions would have given us durability and visual debugging for free, at the cost of per-transition pricing and the 256 KB payload limit between states."*

### Limits

| Limit | Value | What to do |
|---|---|---|
| Payload between states | 256 KB | Pass S3 references, not data. Claim-check again. |
| History events per Standard execution | 25,000 | Long loops blow through this. Use Distributed Map, or start child executions so each gets its own budget. |
| Duration | 1 year Standard, 5 min Express | — |
| Express history | Not stored | Debug through CloudWatch Logs. Turn logging on **before** you need it. |
| Definition size | 1 MB | Large generated workflows can hit this. |

### How Step Functions scales

**Standard** handles roughly 2,000 new executions per second and a few thousand state transitions per second per account. Both are soft limits you can raise. In practice **cost becomes the constraint before the limit does**, because you pay per transition.

**Express** handles over 100,000 new executions per second. It is the right answer whenever volume is high.

**Distributed Map is the scaling feature.** It goes to 10,000 concurrent child executions and reads its work list from S3, so input size is bounded by S3 rather than by a payload limit.

One warning: those 10,000 child executions all hit the same downstream dependency. **Set `MaxConcurrency` to something your database or third-party API can actually survive** — not to the maximum.

---

## 5. Kinesis Data Streams

### What it is

Kinesis is an **ordered, replayable log**. Producers write records in. The stream keeps them for a retention period. Consumers read from a position and move forward at their own pace.

The difference from SQS in one sentence:

> **A queue deletes a message once someone processes it. A log keeps every record for its retention period and lets many independent consumers read the same records.**

### Every concept explained

**Shards.** A shard is both the unit of capacity and the unit of ordering. One shard gives you:

- 1 MB/sec or 1,000 records/sec of **writes**
- 2 MB/sec of **reads, shared across all standard consumers**
- Or 2 MB/sec **per consumer** if you use Enhanced Fan-Out

Total capacity is just shard count × those numbers.

This is the key structural difference from SQS: **Kinesis capacity is something you provision and plan. SQS capacity is not.**

**Partition key.** Every record carries one. Kinesis hashes it (MD5) and uses the hash to pick a shard. Two consequences:

1. All records with the same partition key land on the same shard, so they are read **in order**. Ordering in Kinesis is per partition key — never across the whole stream.
2. If one key is far more popular than the rest, its shard gets far more traffic. That is a **hot shard**, and it is the classic Kinesis problem.

**Sequence number.** Kinesis gives each record an increasing number within its shard. Consumers record how far they have got ("checkpoint") using it. You can start reading from a specific sequence number or a specific timestamp — **this is what makes replay possible.**

**Retention.** 24 hours by default, up to 365 days. Within that window you can rewind a consumer and reprocess everything. SQS cannot do this at all, and it is usually the deciding factor between the two.

**Capacity modes:**

| Mode | How it works | Use when |
|---|---|---|
| **Provisioned** | You pick the shard count and change it yourself by splitting and merging shards. Cheapest per unit at steady load. | Traffic is known and stable. Also the only mode where you fully control resharding. |
| **On-demand** | Kinesis adjusts shards for you based on observed traffic, up to double the peak of the last 30 days. Costs more per unit. | Spiky or unpredictable load, and new workloads where you do not know the shape yet. |

**Consumer types:**

| Type | How it reads | Latency | Throughput |
|---|---|---|---|
| **Standard (shared)** | Polls with `GetRecords`. All consumers of a shard share one 2 MB/sec. | ~200 ms and up, worse with more consumers | 2 MB/s per shard, shared |
| **Enhanced Fan-Out** | Kinesis pushes to you over HTTP/2. Each registered consumer gets its own throughput. | ~70 ms | 2 MB/s per shard, **each** |

Enhanced Fan-Out costs extra per consumer-shard-hour. It is the answer when you have three or more consumers on one stream and they are starving each other.

**Checkpointing.** The Kinesis Client Library keeps leases and checkpoints in a DynamoDB table it manages for you — one row per shard, recording which worker owns it and how far it has read. That is how consumers rebalance when you add or remove workers.

Lambda event source mappings handle the equivalent internally, checkpointing per batch.

### The metric that matters: `IteratorAge`

`IteratorAge` is **how far behind the newest record your consumer is, measured in time.** It is the single most important Kinesis metric. Alarm on it.

A rising iterator age means one of two things:

1. Your consumers are too slow or too few for the volume coming in.
2. A **poison record** is failing over and over at the front of a shard.

**That second case is the crucial difference from SQS.**

In SQS, a bad message eventually goes to the dead letter queue and everything else keeps flowing.

In Kinesis, **a record that keeps failing blocks its entire shard.** Order must be preserved, so the consumer cannot skip past it. Every good record behind it waits.

The fixes, all set on the Lambda event source mapping:

| Setting | What it does |
|---|---|
| `bisectBatchOnFunctionError` | On failure, split the batch in half and retry each half. Repeats until it has isolated the single bad record. |
| `maximumRetryAttempts` | Give up after N attempts instead of retrying until the record expires. |
| `maximumRecordAgeInSeconds` | Skip records older than this. |
| `onFailure` destination | Send the failed record's details to an SQS queue or SNS topic so you can investigate, while the shard moves on. |

**Configure all four.** A Kinesis consumer without them will eventually stall on a bad record, and you will hear about it from a customer rather than from a dashboard.

### How Kinesis scales

This is the service where scaling is most manual, which is exactly why interviewers like it.

**Scaling up in provisioned mode means resharding.** You **split** a shard into two, which doubles capacity for that key range. The split closes the parent shard and opens two children. Consumers must finish the parent before reading the children, so ordering survives the split — but there is a window where the layout is changing. **Merging** two adjacent shards is the reverse.

Resharding is an API call and it takes time. **You cannot do it instantly in response to a spike.**

**Scaling in on-demand mode is automatic but not instant.** Kinesis roughly doubles capacity in response to sustained increases, and can take several minutes to react. A truly sudden spike will still throttle producers with `ProvisionedThroughputExceededException`.

So **producers need retry with backoff regardless of which mode you chose.**

**Hot shards — the classic problem.** Your partition key is `storeId`, and one store generates 60 percent of your traffic. That store's shard is at capacity while the others sit idle. **Adding shards does not help**, because the hash sends that key to one shard no matter how many exist.

Two real fixes:

**1. Composite key.** Use `storeId#bucket`, where bucket is a small random or round-robin number. This spreads one store across several shards.

The cost: you have given up strict ordering within that store. Only do this when the store's records do not need ordering relative to each other — or when you can restore order downstream with a version number.

**2. Narrow the ordering requirement.** Often you do not actually need ordering across *all* of a store's events. You need it across events for a single product, or a single order. That narrower key spreads naturally and keeps the ordering you actually care about.

**Scaling consumers.** Concurrency is bounded by shard count: by default one Lambda invocation per shard at a time. The **`ParallelizationFactor`** setting raises this to up to 10 concurrent invocations per shard, while still keeping order **per partition key**.

This is the cheapest way to speed up a lagging consumer without resharding, and it is a good detail to volunteer unprompted.

### Kinesis or SQS?

**Choose Kinesis when** you need replay, or several independent consumers reading the same records, or strict per-key ordering at high volume.

**Choose SQS when** work items are independent, ordering does not matter, you want simple retry and DLQ behaviour, and you want zero capacity planning.

For most business workloads SQS is the right default. Kinesis is the specialised choice.

---

## 6. API Gateway

### What it is

API Gateway is a managed **front door** for your APIs.

It handles HTTPS, checks who the caller is, throttles them if they ask for too much, optionally validates and reshapes the request, then forwards it to a backend — a Lambda, an HTTP endpoint, a load balancer in your VPC, or an AWS service API directly.

### The three flavours

| | REST API | HTTP API | WebSocket API |
|---|---|---|---|
| Price | Highest | ~70% cheaper | Per message and per connection minute |
| Latency | Higher | Lower | — |
| Request validation | Yes, against JSON Schema | No | No |
| Transformation | Yes, VTL templates | Parameter mapping only | Limited |
| Caching | Yes, 0.5 GB – 237 GB per stage | No | No |
| API keys and usage plans | Yes | No | No |
| Authorizers | IAM, Cognito, Lambda | IAM, JWT, Lambda | IAM, Lambda |
| Web Application Firewall | Yes | Yes (regional) | No |
| Private endpoints | Yes | No | No |
| Canary deployments | Yes | Use stages and weighted routing | No |

**How to choose: start with HTTP API.** Move to REST API only when you specifically need request validation, response caching, usage plans with API keys, private endpoints, or VTL transformation.

The price difference is real at volume, and most REST-only features are things you either do not need or can do just as well in your own code.

**WebSocket API works differently.** You define routes for `$connect`, `$disconnect`, and your own custom routes. AWS gives each connection an id.

To push a message to a client, you call the `@connections` management API with that connection id. **You must store the connection ids yourself**, usually in DynamoDB, and clean up stale ones when the management API returns `410 Gone`.

### Authentication options

| Option | How it works | Best for |
|---|---|---|
| **IAM (SigV4)** | The caller signs the request with AWS credentials. API Gateway checks the signature and evaluates IAM policies. | Service-to-service calls inside AWS, internal tooling. |
| **Cognito user pools** | API Gateway validates a Cognito token directly. | Apps already using Cognito for sign-in. |
| **Lambda authorizer** | Your function receives the token (or the whole request) and returns an IAM policy plus a context object passed on to the backend. | Custom auth logic, third-party identity providers, per-tenant rules. Most flexible. |
| **JWT authorizer** | Built into HTTP API. Give it the issuer and audience; it validates the token with no code from you. | Standard OIDC/OAuth 2.0 providers like Auth0 or Okta. |
| **mTLS** | Client presents a certificate, validated against a truststore you keep in S3. Set on a custom domain. | Partner and business-to-business APIs with strong identity needs. |

Two details worth knowing:

- **Lambda authorizer results are cacheable** by a TTL of up to one hour. Without that, you pay to validate the same token on every single request.
- **Prefer the JWT authorizer over a Lambda authorizer when it fits.** It is faster and free.

There are two Lambda authorizer types. **TOKEN** type gets just the token from one header and is easy to cache. **REQUEST** type gets headers, query parameters, path parameters, and context — more powerful, but a more complicated cache key.

### Throttling and quotas

Four layers, applied from the outside in:

1. **Account level** — around 10,000 requests/sec per region by default, with a burst of about 5,000. Raisable.
2. **Stage level** — a rate and burst for the whole stage.
3. **Method level** — a rate and burst per individual route, so one expensive endpoint cannot eat the whole API's budget.
4. **Usage plans with API keys** (REST API only) — per-client rate, burst, and **quota**, like 10,000 requests per day per key. This is how you build paid tiers for external customers.

**The model is a token bucket.** The rate is how fast tokens refill. The burst is how many can pile up.

So a rate of 100/sec with a burst of 200 allows a sudden spike of 200 requests, then settles down to 100/sec.

Requests over the limit get **HTTP 429** with a `Retry-After` hint. Pass that through to clients honestly rather than turning it into a generic 500 — a well-behaved client will back off correctly if you tell it the truth.

### Integration types

| Integration | What it does | Notable use |
|---|---|---|
| **Lambda proxy** | The whole HTTP request goes to Lambda as JSON. Your function returns status, headers, body. | The default. No templates to maintain, all logic in testable code. |
| **Lambda non-proxy** | You map the request into a payload and map the response back, using VTL templates. | Only when API Gateway must own the contract shape separately from the function. Adds a template language nobody enjoys debugging. |
| **HTTP / HTTP proxy** | Forwards to any HTTP endpoint. | Putting auth, throttling, and metrics in front of an existing service. |
| **AWS service integration** | Calls an AWS API directly, with no compute in between. | **Straight to SQS is the standout.** See below. |
| **VPC Link** | Private connection to an internal ALB or NLB. | Exposing an ECS or EKS service with no public address. |
| **Mock** | Returns a canned response, no backend at all. | CORS preflight responses, and API stubs during development. |

**API Gateway straight to SQS** deserves its own note. You get an endpoint that accepts a burst of requests and drops them into a queue, with **no Lambda to throttle and no cold start.** Excellent for high-volume ingestion. The same trick works straight to DynamoDB, Kinesis, and Step Functions.

### Other properties you should know

**The 29-second integration timeout.** This applies to REST APIs, and to HTTP APIs by default. On REST APIs you can raise it by request.

Treat it as a hard architectural constraint. **Any job that might take longer must return `202 Accepted` with a status URL**, and the client polls or gets notified. Do not try to squeeze a long job into 29 seconds.

**Endpoint types:**

- **Edge-optimised** — routed through CloudFront locations. Good for globally spread clients.
- **Regional** — better when clients are in the same region, or when you want your own CloudFront in front.
- **Private** — reachable only from inside your VPC through an interface endpoint.

**Stages and stage variables.** A stage is a deployed snapshot: `dev`, `staging`, `prod`. Stage variables let one definition point at different Lambda aliases or HTTP endpoints per stage.

**Canary release.** Send a percentage of a stage's traffic to a new deployment, watch the metrics, then promote or roll back. Built into REST API stages.

**Request validation** against JSON Schema rejects malformed requests before they reach your compute. This is your data contract enforced at the edge. It costs nothing to run and removes a whole class of defensive code from every backend function.

**Caching** (REST API only) is a per-stage cache with a size and TTL you choose, keyed on request parameters you pick. It can remove most of the load from a read-heavy endpoint.

Two cautions: allow authorised cache invalidation, and **never cache a response that varies by user** unless the user identity is part of the cache key.

### How API Gateway scales

**What scales on its own:** API Gateway itself. It is a managed regional service and handles very high request rates with no provisioning. Your account limit is the ceiling, and it is raisable.

**What breaks first: the backend.** API Gateway will happily accept 10,000 requests per second and try to invoke your Lambda 10,000 times — which throttles at the account concurrency limit. The gateway is not the bottleneck. The thing behind it is.

The levers, in the order to reach for them:

1. **Caching** (REST API). The cheapest scaling there is — a cached response never touches your backend at all.
2. **Method-level throttling.** Reject excess load at the edge with a fast 429 instead of letting it queue up and time out. **Failing fast beats failing slowly.**
3. **Usage plans**, so one badly behaved client cannot consume everyone else's capacity.
4. **Direct integration to SQS** for write-heavy ingestion. This fully separates the accept rate from the process rate: the API accepts everything at gateway speed, the consumer drains at its own pace. This is the best answer to "we get ten thousand submissions in one minute."
5. **Provisioned concurrency** on Lambdas behind latency-sensitive routes, so a traffic ramp does not produce a wall of cold starts.

---

## 7. ECS Fargate

### What it is

ECS is AWS's container orchestrator. **Fargate is the mode where AWS runs the containers for you** — no EC2 instances to patch, scale, or pack efficiently.

### The object model

| Object | What it is |
|---|---|
| **Task definition** | The blueprint. Container image(s), CPU and memory, environment variables, secrets, ports, logging, and the two IAM roles. Immutable — every change creates a new revision. |
| **Task** | A running instance of a task definition. One or more containers sharing a network namespace on the same host. |
| **Service** | Keeps N tasks running, registers them with a load balancer, replaces unhealthy ones, and does rolling deployments. Use a service for anything long-running; run a standalone task for batch jobs. |
| **Cluster** | A logical grouping. With Fargate it is little more than a namespace. |

**The two IAM roles people constantly confuse:**

- **Task execution role** — used by the ECS agent, *around* your container. It pulls the image from ECR, fetches secrets to inject as environment variables, and writes logs to CloudWatch.
- **Task role** — used by **your application code** at runtime. This is where you grant `s3:GetObject` or `sqs:SendMessage`.

Get these backwards and you get "why can't my container read from S3?"

### Fargate specifics

- **No hosts.** No AMIs to patch, no instance types to choose. Billed per second on the vCPU and memory you request, with a one-minute minimum.
- **`awsvpc` networking.** Every task gets its own network interface, its own private IP, and its own security group. So you can write security group rules **between individual services** — much finer-grained than instance-level rules.
- **Fixed CPU and memory combinations.** You cannot pick arbitrary numbers. CPU goes 0.25, 0.5, 1, 2, 4, 8, 16 vCPU, and each CPU size allows a specific memory range. You choose a *combination*, not two independent values.
- **Ephemeral storage** is 20 GB by default, up to 200 GB. For persistent storage, mount EFS.
- **Platform version** controls which Fargate features you get. Pin it if you depend on something specific; otherwise `LATEST` is fine.

### Scaling Fargate

Fargate has **two independent layers of scaling.** A good interview answer mentions both.

**Layer one: how many tasks (horizontal).** Application Auto Scaling changes the service's desired count.

| Policy | How it works | Use when |
|---|---|---|
| **Target tracking** | Pick a metric and a target value. AWS adds or removes tasks to hold the metric near it. Available metrics: average CPU, average memory, and **ALB request count per target**. | The default, and right for almost everything. |
| **Step scaling** | Define alarm thresholds and how many tasks to add at each. | You want different responses at different severities — add 2 tasks at 60%, add 10 at 85%. |
| **Scheduled scaling** | Change min and max capacity at fixed times. | Known patterns: business hours, nightly batch, a marketing event. |

**Use ALB request count per target as your target-tracking metric where you can.** It reacts before CPU does and it maps directly to work arriving.

**Combine scheduled with target tracking:** the schedule sets the floor, the metric handles the rest.

The properties on a scaling policy:

- **Scale-out cooldown** — how long to wait after adding tasks before adding more. **Keep this short.** Being slow to add capacity hurts users.
- **Scale-in cooldown** — how long before removing tasks. **Keep this long.** Removing capacity eagerly causes flapping, and the saving is small next to the risk.
- **Minimum and maximum capacity** — the maximum is your protection against a runaway bill and against drowning your database. Always set it on purpose.

**Layer two: how big each task is (vertical).** Changing CPU and memory in the task definition.

For JVM applications, bigger tasks are more efficient per unit of work, because you spread the heap and the JIT warm-up over more requests. Smaller tasks give finer-grained scaling and faster start-up. **For Spring Boot, a few larger tasks usually beats many tiny ones.**

**The timing point that matters most.** A Fargate task takes roughly **30 to 60 seconds to start** — pull the image, attach the network interface, start the runtime, pass health checks. For a slow-starting JVM app, longer.

Compare that with Lambda adding capacity in milliseconds to seconds.

The practical consequence: **you must scale Fargate earlier than you would scale Lambda.**

- Set target usage lower — 55 to 65 percent rather than 80 percent — so there is headroom while new tasks boot.
- Keep a warm minimum count so you never start from one task.
- Shrink your image, because the pull is a real part of that start-up time.

**Health checks are part of scaling correctness:**

- **Container health check** (in the task definition) — a command run inside the container.
- **Load balancer health check** — the target group calls a path on your task. Fail it and the task is deregistered and replaced.
- **Health check grace period** on the service — how long after a task starts before load balancer health checks count against it.

**That last one causes one of the most common ECS misconfigurations.** Set it too short for a slow-starting JVM app, and ECS kills the task while it is still warming up, starts another, and you get an infinite restart loop.

### Deployments

**Rolling update** (the default), controlled by two percentages:

- `minimumHealthyPercent` — how far below desired count you will drop during a deploy.
- `maximumPercent` — how far above.

Setting 100 and 200 means new tasks fully start before old ones go away. No capacity dip, briefly double cost.

**Deployment circuit breaker** detects a failing deployment and rolls back to the previous task definition automatically. **Turn it on.** It costs nothing and prevents the most common bad night.

**Blue/green with CodeDeploy** brings up a full replacement set behind a test listener, lets you validate it, then shifts production traffic — all at once, linearly, or as a canary. Automatic rollback on CloudWatch alarms. More infrastructure and more time, but the safest option for a critical service.

### Cost and other features

- **Fargate Spot** — up to ~70 percent cheaper, with a two-minute interruption warning. Perfect for queue consumers and batch work that can restart. Never for a stateful service that cannot tolerate a sudden stop. Use a capacity provider strategy to mix: a base of on-demand tasks for reliability, Spot for everything above it.
- **Compute Savings Plans** apply to Fargate, so committed steady capacity gets a real discount.
- **Service Connect and Cloud Map** — service discovery, so tasks find each other by name instead of IP, with connection metrics built in.
- **Scale-in protection** — mark a task protected while it is mid-way through a long job, so auto-scaling does not kill it half done. Essential for queue consumers handling long messages.

### Lambda, Fargate, or EKS — the decision framework

| | Lambda | Fargate | EKS |
|---|---|---|---|
| Scaling speed | Milliseconds to seconds | 30–60 seconds | 30–60 sec, faster with spare nodes |
| Max runtime | 15 minutes | Unlimited | Unlimited |
| Idle cost | Zero | You pay for running tasks | You pay for nodes and the control plane |
| Cost at high steady load | Expensive | Cheaper | Cheapest at large scale, plus a platform team |
| Cold start | Yes | No, once warm | No |
| Operational burden | Lowest | Low | Highest |
| Best for | Spiky, event-driven, short work | Steady load, long jobs, big containers, gRPC and WebSocket | Kubernetes primitives, portability, service mesh, or a team already on it |

The crossover is roughly this: **Lambda is cheaper below some steady request rate, Fargate is cheaper above it**, because Lambda charges per request while a container's cost is flat once it is running.

Spiky traffic favours Lambda. Flat traffic favours containers.

Being able to say that out loud, with the reasoning, is worth more than remembering an exact number.

---

## 8. S3

### What it is

S3 is object storage. You put objects into buckets under a **key**, and get them back by key.

**It is not a file system.** There are no real directories. There are only keys that happen to contain slashes, and a listing API that can group by prefix.

In an event-driven architecture S3 plays two specific roles:

1. The **claim-check store** for payloads too big for a message.
2. The **durable archive** that makes reprocessing and auditing possible.

### The properties that matter

**Consistency.** Since December 2020, S3 gives **strong read-after-write consistency** for everything — new objects, overwrites, deletes, and LIST. The old "eventual consistency" caveats, and every workaround people built for them, are gone.

Say this confidently. A surprising number of candidates still repeat the old behaviour.

**Storage classes:**

| Class | Retrieval | Minimum duration | Use for |
|---|---|---|---|
| Standard | Instant | None | Active data. |
| Intelligent-Tiering | Instant | None | Unknown or changing access patterns. Moves objects between tiers for you, for a small per-object monitoring fee. |
| Standard-Infrequent Access | Instant | 30 days | Rarely read, but needed instantly when you do. Cheaper storage, but a retrieval fee per GB. |
| Glacier Instant Retrieval | Instant | 90 days | Archives you occasionally need right away. |
| Glacier Flexible Retrieval | Minutes to hours | 90 days | Backups and compliance archives. |
| Glacier Deep Archive | ~12 hours | 180 days | Long-term legal retention. Cheapest storage in AWS. |

**Intelligent-Tiering is the safe default when you genuinely do not know** the access pattern.

A **lifecycle rule** moves objects between classes and eventually deletes them, based on age, filtered by prefix or tag. This is exactly how you justify "S3 archival for auditability at low cost":

> Raw files land in Standard → move to Standard-IA after 30 days → Glacier after 90 → expire after seven years.

Configure it once and it runs forever.

**Watch the minimum duration charges.** Move an object to Glacier and delete it a week later, and you are still billed for 90 days. Aggressive lifecycle rules on short-lived data can *increase* your bill.

**Versioning and Object Lock.** Versioning keeps every version, so an overwrite or delete is recoverable — a delete just adds a delete marker.

**Object Lock** goes further: write-once-read-many.

- **Governance mode** — only users with a specific permission can remove the retention.
- **Compliance mode** — **nobody** can, not even the account root, until the retention period expires.

Add a **legal hold** for indefinite retention. This is a real compliance answer, not a hand-wave, and knowing the difference between the two modes is the part interviewers check.

**Event notifications.** S3 can notify SQS, SNS, Lambda, or EventBridge when objects are created, deleted, restored, or replicated, filtered by prefix and suffix. This is the classic ingestion trigger: a file lands, a pipeline starts.

- Route to **EventBridge** when you want several independent consumers or richer filtering.
- Go **direct to SQS** when you want one simple buffered pipeline.

**Performance and prefixes.** S3 supports roughly **3,500 writes and 5,500 reads per second per prefix**, with no limit on the number of prefixes.

So throughput is essentially unlimited *if you spread keys across prefixes*, and badly limited if you put everything under one.

A key like `raw/2026-08-06/<sequential-id>.xml` puts a whole day's traffic on one prefix. Adding a hash or a source component spreads it.

**Multipart upload.** Required above 5 GB, and worth using above about 100 MB anyway — parts upload in parallel, and a failed part is retried on its own instead of restarting the whole upload.

**Always set a lifecycle rule to abort incomplete multipart uploads after a few days.** Otherwise you pay storage for abandoned parts you cannot even see in a normal listing. This is a genuinely common hidden cost.

**Presigned URLs.** A time-limited URL letting a client upload or download directly, using your permissions, without going through your API.

This keeps large files off your compute entirely — no Lambda payload limit, no API Gateway timeout, no bandwidth cost through your service. **For any "users upload documents" feature, this should be your default design.**

**Security, layered:**

- **Encryption at rest.** SSE-S3 (AWS keys, free, on by default). SSE-KMS (your key, per-key permissions, CloudTrail audit trail — but KMS calls cost money and throttle; use **S3 Bucket Keys** to cut those calls dramatically). SSE-C (you supply the key on every request).
- **Bucket policies** control access to the bucket. **IAM policies** control what an identity can do. **ACLs** are legacy — disable them entirely by setting bucket ownership to enforced.
- **Block Public Access** — turn it on at both account and bucket level unless you have a specific, reviewed reason not to.
- **VPC gateway endpoint** — S3 traffic from inside your VPC goes over the AWS network instead of out through a NAT gateway. Better security, and often a large cost saving, because NAT data processing charges add up fast on a data-heavy pipeline.

**Querying data where it sits.** S3 Select reads part of a single object. **Athena**, backed by Glue, runs SQL across many objects and uses your prefix structure as partitions.

This is how you answer "can you go back and inspect the raw XML we received six months ago?" without building anything new.

### Key layout matters more than people expect

```
raw/dt=2026-08-06/source=partnerA/<id>.xml
```

That one scheme gives you four things at once:

1. Lifecycle rules that can target a date range.
2. Athena partitioning on both date and source, so a query for one partner on one day scans almost nothing.
3. Prefix parallelism for high-volume writes.
4. Human-readable navigation when you are debugging at 2am.

**Design it before you write the first object.** Renaming keys later means copying everything.

### How S3 scales

Storage and object count scale essentially without limit. Request throughput scales with prefix count. S3 also adapts internally to sustained traffic patterns over time.

The things that actually bite:

- **A single hot prefix.** Fix with better key design.
- **Request cost.** At very high volume, per-request charges can exceed storage charges. Fewer large objects are cheaper than many tiny ones. Writing millions of small objects? Batch them.
- **KMS throttling.** If every read decrypts through KMS, you can hit KMS request limits before any S3 limit. S3 Bucket Keys fix this.
- **Listing large prefixes.** `ListObjectsV2` pages at 1,000 keys. Listing ten million objects is slow and expensive. Use **S3 Inventory** (a daily report delivered as a file) instead, and process it with Distributed Map.

---

## 9. CloudWatch

See `07-observability.md` for the wider observability discussion. This section is the AWS mechanics.

### Metrics

A metric is identified by three things:

- A **namespace** — `AWS/Lambda`, or your own `Elevation/Ingestion`.
- A **name**.
- A set of **dimensions** — key-value pairs like `FunctionName=processor`.

**Every unique combination of dimensions is a separate metric for billing.**

That last point is what costs people money. Custom metrics are billed **per metric per month**. Add a `customerId` dimension with 50,000 customers, and you have just created 50,000 metrics.

**Cardinality is the cost driver, and it is very easy to create by accident.**

Three more things:

- **Resolution.** Standard metrics are one-minute. High-resolution custom metrics go to one second, cost more, and are rarely worth it.
- **Statistics.** Sum, average, min, max, sample count, percentiles. **Always look at p99, not average.** An average latency of 100 ms can hide a p99 of 8 seconds — and the p99 is what your users complain about.
- **Metric maths** builds derived metrics, like error rate = errors ÷ invocations. Alarm on those rather than raw counts; they stay stable as traffic changes.

**Embedded Metric Format** is the cheap way to instrument. You write a specially structured JSON line to your logs, and CloudWatch extracts metrics from it automatically.

You get the metric for dashboards and alarms, and you keep the full high-cardinality log line for investigation, **without paying per-metric prices for every dimension combination.** From Lambda this is the recommended approach, and it needs no extra API call in the hot path.

### Logs

- **Log groups** hold log streams. **Set a retention period on every single one.** The default is "never expire", which is quietly one of the most common AWS cost bugs. Most application logs are worthless after 30 days. Keep audit logs longer, deliberately.
- **Logs Insights** is the query language. Learn four things — `filter`, `stats ... by`, `sort`, `parse` — and you can handle the vast majority of incident investigation.
- **Subscription filters** stream matching log events in near real time to Kinesis, Firehose, Lambda, or OpenSearch. This is how you get logs into Datadog or Splunk, and how you alert on log *content* rather than on metrics.
- **Log ingestion is billed per gigabyte ingested**, often a bigger line item than storage. Debug logging left on in production at high volume is a genuinely expensive mistake.

### Alarms

| Feature | What it does |
|---|---|
| **Static threshold** | Alarm when a metric crosses a fixed value. |
| **Anomaly detection** | CloudWatch learns the normal band and alarms when the metric leaves it. Good for metrics with a daily or weekly shape, where a fixed threshold would be wrong at some hours. |
| **M out of N datapoints** | Only alarm when, say, 3 of the last 5 periods breach. Kills most flapping. |
| **`treatMissingData`** | What to do when there is no data: `notBreaching`, `breaching`, `ignore`, `missing`. |
| **Composite alarms** | Combine alarms with boolean logic. |

Two of those deserve more:

**`treatMissingData` matters more than it looks.** A queue that has stopped receiving messages produces no datapoints. Treat that as "not breaching" and you will never find out your producer died.

**Composite alarms** are mostly for noise suppression: alert only if the error rate is high **and** a deployment is not currently running. Also good for building one "service is unhealthy" alarm out of several signals.

### Tracing

**X-Ray** follows one request across services: API Gateway → Lambda → SQS → another Lambda → Aurora.

- **Segments and subsegments** — one segment per service, subsegments for individual calls like a database query.
- **Sampling rules** — you do not trace every request. Default is one per second plus 5 percent of the rest. Tune per route; trace more of the important ones.
- **Annotations against metadata** — **annotations are indexed and searchable**, so put your identifiers there (`orderId`, `tenantId`). **Metadata is stored but not indexed**, so put bulky context there. Get this backwards and you have the trace but cannot find it.

**ServiceLens** joins traces, metrics, and logs into one service map.

### The metrics to name for your pipelines

Being able to reel these off is a strong signal in an interview.

| Service | Metrics that matter | Why |
|---|---|---|
| SQS | `ApproximateAgeOfOldestMessage`; DLQ `ApproximateNumberOfMessagesVisible` | Age is the real backlog indicator. DLQ depth above zero always means a human should look. |
| Lambda | `Errors`, `Throttles`, `Duration` p99, `ConcurrentExecutions`, `IteratorAge` | Throttles mean you hit a concurrency ceiling. p99 duration drives both cost and concurrency use. |
| EventBridge | `FailedInvocations`, `ThrottledRules`, `DeadLetterInvocations` | The only way to find out events are being lost. |
| Kinesis | `IteratorAge`, `WriteProvisionedThroughputExceeded`, `ReadProvisionedThroughputExceeded` | Iterator age is the health of the whole stream. Throughput exceptions mean reshard. |
| Step Functions | `ExecutionsFailed`, `ExecutionsTimedOut`, `ExecutionThrottled` | — |
| API Gateway | `4XXError`, `5XXError`, `Latency`, `IntegrationLatency`, `Count` | The gap between `Latency` and `IntegrationLatency` is the gateway's own overhead — it separates "my backend is slow" from "the gateway is slow". |
| ECS | `CPUUtilization`, `MemoryUtilization`, `RunningTaskCount`, plus ALB `TargetResponseTime` and `UnHealthyHostCount` | — |
| Aurora | `DatabaseConnections`, `CPUUtilization`, `AuroraReplicaLag`, `Deadlocks`, `BufferCacheHitRatio` | Connections is where Lambda pipelines break. Replica lag is where read-after-write bugs come from. |
| **Business** | `RecordsIngested`, `RecordsRejected`, per source | The one nobody adds and everybody needs. Technical metrics tell you the system is **up**. Business metrics tell you it is **correct**. |

---

## 10. Aurora and RDS

### What it is

Aurora is AWS's own relational database engine. It speaks the same wire protocol as MySQL and PostgreSQL, so your existing drivers and tools work unchanged.

The interesting part is the architecture, because **almost every Aurora feature is a consequence of it.**

### The architecture, and why it explains everything

A normal database writes data pages and a write-ahead log to a local disk.

**Aurora separates compute from storage completely.** The database instance is only a query engine and a cache. Storage is a separate distributed service shared by every instance in the cluster.

Three facts:

- Data is stored as **six copies across three availability zones**, two per zone.
- A write is acknowledged once **four of six** copies confirm it. A read needs three of six. (This "how many must agree" rule is called a **quorum**.) The cluster survives losing a whole availability zone plus one more copy without losing data.
- **Only redo log records cross the network.** Aurora does not ship full data pages, does not do a double-write buffer, and does not do full page writes after a checkpoint. The storage layer replays the log and builds the pages itself.

That last point is why Aurora beats stock MySQL and PostgreSQL on write-heavy work: **it is sending far less data over the wire.**

Because every instance shares one storage layer, the benefits cascade:

- **Replicas are cheap and fast.** Up to 15 read replicas, all reading the same storage. Replication lag is typically **low milliseconds**, not the seconds you get from shipping binlogs. There is no data to copy — only cache invalidation to propagate.
- **Failover is fast**, usually 30 seconds or less. A replica does not need to catch up on data. It already shares the storage. It just gets promoted.
- **Storage grows automatically** in 10 GB steps up to 128 TB. You never provision disk or run out of space at 3am.
- **Backups are continuous** to S3 with no performance impact. Point-in-time recovery down to the second, within your retention window.

### The four endpoints, and using them correctly

| Endpoint | Points at | Use for |
|---|---|---|
| **Cluster (writer) endpoint** | Always the current primary. Follows failover automatically. | All writes. Never hard-code an instance address. |
| **Reader endpoint** | Load-balanced across all replicas, by DNS. | Scaling out reads. |
| **Custom endpoint** | A group of instances you define. | Sending analytics or reporting queries to a couple of bigger instances, so they cannot disturb transactional traffic. |
| **Instance endpoint** | One specific instance. | Diagnostics only. Never in application config. |

One caution on the reader endpoint: **DNS-based balancing is coarse.** A long-lived connection pool can end up unevenly spread across replicas, because it resolves DNS once and keeps the connections.

**The read-after-write trap.** Write through the cluster endpoint, then immediately read through the reader endpoint, and you may not see your own write.

Replication lag is milliseconds — but milliseconds is not zero, and a fast client can beat it.

Two fixes: read from the writer for a short window after a write, or design the interaction so it does not need to read back immediately.

This is a favourite interview follow-up, and **the correct answer is not "Aurora replication is fast so it will be fine."**

### Aurora Serverless v2

Scales capacity in fine-grained **Aurora Capacity Units**, where one unit is roughly 2 GB of memory with matching CPU and network. It adjusts within seconds, **while your connections stay open**, and can scale down to a fraction of a unit.

Version 1 had a bad reputation: it paused completely when idle, resumed slowly, and could only scale at a "scaling point" where it was safe to swap. **Version 2 has neither problem.**

Good fit: spiky workloads. A nightly ETL window, a development environment, or a multi-tenant system where each tenant is mostly idle.

For flat, predictable load, provisioned instances with a savings plan are cheaper.

### The other features worth naming

- **Fast clone** — creates a new cluster from an existing one using copy-on-write at the storage layer. Takes minutes regardless of database size, and only bills for pages that diverge. This is how you test a migration against production-sized data without a multi-hour restore.
- **Backtrack** (MySQL-compatible only) — rewinds the cluster in place to a point in time, without restoring to a new cluster. Very fast recovery from "someone ran the wrong UPDATE".
- **Global Database** — one primary region plus up to five read-only secondary regions, replicated through the storage layer with lag typically under a second, and cross-region failover in about a minute. This is disaster recovery and low-latency global reads. **Not multi-master writes.**
- **Performance Insights** — shows which statements and which wait events are eating database time. First place to look when the database is slow, and far more useful than CPU usage.
- **RDS Proxy** — see below.

### The Lambda plus Aurora problem, and RDS Proxy

This is the classic trap and it comes up in interviews constantly.

**The problem.** Each concurrent Lambda container has its own connection pool, and cannot share it with any other container. So **1,000 concurrent Lambdas means up to 1,000 database connections.**

Aurora has a `max_connections` limit that scales with instance memory. A small instance may allow only a few hundred. You exhaust it, new connections are refused, and — this is the important part — **the failure hits your healthy synchronous traffic too, not just the pipeline that caused it.**

It is worse than a counting problem, because each connection also uses memory on the instance. Many idle connections degrade performance well before you hit the hard limit.

**The fixes, in the order to present them:**

**1. RDS Proxy.** A managed connection pool sitting between your functions and Aurora. It multiplexes many client connections onto a small number of real database connections. It also holds the pool through a failover so clients see a much shorter interruption, and supports IAM authentication so your functions need no database password at all.

**This is the standard answer. Name it first.**

**2. Reserved or maximum concurrency** on the pipeline function. A hard ceiling on how many connections that function can ever open. Free, one line of configuration, and it protects everything else in the account.

**3. Batching.** Process 10 messages per invocation and write them in one statement instead of 10. Cuts both connection count and query count by roughly a factor of ten.

**4. Move the heavy database work to Fargate.** A long-running container holds one connection pool for its whole life, shared across thousands of requests. For a genuinely write-heavy consumer this is often the right architecture, not a workaround.

### How Aurora scales

**Reads scale horizontally and easily.** Add replicas, up to 15, and use the reader endpoint. Aurora Auto Scaling can add and remove replicas based on CPU or connection count.

**Writes scale vertically only.** There is exactly one writer. You make it bigger. You cannot add a second one. (Aurora Multi-Master existed for MySQL 5.6, but it is not a general answer.)

When one writer is not enough, your options are the real distributed-systems answers:

- Shard by tenant or another partition key.
- Move high-volume append-only data to DynamoDB or a stream.
- Add caching so fewer writes are needed.
- Batch writes.

**Storage scales automatically** to 128 TB in 10 GB steps, with no action from you.

**Connections are the limit you hit first in a serverless architecture** — see above.

**What to watch:** `DatabaseConnections` against your maximum, `CPUUtilization` on the writer, `AuroraReplicaLag`, `Deadlocks`, and `BufferCacheHitRatio`.

That last one is a good early warning: **a falling cache hit ratio means your working set no longer fits in memory.** Size up before latency degrades, not after.

---

## 11. DynamoDB

`03-databases.md` has a condensed version of this for last-minute revision. This section is the full explanation.

### What it is

DynamoDB is a managed key-value and document database. You give it a key, it gives you back an item, in single-digit milliseconds, at any scale.

The thing to understand is **why** it is fast, because it explains every restriction that follows.

DynamoDB spreads your data across many physical **partitions**. It decides which partition an item lives on by hashing your partition key. So a lookup by partition key goes straight to one machine, with no coordination and no search. That is where the flat, predictable latency comes from.

The price of that design: **DynamoDB can only find things efficiently by key.** There is no "search the table for rows where status is pending" unless you built an index for it in advance.

That leads to the one sentence that captures the whole difference from Aurora:

> **In a relational database you model the data first and write queries later. In DynamoDB you list the queries first and model the data to serve them.**

If you do not know your access patterns yet, DynamoDB is the wrong choice. Not because it is weak, but because you cannot design the table.

### Keys

Every item is identified by a **primary key**, which comes in two shapes.

**Simple primary key — partition key only.** Like a hash map. The partition key must be unique across the table.

```
PK = userId
```

**Composite primary key — partition key plus sort key.** Now the partition key identifies a *group* of items, and the sort key orders them within that group.

```
PK = userId, SK = orderCreatedAt
```

This is the shape you will use most, because it unlocks the only rich query DynamoDB has:

> Give me all items with partition key X, where the sort key is between A and B (or begins with a prefix, or is greater than N), in order, forwards or backwards.

That is a `Query`, and it is fast. Everything else is a `Scan`.

**Query against Scan — get this right in an interview.**

| | Query | Scan |
|---|---|---|
| How it works | Jumps to one partition using the key | Reads **every item in the table**, then filters |
| Cost | Proportional to what you return | Proportional to the size of the whole table |
| Latency | Milliseconds | Grows with the table forever |
| Use it | Always | Almost never in production |

**A filter expression does not save you.** Filters are applied *after* the read, so you pay for every item scanned and then throw most away. Filtering is not indexing.

If you find yourself scanning, you have the wrong key design or you are missing an index.

### Item limits

| Limit | Value | Why it matters |
|---|---|---|
| Item size | **400 KB** | Bigger goes to S3 with a pointer in the item. Claim-check again. |
| Item collection (all items sharing one partition key) | **10 GB**, but only when a local secondary index exists | Without an LSI there is no such cap. With one, you can hit a wall you cannot fix without rebuilding the table. |
| Partition key length | 2,048 bytes | — |
| Sort key length | 1,024 bytes | — |

### Indexes

An index is a second view of your data, with a different key, so you can query by something other than the primary key.

| | Global Secondary Index (GSI) | Local Secondary Index (LSI) |
|---|---|---|
| Partition key | **Different** from the table | **Same** as the table |
| Sort key | Any attribute | Different from the table |
| Consistency | **Eventually consistent only** | Strongly consistent reads possible |
| Capacity | Has its own, separate from the table | Shares the table's |
| When created | Any time, on a live table | **Only when the table is created** |
| Limit | 20 per table | 5 per table |
| Storage | Its own copy of the projected attributes | Shares the item collection, counts toward the 10 GB cap |

Three consequences that catch people:

- **You cannot add an LSI later.** If you might need one, decide at table creation. In practice most teams use only GSIs for exactly this reason.
- **A GSI is never strongly consistent.** It is updated asynchronously after the base table write. Usually a few milliseconds behind, but never zero. Do not read your own write from a GSI.
- **A GSI has its own throughput.** If the GSI is under-provisioned, writes to the *base table* get throttled, because DynamoDB cannot let the index fall arbitrarily behind. A common and confusing production incident.

**Projections** control which attributes get copied into the index: `KEYS_ONLY`, `INCLUDE` (a named list), or `ALL`. Projecting less costs less storage and less write capacity — but if you need an attribute that is not projected, DynamoDB has to go back to the base table for every item, which usually costs more than projecting it would have.

**Sparse indexes are an underrated trick.** An item only appears in a GSI if it has that GSI's key attributes. So set an attribute like `pendingAt` only while an order is pending, index on it, and delete it when the order completes. Your GSI now contains **only pending orders** — you get a small, cheap, purpose-built query over what could be a huge table.

### Single-table design

The pattern DynamoDB is famous for, and the one interviewers ask about.

Instead of one table per entity, you put multiple entity types in **one table**, and make the keys carry the entity type:

```
PK              SK                  attributes
CUSTOMER#123    PROFILE             name, email
CUSTOMER#123    ORDER#2026-08-01    total, status
CUSTOMER#123    ORDER#2026-08-05    total, status
ORDER#9001      ITEM#1              sku, qty
ORDER#9001      ITEM#2              sku, qty
```

Now `Query(PK = "CUSTOMER#123")` returns the customer *and* all their orders in **one request**. With `SK begins_with "ORDER#"` you get just the orders. That is the join you were going to write, done as a single key lookup.

**Why it exists:** DynamoDB has no joins. Fetching a customer and their orders from two tables means two round trips, and the count grows with your page. Single-table design collapses related data into one partition so one query serves one screen.

**GSI overloading** goes with it. Instead of naming index attributes after a domain concept, you name them generically — `GSI1PK`, `GSI1SK` — and different entity types put different values in them. One index then serves several access patterns.

**Be honest about the trade-off in an interview.** Single-table design is genuinely harder: the table is unreadable to a newcomer, ad-hoc queries become impossible, and changing an access pattern can mean rewriting data. It is the right call when your access patterns are well-known, stable, and performance-critical. It is over-engineering when they are not.

A sensible answer: *"I would use single-table design where the read pattern is hot and fixed, and separate tables where the data is queried rarely or the patterns are still moving."*

### Capacity modes

| Mode | How you pay | Scaling | Use when |
|---|---|---|---|
| **On-demand** | Per request | Instant, no configuration | Spiky or unknown traffic, new tables, dev environments |
| **Provisioned** | Per unit of capacity per hour | Auto Scaling adjusts it, over minutes | Steady, predictable traffic. Substantially cheaper at consistent load |

The units:

- **1 WCU** = one write of up to **1 KB** per second.
- **1 RCU** = one **strongly consistent** read of up to **4 KB** per second, or **two eventually consistent** reads.

So a 5 KB item costs 5 WCU to write, and 2 RCU to read eventually-consistently. Round up, always.

Two smoothing mechanisms exist so short spikes do not fail:

- **Burst capacity** — DynamoDB saves up to 5 minutes of unused capacity and lets you spend it on a spike.
- **Adaptive capacity** — it shifts throughput toward partitions that are getting hammered, automatically.

Neither one saves you from a genuinely hot key, though. See below.

### Consistency

**Reads are eventually consistent by default.** DynamoDB writes to three copies; an eventually consistent read may hit a copy that has not caught up yet. The window is typically under a second.

Ask for a **strongly consistent read** and you get the latest data — at the cost of double the RCU, higher latency, and no availability during certain failures.

**Strongly consistent reads are not available on a GSI. Ever.** If your design needs a strongly consistent read on a non-key attribute, DynamoDB cannot do it, and that is a real reason to reach for a relational database instead.

### Writes, concurrency, and idempotency

This is the part that matters most for the pipelines in this file.

**Conditional writes** are the core tool. You attach a condition, and the write only happens if the condition holds.

```
PutItem(item, ConditionExpression = "attribute_not_exists(pk)")
```

If the key already exists, the write fails with `ConditionalCheckFailedException` and **nothing is written**. That single call is an idempotency check and a write in one atomic operation, with no read first and no race between the two.

This is exactly why the dedupe-table pattern uses DynamoDB. One conditional put per message, no locking, no transaction.

**Optimistic locking** is the same idea for updates. Keep a `version` attribute, and write with `ConditionExpression = "version = :expectedVersion"`, incrementing it. If someone else changed the item since you read it, your write fails and you retry with fresh data.

**Atomic counters** use the `ADD` action: `SET views = views + 1` without reading first. Fast, but not idempotent — a retried request increments twice. Use conditional writes when correctness matters, atomic counters when approximate is fine.

**Transactions.** `TransactWriteItems` gives you all-or-nothing across up to **100 items**, possibly in several tables. `TransactGetItems` does the same for reads.

The cost is roughly **double**, because DynamoDB does a two-phase commit underneath. Pass a `ClientRequestToken` to make the whole transaction idempotent for about 10 minutes.

Use transactions where you genuinely need atomicity across items. Do not use them as a default, because you are paying twice for something a well-designed single-partition write often does for free.

### TTL

Set an attribute holding a Unix timestamp, tell DynamoDB which attribute it is, and items get deleted automatically after that time.

Three things to know:

- **Deletion is free.** It does not consume write capacity. This is why TTL is the standard cleanup mechanism for idempotency keys, sessions, and caches.
- **It is not prompt.** Deletion typically happens within 48 hours of expiry, not at the moment. **An expired item can still be returned by a read.** If that matters, filter on the timestamp in your query as well as relying on TTL.
- Deletions appear in DynamoDB Streams, marked as system deletes, so you can archive expiring data.

### DynamoDB Streams

A Stream is an ordered log of every change to the table, kept for **24 hours**.

Each record can carry, depending on the `StreamViewType` you pick: `KEYS_ONLY`, `NEW_IMAGE`, `OLD_IMAGE`, or `NEW_AND_OLD_IMAGES`. The last one is what you want for change-data-capture, because you can see exactly what changed.

**Ordering is per partition key**, exactly like Kinesis. And it behaves like Kinesis in the way that hurts, too: a Lambda consumer that keeps failing on one record **blocks that shard**. Configure `bisectBatchOnFunctionError`, `maximumRetryAttempts`, and an `onFailure` destination, the same as you would for Kinesis.

What people use Streams for:

- **Keeping a search index in sync.** Write to DynamoDB, stream the change to OpenSearch.
- **The transactional outbox.** Write the business item and an outbox item in one transaction, then have the stream publish the outbox item to EventBridge. This is how you get "the database write and the event publish either both happen or neither does" without a distributed transaction.
- **Aggregation.** Maintain counters or roll-ups in a second table.
- **Archiving to S3** for analytics with Athena.

**EventBridge Pipes** reads DynamoDB Streams natively, so for simple "change → filter → send somewhere" flows you do not need a Lambda at all.

### The other features worth naming

- **Point-in-time recovery (PITR)** — restore to any second in the last 35 days. Turn it on. It is cheap next to what it protects.
- **On-demand backups** — full snapshots you keep as long as you like, for compliance.
- **Global tables** — multi-region, multi-writer replication. Conflicts resolve **last-writer-wins**, which is fine for user profiles and dangerous for balances. Know that limitation before you offer it as an answer.
- **DAX** — a managed in-memory cache in front of DynamoDB, giving microsecond reads. It is **write-through and eventually consistent**, so it does not help strongly consistent reads. Reach for it only when you have proven a read-heavy hot spot; often a better key design is the real fix.
- **PartiQL** — SQL-ish syntax over DynamoDB. It is a convenience layer, not a query engine. A PartiQL statement that is not key-based is still a Scan, with all the cost that implies.
- **Export to S3** — a full table export with no capacity consumed, for Athena queries or moving data into a warehouse.

### How DynamoDB scales

**What scales on its own:** storage, without limit. Throughput, instantly in on-demand mode, or over minutes with Auto Scaling in provisioned mode. There is nothing to shard by hand — DynamoDB splits partitions for you as data and traffic grow.

**What limits you: a single partition.** One physical partition tops out at roughly **3,000 RCU and 1,000 WCU**, and 10 GB of data.

So the table can be arbitrarily large and fast in total, while one key is throttled. **That is the hot partition problem**, and it is the DynamoDB scaling question you should expect.

**How hot partitions happen:**

1. **A naturally popular key.** One tenant is 60 percent of your traffic. Same shape as the Kinesis hot shard.
2. **A monotonic key.** Partition key is a timestamp or a sequential id, so every write in a given moment goes to the same partition and the rest of the table sits idle.

**The fixes:**

**Write sharding.** Add a suffix: `PK = tenant#<0-9>`. One tenant now spreads across ten partitions. The cost is that reading everything for that tenant means ten queries instead of one, which you merge in your code. Pick the shard count from the write rate you need, not arbitrarily.

**Pick a higher-cardinality key.** Very often the real fix. `orderId` instead of `storeId`, `userId` instead of `country`. More distinct keys means more even spread, automatically.

**Never use a raw timestamp as a partition key.** Put the timestamp in the **sort** key, where it gives you range queries, and use something well-distributed as the partition key.

**Cache the hot reads.** DAX, or an application-level cache, if the hot key is read-heavy rather than write-heavy.

**What breaks and how it looks.** Over-capacity requests get `ProvisionedThroughputExceededException` (or throttling in on-demand mode when you exceed the previous peak too suddenly). The AWS SDK retries with backoff by default, so mild throttling shows up as latency rather than errors — which is why you must watch the metrics rather than waiting for exceptions.

**Metrics to watch:** `ThrottledRequests`, `ConsumedReadCapacityUnits` and `ConsumedWriteCapacityUnits` against provisioned, `SuccessfulRequestLatency`, `UserErrors`, and `ReplicationLatency` on global tables.

**On-demand has a ramp too.** It handles double your previous peak instantly, but a jump far beyond that can still throttle. If you know a spike is coming — a product launch, a batch load — pre-warm by switching to provisioned with high capacity, or by ramping traffic up gradually.

### DynamoDB or Aurora?

| | DynamoDB | Aurora |
|---|---|---|
| Query model | By key only, planned in advance | Any SQL query, decided later |
| Joins | None. You denormalise. | Native |
| Scaling writes | Horizontal, effectively unlimited | Vertical only, one writer |
| Connections | None. It is an HTTPS API. | A hard limit that Lambda exhausts |
| Latency | Flat single-digit ms at any size | Degrades as data outgrows memory |
| Transactions | Up to 100 items, ~2× cost | Full ACID, arbitrary size |
| Consistency on secondary lookups | Eventual only (GSI) | Strong |
| Operational load | Almost none | Sizing, connections, tuning, failover |

**The connection point is the one to volunteer in a serverless interview.** DynamoDB is an HTTPS API with no connection pool, so a thousand concurrent Lambdas cause it no distress at all. A thousand concurrent Lambdas against Aurora is the incident described in section 10. That single difference is often why an event-driven pipeline uses DynamoDB even when the data is relational-ish.

**When not to use DynamoDB:**

- Access patterns are not stable yet.
- You need ad-hoc queries, reporting, or analytics.
- You need real joins across several entities.
- You need strongly consistent reads on something other than the primary key.
- The team has no DynamoDB experience and the workload does not need it — a badly modelled DynamoDB table is far worse than a well-indexed Postgres table.

---

## 12. How the services combine

Knowing individual services gets you through the first ten minutes. What separates a strong candidate is explaining **why these pieces sit next to each other.**

Here are the combinations that come up again and again.

### API Gateway → Lambda → Aurora: the standard synchronous API

The default web API. The gateway validates requests, a Lambda runs the business logic, Aurora stores the data.

**What to say about it:**

- Protect Aurora with RDS Proxy, because Lambda concurrency maps directly to connections.
- Use provisioned concurrency on latency-critical routes, because Java cold starts are visible to users.
- Enable gateway caching for read-heavy endpoints.
- If sustained load is high and flat, this is exactly when moving to Fargate is cheaper and removes cold starts entirely.

### API Gateway → SQS → Lambda: the burst-tolerant ingestion endpoint

For a write endpoint that gets large spikes, integrate API Gateway **directly with SQS**. No Lambda at the front at all.

The gateway accepts requests at its own very high rate and drops them straight into a queue. A Lambda drains the queue at whatever pace the backend can take.

**Why this is a good answer:**

- No cold starts on the accept path.
- The write path can never throttle because of Lambda concurrency.
- A traffic spike becomes a queue backlog instead of a wall of 429s and 500s.

**The trade-off:** the caller gets `202 Accepted` and does not learn the processing result synchronously. So you need a status endpoint or a notification.

### EventBridge → SQS → Lambda: the standard event consumer

**Never point an EventBridge rule directly at a Lambda for a high-volume workload.** Put a queue between them.

What the queue buys you:

- **Buffering.** A spike becomes a backlog instead of a wave of throttled invocations.
- **Independent retry.** Each consumer retries at its own pace with its own DLQ. A slow consumer does not affect the others.
- **A place to inspect failures.** The DLQ holds the actual failed messages so you can look at them and redrive.
- **Backpressure control.** Maximum concurrency on the event source mapping caps how hard the consumer hits the database.

If you remember one architectural rule from this whole document, make it this:

> **Bus for routing. Queue for buffering.**

### S3 → EventBridge → Step Functions → SQS → Lambda: the large file pipeline

A partner uploads a large file to S3. The S3 notification goes to EventBridge. A rule starts a Step Functions execution. The workflow uses a Fargate task or Distributed Map to stream and split the file into individual records, publishing each to SQS. Lambda consumers validate them and write to Aurora.

**Why each piece is there:**

| Piece | Reason |
|---|---|
| S3 | The file is far too big for any message payload. |
| EventBridge | Several teams may want to know a file arrived. |
| Step Functions | The whole job takes longer than 15 minutes, and you want to see where it is. |
| Fargate or Distributed Map | Splitting a multi-gigabyte file is not a 15-minute job. |
| SQS | A million records need buffering and independent retry. |
| Lambda | Per-record work is small, spiky, and parallel. |

### Kinesis → Lambda → S3 → Athena: the analytics and change-capture path

Events go into a Kinesis stream. One Lambda consumer maintains the live operational view. A Firehose delivery stream writes the raw records to S3 as Parquet, partitioned by date. Athena queries the history.

**Why Kinesis rather than SQS:** several independent consumers read the same records, and you can replay the stream when one of them had a bug. Neither is possible with a queue.

### Step Functions → Fargate → Lambda: mixed-duration workflows

A workflow where some steps are short and some are long. Use `ecs:runTask.sync` for the step that takes 40 minutes, and ordinary Lambda tasks for the short ones. Step Functions waits for the container, and you do not pay while it waits.

**Why it is a good answer:** it addresses the 15-minute Lambda ceiling without abandoning serverless for the whole pipeline, and it shows you pick compute **per step**, not per project.

### Lambda + DynamoDB + Aurora: idempotency alongside the main store

Aurora holds the business data. A small DynamoDB table with a TTL attribute holds processed message ids for deduplication. Each consumer does a conditional write to DynamoDB first; if the key already exists, it skips the work.

**Why DynamoDB rather than a table in Aurora:** the dedupe table takes one write per message — exactly the high-volume, tiny, key-value pattern DynamoDB is built for. And it keeps that write load off the database that is already your connection bottleneck. The TTL attribute expires old keys with no cleanup job.

### CloudWatch across all of it

Every service above emits metrics automatically. The pieces **you** have to add are the ones that tell you the system is *correct*, not just alive:

- Records in, per source.
- Records stored.
- Records rejected, with reason codes.

Then at the end of every batch you can check that **in = stored + rejected**.

That reconciliation is what turns "the pipeline ran" into "the pipeline was right", and volunteering it in an interview lands very well.

---

## 13. Scaling, all in one place

### What scales on its own and what you tune

| Service | Scales automatically | You tune | What breaks first |
|---|---|---|---|
| EventBridge | Everything | Nothing on the bus | The target being invoked |
| SQS (standard) | Everything | Consumer concurrency, batch size, visibility timeout | Not SQS — the consumer or its database |
| SQS (FIFO) | Within group limits | `MessageGroupId` design, high-throughput mode | Throughput per group |
| Lambda | Containers, up to the account limit | Memory, concurrency caps, batch size | The downstream dependency, usually database connections |
| Step Functions Standard | Executions | Standard vs Express, Distributed Map concurrency | Cost per transition, and the 25,000-event history limit |
| Kinesis provisioned | Nothing | Shard count, partition key, parallelisation factor | Hot shard, then iterator age |
| Kinesis on-demand | Shard count, with a delay | Partition key | Sudden spikes still throttle |
| API Gateway | Everything | Throttles, usage plans, caching | The backend integration |
| ECS Fargate | Task count, using a policy you write | Target metric and value, cooldowns, min/max, task size | Task start-up time during a sharp spike |
| S3 | Effectively everything | Key and prefix design | A single hot prefix, or request cost |
| Aurora | Storage, and replicas with auto scaling | Instance size, replica count, connection strategy | Connection count, then single-writer capacity |
| DynamoDB | Storage, and throughput (instantly on-demand, over minutes provisioned) | Key design, index projections, capacity mode | A hot partition — ~3,000 RCU / 1,000 WCU on one key |

### The four scaling patterns worth memorising

**1. Put a queue in front of anything with a hard capacity limit.**

Aurora has a connection limit. A third-party API has a rate limit. A queue turns "too much traffic" into "a longer wait", which is nearly always the better failure. This is the single most useful pattern in event-driven design.

**2. Filter as early as you can.**

An EventBridge pattern that drops 90 percent of events costs nothing and removes 90 percent of downstream work. Request validation at API Gateway rejects bad input before it reaches compute. **Every layer of filtering you push earlier is capacity you did not have to buy.**

**3. Cap the fast component to protect the slow one.**

Lambda can reach thousands of concurrent executions in seconds. Aurora cannot accept thousands of new connections in seconds.

Maximum concurrency on the event source mapping is not a limitation you are working around. **It is you deliberately matching the fast component to the slow one.** Say it that way in an interview — the framing is the point.

**4. Batch to trade latency for throughput.**

Ten messages per invocation is one tenth the invocations, one tenth the concurrency, one tenth the connection pressure, and roughly one tenth the cost. The price is latency, because you wait to fill the batch.

Almost every async pipeline should take that trade. Almost no synchronous user-facing path should.

### How fast each service absorbs a sudden spike

Fastest to slowest. **This ordering is what decides where a spike should land.**

1. **API Gateway, S3, SQS, EventBridge** — effectively instant. No ramp at all.
2. **DynamoDB on-demand** — instant up to double the previous peak. Beyond that it still throttles.
3. **Lambda** — an initial burst of hundreds to a few thousand, then 500 more every 10 seconds.
4. **Lambda from SQS** — much slower: 5 pollers to start, then about 60 more concurrent invocations per minute.
5. **Kinesis on-demand** — minutes to double capacity.
6. **DynamoDB provisioned with Auto Scaling** — minutes to react.
7. **ECS Fargate** — 30 to 60 seconds per task, longer for a slow JVM app.
8. **Aurora replicas** — minutes to add.

The design conclusion follows directly:

> **Land the spike on something in group one, and let the slower components drain it.**

That is exactly why the API-Gateway-to-SQS and EventBridge-to-SQS patterns exist. **Anything that absorbs a spike instantly and holds it is doing the most valuable job in the architecture.**

---

## 14. Rapid-fire questions and answers

**Q: EventBridge or SQS first in your pipeline?**

Bus first, for routing and fan-out. Queue behind each consumer, for buffering and independent retry.

The queue is what lets one slow consumer fall behind without dropping events and without affecting the others.

If you have exactly one consumer and no routing needs, you can skip the bus — but you rarely regret having it when the second consumer shows up.

---

**Q: A message keeps failing. Walk me through exactly what happens.**

The consumer throws, so it never deletes the message. After the visibility timeout the message becomes visible again and another consumer picks it up. This repeats until the receive count hits `maxReceiveCount`, and SQS moves the message to the dead letter queue.

The DLQ depth alarm fires and pages someone. We look at the payload, decide whether it is a bug or a genuinely bad record, fix the code or quarantine the record, then use DLQ redrive to send the messages back to the source queue.

And with `ReportBatchItemFailures` enabled, only the failing message goes round that loop. The other nine in the batch were deleted on the first pass.

---

**Q: How do you guarantee exactly-once processing?**

You do not. Anyone who says they do is describing at-least-once delivery plus idempotent processing. That combination is "effectively once", which is what people actually mean.

Concretely, three pieces:

1. An idempotency key — the message id, or better, a business key.
2. A conditional write or an upsert, so a repeat is a no-op.
3. A version or timestamp check, so an older duplicate cannot overwrite newer data.

---

**Q: Order matters for a store's inventory updates. How do you handle it?**

Three options, and I would pick based on how much ordering is really needed.

1. **FIFO queue with `MessageGroupId = storeId`** — strict ordering per store, full parallelism across stores.
2. **Kinesis with `partitionKey = storeId`** — the same ordering plus replay and multiple consumers, at the cost of managing shards and watching for a hot store.
3. **Avoid ordering entirely** — carry a monotonic `version` or `updatedAt` in the message, and apply an update only if it is newer than what is stored.

That third option is often the best, because it survives both duplicates and out-of-order delivery with no infrastructure constraint at all.

---

**Q: One store is 60 percent of your traffic and its Kinesis shard is saturated. What do you do?**

That is a hot shard, and adding shards will not help — the hash sends that key to one shard no matter how many exist.

Two real fixes:

1. **Composite partition key** like `storeId#bucket`, spreading that store across several shards. I accept that I have given up ordering within the store, which I can often recover downstream with a version number.
2. **Narrow the ordering requirement.** I usually do not need every event for a store in order — only every event for a given product or order. That finer key spreads naturally.

---

**Q: DynamoDB or Aurora for this service?**

I start with the access patterns, not the data.

If I can list every query up front and they are all key lookups or key ranges, DynamoDB gives me flat latency, horizontal write scaling, and — the part that matters in a serverless design — **no connection pool for Lambda to exhaust**.

If the queries are ad-hoc, or reporting matters, or I need real joins, or I need a strongly consistent read on something other than the primary key, that is Aurora. DynamoDB genuinely cannot do that last one, since GSIs are eventually consistent only.

The honest version: if the access patterns are still moving, I pick Aurora, because a badly modelled DynamoDB table is much harder to fix than adding an index to Postgres.

---

**Q: One tenant is 60 percent of your DynamoDB traffic and you are being throttled. What do you do?**

That is a hot partition. One physical partition tops out around 3,000 RCU and 1,000 WCU, so the table can be fine overall while that one key is throttled. Adding capacity does not help, because the hash sends that tenant to one partition regardless.

Same two fixes as a hot Kinesis shard.

**Write sharding** — `PK = tenant#<0-9>` spreads that tenant across ten partitions. The cost is that reading everything for the tenant becomes ten queries that I merge in code.

Or **pick a higher-cardinality key**. Usually I do not actually need everything keyed by tenant. `orderId` or `userId` spreads naturally and still serves the query I really have.

I would also check I am not using a timestamp or sequential id as the partition key, because that concentrates every write in a given moment onto one partition. Timestamps belong in the sort key.

---

**Q: How do you publish an event only if the database write succeeded?**

That is the dual-write problem, and the answer is the **transactional outbox**.

In one `TransactWriteItems` call I write the business item and an outbox item to DynamoDB. Both land or neither does. A DynamoDB Stream then picks up the outbox item and publishes it to EventBridge — through EventBridge Pipes, so there is no Lambda to maintain.

The event now cannot exist without the write, and the write cannot silently fail to produce an event. Delivery to the bus is still at-least-once, so consumers stay idempotent — but I have removed the case where the two stores disagree.

---

**Q: Cost blew up on this pipeline. Where do you look?**

In roughly this order, because this is how often each one turns out to be the culprit:

1. CloudWatch log ingestion, and log groups with no retention set.
2. NAT gateway data processing charges — a VPC gateway endpoint for S3 often removes these entirely.
3. Lambda gigabyte-seconds, usually from over-provisioned memory, or functions billing while they wait on a slow database call.
4. CloudWatch custom metrics with a high-cardinality dimension somebody added.
5. Step Functions state transitions on a Standard workflow that should have been Express.
6. Kinesis shard-hours for shards provisioned for a peak that no longer happens.
7. S3 request counts on many tiny objects, and abandoned incomplete multipart uploads.
8. EventBridge charges per million events, multiplied by how many rules each event matches.

---

**Q: One million XML records arrive at once. Design the pipeline.**

Land the raw files in S3 under partitioned prefixes like `raw/dt=.../source=.../`.

An S3 notification through EventBridge starts a Step Functions execution. A splitter — either a Fargate task or a Distributed Map, because this will take longer than 15 minutes — streams each file and emits one message per record to SQS. Nothing large ever sits in a message payload.

Lambda consumers read the queue in batches of ten, validate each record against the schema, and upsert into Aurora keyed on the natural business identifier, so duplicates are harmless.

The raw file stays in S3 under a lifecycle rule, for audit and reprocessing. Invalid records go to a rejects queue and a rejects prefix in S3 with a reason code — never silently dropped.

The controls I would call out:

- Maximum concurrency on the event source mapping, or RDS Proxy, so consumers cannot exhaust Aurora's connections.
- `ReportBatchItemFailures`, so one bad record does not force nine good ones to be reprocessed.
- A dead letter queue with an alarm and a redrive plan.
- Per-source counters, so at the end I can reconcile records received against records stored plus records rejected.

If those numbers do not add up, the pipeline ran but was not correct — and I want to hear that from a dashboard, not from a customer.

---

**Q: When would you not use Lambda?**

- When the work runs longer than 15 minutes.
- When traffic is high and flat, because a container is cheaper once you are paying for capacity all the time anyway.
- When cold starts are visible on a user-facing path and provisioned concurrency would cost more than just running containers.
- When I need a protocol Lambda does not front well, like gRPC streaming or long-lived WebSocket connections.
- When the workload holds an expensive shared resource, like a large connection pool or a big in-memory cache. That is exactly what Lambda's isolation model is worst at.
