# Auth & Third-Party Integrations — JWT, Ory Kratos, Stripe, Cal.com, Directus

---

## 1. Authentication fundamentals

### JWT anatomy
`base64url(header).base64url(payload).signature` — **signed, not encrypted** (JWS). Anyone can read the payload; use JWE if you must hide claims (rare — better to keep secrets out of tokens).
- **Algorithms**: `HS256` (shared secret — every verifier can also mint tokens) vs `RS256`/`ES256` (asymmetric — issuer signs with the private key, services verify with the public key from **JWKS** `/.well-known/jwks.json`; `kid` in the header selects the key and enables rotation).
- **Registered claims**: `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`.
- **Validation checklist** (say all of it): verify signature with the expected algorithm (**pin it** — never trust the token's `alg`), check `exp`/`nbf` with small clock skew, check `iss` and `aud`, then authorize on claims. Classic attacks: `alg: none`, RS256→HS256 confusion (feeding the public key as an HMAC secret), unvalidated `kid` path traversal/SQL injection.

### The hard part: revocation
A stateless JWT is valid until it expires — you can't log someone out. Mitigations: short-lived access tokens (5–15 min) + refresh tokens, a denylist keyed by `jti` (reintroduces state), or token introspection (fully stateful). This is exactly the tradeoff behind moving from hand-rolled JWT to a session-based identity provider like **Ory Kratos**.

### Access vs refresh tokens
Access token: short-lived, sent on every request. Refresh token: long-lived, stored securely, exchanged for new access tokens. Best practice: **refresh token rotation with reuse detection** — each refresh issues a new one; if an old one is replayed, the whole family is revoked (that's a stolen-token signal).

### Storage & transport
- Browser: `httpOnly; Secure; SameSite=Lax/Strict` cookie is the safer default (immune to JS exfiltration via XSS, needs CSRF protection for state-changing requests). `localStorage` is XSS-exfiltratable.
- Mobile/native: secure keychain/keystore.
- Never put tokens in URLs (they leak into logs, referrers, history).

### Sessions vs JWT
| | Server session (opaque ID) | JWT |
|---|---|---|
| Revocation | Immediate | Hard (needs denylist/short TTL) |
| Scale | Session store lookup (Redis) | No lookup — stateless verify |
| Payload | Nothing leaks | Claims readable by client |
| Best for | User-facing apps, immediate logout, step-up auth | Service-to-service, short-lived, cross-domain APIs |
Common hybrid: sessions for the browser edge, short-lived JWTs minted for internal service-to-service calls.

### OAuth2 / OIDC (know the difference)
OAuth2 = **authorization** (delegated access via tokens). OIDC = **authentication** layer on top (adds `id_token`, `userinfo`, discovery document).
Flows: **authorization code + PKCE** (all interactive clients, including SPAs and mobile — implicit is deprecated), **client credentials** (machine-to-machine), **device code** (TVs/CLIs), refresh grant. ROPC (password grant) is deprecated.
Terms: authorization server, resource server, scopes vs claims, consent, `state` (CSRF), `nonce` (replay), redirect URI exact matching.

### Authorization models
- **RBAC** — roles → permissions. Simple, coarse; explodes with per-resource nuance.
- **ABAC** — policies over attributes (user dept, resource owner, time, IP).
- **ReBAC** — relationship graph (Google Zanzibar; **Ory Keto**, OpenFGA, SpiceDB): "user X is editor of doc Y because they're a member of team Z."
Always enforce **object-level** authorization at the data access layer, not just route-level roles (OWASP API #1: BOLA).

---

## 2. Ory Kratos (your migration bullet)

### What it is
An open-source, **headless** identity server (self-hosted). No UI: your app renders the forms; Kratos exposes **self-service flows** via API and returns "UI nodes" describing the fields, CSRF token, and validation messages you should render.

### Flows
Registration, login, **settings** (profile/password/2FA changes), **recovery** (link/code), **verification** (email), logout — each begins with a flow ID, is stateful and expiring, and prevents CSRF/replay.

### Identity model
- **Identity schema** = JSON Schema defining `traits` (email, name, tenant). Multiple schemas can coexist (customer vs admin), and schema changes are versioned.
- **Credentials**: password (argon2/bcrypt), OIDC (social), WebAuthn/passkeys, TOTP, lookup codes, one-time code.
- **Identity states**: active/inactive; `metadata_public` vs `metadata_admin` for app-owned data Kratos shouldn't let the user edit.

### Sessions
Cookie (browser) or bearer token (native). `GET /sessions/whoami` is the canonical "who is this?" call — services or a gateway call it (or validate via Ory Oathkeeper) rather than parsing a JWT themselves. Features: session lifespan and refresh, **AAL** (`aal1`/`aal2`) for step-up MFA on sensitive actions, revoke all sessions for an identity (the thing plain JWT couldn't do), privileged session windows for settings changes.

### The Ory stack
- **Kratos** — identities & sessions.
- **Hydra** — OAuth2/OIDC provider (issue tokens to third-party clients).
- **Oathkeeper** — identity & access proxy: authenticates requests at the edge, authorizes, and **mutates** them (e.g. injects a signed JWT/headers for upstream services) so downstream services don't each implement auth.
- **Keto** — Zanzibar-style permissions.

### The migration story (be ready to walk this end-to-end)
1. **Model mapping**: legacy user table → identity schema traits; decide what stays in your DB (domain profile) vs Kratos (credentials/identity).
2. **Password import**: Kratos accepts pre-hashed passwords (bcrypt/argon2/pbkdf2 formats) via the admin API — so users don't need a reset. If the legacy hash format is unsupported, use lazy migration: verify against the old hash on next login, then write the Kratos credential.
3. **Dual-run**: import identities in batches (idempotent, keyed by legacy user ID stored in `metadata_admin`), reconcile counts, and validate a sample end-to-end.
4. **Cutover**: gateway (Oathkeeper or an auth middleware in each Nest service) starts accepting Kratos sessions while still accepting old JWTs for a grace window; then flip and expire old tokens.
5. **Rollback plan**: feature flag on the auth path, old system untouched during the grace window, and a documented "re-point the gateway" procedure.
6. **Verification**: login success rate, session error rate, support tickets, and a synthetic login canary — a metric-backed cutover, not a hope-based one.

### Why Kratos over Cognito/Auth0/Keycloak
Self-hosted (data residency, no per-MAU pricing at 15k+ users), API-first with full UI control, schema flexibility, and it fits a Kubernetes/microservice deployment. Costs: you run and upgrade it, you build the UI, and the flow model has a learning curve. Give both sides — mature interviewers want the tradeoff, not advocacy.

---

## 3. Stripe (payments & subscriptions)

### Object model
`Customer` → `PaymentMethod` → `PaymentIntent` (one-off) / `SetupIntent` (save a card for later) → `Charge`. Recurring: `Product` → `Price` → `Subscription` → `Invoice` → `PaymentIntent`. Hosted flows: `Checkout Session`, `Billing Portal` (self-serve plan changes/cancellation — huge scope reduction).

### Subscription lifecycle (know the states)
`incomplete` → `trialing` → `active` → `past_due` → `canceled` / `unpaid`.
Also: trials, **proration** on upgrade/downgrade, `billing_cycle_anchor`, `cancel_at_period_end` (keep access until the period ends), pause collection, **dunning** (Smart Retries + emails) for failed payments, and grace-period logic in your app for `past_due`.

### Webhooks — the interview magnet
1. **Verify the signature**: `Stripe-Signature` header, computed HMAC over `timestamp.payload` with the endpoint secret; reject if the timestamp is outside the tolerance (**replay protection**). Verify against the **raw body** — a JSON body-parser that re-serializes breaks the signature (very common bug worth mentioning).
2. **Idempotency**: Stripe retries for up to ~3 days on non-2xx; store `event.id` in a processed-events table with a unique constraint and no-op on repeats.
3. **Ordering is not guaranteed** — events can arrive out of order. Don't blindly apply state transitions; either re-fetch the object from the API (source of truth) or compare `created`/object version before applying.
4. **Respond fast (2xx) and process async** — enqueue the event and return immediately; long handlers cause Stripe timeouts and unnecessary retries.
5. **Reconciliation job**: periodically pull subscriptions/invoices and repair drift, because a webhook endpoint that was down for an hour, or an event you silently 500'd, will otherwise leave entitlements wrong. This is the answer to "what if a webhook is missed?"
6. Key events to handle: `checkout.session.completed`, `customer.subscription.created/updated/deleted`, `invoice.paid`, `invoice.payment_failed`, `payment_intent.succeeded/payment_failed`.

### Other must-knows
- **`Idempotency-Key`** on write API calls (prevents double charges on client retries).
- **SCA/3DS**: `requires_action` status → the client must complete authentication; your backend must not treat "created" as "paid."
- **Money handling**: smallest currency unit as integers (paise/cents), never floats; store currency; make your `subscription`/`entitlement` state derived from Stripe, not duplicated by hand.
- **PCI scope**: use Elements/Checkout so card data never touches your servers (SAQ-A). Never log card data or full webhook bodies containing PII.
- **Testing**: test-mode keys, `stripe listen --forward-to localhost` CLI, test clocks for simulating renewals/trial ends.

### 15k users — the operational angle
Entitlement checks must be fast (cache subscription status in your DB/Redis, refreshed by webhooks), failure handling must be humane (grace period, dunning emails), and you need a support path: "why does this user have access?" answered by an audit log of webhook events and entitlement changes.

---

## 4. Cal.com integration

- **Domain**: event types (duration, buffers, minimum notice), availability schedules, bookings, attendees, recurring events, and calendar sync (Google/Microsoft) for conflict checking.
- **API/auth**: API keys or OAuth for managed users; self-hosted vs cloud; **webhooks** for `BOOKING_CREATED`, `BOOKING_RESCHEDULED`, `BOOKING_CANCELLED` (verify the signing secret; the same idempotency and out-of-order rules as Stripe apply).
- **Time correctness — the actual hard part**: store instants in UTC **plus** the user's IANA timezone (`Asia/Kolkata`), never a fixed offset; DST transitions mean "9 AM every Monday" is not a fixed UTC time; recurrence follows RFC 5545 (RRULE) semantics; render in the viewer's timezone.
- **Double-booking**: two requests for the same slot must not both succeed — enforce with a unique constraint on `(resource, slot_start)` or a row lock/advisory lock on the calendar, not just an availability read (check-then-act is a race).
- **Integration hygiene**: anti-corruption layer (map their model to yours so their schema changes don't ripple), retries with backoff on their API, circuit breaker + degraded mode when they're down, and reconciliation (periodic sync of bookings) since webhooks can be missed.

---

## 5. Directus (headless CMS)

- **What it is**: a headless CMS/data platform that wraps an existing SQL database — it introspects your schema and generates REST + GraphQL APIs, an admin UI, roles/permissions (including field-level and row-level filters), file/asset management with storage adapters (S3), and **Flows** (event/webhook automation).
- **Headless vs traditional**: content is delivered as an API to any front end; content modelling, publishing workflow (draft/published status), and previews are yours to design.
- **Integration pattern from your resume** — a dedicated microservice with its **own RDS instance** per integration:
  - *Why*: blast radius isolation (a CMS query storm can't degrade the core product DB), independent schema ownership and upgrade cadence, separate scaling/backup policy, clear security boundary for third-party tooling.
  - *Cost*: more infra and cost, no cross-database joins, data duplication and sync (events or scheduled sync), more deployment surface.
  - Be ready to defend it: "the isolation was worth it because Directus manages its own schema — letting a third-party tool run migrations against our core database was not acceptable."
- **Delivery concerns**: cache aggressively (CDN + `Cache-Control`, or a read-through cache) because CMS content is read-heavy and rarely changes; invalidate via Directus webhooks/Flows on publish.

---

## Rapid-fire Q&A

**Q: Why move off JWT to Kratos sessions?**
Revocation and lifecycle. Hand-rolled JWT gave us no immediate logout, no MFA/step-up, no recovery/verification flows, and every service re-implemented validation. Kratos centralized identity flows, gave revocable sessions and step-up auth, and let services ask one question (`whoami`/edge-injected identity) instead of each owning crypto.

**Q: Someone steals a valid access token. What limits the damage?**
Short TTL, refresh rotation with reuse detection, binding where possible (device/IP heuristics, DPoP/mTLS in stricter setups), audience restriction so the token only works on the intended service, immediate revocation via the session store, and anomaly alerts on token use patterns.

**Q: Stripe webhook arrives twice, out of order, an hour late. Does your system break?**
No: signature + timestamp tolerance rejects replays outside the window, `event.id` dedupe makes repeats no-ops, and state changes are applied from the current object state (re-fetched) or guarded by a version/`created` comparison. A nightly reconciliation job repairs anything the webhook path missed.

**Q: How did you validate a 25k-user migration?**
Row counts per source and target, checksum/hash comparison on a canonical subset of fields, uniqueness checks (emails, external IDs), spot-check sampling, and a shadow login test for a cohort before cutover. Migration scripts were idempotent and re-runnable, with a mapping table storing legacy ID → new identity ID so re-runs updated rather than duplicated.
