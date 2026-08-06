# Observability — Prometheus, Grafana, Datadog, CloudWatch, SLOs

Your resume claims **99.9% reliability**. That claim is only credible if you can describe how it was measured, alerted on, and defended.

---

## 1. Foundations

### Signals
- **Metrics** — cheap, aggregatable, low cardinality, great for alerting and trends.
- **Logs** — high detail, expensive at volume; structure them (JSON) so they're queryable.
- **Traces** — causal, per-request path across services; essential in event-driven systems.
- (**Profiles** — continuous CPU/memory profiling, increasingly standard.)

### Methods
- **RED** (per service/endpoint): **R**ate, **E**rrors, **D**uration — what your API dashboards should show.
- **USE** (per resource): **U**tilization, **S**aturation, **E**rrors — for CPU, memory, pools, queues.
- **Four golden signals**: latency, traffic, errors, saturation.

### OpenTelemetry
The vendor-neutral standard: SDKs (auto-instrumentation for Spring, Node), **Collector** (receive → process → export; batching, sampling, redaction), OTLP protocol, semantic conventions (`service.name`, `http.route`, `messaging.system`). Strategic answer to "how do you avoid vendor lock-in with Datadog?" — instrument with OTel, export to whatever backend.

### Correlation across async boundaries (crucial for your pipelines)
Propagate W3C `traceparent` and a business `correlationId` through **message attributes** (SQS message attributes, EventBridge `detail`, AMQP headers) — HTTP header propagation stops at the queue. Then a single trace shows API → EventBridge → SQS → Lambda → Aurora, and log lines carry the same ID. In Node use `AsyncLocalStorage`; in Spring use MDC + Micrometer Tracing.

---

## 2. Prometheus

### Model
**Pull-based**: Prometheus scrapes `/metrics` endpoints on an interval (typically 15–60s). Targets come from static config or **service discovery** (Kubernetes SD, EC2 SD). Push is only for short-lived batch jobs via **Pushgateway** (don't use it for services — it breaks target liveness semantics).

### Metric types
- **Counter** — monotonic; always query with `rate()`/`increase()`, never raw.
- **Gauge** — up/down (queue depth, in-flight requests, memory).
- **Histogram** — bucketed observations; `histogram_quantile(0.99, sum by (le) (rate(http_duration_seconds_bucket[5m])))`. Quantiles are computed **server-side** and are aggregatable across instances.
- **Summary** — client-computed quantiles; **not aggregatable** across instances — prefer histograms.

### Cardinality — the #1 operational failure
Every unique label-value combination is a separate time series. Putting `userId`, `orderId`, or a raw URL path in a label will kill your Prometheus. Rules: bounded label sets (`route` templates not raw paths, `status_code` classes), and use logs/traces (or exemplars) for high-cardinality detail.

### PromQL you should be able to write live
```promql
# request rate by route
sum by (route) (rate(http_requests_total[5m]))

# error ratio (SLI)
sum(rate(http_requests_total{status=~"5.."}[5m]))
  / sum(rate(http_requests_total[5m]))

# p99 latency
histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))

# queue backlog growth
deriv(sqs_approximate_number_of_messages_visible[10m]) > 0

# pods restarting
increase(kube_pod_container_status_restarts_total[15m]) > 3
```
Also know: `rate` vs `irate` vs `increase`, `sum/avg/max by/without`, `topk`, `offset`, `absent()` (alert on *missing* data), recording rules (precompute expensive queries), and `for:` in alert rules (sustained condition, not a blip).

### Alerting
Prometheus evaluates alert rules → fires to **Alertmanager**, which handles **grouping** (one notification for 50 related alerts), **inhibition** (suppress node-level alerts when a cluster alert is firing), **silences** (planned maintenance), and routing (team → Slack/PagerDuty by label).

### Storage & scale
Local TSDB (WAL + 2h blocks, compacted), retention by time/size. Long-term/HA/global view → **Thanos**, **Mimir**, or **Cortex** via remote write. Exporters: node_exporter, kube-state-metrics, postgres_exporter, rabbitmq_exporter, cloudwatch_exporter, blackbox_exporter (synthetic probes).

---

## 3. Grafana

- **Data sources**: Prometheus, Loki (logs), Tempo (traces), CloudWatch, Postgres, Datadog. Mixed-datasource panels let one dashboard tell a whole story.
- **Dashboards**: template **variables** (`$env`, `$service`, `$queue`) for reuse; rows per concern (traffic, errors, latency, saturation, dependencies, business KPIs); transformations to join/reshape series; annotations for deploys/incidents (correlating "latency doubled" with "deploy at 14:02" is half of incident response).
- **Provisioning as code** — dashboards and data sources in Git (JSON/Terraform/Grafonnet), not clicked into existence.
- **Unified alerting** in Grafana (multi-datasource rules, contact points, notification policies) vs alerting in Prometheus — pick one place, or you'll duplicate.
- **Loki**: index only labels, store the log body compressed — cheap, but label design matters as much as Prometheus cardinality. **Tempo**: trace storage with trace-ID lookup, and **exemplars** link a latency histogram bucket straight to a slow trace.

**Dashboard I'd describe for your pipeline**: ingest rate by source, validation reject rate, SQS visible + age of oldest message, DLQ depth, Lambda invocations/errors/throttles/duration p50–p99, Aurora connections/CPU/replica lag, and end-to-end freshness (event time → applied time p95).

---

## 4. Datadog

- **Agent architecture**: node agent runs integration checks, receives custom metrics via **DogStatsD** (UDP), runs the **trace agent** (APM) and the logs agent. In K8s it's a DaemonSet + Cluster Agent (cluster-level metadata, kube-state-metrics, HPA external metrics).
- **Unified service tagging**: `env`, `service`, `version` on metrics, traces and logs — this is what makes the correlation between the three work, and what makes deploy-based regression detection possible ("version:1.42 has 3× the error rate").
- **APM**: distributed traces, flame graphs, service map, span tags, trace-to-log correlation via `dd.trace_id` injection, retention filters + head/tail sampling (you can't afford 100% at volume).
- **Metrics cost model**: billed on **custom metrics** (unique metric+tag combos) — the same cardinality discipline as Prometheus, but with a direct invoice attached. Metrics without Limits™ / rollups control this.
- **Monitors**: threshold, anomaly, outlier (one host behaving differently), forecast (disk full in 3 days), composite; multi-alert by tag; recovery thresholds to stop flapping; downtimes for maintenance.
- **Logs pipeline**: ingest → processors (grok parsing, remapping, PII scrubbing) → **indexes** with retention and **exclusion filters** (index 100% of errors, sample the noisy info logs) — the standard way to cut log spend.
- **SLOs** as first-class objects (metric-based or monitor-based) with error-budget burn alerts.
- vs Prometheus/Grafana: Datadog is turnkey, correlated, expensive, and vendor-locked; Prom/Grafana is cheap in license, expensive in engineering time, and fully yours. Most orgs land on Datadog for APM + Prom for infra, or OTel in the middle to keep options open.

---

## 5. CloudWatch specifics

Covered in `01-aws-serverless.md`; the observability-relevant additions:
- **EMF** to emit structured metrics from Lambda logs without a separate metrics API call.
- **Logs Insights** for ad-hoc queries: `fields @timestamp, @message | filter status = "REJECTED" | stats count() by reason`.
- **Composite alarms** to fight alert fatigue.
- **Metric math** for SLIs: `errors / invocations`, `IF(rate > x, 1, 0)`.
- CloudWatch **Synthetics** canaries for external availability checks (a real SLI for user-facing APIs).
- Export logs to Datadog/OpenSearch via subscription filters if that's your central platform.

---

## 6. SLIs, SLOs, error budgets — how to defend "99.9%"

### Definitions
- **SLI** — a measured ratio: `good events / valid events`.
- **SLO** — the target (99.9% over 30 days).
- **Error budget** — 100% − SLO = permitted failure. 99.9% monthly ≈ **43.8 minutes** of downtime, or 0.1% of events.

### For your pipeline, the SLI is not "uptime"
Better SLIs for an ingestion pipeline:
- **Success ratio**: `records successfully persisted / records received` ≥ 99.9%.
- **Freshness**: p95 (or p99) of `applied_at − event_time` ≤ N minutes.
- **Completeness**: reconciliation — records in source file = persisted + rejected (no silent loss).

At 50,000 events/day, a 99.9% success SLO allows **~50 failed events/day** — and those must land in a DLQ with a reason, not vanish. Being able to say that number is what makes the resume claim believable.

### Burn-rate alerting (the mature answer)
Instead of "alert if error rate > 1%," alert on how fast you're consuming the error budget: fast burn (14.4× over 1h **and** 5m windows → page) and slow burn (6× over 6h, 3× over 24h → ticket). Multi-window/multi-burn-rate reduces both false pages and missed slow degradations.

### Alert design principles
Alert on **symptoms** (users/consumers affected), not causes (CPU 80%). Every page needs: an owner, a runbook link, and a plausible action. If an alert has fired 20 times with no action, delete it or turn it into a dashboard. Track alert-to-incident ratio.

### Pipeline-specific alerts to name
| Alert | Why |
|---|---|
| DLQ depth > 0 (sustained) | Any dead-lettered record is unprocessed business data |
| `ApproximateAgeOfOldestMessage` > SLA | Backlog turning into staleness (better than depth) |
| Lambda `Throttles` > 0 | Concurrency ceiling hit — downstream or account limit |
| Error ratio burn rate | SLO protection |
| **Freshness / heartbeat**: no successful record in N minutes | Catches the silent stall where errors are zero because nothing is flowing |
| Aurora connections near max, replica lag | The pipeline's usual first bottleneck |
| Step/saga runs stuck `IN_PROGRESS` past SLA | Orphaned workflows needing compensation |

---

## Rapid-fire Q&A

**Q: How did you measure 99.9%?**
"Per-record: successfully persisted / received, computed from counters we emitted per stage, evaluated over a rolling 30-day window with a CloudWatch metric-math alarm. Failures were visible as DLQ entries and rejects with reason codes, so the number was auditable rather than a feeling — and at ~50k events/day the budget was about 50 records/day."

**Q: Metrics, logs, or traces — which do you reach for first?**
Metrics tell me *that* something is wrong and how bad (which SLO, which service). Traces tell me *where* in the request path. Logs tell me *why* for that specific case. In that order — starting in logs at volume is how you burn an hour.

**Q: What's high cardinality and why do you care?**
Unique label combinations create separate time series. `userId` as a label turns one metric into millions — it OOMs Prometheus and directly inflates a Datadog bill. Keep identifiers in traces/logs (or exemplars), keep metrics dimensioned by bounded things like route, status class, queue, tenant tier.

**Q: A service is slow but no alerts fired. What was missing?**
Probably: latency measured as an average instead of p99, no per-endpoint breakdown, no saturation signal (pool exhaustion, queue age), and no client-side/synthetic view. The fix is RED per endpoint with histograms, saturation metrics on every pool, and an SLO with burn-rate alerting instead of a static threshold.
