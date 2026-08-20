# HLD Practice Bank

Companion to the LLD bank one level up. Every problem here is meant to be done as a **timed 45-minute solo session on Excalidraw/paper first**, then compared against a reference (Hello Interview / Alex Xu), then logged in the mistakes journal at the bottom. Reading solutions without attempting first is the #1 way this phase gets wasted.

Each entry lists **the deep dive the interviewer will steer you to** — that 10-minute stretch decides the round. The middle sections (requirements, estimates, API, boxes-and-arrows) are table stakes; the deep dive is where SDE-2 passes and SSE differentiates.

## The 45-minute clock (use it every single time)

| Minutes | Step | Pass bar |
|---|---|---|
| 0–5 | Functional + non-functional requirements | You asked ≥3 clarifying questions and stated scale/latency/consistency targets out loud |
| 5–10 | Back-of-envelope | DAU → QPS (peak ≈ 2–3× avg), storage/year, read:write ratio. 1 day ≈ 100K s |
| 10–15 | API + data model | 3–5 endpoints, key tables with partition/index choice justified |
| 15–25 | High-level diagram | Every arrow labeled with protocol/pattern; no magic boxes |
| 25–38 | **Deep dive ×1–2** | The column below. Go deep on one, don't skim three |
| 38–45 | Failures & scale | "What breaks at 10×?" + one SPOF named and fixed |

## Tier 1 — learn the framework (do all four)

| # | Problem | What it's really testing | The deep dive you'll be steered to | Theory doc |
|---|---|---|---|---|
| 1 | **URL Shortener** | ID generation, read-heavy caching | Hash collision vs counter+base62; 301 vs 302 and its analytics consequence | 2, 4 |
| 2 | **Rate Limiter** | Algorithm choice + distributed state | Sliding window in Redis (Lua atomicity); race between check-and-increment | 3, 4 |
| 3 | **Pastebin / File Storage** | Blob vs metadata split | Why files go to object storage + CDN, only metadata in the DB; presigned URLs | 1, 4 |
| 4 | **Notification Service** | Fan-out, retries, dedup | At-least-once + idempotent consumer; per-channel rate limits; DLQ for dead tokens | 6, 7, 8 |

## Tier 2 — the core SDE-2 set

| # | Problem | What it's really testing | The deep dive you'll be steered to | Theory doc |
|---|---|---|---|---|
| 5 | **Twitter/Instagram Feed** | Fan-out on write vs read | The celebrity problem — hybrid fan-out; feed cache in Redis sorted sets | 4, 5 |
| 6 | **WhatsApp / Chat** | Stateful connections at scale | Message ordering per conversation; delivery receipts state machine; WebSocket server discovery (which box holds user B's socket?) | 1, 6 |
| 7 | **YouTube / Video Streaming** | Async pipeline | Upload → chunk → transcode (queue + workers) → CDN; adaptive bitrate is a client concern | 4, 6 |
| 8 | **Uber / Ola** | Geo-indexing + fast-changing state | Geohash vs quadtree; driver location updates (write-heavy, short-TTL — why Redis not Postgres); matching lock so two riders don't get one driver | 3, 5 |
| 9 | **Distributed Job Scheduler** | Exactly-once-ish execution | Worker crash mid-job: visibility timeout, idempotent re-runs, fencing | 7, 8 |
| 10 | **Typeahead / Autocomplete** | Precompute vs compute-on-read | Trie/prefix-cache updated offline; why you don't hit the DB per keystroke | 4 |
| 11 | **Web Crawler** | Politeness + dedup at scale | URL frontier priority queues; Bloom filter for seen-URLs; per-domain rate limits | 6, 8 |

## Tier 3 — hard / India company-flavored

| # | Problem | What it's really testing | The deep dive you'll be steered to | Asked at (flavor) |
|---|---|---|---|---|
| 12 | **Payment System** | Correctness over availability | Idempotency keys end-to-end; double-entry ledger; reconciliation with the bank; saga for order+payment | Razorpay, PhonePe, Paytm, CRED, Juspay |
| 13 | **Flash Sale / Ticket Booking** | Inventory contention | Hold-then-confirm with TTL; where the lock lives (DB row vs Redis); queue-based admission when demand ≫ stock | Flipkart (BBD), BookMyShow, Zepto |
| 14 | **Food Delivery (order + dispatch)** | Composition of feed+geo+payments | Order state machine across 3 parties; rider assignment under partial failure | Swiggy, Zomato |
| 15 | **Google Docs (collab editing)** | Conflict resolution | OT vs CRDT at intuition level; why last-write-wins destroys edits | Atlassian, Microsoft, Google |
| 16 | **Distributed KV Store (mini-Dynamo)** | Phase-2 theory, productized | Consistent hashing + vnodes; quorum R/W tuning; hinted handoff | Tests depth everywhere |
| 17 | **Search / Feed Ranking** | Two-phase retrieve-then-rank | Inverted index vs candidate generation; why ranking is a separate service | Flipkart, Meesho, LinkedIn |

## Self-grading after each session (be harsh)

- [ ] Did I state non-functional requirements as **numbers**, not adjectives?
- [ ] Did every datastore choice come with a one-sentence *why this, not Postgres*?
- [ ] Did I address the deep-dive column myself, or would the interviewer have had to drag me there?
- [ ] Did I name one failure mode per component and what happens to in-flight requests?
- [ ] Did I speak continuously? (Record one session per week — silence is the most common unnoticed failure.)
- [ ] What did the reference solution have that I missed? → **write it in the journal below**

## Mistakes journal

Append after every session. Reviewing this list weekly is worth more than doing a new problem.

| Date | Problem | What I missed / got wrong |
|---|---|---|
| | | |
