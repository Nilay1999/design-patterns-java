# Machine-Coding Round — How to Approach It

A playbook for the 60–90 min LLD/machine-coding interview: read the prompt, pull out the
classes, and ship a working, extensible design without running out of clock. The question
bank lives in [`README.md`](README.md); worked solutions are under [`solutions/`](solutions/).

The one rule everything else serves: **finish something that runs.** A small design that
compiles and demos beats a "complete" design that's half-written pseudocode when time is called.

---

## What the round is actually grading

Not the domain story (parking, elevators, splitwise) — that's set dressing. The interviewer
is watching for:

1. **Requirement discipline** — do you scope before you code, or dive in and thrash?
2. **Clean modeling** — one responsibility per class, sane relationships, no god-object.
3. **The right data structure / algorithm** — the crux each question hides (see the "what it's
   really testing" column in the [question bank](README.md)).
4. **Extensibility** — can a new variant (new pricing scheme, new vehicle type) slot in
   _without_ rewriting existing classes?
5. **Working code** — it compiles, a `main()` demos the happy path, edge cases are named.

You lose points for _thrashing, over-engineering, and not finishing_ — far more than for a
missing feature you explicitly deferred out loud.

---

## The clock (a 75-min template)

| Phase                            | Time      | Output                                                                 |
| -------------------------------- | --------- | ---------------------------------------------------------------------- |
| **1. Clarify & scope**           | 5–10 min  | Written list of in-scope vs out-of-scope requirements                  |
| **2. Model**                     | 10 min    | Entity list + relationships, sketched (comments or a quick class list) |
| **3. Tier 1 — baseline**         | 20 min    | Core classes + happy-path flow, _compiling_                            |
| **4. Tier 2 — headline feature** | 20 min    | The thing the question is actually testing                             |
| **5. Tier 3 + demo**             | 10–15 min | One extension, a `main()` demo, name the edge cases you skipped        |

If you're not coding by minute 20, you over-planned. If you're still adding features at minute
70 instead of making it run, you mis-prioritized.

---

## Step 1 — Interrogate the problem statement

The prompt is deliberately vague. **Don't start with classes. Start with com.lld.questions.** Turn the
one-liner into a bounded spec you and the interviewer agree on.

Ask about:

- **Actors** — who uses this? (user, admin, system/cron) Each actor hints at an entry point.
- **Core actions** — the 3–5 verbs the system must support. _These become your Tier 1._
- **Scale of variation** — "how many pricing schemes? payment types? vehicle types?" The answer
  tells you where a **strategy/factory** earns its keep vs. where an `enum` is enough.
- **Boundaries** — persistence? UI? network? Almost always the answer is **in-memory, no UI, no
  DB** — say so and move on. Don't build a repository layer nobody asked for.
- **Concurrency** — "single-threaded, or concurrent access?" Decide _once_, up front — it changes
  your data structures (plain `HashMap` vs `ConcurrentHashMap`, locks, atomics).

Then **cut scope out loud**: _"I'll model X and Y fully, stub Z, and skip auth entirely —
that OK?"_ Getting a "yes" is a scoring event: it shows you can carve an MVP.

> Write the agreed scope as a comment block at the top of your file. It's your contract and your
> checklist.

---

## Step 2 — Find the classes and entities

A mechanical technique that works under pressure:

**Nouns → classes/fields. Verbs → methods. Fixed sets of values → enums.**

Read your scoped spec and underline them:

- _"A **vehicle** parks in a **spot** on a **floor** and gets a **ticket**"_ → `Vehicle`,
  `ParkingSpot`, `Floor`, `Ticket` (classes). `VehicleType`, `SpotType`, `TicketStatus`
  (enums — closed sets of values).
- _"**park** the vehicle, **compute** the fee, **close** the ticket"_ → `park()`,
  `calculateFee()`, `closeTicket()` (methods, placed on whichever class owns the data).

Then apply three filters:

1. **One responsibility per class.** If a class both _finds a spot_ and _computes pricing_,
   split it. (In the parking solution, `SpotMatcher` matches, `PricingStrategy` prices,
   `ParkingArea` orchestrates.)
2. **Find the facade / orchestrator.** There's usually one top-level class that holds the others
   and exposes the public API (`ParkingArea`, `ElevatorSystem`, `LibraryManager`). It's the only
   place that knows several collaborators at once. Callers talk to _it_, not the internals.
3. **Derive, don't duplicate, state.** If occupancy = "has a vehicle," expose
   `isOccupied() { return vehicle != null; }` instead of storing a `boolean` you must keep in
   sync. One source of truth per fact. (See connascence notes in [`../connascence.md`](../connascence.md).)

You do **not** need an interface for everything. Reach for one only where a _real second
implementation_ is plausible (pricing schemes, notification channels). Otherwise a concrete
class is the honest choice — premature abstraction reads as noise.

---

## Step 3 — Wire the relationships

Decide ownership and multiplicity before writing bodies:

- **owns / composition** (`*--`) — `Floor` owns its `ParkingSpot`s; they die with it.
- **references / association** (`-->`) — `Ticket` references a `Vehicle` it doesn't own.
- **uses transiently** (`..>`) — `ParkingArea` uses `SpotMatcher` to compute, holds no state from it.

A 30-second sketch (even as a comment list `A owns List<B>; C references D`) prevents the two
classic messes: **circular ownership** and the **god-object** that holds everything.

---

## Step 4 — Build in tiers (don't build breadth-first)

This repo's house style, and the way to guarantee something runs. Same pattern as the
[in-memory SQL / Mongo](solutions/inmemorysql/) write-ups:

- **Tier 1 — working baseline.** The minimum classes for the _happy path_ end-to-end. Hardcode
  where you must, stub the rest. Get it **compiling and demoable** before anything fancy.
- **Tier 2 — the headline feature.** The crux the question tests — the O(1) LRU trick, the state
  machine, the concurrency guard. This is where most of the score is.
- **Tier 3 — one extension.** _Show_ extensibility by plugging in one variant (a second strategy,
  one edge case). You don't have to build them all — building the seam that makes them cheap is
  the point.

Announce which tier you're in. It signals you're prioritizing deliberately, not randomly.

---

## Patterns you'll actually reach for

Know these cold — they cover the vast majority of LLD prompts. **Use them where the prompt
demands variation, not by reflex.**

| Pattern                | Use when                                                                 | Seen in                        |
| ---------------------- | ------------------------------------------------------------------------ | ------------------------------ |
| **Strategy**           | Multiple interchangeable algorithms (pricing, matching, eviction)        | Parking pricing, LRU eviction  |
| **State**              | Object behaves differently by lifecycle stage; transitions are the point | Elevator, vending machine, ATM |
| **Factory**            | Creation logic varies or should be centralized                           | Vehicle/spot creation          |
| **Observer / pub-sub** | One event, many reactions                                                | Notification system            |
| **Command**            | Encapsulate an action to queue/undo it                                   | Text-editor undo/redo          |
| **Singleton**          | Exactly one instance (use sparingly — it's a global)                     | Config, ID generators          |

**Anti-pattern warning:** don't open with an `AbstractFactoryBuilderProvider`. The memo for this
repo is explicit — _interview-simple first, no patterns/abstractions on the first pass_. Add a
pattern the moment a second implementation appears, not before.

---

## Concurrency (only if asked)

If the prompt says "concurrent" (rate limiter, thread-safe cache, ticket booking):

- **Name the race first** — _"two threads could grab the same spot between find and claim."_
- Prefer the **smallest lock that's correct**: an atomic claim / CAS or a per-entity lock over
  one global `synchronized`. Mention `ConcurrentHashMap`, `AtomicInteger`, or segment locks.
- If not asked, say _"single-threaded for now, here's where I'd add locking"_ and move on. Don't
  spend Tier-1 budget on synchronization nobody requested.

---

## Common ways people lose the round

- **Coding before scoping** — building the wrong thing fast.
- **Over-engineering** — five interfaces and a DI container for a 60-minute problem.
- **God-object** — one class holding all data and all logic.
- **Duplicated/derivable state** kept out of sync (a `count` field that drifts from the list).
- **Never running it** — no `main()`, so nothing is proven to work.
- **Silence** — the interviewer can't grade reasoning they can't hear. Narrate trade-offs.
- **Gold-plating Tier 1** — perfecting the easy part, no time left for the crux.

---

## 60-second checklist

Before you start typing:

- [ ] In-scope vs out-of-scope written down and **agreed**
- [ ] 3–5 core actions identified → that's Tier 1
- [ ] Entities (nouns) and enums (fixed value sets) listed
- [ ] Facade/orchestrator class identified
- [ ] Concurrency decision made (usually: single-threaded, note where locks go)

While coding:

- [ ] Tier 1 compiles and demos the happy path **before** adding features
- [ ] One responsibility per class; state derived, not duplicated
- [ ] Interfaces only where a second implementation is real
- [ ] A `main()` that exercises the flow
- [ ] Edge cases named out loud, even if deferred

---

**TL;DR:** Clarify → cut scope → nouns become classes, verbs become methods → find the one
orchestrator → build Tier 1 until it _runs_, then the headline feature, then one extension.
Narrate throughout. Finishing small beats designing big.
