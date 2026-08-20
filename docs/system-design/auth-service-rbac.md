# Authentication & Role-Based Authorization Service

Every request in the platform passes through this. It has to be fast, it has to be right, and when it's down everything is down.

> **Resume bullet this supports:** _"Migrated auth from basic JWT to Ory Kratos across 6 NestJS microservices in an NX monorepo on AWS EKS with RabbitMQ, covering session management, identity flows, and token lifecycle."_

---

## The Problem

**Functional requirements**

- Sign up, log in, log out, password reset, email verification
- Multi-factor authentication, and social / enterprise SSO
- Users belong to **multiple organisations** with a different role in each
- Roles grant permissions; permissions gate actions on resources
- Services authenticate to each other, not just users to services
- An admin can revoke someone's access and it must take effect quickly

**Non-functional constraints**

| Constraint | Value |
|---|---|
| Users | ~500k across ~3,000 organisations |
| Token validations | ~50,000/sec — **every request needs one** |
| Logins | ~5,000/day |
| Authorization check | **< 5 ms p99** — it's on the hot path of everything |
| Revocation window | Access must be gone within ~15 minutes, instantly for high-risk actions |
| Availability | Highest in the platform. Auth down = platform down |

**The ratio that drives the design:**

```
50,000 validations/sec   vs   5,000 logins/day
                              ────────────────
      roughly 10,000 : 1
```

Logins are rare and can afford to be slow and careful. **Validation happens constantly and must be nearly free.** Every decision below follows from that gap.

---

## First: two different problems

Candidates lose points by blurring these. Separate them out loud:

| | **Authentication (AuthN)** | **Authorization (AuthZ)** |
|---|---|---|
| Question | *Who are you?* | *What are you allowed to do?* |
| Happens | Once per session | On every single request |
| Changes | Rarely | Whenever roles or org membership change |
| Buy or build | **Buy it** | **Build it** |

They have different lifecycles, different performance profiles, and different risk. Treat them as separate services.

---

## Architecture

```
   Browser / mobile
        │
        │  1. login (rare, slow path)
        ▼
  ┌───────────────────────────┐
  │  Identity Provider        │   Ory Kratos / Auth0 / Cognito
  │  passwords, MFA, SSO,     │   — bought, not built
  │  password reset, sessions │
  └────────────┬──────────────┘
               │  issues:  access token (15 min, JWT)
               │           refresh token (30 days, stateful)
               ▼
  ┌───────────────────────────┐
  │  API Gateway              │   validates signature locally (no network call)
  │  coarse checks only       │   rejects expired / malformed / wrong audience
  └────────────┬──────────────┘
               │  forwards user_id, org_id, roles
               ▼
  ┌───────────────────────────────────────────────┐
  │  Services (×6)                                │
  │  fine-grained checks: does THIS user have     │
  │  permission on THIS specific record?          │
  └────────────┬──────────────────────────────────┘
               │
               ▼
  ┌───────────────────────────┐        ┌──────────────────────┐
  │  Authorization Service    │◀──────▶│  Redis               │
  │  roles → permissions      │        │  permission cache    │
  │  org membership           │        │  revocation denylist │
  └───────────────────────────┘        └──────────────────────┘
               │
               │  revocation events (RabbitMQ fanout)
               ▼
        all services keep a small in-memory denylist
```

---

## Part A — Sessions or JWTs?

The central question in any auth design. Know both sides properly.

| | **Stateless JWT** | **Server-side session** |
|---|---|---|
| Validating it | Check a signature locally. No network call | Look it up in Redis or a database |
| Speed | Microseconds | ~1 ms, plus a network hop |
| Revoking it | **Hard** — it stays valid until it expires | Instant. Delete the row |
| Scaling | Trivial. Services need no shared state | Needs a fast, highly available shared store |
| Size | Grows with every claim you add | A tiny opaque ID |
| If auth service dies | **Existing sessions keep working** | Everything stops |

Neither wins outright, which is why almost every mature system runs **both**:

> "A short-lived access token — 15 minutes, a JWT, validated locally with no network call, which is what makes 50,000 validations a second affordable. Plus a long-lived refresh token — 30 days, stored server-side, which is the part we can actually revoke.
>
> The trade is explicit: I get stateless speed on the hot path, and I accept that revocation takes up to 15 minutes on the normal path. Where that's not acceptable, there's a denylist — but only for the cases that need it."

### Refresh token rotation, and why reuse detection matters

A 30-day refresh token is a valuable thing to steal. So:

- Every time a refresh token is used, it's **invalidated and replaced**
- Tokens are tracked as a **family** descending from one login
- **If an already-used refresh token is presented again, that's theft** — either the attacker is replaying the old one or the real user is. You can't tell which, so **revoke the entire family** and force a fresh login

That reuse-detection rule is a small amount of code that turns a stolen token from an open door into a single-use window. Worth naming explicitly.

---

## Part B — The revocation problem

> *"You fire an employee at 14:00. When do they actually lose access?"*

With plain JWTs the honest answer is "whenever their token expires", and that's often not good enough. Five options, and the last is the one to lead with:

**1. Just use short expiry.** 15 minutes bounds the damage with zero extra infrastructure. Often genuinely enough — say so rather than over-engineering.

**2. Check a denylist on every request.** Correct, but now every request does a Redis lookup, which throws away the main reason you chose JWTs.

**3. Check the denylist only for sensitive actions.** Reading a dashboard skips it; issuing a card or approving a payment doesn't. Cheap and pragmatic.

**4. Token version on the user record.** Bump a counter to invalidate everything that user holds. Still needs a lookup.

**5. Broadcast revocations over the message bus.** ← *the good one*

> "The auth service publishes a revocation event to a fanout exchange. Every service keeps a small in-memory denylist and checks it locally — no network call, so we keep the speed. Entries expire after the maximum access-token lifetime, so the list stays tiny: at 15-minute tokens it only ever holds revocations from the last 15 minutes.
>
> It's eventually consistent, with a propagation window of milliseconds rather than the 15 minutes we'd otherwise have."

This fits your RabbitMQ-across-six-services experience exactly, and it's a genuinely good answer to a question most candidates fumble.

---

## Part C — The RBAC model

### Don't attach permissions to users

```
❌  user → permissions          every role change means touching every user
✓   user → role → permissions   change the role once, everyone gets it
```

### The model

```
users            (id, email, ...)
organisations    (id, name, ...)
roles            (id, org_id, name)              -- 'finance_admin', 'employee'
permissions      (id, name)                      -- 'invoice:approve'
role_permissions (role_id, permission_id)
memberships      (user_id, org_id, role_id)      ← the important one
```

**`memberships` is where multi-tenancy lives.** A user's role is *scoped to an organisation* — the same person can be an admin in one and a read-only viewer in another. If your model has roles hanging directly off users, multi-tenancy is already broken.

**Permissions are `resource:action` strings** — `invoice:read`, `invoice:approve`, `card:issue`. Verb-on-noun, not vague names like `admin_access`, which nobody can reason about later.

### RBAC alone isn't enough

Real rules have conditions attached:

> *"Managers can approve invoices **under AED 5,000** **in their own department**."*

Roles can't express that — amount and department are attributes, not roles. So:

- **RBAC** for the coarse layer: does this user hold `invoice:approve` at all?
- **Attribute conditions** for the fine layer: is the amount under their limit, is it their department, do they own this record?

> "I'd use RBAC as the coarse gate and evaluate attribute conditions at the service that owns the data. Pure RBAC leads to role explosion — you end up with `manager_marketing_under_5k` and forty variants of it, which nobody can audit."

**Worth naming if they push further:** at large scale this becomes relationship-based authorization — Google's Zanzibar model, and open-source implementations like OpenFGA or Ory Keto — where the question is "is there a path from this user to this document through ownership or sharing relationships." That's the right answer when permissions are graph-shaped, like nested folders or document sharing.

### Where do you enforce it?

**Both places, and this is not optional:**

- **The gateway** does authentication and coarse checks — is the token valid, is it for the right audience, does the user hold roughly the right role
- **Each service** does resource-level checks — does *this* user have permission on *this specific record*

> "The gateway can't answer 'does this user own invoice 12345', because it doesn't know what an invoice is. That check has to happen where the data lives. And I'd never rely on the gateway alone, because anything that reaches a service internally would bypass it entirely."

### What goes in the token?

A common trap is stuffing every permission into the JWT.

- **Pro:** no lookup needed
- **Con:** a user with 200 permissions makes an enormous header on every single request, and their permissions are stale until the token expires

> "User ID, organisation ID, and roles — roles are few, permissions are many. Services resolve roles to permissions against a cached lookup, which keeps the token small and means a permission change takes effect on the next cache refresh rather than the next login."

---

## Part D — Multi-tenancy, and the vulnerability that matters

A user belongs to several organisations. The token carries the **currently active** one. Switching organisations issues a new token.

**The single most common real-world auth vulnerability lives here:**

```
Valid token, org_id = A     →     GET /invoices/98765   (belongs to org B)
                                  ↑ token is genuine, user is real,
                                    permission check passes... wrong data returned
```

This is broken object-level authorization — consistently number one in the OWASP API Security Top 10. The token was valid. The role was correct. Nobody checked that the *record* belonged to the caller's organisation.

> "Every data access filters by organisation ID from the token, and never from anything the client sent. I'd enforce it structurally rather than by convention — the org filter goes in a shared repository layer or Postgres row-level security, so a developer can't forget it in one endpoint. Relying on every engineer remembering a `WHERE org_id = ?` on every query is how this bug happens."

Enforcing it in one place rather than trusting discipline is the senior answer.

---

## Part E — Services talking to each other

"It's an internal network, so it's fine" is not an answer. Options:

- **mTLS** — both sides present certificates. Strong, and a service mesh gives it to you without application code
- **Client credentials** — each service has its own identity and gets its own token, with its own scopes
- **Signed internal tokens** carrying the original user's context so an audit trail survives across hops

Whichever you pick, the principle: **a service should hold only the permissions it needs.** The invoice service having blanket database access is how one compromised service becomes a whole-platform breach.

---

## Build or buy

You migrated to Ory Kratos, so you have a real position here. State it plainly:

> **"Never build authentication. Do build authorization."**

**Buy authentication.** Password reset flows, MFA enrolment and recovery, account-enumeration protection, timing-safe comparison, session fixation, SSO protocol handling — these are solved problems with a long tail of security-sensitive edge cases, and each one you get wrong is a breach. There's no product differentiation in it.

**Build authorization.** Your permission model *is* your business domain. Off-the-shelf role systems never quite fit, and mapping your concepts onto someone else's fights you forever.

### The migration, since it's on your resume

Moving live users to a new identity provider with zero downtime:

1. **Dual-read.** For a transition period, accept both the old JWT and the new session. Services check new first, fall back to old.
2. **Passwords can't be decrypted.** Two options: import the hashes if the algorithms are compatible, or **migrate lazily** — on login, verify against the old system, then create the identity in the new one with a freshly-hashed password. Lazy migration means dormant accounts migrate whenever they return, so you keep a fallback until the tail is small.
3. **Cut over** once the new path handles all traffic.
4. **Remove the old path** — and actually delete it. Dead auth code paths are a liability, not a safety net.

The honest number to have ready: what percentage of users had migrated before you removed the fallback, and how you handled the remainder.

---

## Security checklist

- **Password hashing** — argon2id or bcrypt with a proper work factor. Never SHA or MD5, which are built to be fast, which is exactly the wrong property
- **Constant-time comparison** for tokens and hashes, so response timing doesn't leak information
- **No account enumeration** — "wrong email" and "wrong password" must be indistinguishable in response *and* in timing
- **Rate limit login attempts.** This is the one place a limiter should **fail closed** — if the limiter is unavailable, refuse rather than hand an attacker an unlimited credential-stuffing window *(see `distributed-rate-limiter.md`)*
- **Tokens in `httpOnly`, `Secure`, `SameSite` cookies**, not `localStorage`, which any XSS can read. Cookies then need CSRF protection
- **Rotate signing keys**, publish them via JWKS, and support two valid keys during rotation
- **Log every authorization failure.** A spike is either a bug or an attack, and you want to know which

---

## Failure modes

**The auth service is down.** This is the one that matters, and there's a genuinely good answer:

> "Because access tokens are validated locally against a cached public key, **existing sessions keep working through an auth service outage.** Nobody can log in, and nobody can refresh, but the platform doesn't stop. That's a real availability argument for stateless validation, and it's why I wouldn't put a session lookup on every request."

**Redis is down.** The permission cache misses, so services fall back to the authorization database with higher latency. Degraded, not broken. The revocation denylist is in-process memory, so it survives independently.

**Signing key compromised.** Rotate immediately and revoke all refresh tokens, which forces everyone to log in again. Painful and disruptive, which is exactly why key rotation should be rehearsed rather than improvised.

---

## Trap table

| Trap | Answer |
|---|---|
| "JWT or sessions?" | Both. Short JWT access token for speed, stateful refresh token for revocability. Name the trade. |
| "Employee is fired — when do they lose access?" | Up to the access-token lifetime by default. Broadcast revocations over the bus for an in-memory denylist if that's too slow. |
| "Why not check a denylist on every request?" | It throws away the reason you chose JWTs. Do it for sensitive operations only. |
| "Where do you enforce authorization?" | Gateway for coarse, service for resource-level. The gateway doesn't know what an invoice is. |
| "Put all permissions in the token?" | No — header bloat and stale permissions. Roles in the token, permissions resolved from cache. |
| "User in org A requests org B's invoice." | The number-one real vulnerability. Filter by org ID from the token, enforced in a shared layer or RLS, never per-endpoint discipline. |
| "RBAC can't express 'under AED 5,000'." | Correct — that's an attribute, not a role. RBAC coarse, attribute conditions fine. Avoid role explosion. |
| "Auth service is down — is everything down?" | No. Local validation means existing sessions survive; only login and refresh fail. |
| "Would you build this yourself?" | Buy authentication, build authorization. Identity flows are commodity and dangerous; permission models are your domain. |
| "How did you migrate without downtime?" | Dual-read, lazy password migration on login, cut over, delete the old path. |

---

## Related

- `distributed-rate-limiter.md` — login rate limiting, and why it fails closed
- `../../interview-prep/08-auth-integrations.md` — JWT, OAuth2/OIDC, Kratos specifics
- `../../interview-prep/scenarios/` — resume-bullet scenarios in the same format
