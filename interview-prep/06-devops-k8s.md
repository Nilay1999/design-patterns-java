# DevOps — Docker, Kubernetes/EKS, ArgoCD/GitOps, GitLab CI/CD, NX

---

## 1. Docker

### What a container actually is
Not a VM: a normal Linux process isolated by **namespaces** (pid, net, mnt, uts, ipc, user, cgroup) and limited by **cgroups** (cpu, memory, io, pids), with a layered root filesystem from an image. That's the answer to "how does Docker work under the hood."

### Images & layers
Each Dockerfile instruction creates a layer; layers are content-addressed and cached. Union filesystem (overlay2) stacks them read-only with a thin writable container layer on top (copy-on-write).
**Build practices that matter:**
- Order instructions **least- to most-frequently-changing**: base → package manifests → `install` → source. Copying source before installing dependencies busts the cache on every commit.
- **Multi-stage builds**: compile in a fat builder stage, copy only artifacts into a slim/distroless runtime image. Smaller image = faster pulls = faster scale-out and less CVE surface.
- `.dockerignore` (never ship `.git`, `node_modules` from host, secrets).
- Run as a **non-root** user; read-only root filesystem where possible.
- Pin base images by digest or specific tag; scan images (Trivy/Grype/GitLab container scanning).
- Never bake secrets into layers — they persist in history even if deleted in a later layer. Use build secrets or runtime injection.
- `ENTRYPOINT` (the executable) vs `CMD` (default args); use exec form so the process is PID 1 and receives `SIGTERM` (otherwise shell wrappers swallow signals and your graceful shutdown never runs — use `--init` or `tini` if you need reaping).
- Alpine/musl vs Debian slim: smaller, but musl can break native modules and cause DNS/perf oddities — a real tradeoff worth mentioning.

### Runtime concerns
- **Resource limits & runtimes**: the JVM and Node must be told about cgroup limits — JVM ≥ 10 honours container limits (`-XX:MaxRAMPercentage=75`); Node needs `--max-old-space-size` below the container limit. Exceeding the limit = **OOMKilled, exit 137**.
- Networking: bridge (default), host, none, overlay (swarm); published ports; embedded DNS by container name in user-defined networks.
- Storage: named **volumes** (managed, portable) vs bind mounts (host paths, dev-time) vs tmpfs (secrets in memory).
- `HEALTHCHECK` for orchestrators that use it (K8s uses its own probes).
- Logs to stdout/stderr — the container contract; let the platform ship them.

---

## 2. Kubernetes / EKS

### Architecture
**Control plane**: API server (the only thing that talks to etcd), **etcd** (state store), scheduler (pod → node placement), controller manager (reconciliation loops), cloud controller (LBs, volumes). **Nodes**: kubelet (runs pods, reports status), container runtime (containerd), kube-proxy (service routing via iptables/IPVS).
The mental model: **declarative desired state + reconciliation loops**. You never "run a container"; you declare intent and controllers converge.

### Workload objects
- **Pod** — one or more containers sharing network namespace and volumes; ephemeral.
- **Deployment** → ReplicaSet → Pods; rolling updates via `maxSurge`/`maxUnavailable`, `kubectl rollout undo`, revision history.
- **StatefulSet** — stable identities (`app-0`, `app-1`), ordered rollout, per-pod PVCs — for brokers/DBs, rarely for stateless services.
- **DaemonSet** — one pod per node (log/metric agents, CNI).
- **Job / CronJob** — batch runs, `backoffLimit`, `activeDeadlineSeconds`, concurrency policy. (Good fit for the ETL runs you'd otherwise cron.)

### Networking
- **Service** types: `ClusterIP` (internal VIP), `NodePort`, `LoadBalancer` (provisions an NLB/ALB on EKS), `ExternalName`, and **headless** (`clusterIP: None`, DNS returns pod IPs — used by StatefulSets and client-side LB/gRPC).
- **Ingress** + controller (AWS Load Balancer Controller creates an ALB; nginx-ingress is the portable choice) for host/path routing, TLS termination. **Gateway API** is the successor.
- kube-proxy programs iptables/IPVS rules; DNS via CoreDNS (`svc.namespace.svc.cluster.local`).
- **Network policies** for pod-to-pod firewalling (needs a CNI that supports it; EKS VPC CNI supports policies in recent versions, Calico is the common add-on).

### Config & secrets
`ConfigMap` for non-sensitive config, `Secret` (base64, **not encrypted at rest by default** — enable KMS envelope encryption on EKS). Better: **External Secrets Operator** or **Secrets Store CSI driver** pulling from AWS Secrets Manager/SSM, so secrets never live in Git. Mount as env or files; note that env vars don't hot-reload — file mounts do (with a delay).

### Scheduling & resources
- **requests** (used for scheduling and as the guaranteed floor) vs **limits** (hard ceiling; CPU is throttled, memory is OOMKilled).
- **QoS classes**: Guaranteed (requests == limits) > Burstable > BestEffort — determines eviction order under node pressure.
- Placement: `nodeSelector`, node/pod **affinity & anti-affinity** (spread replicas across AZs/nodes), **taints/tolerations** (dedicated node pools), **topologySpreadConstraints**, `PriorityClass` + preemption.
- **CPU limits caution**: aggressive CPU limits cause throttling and tail latency; many teams set requests and omit CPU limits (always set memory limits).

### Probes (get these right — a classic incident source)
- **liveness** — restart if failing. Point it at something cheap and local; never at a downstream dependency or you cascade a DB outage into a cluster-wide restart loop.
- **readiness** — remove from Service endpoints while failing (dependencies *can* be part of this).
- **startup** — protects slow starters (Spring Boot!) from liveness kills during boot.

### Scaling
- **HPA** on CPU/memory (metrics-server) or custom/external metrics (Prometheus Adapter, **KEDA** — scale on SQS queue depth or Rabbit backlog, including scale-to-zero). KEDA is the right answer for "how did your consumers scale with the queue?"
- **VPA** for right-sizing requests (don't run it with HPA on the same metric).
- Node scaling: **Cluster Autoscaler** (node groups) or **Karpenter** (provisions right-sized nodes directly, faster, consolidation for cost).

### Rollouts & availability
`maxSurge/maxUnavailable`, readiness gating the rollout, **PodDisruptionBudget** to bound voluntary disruptions (node drains/upgrades), `terminationGracePeriodSeconds` + `preStop` hook (short sleep) so endpoints deregister before SIGTERM — otherwise you drop in-flight requests on every deploy. Progressive delivery via Argo Rollouts/Flagger (canary, blue-green, analysis on metrics).

### EKS specifics (name these — they signal real EKS use)
- **IRSA (IAM Roles for Service Accounts)**: cluster OIDC provider + annotated ServiceAccount → pods get scoped AWS credentials, no node-wide IAM or static keys. (EKS Pod Identity is the newer alternative.)
- **VPC CNI**: pods get real VPC IPs — beware **IP exhaustion** in small subnets; prefix delegation increases pod density per node.
- Managed node groups vs self-managed vs **Fargate profiles** (per-pod isolation, no node ops, some limitations: no DaemonSets, no privileged, fixed sizes).
- Access management: legacy `aws-auth` ConfigMap vs newer **EKS access entries**.
- Cluster upgrades: control plane then nodes, one minor version at a time; check deprecated APIs (`kubectl` deprecation reports, Pluto) before upgrading.
- Add-ons: CoreDNS, kube-proxy, VPC CNI, EBS CSI driver (needed for PVCs since in-tree drivers were removed).

### Debugging playbook
| Symptom | Usual cause |
|---|---|
| `CrashLoopBackOff` | App exits on startup — check `kubectl logs --previous`, missing config/secret, failing liveness too early |
| `ImagePullBackOff` | Wrong tag/registry auth (ECR permissions on the node/IRSA) |
| `Pending` | Unschedulable: insufficient CPU/mem, no matching node selector/taint toleration, no free IPs, PVC unbound |
| `OOMKilled` (137) | Memory limit too low or a leak; runtime not honouring cgroup limits |
| `Evicted` | Node pressure (disk/memory); BestEffort pods first |
| 502/503 through Ingress | Readiness not gating, port mismatch, or target group draining behaviour |
Tools: `kubectl describe` (events!), `logs`, `exec`, `port-forward`, `top`, `get events --sort-by=.lastTimestamp`, `kubectl debug` ephemeral containers.

---

## 3. ArgoCD & GitOps

### GitOps principles
1. Declarative desired state, 2. versioned & immutable in Git, 3. **pulled automatically** by an agent in the cluster, 4. continuously reconciled (drift is corrected).
Key security win over push-based CI: **no cluster credentials in CI**; the cluster pulls. Rollback = `git revert`, and the audit trail is the Git history.

### ArgoCD mechanics
- **Application** CR: source (repo, path, targetRevision, Helm/Kustomize config) + destination (cluster, namespace) + **syncPolicy** (`automated: {prune: true, selfHeal: true}`).
- **Sync waves** (`argocd.argoproj.io/sync-wave`) order resources (CRDs → DB migration Job → app); **hooks** `PreSync`/`Sync`/`PostSync`/`SyncFail` (PreSync Job = run migrations before the new version rolls).
- **Health & diff**: built-in health assessments per resource kind (custom Lua for CRDs); `ignoreDifferences` for fields mutated by controllers/webhooks (otherwise permanent "OutOfSync").
- **App of Apps** and **ApplicationSets** (generators: list, git directory, cluster, matrix) to manage many services/environments without copy-paste.
- **Image promotion**: CI builds and pushes an image, then updates the tag/digest in the config repo (via MR or Argo CD Image Updater); Argo syncs. Keep **app code repo separate from config repo** so a config change doesn't rebuild and vice versa.
- **Secrets**: Sealed Secrets, SOPS+age/KMS, or External Secrets — never plaintext in Git.
- **Multi-env promotion**: directory or branch per environment, promotion = a PR moving a digest from staging to prod, with approvals.

**Argo CD vs Flux**: same philosophy; Argo has a strong UI/RBAC/app model, Flux is more controller-composable and lighter. Either is a valid answer; know why your team chose one.

---

## 4. GitLab CI/CD

### Pipeline anatomy
```yaml
stages: [build, test, scan, package, deploy]

variables:
  IMAGE: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

build:
  stage: build
  script: [ "npm ci", "npx nx affected -t build --base=$CI_MERGE_REQUEST_DIFF_BASE_SHA" ]
  cache:
    key: { files: [package-lock.json] }
    paths: [ .npm/, node_modules/ ]
  artifacts:
    paths: [ dist/ ]
    expire_in: 1 day

deploy:prod:
  stage: deploy
  needs: [package]
  environment: { name: production, url: https://app.example.com }
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
      when: manual
```
- **Runners**: shared vs project-specific; executors (docker, kubernetes, shell); `tags` route jobs to runners.
- **`rules`** (modern) replaces `only/except`: `if`, `changes` (monorepo path filters), `exists`, `when: manual/never/on_success`, `allow_failure`.
- **`needs`** turns stages into a DAG so independent jobs don't wait for a whole stage.
- **Artifacts** (pass build outputs downstream, expire them) vs **cache** (speed up repeat installs; keyed on lockfile). Confusing the two is a common mistake: artifacts are outputs, cache is a heuristic.
- **Environments + deployments** give you a deployment history, rollback button, and review apps per MR.
- Secrets: masked/protected CI variables, or better **OIDC federation to AWS** (`id_tokens` → `sts:AssumeRoleWithWebIdentity`) so no long-lived AWS keys live in GitLab.
- Building images: Docker-in-Docker (privileged, fast with cache) vs **Kaniko/Buildah** (no privileged daemon — preferred on shared/K8s runners).
- Built-in security jobs: SAST, dependency scanning, secret detection, container scanning, license compliance.
- Efficiency: cache keys per lockfile, `parallel: matrix` for test sharding, `interruptible: true` + auto-cancel redundant pipelines, fail fast on lint.

**CI vs CD split with Argo**: GitLab builds/tests/pushes the image and bumps the digest in the config repo; Argo CD does the actual cluster apply. Cleanest separation and the thing to say if asked how the two coexist on your resume.

---

## 5. NX monorepo

- **Project graph**: NX parses imports to build a dependency graph of apps/libs (`nx graph`).
- **`nx affected`**: given a base commit, runs targets only for projects impacted by the change — the reason a 6-service monorepo CI stays fast.
- **Computation caching**: task outputs are hashed (source + deps + config + env); a cache hit replays outputs instantly. Remote cache (Nx Cloud or self-hosted) shares hits across CI and developers — a genuinely impressive metric to quote if you have one ("CI dropped from ~18 min to ~6 min").
- **Task pipeline** (`dependsOn: ["^build"]`) so libraries build before dependents.
- **Boundaries**: tags + the `@nx/enforce-module-boundaries` lint rule stop `feature-a` importing `feature-b`'s internals — this is how you enforce **service/domain boundaries at compile time**, which pairs directly with your "drove technical direction on service boundaries" bullet.
- **Library taxonomy**: `feature` / `ui` / `data-access` / `util`, plus `shared` libs for DTOs and contracts — shared DTO libs are how a Nest monorepo keeps request/response and event contracts in sync across 6 services.
- Generators/executors for consistent scaffolding.

**Monorepo tradeoffs**: atomic cross-service changes, one dependency version, shared tooling and contracts — vs. coupled release cadence risk, bigger CI complexity, and access control being all-or-nothing. Answer the "why monorepo?" question with the contract-sharing argument.

---

## Rapid-fire Q&A

**Q: Deploying a new version drops requests. Why?**
Pods receive SIGTERM before being removed from the Service endpoints (endpoint propagation is asynchronous). Fix: `preStop` sleep (5–15s), readiness probe flipping to failed on shutdown, graceful shutdown in the app that stops accepting new work and drains in-flight, and `terminationGracePeriodSeconds` longer than the drain.

**Q: How do your pods get AWS permissions?**
IRSA: OIDC provider on the cluster, IAM role with a trust policy scoped to `system:serviceaccount:<ns>:<sa>`, annotate the ServiceAccount with the role ARN. Least privilege per workload, no static credentials, rotates automatically.

**Q: What's in your Deployment manifest that a junior would forget?**
Resource requests/limits, all three probes with sane thresholds, PDB, topology spread across AZs, `preStop` + grace period, securityContext (non-root, read-only FS, dropped capabilities), and a rollout strategy that keeps capacity during updates.

**Q: Argo shows OutOfSync but nothing changed.**
Something mutates the live object: a mutating webhook, HPA changing `replicas`, a defaulted field, or metadata added by a controller. Fix with `ignoreDifferences` on those fields (or remove `replicas` from Git when HPA owns it).

**Q: How would you roll back a bad release?**
Config repo revert (Argo syncs the previous digest) or `kubectl rollout undo` for immediacy, then revert in Git so the state matches — with GitOps, an out-of-band rollback that isn't reflected in Git will be reverted back by self-heal. And check whether the DB migration is backwards-compatible; if it isn't, the rollback plan has to include that (which is why expand/contract migrations matter).
