# Kubernetes Troubleshooting SOP (Standard Operating Procedures)
This document contains a comprehensive, classified guide for diagnosing and resolving Kubernetes issues based on the K8sQuest simulator.
## 🔍 Table of Contents

### Kubernetes Basics & Pod Debugging (Levels 1-10)
- [Level 1: Fix the Crashing Pod](#level-1-fix-the-crashing-pod)- [Level 2: Fix the Deployment](#level-2-fix-the-deployment)- [Level 3: ImagePullBackOff Mystery](#level-3-imagepullbackoff-mystery)- [Level 4: Pending Pod Problem](#level-4-pending-pod-problem)- [Level 5: Lost Connection - Labels & Selectors](#level-5-lost-connection---labels--selectors)- [Level 6: Port Mismatch Mayhem](#level-6-port-mismatch-mayhem)- [Level 7: Sidecar Sabotage](#level-7-sidecar-sabotage)- [Level 8: Pod Logs Mystery](#level-8-pod-logs-mystery)- [Level 9: Init Container Gridlock](#level-9-init-container-gridlock)- [Level 10: Namespace Confusion](#level-10-namespace-confusion)
### Deployments, Rollouts & Pod Lifecycle (Levels 11-20)
- [Level 11: Deployment Update Stuck](#level-11-deployment-update-stuck)- [Level 12: The Restart Loop](#level-12-the-restart-loop)- [Level 13: Traffic to Unready Pods](#level-13-traffic-to-unready-pods)- [Level 14: HPA Can't Scale](#level-14-hpa-can't-scale)- [Level 15: Zero-Downtime Deployment Failure](#level-15-zero-downtime-deployment-failure)- [Level 16: PDB Blocks All Evictions](#level-16-pdb-blocks-all-evictions)- [Level 17: Blue-Green Deployment Gone Wrong](#level-17-blue-green-deployment-gone-wrong)- [Level 18: Canary Weight Imbalance](#level-18-canary-weight-imbalance)- [Level 19: Stateful App Data Loss](#level-19-stateful-app-data-loss)- [Level 20: ReplicaSet Without Deployment](#level-20-replicaset-without-deployment)
### Services, DNS & Networking (Levels 21-30)
- [Level 21: Level: 21 Debrief: Service Selector Mismatch](#level-21-level-21-debrief-service-selector-mismatch)- [Level 22: Level: 22 Debrief: NodePort Configuration](#level-22-level-22-debrief-nodeport-configuration)- [Level 23: Level: 23 Debrief: DNS Resolution in Kubernetes](#level-23-level-23-debrief-dns-resolution-in-kubernetes)- [Level 24: Level: 24: Ingress Path Mismatch - Mission Debrief](#level-24-level-24-ingress-path-mismatch---mission-debrief)- [Level 25: Level: 25: NetworkPolicy Too Restrictive - Mission Debrief](#level-25-level-25-networkpolicy-too-restrictive---mission-debrief)- [Level 26: Level: 26: Session Affinity Missing - Mission Debrief](#level-26-level-26-session-affinity-missing---mission-debrief)- [Level 27: Level: 27: Cross-namespace Service Communication - Mission Debrief](#level-27-level-27-cross-namespace-service-communication---mission-debrief)- [Level 28: Level: 28 Debrief: Service Endpoints & Readiness Probes](#level-28-level-28-debrief-service-endpoints--readiness-probes)- [Level 29: Level: 29 Debrief: LoadBalancer vs NodePort Service Types](#level-29-level-29-debrief-loadbalancer-vs-nodeport-service-types)- [Level 30: Level: 30 Debrief: Headless Services & StatefulSet DNS](#level-30-level-30-debrief-headless-services--statefulset-dns)
### Volumes, Storage & Configuration (Levels 31-40)
- [Level 31: Level: 31 Debrief: PersistentVolumes & PersistentVolumeClaims](#level-31-level-31-debrief-persistentvolumes--persistentvolumeclaims)- [Level 32: LEVEL 32 DEBRIEF: Volume Mount Path Configuration](#level-32-level-32-debrief-volume-mount-path-configuration)- [Level 33: LEVEL 33 DEBRIEF: PV/PVC Access Modes](#level-33-level-33-debrief-pvpvc-access-modes)- [Level 34: LEVEL 34 DEBRIEF: StatefulSet Volume Templates](#level-34-level-34-debrief-statefulset-volume-templates)- [Level 35: LEVEL 35 DEBRIEF: StorageClass Configuration](#level-35-level-35-debrief-storageclass-configuration)- [Level 36: LEVEL 36 DEBRIEF: ConfigMap Key Management](#level-36-level-36-debrief-configmap-key-management)- [Level 37: LEVEL 37 DEBRIEF: Base64 Encoding ≠ Encryption](#level-37-level-37-debrief-base64-encoding-≠-encryption)- [Level 38: LEVEL 38 DEBRIEF: Volume Permissions & fsGroup](#level-38-level-38-debrief-volume-permissions--fsgroup)- [Level 39: LEVEL 39 DEBRIEF: PV Reclaim Policies](#level-39-level-39-debrief-pv-reclaim-policies)- [Level 40: LEVEL 40 DEBRIEF: emptyDir vs PersistentVolumeClaim](#level-40-level-40-debrief-emptydir-vs-persistentvolumeclaim)
### Security, RBAC, Scheduling & Cluster Policies (Levels 41-50)
- [Level 41: LEVEL 41 DEBRIEF: Kubernetes RBAC (Role-Based Access Control)](#level-41-level-41-debrief-kubernetes-rbac-role-based-access-control)- [Level 42: LEVEL 42 DEBRIEF: Container SecurityContext & Privilege Escalation](#level-42-level-42-debrief-container-securitycontext--privilege-escalation)- [Level 43: LEVEL 43 DEBRIEF: Kubernetes ResourceQuota & Resource Management](#level-43-level-43-debrief-kubernetes-resourcequota--resource-management)- [Level 44: LEVEL 44 DEBRIEF: Kubernetes NetworkPolicy](#level-44-level-44-debrief-kubernetes-networkpolicy)- [Level 45: LEVEL 45 DEBRIEF: Node Affinity & Advanced Scheduling](#level-45-level-45-debrief-node-affinity--advanced-scheduling)- [Level 46: LEVEL 46 DEBRIEF: Taints & Tolerations](#level-46-level-46-debrief-taints--tolerations)- [Level 47: LEVEL 47 DEBRIEF: PodDisruptionBudget](#level-47-level-47-debrief-poddisruptionbudget)- [Level 48: LEVEL 48 DEBRIEF: Pod Security Standards](#level-48-level-48-debrief-pod-security-standards)- [Level 49: LEVEL 49 DEBRIEF: PriorityClass](#level-49-level-49-debrief-priorityclass)- [Level 50: LEVEL 50 DEBRIEF: CHAOS FINALE - The Perfect Storm](#level-50-level-50-debrief-chaos-finale---the-perfect-storm)
================================================================================
## 📂 Category: Kubernetes Basics & Pod Debugging (Levels 1-10)

### 🚀 Level 1: Fix the Crashing Pod

**Symptom & Root Cause:**
Your pod was crashing because it tried to run a command called `nginxzz` - but that command doesn't exist in the nginx container image.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check pod status
kubectl get pod <name> -n <namespace>

# See detailed events and state
kubectl describe pod <name> -n <namespace>

# View logs (even from crashed containers)
kubectl logs <name> -n <namespace>
kubectl logs <name> -n <namespace> --previous

# Delete and recreate
kubectl delete pod <name> -n <namespace>
kubectl apply -f <file>.yaml

# Edit on-the-fly (limited fields)
kubectl edit pod <name> -n <namespace>
```

**Prevention & Best Practices:**
### Key Concepts:

1. **Pods are ephemeral**
   - You can't edit most fields of a running pod
   - When you need changes, delete and recreate
   - This is why Deployments exist (they manage this for you)

2. **Container images define what CAN run**
   - The nginx image has: nginx, bash, sh, etc.
   - It doesn't have: nginxzz
   - The `command` field overrides the image's default command

----------------------------------------

### 🚀 Level 2: Fix the Deployment

**Symptom & Root Cause:**
Your deployment was configured with `replicas: 0`, which tells Kubernetes: "I want ZERO instances of this application running."
This is technically valid configuration - just not useful for serving traffic!

**Diagnostic & Troubleshooting Steps:**
```bash
# View deployment status
kubectl get deployment <name> -n <namespace>
kubectl get deployment <name> -n <namespace> -o wide

# See detailed events
kubectl describe deployment <name> -n <namespace>

# Scale imperatively
kubectl scale deployment <name> --replicas=N -n <namespace>

# Edit declaratively
kubectl edit deployment <name> -n <namespace>

# Watch rollout status
kubectl rollout status deployment/<name> -n <namespace>

# See the ReplicaSets created by deployment
kubectl get rs -n <namespace>

# See the pods managed by deployment
kubectl get pods -l app=<label> -n <namespace>
```

**Prevention & Best Practices:**
### Key Concepts:

1. **Deployments manage ReplicaSets**
   - Deployment = desired state (how many pods, which image, etc.)
   - ReplicaSet = ensures that many pods exist
   - Pods = actual running containers

2. **Replicas = High Availability**
   - `replicas: 0` = nothing running (maintenance mode)
   - `replicas: 1` = one pod (no redundancy)
   - `replicas: 3` = three pods (survives failures)

----------------------------------------

### 🚀 Level 3: ImagePullBackOff Mystery

**Symptom & Root Cause:**
Your pod was stuck in `ImagePullBackOff` status because Kubernetes couldn't pull the container image `nginx:nonexistent-tag-xyz-123` from Docker Hub. This tag doesn't exist, so the kubelet kept trying and backing off between attempts.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check pod status
kubectl get pod <name> -n <namespace>

# See detailed events (this is your best friend!)
kubectl describe pod <name> -n <namespace>

# Check the exact image being used
kubectl get pod <name> -n <namespace> -o yaml | grep image:

# Delete and recreate a pod
kubectl delete pod <name> -n <namespace>
kubectl apply -f <file>.yaml
```

**Prevention & Best Practices:**
**Container images** are like blueprints for your application. They consist of:
- **Repository**: Where the image lives (e.g., `nginx`, `mysql`, `myapp`)
- **Tag**: A specific version (e.g., `latest`, `1.21`, `v2.0.3`)
- **Full reference**: `repository:tag` (e.g., `nginx:1.21`)

**Image pull process**:
```
kubectl apply → Scheduler assigns node → Kubelet pulls image → Creates container → Pod runs
                                             ↑
                                        You were stuck here!
```

Common mistakes:
- Typos in image names
- Non-existent tags
- Private images without pull secrets
- Wrong registry URLs
1. **Use specific tags**, not `latest` in production
2. **Implement image scanning** in CI/CD to verify images exist
3. **Set up alerts** for ImagePullBackOff events
4. **Use admission controllers** to validate image references before deployment
5. **Keep a local registry mirror** for critical images
When you create a pod, Kubernetes goes through several phases:

----------------------------------------

### 🚀 Level 4: Pending Pod Problem

**Symptom & Root Cause:**
Your pod was stuck in `Pending` status because it requested 999 CPUs and 999Gi of memory—far more than any node in your cluster can provide. The Kubernetes scheduler couldn't find a node with enough resources, so the pod never started.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check pod status
kubectl get pod <name> -n <namespace>

# See why pod isn't scheduling (Events are key!)
kubectl describe pod <name> -n <namespace>

# Check resource requests/limits
kubectl get pod <name> -n <namespace> -o yaml | grep -A 6 resources:

# See node capacity and allocatable resources
kubectl describe nodes

# Check actual resource usage (requires metrics-server)
kubectl top pod <name> -n <namespace>
kubectl top nodes

# See all cluster events sorted by time
kubectl get events --sort-by='.lastTimestamp' -n <namespace>
```

**Prevention & Best Practices:**
**Resource Requests vs Limits**:

- **Requests**: Guaranteed minimum resources (used for scheduling)
- **Limits**: Maximum resources allowed (enforced at runtime)

```yaml
resources:
  requests:      # "I need at least this much"
    memory: "64Mi"
    cpu: "100m"
  limits:        # "Don't let me use more than this"
    memory: "128Mi"
    cpu: "200m"
```

----------------------------------------

### 🚀 Level 5: Lost Connection - Labels & Selectors

**Symptom & Root Cause:**
Your Service had a selector for `app: frontend`, but your Pod had the label `app: backend`. Since the labels didn't match, the Service couldn't find the Pod and had no endpoints. Without endpoints, traffic sent to the Service had nowhere to go.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check service and its selector
kubectl get service <name> -n <namespace>
kubectl describe service <name> -n <namespace>
kubectl get service <name> -n <namespace> -o yaml | grep -A 5 selector

# Check endpoints (the IPs service routes to)
kubectl get endpoints <name> -n <namespace>
kubectl describe endpoints <name> -n <namespace>

# View pod labels
kubectl get pods --show-labels -n <namespace>
kubectl get pod <name> -n <namespace> --show-labels

# Find pods matching a selector
kubectl get pods --selector=app=backend -n <namespace>
kubectl get pods -l app=backend,tier=api -n <namespace>

# Add/modify labels on running pods
kubectl label pod <name> app=frontend -n <namespace>
kubectl label pod <name> app=backend --overwrite -n <namespace>

# Delete resources
kubectl delete -f <file>.yaml
```

**Prevention & Best Practices:**
**Labels** are key-value pairs attached to Kubernetes objects:

```yaml
metadata:
  labels:
    app: backend
    tier: api
    environment: prod
    version: v2
```

**Selectors** are queries that filter objects by labels:

----------------------------------------

### 🚀 Level 6: Port Mismatch Mayhem

**Symptom & Root Cause:**
Your Service was forwarding traffic to port 8080 on the container, but the NGINX container actually listens on port 80. Result: Every request hit a closed port and failed with "connection refused."

**Diagnostic & Troubleshooting Steps:**
```bash
# Check container ports
kubectl get pod <name> -n <namespace> -o yaml | grep -A 2 ports:
kubectl describe pod <name> -n <namespace> | grep Port

# Check service ports
kubectl get service <name> -n <namespace>
kubectl describe service <name> -n <namespace>
kubectl get service <name> -n <namespace> -o yaml | grep -A 3 ports:

# Test connectivity directly
kubectl port-forward pod/<name> 8080:80 -n <namespace>
kubectl port-forward service/<name> 8080:80 -n <namespace>

# Execute commands in container to test
kubectl exec -it <pod-name> -n <namespace> -- curl localhost:80
kubectl exec -it <pod-name> -n <namespace> -- netstat -tlnp
```

**Prevention & Best Practices:**
**Three port concepts**:

```yaml
apiVersion: v1
kind: Service
spec:
  ports:
  - port: 80          # External: what clients connect to
    targetPort: 8080  # Internal: what port on the Pod
    nodePort: 30080   # (Optional) Port on node for NodePort services
```

**Matching ports**:
```yaml
# Container listens on port 8080
containerPort: 8080

----------------------------------------

### 🚀 Level 7: Sidecar Sabotage

**Symptom & Root Cause:**
Your pod had two containers: a main app and a log-sidecar. The sidecar tried to `tail -f` a file that didn't exist, causing it to crash immediately. In Kubernetes, **if any container in a pod crashes, the entire pod is considered unhealthy**.

**Diagnostic & Troubleshooting Steps:**
```bash
# View all containers in a pod
kubectl get pod <name> -n <namespace> -o jsonpath='{.spec.containers[*].name}'

# Check ready status (shows X/Y containers ready)
kubectl get pod <name> -n <namespace>

# See status of each container
kubectl describe pod <name> -n <namespace>

# View logs from specific container
kubectl logs <pod-name> -c <container-name> -n <namespace>

# View logs with previous container instance (if crashed)
kubectl logs <pod-name> -c <container-name> --previous -n <namespace>

# Follow logs in real-time
kubectl logs <pod-name> -c <container-name> -f -n <namespace>

# Execute command in specific container
kubectl exec <pod-name> -c <container-name> -it -n <namespace> -- sh

# Stream logs from all containers
kubectl logs <pod-name> --all-containers=true -f -n <namespace>
```

**Prevention & Best Practices:**
**Why multi-container pods?**

Common patterns:

| Pattern | Main Container | Sidecar Container | Use Case |
|---------|----------------|-------------------|----------|
| **Sidecar** | Web app | Log forwarder | Ship logs to Elasticsearch |
| **Ambassador** | App | Proxy | Connect to external services |
| **Adapter** | Legacy app | Format converter | Convert logs to standard format |

----------------------------------------

### 🚀 Level 8: Pod Logs Mystery

**Symptom & Root Cause:**
The PostgreSQL container needed the `POSTGRES_PASSWORD` environment variable to initialize, but it wasn't provided. The container started, failed immediately, restarted, and repeated—entering CrashLoopBackOff. The only way to discover this was by checking the logs.

**Diagnostic & Troubleshooting Steps:**
```bash
# View current logs
kubectl logs <pod> -n <namespace>

# View previous container logs (after crash)
kubectl logs <pod> --previous -n <namespace>

# Follow logs in real-time
kubectl logs <pod> -f -n <namespace>

# Specific container in multi-container pod
kubectl logs <pod> -c <container> -n <namespace>

# Last N lines
kubectl logs <pod> --tail=50 -n <namespace>

# Logs since timestamp
kubectl logs <pod> --since=1h -n <namespace>
```

**Prevention & Best Practices:**
**Logs are your debugging superpower**. Not all failures are visible in `kubectl describe`. Some applications:
- Start successfully (so the pod shows "Running")
- Fail due to configuration errors
- Exit immediately
- Restart and repeat

**Log locations in Kubernetes**:
- Container logs: Captured from stdout/stderr
- Access via: `kubectl logs`
- Stored temporarily on node
- Rotated when they get too large

----------------------------------------

### 🚀 Level 9: Init Container Gridlock

**Symptom & Root Cause:**
Your init container was waiting for a service that doesn't exist. Init containers must complete before main containers start, so your pod was stuck in "Init:0/1" status forever.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check init container status
kubectl get pod <name> -n <namespace>
# Look for "Init:0/1" or "Init:Error"

# View init container logs
kubectl logs <pod> -c <init-container-name> -n <namespace>

# See init container details
kubectl describe pod <name> -n <namespace>
# Look at "Init Containers:" section
```

**Prevention & Best Practices:**
**Lifecycle**:
```
Init Container 1 → Init Container 2 → Main Container 1 & Main Container 2
  (sequential)       (sequential)          (parallel)
```

**Common use cases**:
- Wait for dependencies (databases, services)
- Clone git repositories
- Generate configuration files
- Set up permissions
- Database schema migrations
**Init containers** run before app containers and must complete successfully:

1. Init containers run sequentially (one after another)
2. Each must exit with status 0 (success)
3. Only after ALL init containers complete do app containers start
4. If init container fails, pod restarts (subject to restartPolicy)

----------------------------------------

### 🚀 Level 10: Namespace Confusion

**Symptom & Root Cause:**
Your resources were deployed to the "default" namespace instead of "k8squest". Namespaces provide isolation—resources in different namespaces can't easily find each other.

**Diagnostic & Troubleshooting Steps:**
```bash
# List all namespaces
kubectl get namespaces

# View resources in specific namespace
kubectl get all -n <namespace>

# View resources in all namespaces
kubectl get pods --all-namespaces
kubectl get pods -A

# Create namespace
kubectl create namespace <name>

# Set default namespace for context
kubectl config set-context --current --namespace=<namespace>

# Delete namespace (careful!)
kubectl delete namespace <namespace>
```

**Prevention & Best Practices:**
**Namespace isolation**:
```
Cluster
├── default namespace
│   ├── pod: app-1
│   └── service: api
├── k8squest namespace
│   ├── pod: client-app
│   └── service: backend-service
└── production namespace
    ├── pod: payment-processor
    └── service: payment-api
```

**DNS resolution**:
- Same namespace: `service-name`
- Cross-namespace: `service-name.namespace-name.svc.cluster.local`
**Namespaces** are virtual clusters within a physical cluster:

- Provide scope for names (can have "web" pod in multiple namespaces)
- Enable resource quotas and limits per namespace
- Provide access control boundaries (RBAC per namespace)
- Services can communicate within namespace easily
- Cross-namespace communication requires fully qualified DNS

----------------------------------------


## 📂 Category: Deployments, Rollouts & Pod Lifecycle (Levels 11-20)

### 🚀 Level 11: Deployment Update Stuck

**Symptom & Root Cause:**
Your deployment tried to roll out a new version with image `nginx:nonexistent-v2.0-xyz` that doesn't exist in Docker Hub. The deployment got stuck with some pods on the old working version and some failing to start with the new broken version.
Kubernetes' RollingUpdate strategy protected you from total downtime by keeping old pods running while new ones failed.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check deployment status
kubectl get deployment <name> -n <namespace>
kubectl describe deployment <name> -n <namespace>

# Check rollout status (shows if stuck/progressing/complete)
kubectl rollout status deployment/<name> -n <namespace>

# View rollout history
kubectl rollout history deployment/<name> -n <namespace>

# View specific revision details
kubectl rollout history deployment/<name> --revision=2 -n <namespace>

# Rollback to previous version (MOST IMPORTANT!)
kubectl rollout undo deployment/<name> -n <namespace>

# Rollback to specific revision
kubectl rollout undo deployment/<name> --to-revision=3 -n <namespace>

# Pause a rollout (stop updates mid-rollout)
kubectl rollout pause deployment/<name> -n <namespace>

# Resume a paused rollout
kubectl rollout resume deployment/<name> -n <namespace>

# Restart deployment (rolling restart with same image)
kubectl rollout restart deployment/<name> -n <namespace>

# See all ReplicaSets (old ones are kept for rollback!)
kubectl get replicasets -n <namespace>
```

**Prevention & Best Practices:**
**How Deployments manage ReplicaSets**:

```
Deployment: web-app
├── ReplicaSet-abc123 (old version, replicas: 3)
│   ├── Pod-1 (Running) ✅
│   ├── Pod-2 (Running) ✅
│   └── Pod-3 (Running) ✅
└── ReplicaSet-xyz789 (new version, replicas: 0 → trying to become 3)
    ├── Pod-4 (ImagePullBackOff) ❌
    ├── Pod-5 (ImagePullBackOff) ❌
    └── Pod-6 (Not created yet)
```

**RollingUpdate parameters**:
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1        # Max extra pods during update (1 = 4 total pods max)
    maxUnavailable: 0  # Max unavailable pods (0 = always keep 3 running)
```

----------------------------------------

### 🚀 Level 12: The Restart Loop

**Symptom & Root Cause:**
Your pods were stuck in a restart loop because the liveness probe was checking endpoint `/nonexistent-healthz` which returned 404 (Not Found). Kubernetes interpreted this as "pod is unhealthy" and kept restarting it, which of course failed the health check again immediately.
This is a classic configuration error that can cause cascading failures in production.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check restart counts
kubectl get pods -n <namespace>
# Look at RESTARTS column

# Describe pod to see liveness probe failures
kubectl describe pod <name> -n <namespace>
# Look at Events: "Liveness probe failed"

# Check deployment probe configuration
kubectl get deployment <name> -n <namespace> -o yaml | grep -A 20 livenessProbe

# Edit deployment (fix probe config)
kubectl edit deployment <name> -n <namespace>

# Check probe results in real-time
kubectl get events -n <namespace> --watch

# View container logs (might show probe requests)
kubectl logs <pod> -n <namespace>
```

**Prevention & Best Practices:**
**Liveness vs Readiness Probes**:

| Probe Type | Purpose | Action on Failure | Use Case |
|------------|---------|-------------------|----------|
| **Liveness** | Is container alive? | Restart container | Detect deadlocks, infinite loops |
| **Readiness** | Is container ready for traffic? | Remove from service | Slow startup, dependencies not ready |

**Liveness Probe Types**:

----------------------------------------

### 🚀 Level 13: Traffic to Unready Pods

**Symptom & Root Cause:**
Your pods were receiving traffic **before they were ready to handle it**, causing 502 Bad Gateway errors for users.
The root cause: **No readiness probe configured**.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check pod readiness status
kubectl get pods -n <namespace>
# Look at READY column: "0/1" = not ready, "1/1" = ready

# Check which pods are receiving traffic
kubectl get endpoints <service-name> -n <namespace>
# Shows IP addresses of READY pods

# Describe pod to see readiness probe status
kubectl describe pod <name> -n <namespace>
# Look for "Readiness" in Conditions section

# Check deployment readiness configuration
kubectl get deployment <name> -n <namespace> -o yaml | grep -A 10 readinessProbe

# Watch pods become ready in real-time
kubectl get pods -n <namespace> -l app=<label> -w

# Edit deployment to add readiness probe
kubectl edit deployment <name> -n <namespace>

# Check deployment rollout status
kubectl rollout status deployment/<name> -n <namespace>
```

**Prevention & Best Practices:**
### Liveness vs Readiness: The Critical Difference

| Aspect | Liveness Probe | Readiness Probe |
|--------|---------------|-----------------|
| **Question** | "Is the container alive?" | "Is the container ready for traffic?" |
| **Action on failure** | **Restart** the container | **Remove** from Service endpoints |
| **Use case** | Detect deadlocks, infinite loops | Prevent traffic during startup/overload |
| **Failure is** | Fatal (needs restart) | Temporary (will recover) |
| **Example** | Process crashed | Database connection not ready |

### When to Use Each Probe

----------------------------------------

### 🚀 Level 14: HPA Can't Scale

**Symptom & Root Cause:**
Your HorizontalPodAutoscaler (HPA) was configured correctly, but it couldn't scale because **metrics-server was not installed**.
Without metrics-server, Kubernetes has no way to know the CPU/memory usage of pods, so HPA can't make scaling decisions.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check HPA status
kubectl get hpa -n <namespace>
# Look at TARGETS - should show "X%/50%", not "<unknown>/50%"

# Describe HPA (see detailed status)
kubectl describe hpa <name> -n <namespace>

# Check if metrics-server is installed
kubectl get deployment metrics-server -n kube-system

# Install metrics-server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# For local clusters, add insecure TLS flag
kubectl patch deployment metrics-server -n kube-system --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'

# Wait for metrics-server to be ready
kubectl wait --for=condition=available --timeout=60s deployment/metrics-server -n kube-system

# Test if metrics work
kubectl top nodes           # Node CPU/memory
kubectl top pods -n <ns>    # Pod CPU/memory

# Watch HPA scale in real-time
kubectl get hpa -n <namespace> -w

# Generate load to trigger scaling (testing)
kubectl run -it --rm load-generator --image=busybox --restart=Never -- /bin/sh -c "while true; do wget -q -O- http://service-name; done"
```

**Prevention & Best Practices:**
### Kubernetes Metrics Architecture

```
┌─────────────────────────────────────────────────┐
│                  kubectl top                     │
│                      HPA                         │
│            Dashboard / Monitoring                │
└────────────────────┬────────────────────────────┘
                     │ Query metrics
                     ↓
         ┌───────────────────────┐
         │    Metrics API        │
         │ (metrics.k8s.io/v1)   │
         └──────────┬────────────┘
                    │ Implemented by
                    ↓
         ┌───────────────────────┐
         │   metrics-server      │
         │  (kube-system ns)     │
         └──────────┬────────────┘
                    │ Scrapes metrics
                    ↓
    ┌───────────────────────────────────┐
    │  kubelet on each node             │
    │  (cAdvisor provides container     │
    │   CPU/memory stats)                │
    └───────────────────────────────────┘
```

### HPA Scaling Logic

----------------------------------------

### 🚀 Level 15: Zero-Downtime Deployment Failure

**Symptom & Root Cause:**
Your deployment was configured with `maxUnavailable: 100%` and `maxSurge: 0`, which allowed Kubernetes to **terminate all pods simultaneously** during a rolling update.
This caused complete service outage every time you deployed a new version!

**Diagnostic & Troubleshooting Steps:**
```bash
# Check deployment strategy
kubectl get deployment <name> -n <namespace> -o yaml | grep -A 5 strategy

# Edit deployment strategy
kubectl edit deployment <name> -n <namespace>

# Watch rollout in progress
kubectl rollout status deployment/<name> -n <namespace>

# Watch pods during rollout
kubectl get pods -n <namespace> -l app=<label> -w

# Trigger a rollout (for testing)
kubectl set image deployment/<name> container=new-image:tag -n <namespace>

# Pause a problematic rollout
kubectl rollout pause deployment/<name> -n <namespace>

# Resume after fixing
kubectl rollout resume deployment/<name> -n <namespace>

# Rollback if deployment went bad
kubectl rollout undo deployment/<name> -n <namespace>

# Check rollout history
kubectl rollout history deployment/<name> -n <namespace>
```

**Prevention & Best Practices:**
### RollingUpdate Parameters Explained

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 1  # or "25%" - Max pods that can be down
    maxSurge: 1        # or "25%" - Max extra pods during rollout
```

**maxUnavailable**: Maximum number of pods that can be unavailable during the update.

----------------------------------------

### 🚀 Level 16: PDB Blocks All Evictions

**Symptom & Root Cause:**
Your PodDisruptionBudget (PDB) was configured with `minAvailable: 3` for a deployment with 3 replicas.
This created an impossible requirement: "Keep all 3 pods available while trying to evict one" - mathematically impossible!

**Diagnostic & Troubleshooting Steps:**
```bash
# List all PDBs
kubectl get pdb --all-namespaces

# Check PDB status
kubectl get pdb <name> -n <namespace>
# Look at ALLOWED column

# Describe PDB (see detailed status)
kubectl describe pdb <name> -n <namespace>

# Check if PDB allows any disruptions
kubectl get pdb <name> -n <namespace> -o jsonpath='{.status.disruptionsAllowed}'

# Edit PDB
kubectl edit pdb <name> -n <namespace>

# Test node drain (dry-run)
kubectl drain <node-name> --dry-run=server --ignore-daemonsets

# Actually drain a node
kubectl drain <node-name> --ignore-daemonsets --delete-emptydir-data

# If stuck, see which pods block drain
kubectl drain <node-name> --dry-run=server

# View all PDBs and their disruption allowance
kubectl get pdb --all-namespaces -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,MIN_AVAILABLE:.spec.minAvailable,MAX_UNAVAILABLE:.spec.maxUnavailable,ALLOWED:.status.disruptionsAllowed
```

**Prevention & Best Practices:**
### PodDisruptionBudget Purpose

**PDBs protect applications from voluntary disruptions**:

| Voluntary Disruptions (PDB applies) | Involuntary Disruptions (PDB doesn't apply) |
|-------------------------------------|---------------------------------------------|
| `kubectl drain` | Node crash |
| Cluster upgrades | Node hardware failure |
| Autoscaler scale-down | Pod killed by OOMKiller |
| Node decommissioning | Network partition |

----------------------------------------

### 🚀 Level 17: Blue-Green Deployment Gone Wrong

**Symptom & Root Cause:**
You deployed a new version of your application (GREEN) using a blue-green deployment strategy, but users were still seeing the old version (BLUE).
The root cause: **The service selector wasn't updated to point to the new deployment**.

**Diagnostic & Troubleshooting Steps:**
```bash
# Check service selector
kubectl get service <name> -n <namespace> -o yaml | grep -A 5 selector

# Check which pods match the service
kubectl get endpoints <service-name> -n <namespace>

# Patch service selector (atomic update)
kubectl patch service <name> -n <namespace> -p '{"spec":{"selector":{"version":"green"}}}'

# Edit service (manual)
kubectl edit service <name> -n <namespace>

# Test which version is serving traffic
kubectl run -it --rm test --image=busybox --restart=Never -n <namespace> -- wget -q -O- <service-name>

# Get pod IPs and their labels
kubectl get pods -n <namespace> -o wide -L version

# Check pod labels
kubectl get pods -n <namespace> --show-labels

# Port-forward to specific pod (test green directly)
kubectl port-forward deployment/app-green 8080:8080 -n <namespace>
```

**Prevention & Best Practices:**
### Blue-Green Deployment Strategy

**Concept**: Run two identical production environments (Blue and Green), switch traffic instantly by updating service selector.

```
┌─────────────────────────────────────────────┐
│         STEP 1: Initial State               │
│  ┌──────────────┐        ┌──────────────┐   │
│  │   Service    │───────▶│  Blue Pods   │   │
│  │ (selector:   │        │ (v1.0)       │   │
│  │ version=blue)│        │ 3 replicas   │   │
│  └──────────────┘        └──────────────┘   │
│                                              │
│  Users → Service → Blue (v1.0)               │
└─────────────────────────────────────────────┘

----------------------------------------

### 🚀 Level 18: Canary Weight Imbalance

**Symptom & Root Cause:**
Your canary deployment had a 50/50 traffic split (5 stable pods, 5 canary pods) instead of the intended 90/10 split.
This means half your users were exposed to the new, untested canary version - defeating the entire purpose of canary deployments!

**Diagnostic & Troubleshooting Steps:**
```bash
# Scale deployments for canary
kubectl scale deployment app-stable --replicas=9 -n <namespace>
kubectl scale deployment app-canary --replicas=1 -n <namespace>

# Check replica counts
kubectl get deployments -n <namespace>

# Calculate actual traffic split
STABLE=$(kubectl get deployment app-stable -n <namespace> -o jsonpath='{.status.readyReplicas}')
CANARY=$(kubectl get deployment app-canary -n <namespace> -o jsonpath='{.status.readyReplicas}')
TOTAL=$((STABLE + CANARY))
CANARY_PCT=$((CANARY * 100 / TOTAL))
echo "Canary traffic: $CANARY_PCT%"

# Check service endpoints (see all pods)
kubectl get endpoints app-service -n <namespace>

# Test traffic distribution (sampling)
for i in {1..100}; do
  kubectl run -it --rm test-$i --image=busybox --restart=Never -n <namespace> -- wget -q -O- app-service
done | grep -c "Canary"

# Quick rollback (scale canary to 0)
kubectl scale deployment app-canary --replicas=0 -n <namespace>

# Progressive rollout (increase canary)
kubectl scale deployment app-canary --replicas=2 -n <namespace>  # 20%
# Monitor...
kubectl scale deployment app-canary --replicas=5 -n <namespace>  # 50%
# Monitor...
kubectl scale deployment app-canary --replicas=10 -n <namespace> # 100%
kubectl scale deployment app-stable --replicas=0 -n <namespace>  # Remove old
```

**Prevention & Best Practices:**
### Canary Deployment Strategy

**Concept**: Gradually roll out new version to a small subset of users, monitor for issues, then progressively increase traffic.

```
┌─────────────────────────────────────────────┐
│         PHASE 1: Stable Only                 │
│  ┌──────────────┐                            │
│  │   Service    │───────▶┌──────────────┐   │
│  │ (selector:   │        │ Stable Pods  │   │
│  │  app=myapp)  │        │ (v1.0)       │   │
│  └──────────────┘        │ 10 replicas  │   │
│                          └──────────────┘   │
│                                              │
│  All users → v1.0                            │
└─────────────────────────────────────────────┘

----------------------------------------

### 🚀 Level 19: Stateful App Data Loss

**Symptom & Root Cause:**
You were using a **Deployment** for a database, which is designed for stateless applications.
Databases are stateful workloads that need:
- Stable, predictable pod names
- Persistent storage that follows the pod
- Ordered startup and shutdown
- Stable network identities

**Diagnostic & Troubleshooting Steps:**
```bash
# Create StatefulSet
kubectl apply -f statefulset.yaml

# Check StatefulSet status
kubectl get statefulset -n <namespace>

# Get pod names (should be stable: app-0, app-1, app-2)
kubectl get pods -n <namespace> -l app=<label>

# Describe StatefulSet
kubectl describe statefulset <name> -n <namespace>

# Check pod DNS names
kubectl run -it --rm debug --image=busybox --restart=Never -n <namespace> -- nslookup database-0.database-service

# Scale StatefulSet (scales in order)
kubectl scale statefulset <name> --replicas=5 -n <namespace>

# Delete StatefulSet (keeps PVCs by default)
kubectl delete statefulset <name> -n <namespace>

# Delete StatefulSet AND PVCs
kubectl delete statefulset <name> -n <namespace> --cascade=orphan
kubectl delete pvc --all -n <namespace>

# Check PVCs created by StatefulSet
kubectl get pvc -n <namespace>

# Restart pod (StatefulSet recreates with same name)
kubectl delete pod <name>-0 -n <namespace>
# Watch it recreate with same name!

# Check pod startup order
kubectl get pods -n <namespace> -l app=<label> -w
```

**Prevention & Best Practices:**
### Stateless vs Stateful Workloads

**Stateless** (use Deployment):

```
Web Server Example:
┌─────────┐  ┌─────────┐  ┌─────────┐
│ web-abc │  │ web-xyz │  │ web-mno │
└─────────┘  └─────────┘  └─────────┘
     ↓            ↓            ↓
  [No local state - all identical]
  
Any pod can handle any request
Pods are interchangeable
No need for stable identity
Can scale up/down instantly
```

----------------------------------------

### 🚀 Level 20: ReplicaSet Without Deployment

**Symptom & Root Cause:**
You had a standalone ReplicaSet, which is a low-level Kubernetes resource that doesn't provide update management capabilities.
While ReplicaSets ensure the right number of pods are running, they don't handle:
- Rolling updates
- Rollbacks
- Declarative version changes
- Rollout history

**Diagnostic & Troubleshooting Steps:**
```bash
# Create Deployment (recommended)
kubectl create deployment web-app --image=myapp:v1 --replicas=3

# Update Deployment image (triggers rolling update)
kubectl set image deployment/web-app myapp=myapp:v2

# Check rollout status
kubectl rollout status deployment/web-app

# See rollout history
kubectl rollout history deployment/web-app

# Rollback to previous version
kubectl rollout undo deployment/web-app

# Rollback to specific revision
kubectl rollout undo deployment/web-app --to-revision=2

# Pause rollout (for testing canary)
kubectl rollout pause deployment/web-app

# Resume rollout
kubectl rollout resume deployment/web-app

# Check Deployment's ReplicaSets
kubectl get replicasets -l app=webapp

# Edit Deployment (declarative update)
kubectl edit deployment web-app

# Scale Deployment
kubectl scale deployment web-app --replicas=5

# DON'T do this (create standalone ReplicaSet)
kubectl create -f replicaset.yaml  # ❌ Use Deployment instead!
```

**Prevention & Best Practices:**
### Kubernetes Resource Hierarchy

```
┌─────────────────────────────────────┐
│         Deployment                   │  ← YOU manage this
│  (High-level, declarative)           │
│  - Rolling updates                   │
│  - Rollback                          │
│  - Version history                   │
└───────────────┬─────────────────────┘
                │ Creates & manages
                ↓
┌─────────────────────────────────────┐
│         ReplicaSet                   │  ← Deployment manages this
│  (Mid-level)                         │     (you don't touch it)
│  - Ensures N pods running            │
│  - Replaces crashed pods             │
└───────────────┬─────────────────────┘
                │ Creates & manages
                ↓
┌─────────────────────────────────────┐
│         Pods                         │  ← ReplicaSet manages this
│  (Low-level, ephemeral)              │     (you don't touch it)
│  - Runs containers                   │
└─────────────────────────────────────┘
```

**Abstraction levels**:

----------------------------------------


## 📂 Category: Services, DNS & Networking (Levels 21-30)

### 🚀 Level 21: Level: 21 Debrief: Service Selector Mismatch

**Symptom & Root Cause:**
You just fixed a **service selector mismatch** - one of the most common networking issues in Kubernetes!
The service existed and looked healthy, but it couldn't route traffic to the backend pods because its **selector didn't match the pod labels**.

----------------------------------------

### 🚀 Level 22: Level: 22 Debrief: NodePort Configuration

**Symptom & Root Cause:**
You fixed a **NodePort service configuration** issue! The service was created with type NodePort, but without an explicit `nodePort` value, Kubernetes assigned a random port.
While the service worked technically, it was unpredictable and hard to document. Production services need consistent, well-known ports.

----------------------------------------

### 🚀 Level 23: Level: 23 Debrief: DNS Resolution in Kubernetes

**Symptom & Root Cause:**
You fixed a **DNS resolution failure** where a pod couldn't connect to a service because it was using the wrong hostname!
This is one of the most common issues in Kubernetes - assuming service names match what you think they are, instead of checking what they actually are.

----------------------------------------

### 🚀 Level 24: Level: 24: Ingress Path Mismatch - Mission Debrief

**Symptom & Root Cause:**
**Objective:** Fix an Ingress configuration where the path routing was incorrectly configured, causing 404 errors for all requests to the application.
**XP Awarded:** 250 XP  
**Difficulty:** Intermediate  
**Concepts:** Kubernetes Ingress, Path-based Routing, HTTP Routing, PathType

----------------------------------------

### 🚀 Level 25: Level: 25: NetworkPolicy Too Restrictive - Mission Debrief

**Symptom & Root Cause:**
**Objective:** Fix an overly restrictive NetworkPolicy that was blocking legitimate traffic between frontend and backend pods.
**XP Awarded:** 250 XP  
**Difficulty:** Intermediate  
**Concepts:** Kubernetes NetworkPolicy, Pod-to-pod Communication, Label Selectors, Ingress Rules

**Prevention & Best Practices:**
### 1. Start Permissive, Then Tighten

```bash
# Phase 1: No NetworkPolicy (allow all)
# Deploy application, verify it works

# Phase 2: Default deny with broad allow
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
spec:
  podSelector:
    matchLabels:
      tier: backend
  ingress:
  - from:
    - podSelector: {}  # Allow from all pods in namespace

----------------------------------------

### 🚀 Level 26: Level: 26: Session Affinity Missing - Mission Debrief

**Symptom & Root Cause:**
**Objective:** Fix a stateful application that was losing user sessions by configuring session affinity on the Kubernetes Service.
**XP Awarded:** 200 XP  
**Difficulty:** Intermediate  
**Concepts:** Session Affinity (Sticky Sessions), Service Load Balancing, Stateful vs Stateless Applications

**Prevention & Best Practices:**
### 1. Use Session Affinity as Temporary Solution

```
Phase 1: Deploy with sessionAffinity (quick fix)
Phase 2: Implement shared session storage (proper fix)
Phase 3: Remove sessionAffinity (no longer needed)
```

### 2. Set Appropriate Timeout

----------------------------------------

### 🚀 Level 27: Level: 27: Cross-namespace Service Communication - Mission Debrief

**Symptom & Root Cause:**
**Objective:** Fix a frontend application that couldn't communicate with a backend service in a different namespace by using the proper DNS FQDN format.
**XP Awarded:** 250 XP  
**Difficulty:** Intermediate  
**Concepts:** Cross-namespace Communication, Kubernetes DNS, FQDN, Service Discovery

**Prevention & Best Practices:**
### 1. Always Use FQDN for Cross-Namespace

```yaml
# ❌ BAD: Relies on same namespace
env:
- name: API_URL
  value: "http://api-service:8080"

# ✅ GOOD: Explicit namespace
env:
- name: API_URL
  value: "http://api-service.backend.svc.cluster.local:8080"
```

----------------------------------------

### 🚀 Level 28: Level: 28 Debrief: Service Endpoints & Readiness Probes

**Symptom & Root Cause:**
### The Problem
Your service was routing traffic to pods **before they were ready to handle requests**, causing:
- **500 errors** during pod initialization
- **Failed requests** hitting pods still loading data
- **Inconsistent behavior** as some pods worked while others didn't
- **Poor user experience** with intermittent failures
### The Root Cause
```yaml
# ❌ BROKEN: No readiness probe
apiVersion: v1
kind: Pod
metadata:
  name: web-app-1
spec:
  containers:
  - name: web
    image: nginx:1.21
    # Missing readinessProbe!
    # Pod is added to endpoints IMMEDIATELY
```

----------------------------------------

### 🚀 Level 29: Level: 29 Debrief: LoadBalancer vs NodePort Service Types

**Symptom & Root Cause:**
### The Problem
Your service was configured as type `LoadBalancer` in a local development cluster, causing:
- **Service stuck in "Pending" state** indefinitely
- **No external IP assigned** to the service
- **Application completely inaccessible** from outside the cluster
- **Confusion** about why it works in cloud but not locally
### The Root Cause
```yaml
# ❌ BROKEN: LoadBalancer in local cluster
apiVersion: v1
kind: Service
metadata:
  name: web-service
spec:
  type: LoadBalancer  # ❌ Requires cloud provider!
  selector:
    app: web
  ports:
  - port: 80
    targetPort: 80
```

----------------------------------------

### 🚀 Level 30: Level: 30 Debrief: Headless Services & StatefulSet DNS

**Symptom & Root Cause:**
### The Problem
Your StatefulSet pods couldn't communicate with each other using predictable DNS names, causing:
- **Pods unable to discover peers** in the cluster
- **Database replication failing** (can't find master/slave nodes)
- **Distributed systems broken** (peers can't coordinate)
- **Random pod assignment** instead of specific pod targeting
### The Root Cause
```yaml
# ❌ BROKEN: Regular ClusterIP service
apiVersion: v1
kind: Service
metadata:
  name: web-cluster
spec:
  clusterIP: 10.96.100.50  # ❌ Has virtual IP (not headless)
  selector:
    app: web-cluster
  ports:
  - port: 80
```

----------------------------------------


## 📂 Category: Volumes, Storage & Configuration (Levels 31-40)

### 🚀 Level 31: Level: 31 Debrief: PersistentVolumes & PersistentVolumeClaims

**Symptom & Root Cause:**
### The Problem
Your PersistentVolumeClaim was stuck in Pending state, preventing the pod from starting:
- **PVC never binds** to a PersistentVolume
- **Pod stuck in ContainerCreating** waiting for volume
- **Application can't start** without persistent storage
- **Data can't be persisted** across pod restarts
### The Root Cause
```yaml
# ❌ BROKEN: PV doesn't match PVC requirements
apiVersion: v1
kind: PersistentVolume
metadata:
  name: app-storage
spec:
  capacity:
    storage: 1Gi              # ❌ Too small! PVC needs 5Gi
  storageClassName: standard  # ❌ Wrong! PVC needs "fast"
  
---
apiVersion: v1
kind: PersistentVolumeClaim
spec:
  storageClassName: fast      # Needs "fast" class
  resources:
    requests:
      storage: 5Gi            # Needs 5Gi capacity
```

----------------------------------------

### 🚀 Level 32: LEVEL 32 DEBRIEF: Volume Mount Path Configuration

**Symptom & Root Cause:**
**The Problem:**
```yaml
volumeMounts:
- name: config-volume
  mountPath: /data  # ❌ Wrong path
```
**The Application Expected:**
```
/app/config/app.conf
```

----------------------------------------

### 🚀 Level 33: LEVEL 33 DEBRIEF: PV/PVC Access Modes

**Symptom & Root Cause:**
**The Problem:**
```yaml
# PersistentVolume & PersistentVolumeClaim
accessModes:
  - ReadWriteOnce  # ❌ Only one node can mount at a time!
```
**The Solution:**
```yaml
# PersistentVolume & PersistentVolumeClaim
accessModes:
  - ReadWriteMany  # ✅ Multiple nodes can mount simultaneously
```

----------------------------------------

### 🚀 Level 34: LEVEL 34 DEBRIEF: StatefulSet Volume Templates

**Symptom & Root Cause:**
**The Problem:**
```yaml
# All pods sharing ONE PVC
volumes:
- name: database-storage
  persistentVolumeClaim:
    claimName: database-storage  # ❌ All pods use same PVC!
```
**Result:** Database corruption - all 3 postgres pods writing to same files!

----------------------------------------

### 🚀 Level 35: LEVEL 35 DEBRIEF: StorageClass Configuration

**Symptom & Root Cause:**
**The Problem:**
```yaml
spec:
  storageClassName: premium-ssd  # ❌ Doesn't exist!
```
Result: PVC stuck in Pending forever
**The Solution:**
```yaml
spec:
  storageClassName: standard  # ✅ Use existing StorageClass
```
Result: PVC automatically provisioned and bound

----------------------------------------

### 🚀 Level 36: LEVEL 36 DEBRIEF: ConfigMap Key Management

**Symptom & Root Cause:**
**The Problem:**
```yaml
data:
  app_name: "MyApp"
  app_version: "1.0.0"
  # ❌ Missing: database_host
  
env:
- name: DATABASE_HOST
  valueFrom:
    configMapKeyRef:
      key: database_host  # ❌ Key doesn't exist!
```
**The Solution:**
```yaml
data:
  app_name: "MyApp"
  app_version: "1.0.0"
  database_host: "postgres.k8squest.svc.cluster.local"  # ✅ Added!
```

----------------------------------------

### 🚀 Level 37: LEVEL 37 DEBRIEF: Base64 Encoding ≠ Encryption

**Symptom & Root Cause:**
**What You Saw:**
```yaml
data:
  username: YWRtaW4=  # Looks encrypted... but it's not!
  password: c2VjcmV0cGFzczEyMw==  # Anyone can decode this!
```
**What Actually Happens:**
```bash
# Anyone with kubectl access can decode instantly:
$ kubectl get secret db-credentials -n k8squest -o jsonpath='{.data.username}' | base64 -d
admin

----------------------------------------

### 🚀 Level 38: LEVEL 38 DEBRIEF: Volume Permissions & fsGroup

**Symptom & Root Cause:**
**The Problem:**
```yaml
spec:
  containers:
  - securityContext:
      runAsUser: 1000  # Runs as user 1000
  # ❌ No fsGroup! Volume owned by root
```
Result: Permission denied when writing to volume
**The Solution:**
```yaml
spec:
  securityContext:  # ✅ Pod-level
    fsGroup: 1000
  containers:
  - securityContext:
      runAsUser: 1000
      runAsGroup: 1000
```
Result: Volume group ownership changed to 1000, write access granted

----------------------------------------

### 🚀 Level 39: LEVEL 39 DEBRIEF: PV Reclaim Policies

**Symptom & Root Cause:**
**The Problem:**
```yaml
spec:
  persistentVolumeReclaimPolicy: Delete  # ❌ Dangerous!
```
Result: When PVC deleted → PV deleted → All data permanently lost!
**The Solution:**
```yaml
spec:
  persistentVolumeReclaimPolicy: Retain  # ✅ Safe!
```
Result: When PVC deleted → PV marked "Released" → Data preserved

----------------------------------------

### 🚀 Level 40: LEVEL 40 DEBRIEF: emptyDir vs PersistentVolumeClaim

**Symptom & Root Cause:**
**The Problem:**
```yaml
volumes:
- name: data
  emptyDir: {}  # ❌ Ephemeral! Data lost on restart
```
Result: All data disappears when pod restarts
**The Solution:**
```yaml
volumes:
- name: data
  persistentVolumeClaim:
    claimName: app-data  # ✅ Persistent!
```
Result: Data survives pod restarts, deletions, and recreations

----------------------------------------


## 📂 Category: Security, RBAC, Scheduling & Cluster Policies (Levels 41-50)

### 🚀 Level 41: LEVEL 41 DEBRIEF: Kubernetes RBAC (Role-Based Access Control)

**Symptom & Root Cause:**
**The Problem:**
```yaml
# ServiceAccount exists
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pod-reader
# ❌ No Role - no permissions defined
# ❌ No RoleBinding - ServiceAccount not granted anything
```

----------------------------------------

### 🚀 Level 42: LEVEL 42 DEBRIEF: Container SecurityContext & Privilege Escalation

**Symptom & Root Cause:**
**The Problem:**
```yaml
securityContext:
  runAsNonRoot: true  # Enforced but...
  # ❌ No runAsUser specified!
  # ❌ No allowPrivilegeEscalation setting!
```
**Result:** Pod rejected with "container has runAsNonRoot and image will run as root"

----------------------------------------

### 🚀 Level 43: LEVEL 43 DEBRIEF: Kubernetes ResourceQuota & Resource Management

**Symptom & Root Cause:**
**The Problem:**
```yaml
# Quota allows only 2 CPUs total
spec:
  hard:
    requests.cpu: "2"
# Pod requests 2.5 CPUs
resources:
  requests:
    cpu: "2500m"  # 2.5 CPUs > 2 quota = REJECTED!
```

----------------------------------------

### 🚀 Level 44: LEVEL 44 DEBRIEF: Kubernetes NetworkPolicy

**Symptom & Root Cause:**
**The Problem:**
```yaml
# Deny-all policy blocking everything
spec:
  podSelector: {}  # All pods
  policyTypes:
  - Ingress
  - Egress
  # ❌ No rules = block ALL traffic
```
**Result:** Backend couldn't connect to database, connection refused

----------------------------------------

### 🚀 Level 45: LEVEL 45 DEBRIEF: Node Affinity & Advanced Scheduling

**Symptom & Root Cause:**
**The Problem:**
```yaml
nodeAffinity:
  requiredDuringSchedulingIgnoredDuringExecution:
    nodeSelectorTerms:
    - matchExpressions:
      - key: gpu-type  # ❌ Nodes don't have this label
        values: [nvidia-tesla]  # ❌ Wrong value
```
**Result:** Pod stuck Pending, "didn't match node affinity"

----------------------------------------

### 🚀 Level 46: LEVEL 46 DEBRIEF: Taints & Tolerations

**Symptom & Root Cause:**
**Problem:** Node tainted, pod has no toleration
```yaml
# Node: dedicated=gpu:NoSchedule
# Pod: No tolerations → Can't schedule
```
**Solution:** Added matching toleration
```yaml
tolerations:
- key: "dedicated"
  operator: "Equal"
  value: "gpu"
  effect: "NoSchedule"
```

----------------------------------------

### 🚀 Level 47: LEVEL 47 DEBRIEF: PodDisruptionBudget

**Symptom & Root Cause:**
**Problem:** PDB requires 3 pods, deployment has 2
```yaml
replicas: 2
minAvailable: 3  # Impossible!
```
**Solution:** Scaled deployment and adjusted PDB
```yaml
replicas: 3
minAvailable: 2  # ✅ Can lose 1 pod
```

----------------------------------------

### 🚀 Level 48: LEVEL 48 DEBRIEF: Pod Security Standards

**Symptom & Root Cause:**
Three levels of security enforcement:
### 1. Privileged (Unrestricted)
No restrictions - use with caution!

----------------------------------------

### 🚀 Level 49: LEVEL 49 DEBRIEF: PriorityClass

**Symptom & Root Cause:**
**PriorityClass** assigns importance to pods.
```yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: high-priority
value: 1000  # Higher value = higher priority
preemptionPolicy: PreemptLowerPriority
```

----------------------------------------

### 🚀 Level 50: LEVEL 50 DEBRIEF: CHAOS FINALE - The Perfect Storm

**Symptom & Root Cause:**
You've conquered the **CHAOS FINALE** - the ultimate test combining ALL World 5 concepts!
---

----------------------------------------
