# Frameworks & Runtimes — Spring Boot, Hibernate/JPA, Node.js, NestJS, Express

Framework internals round. Language syntax excluded; mechanics included, because "how does `@Transactional` actually work" is a question about the framework, not the language.

---

## 1. Spring Boot

### Core container
- **IoC/DI**: the `ApplicationContext` builds and wires beans. Constructor injection is the standard (immutable, testable, fails fast on cycles).
- **Stereotypes**: `@Component`, `@Service`, `@Repository` (adds exception translation to `DataAccessException`), `@Controller`/`@RestController`, `@Configuration` (+ `@Bean` factory methods; CGLIB-proxied so repeated `@Bean` calls return the singleton).
- **Scopes**: `singleton` (default, one per context — must be stateless/thread-safe), `prototype`, plus web scopes `request`, `session`.
- **Bean lifecycle**: instantiate → populate dependencies → `Aware` callbacks → `BeanPostProcessor.before` → `@PostConstruct`/`InitializingBean` → `BeanPostProcessor.after` (this is where **AOP proxies are created**) → in use → `@PreDestroy`.

### Auto-configuration (the "what makes it Boot" question)
`@SpringBootApplication` = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. Boot reads auto-configuration classes listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (previously `spring.factories`) and applies them **conditionally**: `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`. That's why declaring your own `DataSource` bean silently disables Boot's. Debug with `--debug` → the *conditions evaluation report*.

### Configuration
- Property precedence (highest → lowest): command-line args → env vars/`SPRING_APPLICATION_JSON` → `application-{profile}.yml` outside jar → inside jar → defaults.
- `@ConfigurationProperties` with validation over scattered `@Value`.
- **Profiles** (`dev`, `prod`) for environment differences; avoid profile-conditional beans for business logic.
- Secrets from environment/Secrets Manager/Vault, never in the jar.

### Web request lifecycle (MVC)
`Filter`s (servlet level: auth, correlation ID, logging) → `DispatcherServlet` → `HandlerMapping` → `HandlerInterceptor.preHandle` → argument resolvers + `HttpMessageConverter` (Jackson) → controller → `@ControllerAdvice` exception handling → converters back to JSON → interceptors post/afterCompletion.
Know the difference: **filters** are servlet-container level and see everything including static/error dispatch; **interceptors** are Spring-level and know the handler; **AOP** wraps beans regardless of transport.

### Validation & errors
JSR-380 (`@Valid`, `@NotNull`, `@Size`, custom validators), `@ControllerAdvice` + `@ExceptionHandler` for a single error shape, Spring 6's `ProblemDetail` (RFC 7807). Map domain exceptions to codes centrally — never leak stack traces.

### Spring Data JPA
- Repository interfaces with derived query methods, `@Query` (JPQL/native), **projections** (interface or DTO constructor expressions — the fix for over-fetching), `Pageable`/`Slice` (Slice avoids the extra `COUNT(*)`).
- **Specification / Criteria API** for dynamic predicates — this is Spring's answer to predicate-based filtering (nice parallel to your Backstage search work).
- `@Modifying @Query` for bulk updates (bypasses the persistence context — must `clearAutomatically`).

### Transactions
- `@Transactional` is implemented by an **AOP proxy**: the proxy opens/commits/rolls back around the method.
  - **Self-invocation doesn't work** — calling `this.otherTransactionalMethod()` bypasses the proxy. Classic interview trap; fix by moving the method to another bean or using `AopContext`/self-injection.
  - **Only unchecked exceptions roll back by default** — `rollbackFor = Exception.class` for checked ones.
  - Only public methods are proxied (with default Spring AOP).
- **Propagation**: `REQUIRED` (join or start), `REQUIRES_NEW` (suspend outer, new physical tx — outer rollback won't undo it), `NESTED` (savepoint), `SUPPORTS`, `MANDATORY`, `NEVER`, `NOT_SUPPORTED`.
- **Isolation** per transaction, `readOnly=true` (flush mode MANUAL, enables replica routing), `timeout`.
- Boundary rule: transaction spans the use case in the service layer, contains **only DB work**, and publishes events after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`).

### Threading & tuning
- Servlet model = **thread per request**. Tomcat `server.tomcat.threads.max` (default 200) is your concurrency ceiling; blocking DB calls hold a thread the whole time.
- **HikariCP** pool: size for the DB, not the app (`pool ≈ cores × 2`); `connectionTimeout` failures mean pool exhaustion, usually from long transactions or N+1 storms.
- Alternatives: **WebFlux** (reactive, non-blocking, for high-concurrency I/O-bound fan-out; harder to debug, needs reactive drivers) and **virtual threads** (Java 21 / Boot 3.2, `spring.threads.virtual.enabled=true`) which give reactive-like scalability with blocking-style code.

### Production plumbing
- **Actuator**: `/health` (with liveness/readiness groups for K8s probes), `/metrics` via **Micrometer** → Prometheus/Datadog, `/info`, `/loggers` (runtime log-level changes), `/env`. Secure these endpoints.
- **Graceful shutdown**: `server.shutdown=graceful` + K8s `preStop` sleep so the load balancer deregisters before the process stops.
- Testing: `@SpringBootTest` (full context, slow) vs slices (`@WebMvcTest`, `@DataJpaTest`), **Testcontainers** for a real Postgres/RabbitMQ, `@MockBean` sparingly.

---

## 2. Hibernate / JPA

### Persistence context (L1 cache)
The `EntityManager`/`Session` is an identity map: one instance per entity ID per session. It enables **dirty checking** (on flush, Hibernate diffs the snapshot and issues UPDATEs — you don't call `save()` on a managed entity) and **write-behind** batching.

### Entity states
`transient` → `persist()` → `managed` → `detach()`/session close → `detached` → `merge()` → managed; `remove()` → `removed`.
Flush triggers: transaction commit, explicit `flush()`, or before a query that overlaps dirty state (`FlushMode.AUTO`).

### The N+1 problem (must be able to explain and fix)
Loading 100 orders then touching `order.getItems()` fires 1 + 100 queries. Fixes:
- `JOIN FETCH` in JPQL or `@EntityGraph` on the repository method.
- `@BatchSize(size = 50)` → fetches lazy collections in batches (`IN (…)`).
- DTO projections that select exactly the columns needed.
- Careful with **multiple bag fetches** → `MultipleBagFetchException` / Cartesian explosion; fetch one collection per query, or use `Set`, or split queries.
- Pagination + `JOIN FETCH` on a collection = in-memory pagination (`HHH000104` warning) — use two queries (IDs first, then fetch).

### Fetching
`LAZY` by default for `@OneToMany`/`@ManyToMany`; `EAGER` by default for `@ManyToOne`/`@OneToOne` — **override `@ManyToOne` to LAZY** in most apps. Lazy access outside a session = `LazyInitializationException`; the "fix" of Open-Session-In-View (`spring.jpa.open-in-view=true`, on by default) is an anti-pattern: it holds a DB connection for the whole request and hides N+1 into the view layer. Turn it off and fetch explicitly.

### Batching / bulk (relevant to ETL)
- `hibernate.jdbc.batch_size=50`, `order_inserts=true`, `order_updates=true`.
- **`GenerationType.IDENTITY` disables JDBC batching for inserts** (Hibernate needs the generated key per row) → use `SEQUENCE` with a pooled optimizer for bulk loads.
- Flush + `clear()` every N rows to keep the persistence context from growing unbounded (memory + slower dirty checking).
- `StatelessSession` or plain JDBC/`COPY` for genuinely large loads — the honest answer is "ORM for domain logic, raw bulk paths for ETL."

### Caching layers
L1 (session, always on), **L2** (shared across sessions — Ehcache/Infinispan/Redis; needs care with invalidation and clustering), query cache (only useful with a stable read set). Cache strategies: `READ_ONLY`, `NONSTRICT_READ_WRITE`, `READ_WRITE`, `TRANSACTIONAL`.

### Concurrency
- **Optimistic**: `@Version` column → `OptimisticLockException` on conflicting update; retry at the use-case level. Default choice for web apps.
- **Pessimistic**: `PESSIMISTIC_WRITE` (`SELECT … FOR UPDATE`) when contention is real and retries are expensive; keep the transaction tiny.

### Mapping pitfalls
Owning side vs `mappedBy`; `cascade` types (`PERSIST`, `MERGE`, `REMOVE`, `ALL`) and `orphanRemoval`; `equals`/`hashCode` on entities (use a business key or the ID with null-safety — never all fields, never a lazily-loaded association); `toString()` touching lazy associations causes surprise queries or exceptions; `@Enumerated(EnumType.STRING)` (never ORDINAL).

---

## 3. Node.js runtime

### Event loop phases
`timers` (setTimeout/Interval) → `pending callbacks` → `idle/prepare` → **`poll`** (I/O events; blocks here when idle) → `check` (`setImmediate`) → `close callbacks`. Between every phase (and between each callback) the **microtask queue** drains: `process.nextTick` first, then promise jobs.
Practical implications:
- **CPU-bound work blocks everything** — XML/JSON parsing of huge payloads, crypto, image work. Fix: stream it, chunk it with `setImmediate`, move to `worker_threads`, or offload to another service/Lambda.
- `libuv` thread pool (default **4**, `UV_THREADPOOL_SIZE`) serves fs, dns.lookup, crypto, zlib — a burst of fs work can queue behind itself.
- Single process = single core; scale with `cluster` or (better in containers) more replicas.

### Streams & backpressure (directly relevant to 1M XML records)
`readable.pipe(writable)` / `stream.pipeline()` honour backpressure via `highWaterMark` and the `drain` event: if you ignore backpressure (e.g. `for` loop pushing into a socket, or accumulating parsed records in an array), memory grows until the process is OOM-killed. For big files: SAX/streaming XML parser + a transform stream + batched writes, with concurrency bounded.

### Memory & GC
V8 heap default is capped (`--max-old-space-size`); containers should set it *below* the container memory limit or you get OOMKill (exit 137) instead of a graceful heap error. Watch for leaks: unbounded caches/Maps, event listener accumulation, closures retaining large buffers, and unresolved promises. Tools: `--inspect` heap snapshots, `clinic.js`, `process.memoryUsage()`.

### Async correctness
- `unhandledRejection`/`uncaughtException` → log and **exit** (let the orchestrator restart); trying to continue leaves undefined state.
- **`AsyncLocalStorage`** for request-scoped context (correlation/trace IDs across async boundaries) — the Node answer to MDC.
- Graceful shutdown: stop accepting new work on `SIGTERM`, drain in-flight requests/consumers (stop pulling from SQS/Rabbit, ack what's in hand), close DB pools, then exit — otherwise rolling deploys drop messages.
- Keep-alive HTTP agents and connection pools sized deliberately; without keep-alive you pay TLS handshakes per call.

---

## 4. NestJS

### Structure
Modules (`@Module` with `imports/providers/controllers/exports`) form a DI graph. Providers are **singletons by default**; `Scope.REQUEST` creates a new instance per request (convenient for request context, but it bubbles: any provider injecting a request-scoped one becomes request-scoped too, with real performance cost). `Scope.TRANSIENT` for per-injection instances.
`forRoot`/`forRootAsync` **dynamic modules** for configurable infrastructure modules; custom providers via `useClass`/`useValue`/`useFactory` + injection tokens for interfaces.

### Request lifecycle (order matters — commonly asked)
**Middleware → Guards → Interceptors (pre) → Pipes → Handler → Interceptors (post) → Exception filters**
- **Guards**: authn/authz decisions (`CanActivate`), have access to the execution context and metadata (`@Roles()` via `Reflector`).
- **Pipes**: validation & transformation (`ValidationPipe` with `class-validator`/`class-transformer`, or Zod) — `whitelist: true, forbidNonWhitelisted: true` to reject unknown fields; that's contract enforcement at the edge.
- **Interceptors**: cross-cutting before/after — logging, timing, response shaping, caching, `timeout()`, retry via RxJS.
- **Exception filters**: map exceptions to HTTP/RPC responses centrally.

### Microservices (your Kevit stack)
- Transports: TCP, Redis, **RabbitMQ**, Kafka, NATS, MQTT, gRPC.
- **`@MessagePattern`** = request/response (RPC over the broker; the client waits for a reply queue) vs **`@EventPattern`** = fire-and-forget event. Choosing `@MessagePattern` everywhere recreates synchronous coupling over a queue — a good thing to say you avoided.
- `ClientProxy` (`send()` returns an Observable for req/res; `emit()` for events); hybrid apps run HTTP + a microservice listener in one process.
- Manual acknowledgement mode with RabbitMQ (`noAck: false`) so a crash re-queues rather than loses; `prefetchCount` to bound in-flight work.
- Serialization/deserialization customization, and exception filters for RPC (`RpcException`).

### Other Nest pieces worth naming
`ConfigModule` with schema validation of env vars at boot (fail fast), `@nestjs/terminus` health checks for K8s probes, `@nestjs/schedule` cron, **BullMQ** (Redis) for job queues with retries/backoff, `CacheModule`, the CQRS module (commands/queries/events/sagas in-process), `Testing` module with `Test.createTestingModule` for DI-aware tests, and interceptors + `AsyncLocalStorage` for correlation IDs.

### Nest vs Express
Nest is a framework layered on Express (or Fastify adapter): it adds DI, modules, decorators, a lifecycle, and first-class testing. Express gives you a middleware chain and nothing else — fine for tiny services, painful for a 6-service monorepo where consistency and testability matter.

---

## 5. Express

- **Middleware chain**: `(req, res, next)` executed in registration order; error middleware has the 4-arg signature `(err, req, res, next)` and must be registered **last**.
- Routers for modularity, `app.use` mounting, path params/query parsing, `express.json()` body limits (set them — an unbounded body is a DoS vector).
- Common production concerns you'd add manually: `helmet`, CORS policy, rate limiting, request ID, structured logging (pino), async error wrapping (Express 4 doesn't catch async throws), validation (zod/joi), graceful shutdown.
- Why teams migrate to Nest/Fastify: structure and DI (Nest), or raw throughput and schema-based serialization (Fastify).

---

## Rapid-fire Q&A

**Q: `@Transactional` on a private method / called from within the same class — what happens?**
Nothing. Spring AOP proxies intercept external calls to public methods; self-invocation and private methods bypass the proxy, so no transaction is started. Fix by relocating the method to a collaborating bean.

**Q: How does Hibernate know to UPDATE when I never called save?**
Dirty checking: the persistence context keeps a loaded-state snapshot; at flush time it compares and generates UPDATEs for changed managed entities.

**Q: Node is single-threaded — how does it handle thousands of concurrent connections?**
Non-blocking I/O + event loop: connections wait in the kernel/libuv, callbacks run when data is ready. Concurrency ≠ parallelism; CPU-bound work still serializes, which is why you stream, batch, and use worker threads or more replicas.

**Q: Spring Boot vs NestJS — when would you pick which?**
Spring Boot for JVM-shop, heavy transactional/domain logic, mature ecosystem, strong typing at the data layer, and where thread-per-request + connection pools are fine. Nest for I/O-heavy services, teams already in TypeScript, fast iteration, and shared types with front-end in a monorepo. On your resume you have both — say you optimize for the team's operational familiarity rather than personal preference.

**Q: A Nest service consuming RabbitMQ crashes mid-message. What did you configure so nothing is lost?**
Durable queue + persistent messages + publisher confirms on the producer; manual ack on the consumer with `prefetchCount` bounded; a DLX with a retry queue for failures; graceful SIGTERM handling that stops consuming and finishes in-flight messages before exit.
