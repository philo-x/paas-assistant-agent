---
name: pods_diagnosis
version: "1.0"
category: Pods
description: >
  诊断 Pod 生命周期中容器层面的故障：容器启动失败（CrashLoopBackOff、
  ImagePullBackOff、Init 阻塞）、Liveness/Readiness 探针配置错误，
  以及环境变量/配置注入异常。本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Pod 状态为 CrashLoopBackOff，且用户明确要求排查原因"
  - "Pod 状态为 ImagePullBackOff 或 ErrImagePull，且作为故障现象输入"
  - "Pod 状态为 Init:0/N，且用户询问为什么无法启动"
  - "Pod READY 列为 X/N（X 小于 N），某容器未就绪，且用户要求诊断"
  - "Pod RESTARTS 列持续递增，Events 含 Liveness probe failed"
  - "Pod Running 但 Endpoints 为空，且作为故障现象被要求排查原因"
  - "新版本 Pod 上线后大量 502/503 错误"
references:
  - references/exit_code_reference.md    # 容器退出码含义速查
  - references/probe_config_guide.md     # 探针字段说明与常见误配置
---

# pods_diagnosis — Pod 诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 诊断工作流

### Step 0：自动化诊断（K8sGPT）

> **💡 最佳实践**：在进行繁琐的手工抓取日志和事件前，优先让 K8sGPT 进行自动化扫描定界。
> **💡 最佳实践**：在进行繁琐的手工抓取日志和事件前，优先让 K8sGPT 进行自动化扫描定界。

```
工具：analyze(cluster, namespace=<ns>, name=<pod>, filters=["Pod"])
→ 若输出结果已包含明确错误原因（如 CrashLoopBackOff 退出码解释、ImagePullBackOff 原因），可直接参考该结论，跳过后续部分手工排查步骤。
```

---

### Step 1：确认 Pod 状态

```
工具：list_k8s_pod(cluster, namespace)
或使用 kubectl 兜底工具查看宽表以获取 Pod IP 和主机节点名：
工具：kubectl(cluster, args=["get", "pods", "-n", "<namespace>", "-o", "wide"])
→ 观察 STATUS / READY / RESTARTS 列

STATUS = Pending
  → 调度层问题，请切换至 nodes_diagnosis Skill

STATUS = ImagePullBackOff / ErrImagePull
  → 跳转 Step 2A（镜像拉取失败）

STATUS = CrashLoopBackOff
  → 跳转 Step 2B（容器崩溃）

STATUS = Init:0/N 或 Init:Error
  → 跳转 Step 2C（Init 容器阻塞）

READY = X/N（X < N）
  → 跳转 Step 2D（部分容器异常）

RESTARTS 递增，STATUS = Running
  → 跳转 Step 2E（Liveness Probe）

Running 但 Endpoints 为空
  → 跳转 Step 2F（Readiness Probe）
```

---

### Step 2A：镜像拉取失败诊断

```
① 获取 Pod Events（获取拉取失败的具体错误）
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<pod>, involvedObjectKind="Pod")
   → 读取 "Failed to pull image" 事件的 Message 字段

② 确认镜像全名（含 registry 地址和 tag）
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers.Image 字段
```

**错误关键字判断表**（参见 [exit_code_reference.md](references/exit_code_reference.md)）：

| Events 关键字 | 诊断结论 |
|-------------|---------|
| `not found` / `manifest unknown` | 镜像 Tag 不存在于 Registry |
| `unauthorized` / `pull access denied` | 缺少 imagePullSecrets 或 Secret 已过期 |
| `no such host` / `dial tcp: lookup` | Registry 地址错误或网络不通 |
| `context deadline exceeded` | 网络超时（Registry 连接慢/不通） |

---

### Step 2B：容器崩溃诊断（CrashLoopBackOff）

```
① 查看当前日志（容器刚重启后的输出）
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>, tail=100)

② 查看上次崩溃日志（最关键！）
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>, previous=true, tail=100)
   ⚠️ SRE 提醒：若 Exit Code 为 137 (OOMKilled)，Linux Kernel 的 OOM Killer 是强行发送 SIGKILL 瞬间杀死进程，应用通常来不及输出任何 "Out of Memory" 日志。此时 previous 日志可能完全没有报错信息，这是正常现象，不要因此困惑。

③ 查看容器 Exit Code 和 Last State
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers[].Last State.Exit Code 字段
```

**Exit Code 诊断表**（参见 [exit_code_reference.md](references/exit_code_reference.md)）：

| Exit Code | 含义 | 诊断方向 |
|-----------|------|---------|
| `1` | 应用运行时错误 | 查看日志找具体错误 |
| `137` | OOMKilled（内存超限） | Exit Code=137 为强关联候选原因，仍建议通过 `get_k8s_pod_resource_usage` 核对是否发生实际内存超限 |
| `127` | 命令不存在 | 检查 command/args 配置 |
| `126` | 命令存在但无执行权限 | 检查镜像内文件权限 |
| `2` | bash/sh 脚本语法错误 | 检查 entrypoint 脚本 |

```
④ 查看 Pod 资源用量（确认是否 OOMKilled）
   工具：get_k8s_pod_resource_usage(cluster, namespace, name=<pod>)
   → 对比 limits.memory 与实际内存使用量

⑤ 查看运行时环境变量（应用可能因缺失配置项崩溃）
   工具：get_k8s_pod_linked_env(cluster, namespace, name=<pod>)
   工具：get_pod_linked_env_from_yaml(cluster, namespace, name=<pod>)
```

---

### Step 2C：Init 容器阻塞诊断

```
① 查看 Init 容器信息
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Init Containers 区块（状态、Exit Code）

② 查看具体 Init 容器的日志
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>,
         container=<init-container-name>, tail=100)
   → 常见原因：等待依赖服务就绪（DNS 超时、端口检测失败）
```

---

### Step 2D：Sidecar 容器异常（READY X/N）

```
① 识别哪个容器未就绪
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取每个容器的 State / Ready / Last State / Exit Code

② 针对异常容器查看日志
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>,
         container=<crashed-container>, previous=true, tail=100)
   → 按 Step 2B 逻辑继续分析
```

---

### Step 2E：Liveness Probe 错误诊断（RESTARTS 递增）

```
① 确认是 Liveness Probe 触发重启
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<pod>, involvedObjectKind="Pod")
   → 确认 Events 含 "Liveness probe failed"，记录具体错误信息

② 查看 Liveness Probe 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers[].Liveness Probe 字段
   → 关注：path / port / initialDelaySeconds / timeoutSeconds / failureThreshold

③ 验证探针目标是否可达
   工具：diagnose_k8s_pod_network(cluster, namespace, pod=<pod>, targetIP="127.0.0.1", targetPort=<port>)
   ⚠️ SRE 提醒：该工具会自动运行连接测试，若镜像中缺少 wget/nc 等命令，它会自动启动一个临时 busybox Pod 进行连通性探测，能彻底解决 scratch/distroless 等极简镜像无法执行探测的问题。不要使用 run_command_in_k8s_pod。
   → 若连通性失败 → 应用本身未启动或未监听该端口
   → 若连通性成功，但依然报错 → 结合 `list_k8s_event` 中探针失败事件记录的 HTTP 状态码（如 404），或应用日志来确认是否为具体的业务接口逻辑错误。
```

> 参考：[探针配置字段说明](references/probe_config_guide.md)

---

### Step 2F：Readiness Probe 错误诊断（Endpoints 为空）

```
① 确认 Pod 关联 Endpoints 是否为空
   工具：get_pod_linked_endpoints(cluster, namespace, name=<pod>)

② 若 Endpoints 为空，查看 Readiness 状态
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<pod>, involvedObjectKind="Pod")
   → 找 "Readiness probe failed" 事件

③ 查看 Readiness Probe 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers[].Readiness Probe 字段
   → 若 Readiness Probe 字段为空 → 未配置探针

④ 若未配置 Readiness Probe，确认应用是否在启动阶段需要时间预热
   工具：get_k8s_pod_logs(cluster, namespace, name=<pod>, tail=50)
   → 查看应用是否还在启动/初始化
```

> 参考：[探针配置字段说明](references/probe_config_guide.md)

---


**示例（Liveness Probe 路径错误）**：
> **Root Cause:** Deployment `livenessProbe.httpGet.path` 配置为 `/health`，但应用实际暴露的健康检查路径为 `/healthz`。  
> **Evidence Chain:** `livenessProbe.path=/health` 配置写入 → kubelet 每 10 秒 GET /health 收到 404 → 达到 `failureThreshold=3` 后 kubelet 重启容器 → 容器反复重启，RESTARTS 持续递增，用户观察到 CrashLoopBackOff。  
> **Confidence:** High — `list_k8s_event` 返回 "Liveness probe failed: HTTP probe failed with statuscode: 404" ；且 `diagnose_k8s_pod_network` 证实本地端口处于监听就绪状态。  
> **Recommended Fix:** 建议运维人员将 Deployment 的 `livenessProbe.httpGet.path` 修改为 `/healthz`，同时检查 `initialDelaySeconds` 是否留有足够启动预热时间（建议 ≥ 30s）。

---

## 兜底排查机制（kubectl）

在上述专用 MCP 工具（如 `get_k8s_pod_logs` 或 `describe_k8s_pod`）不可用、执行出错或有局限性时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查或检查 Pod 运行所在 IP 与 Node 节点：
  `工具：kubectl(cluster, args=["get", "pods", "-n", "<namespace>", "-o", "wide"])`
- 查看 Pod 运行事件与具体条件详情：
  `工具：kubectl(cluster, args=["describe", "pod", "<pod-name>", "-n", "<namespace>"])`
- 获取容器崩溃前的 previous 日志：
  `工具：kubectl(cluster, args=["logs", "<pod-name>", "-n", "<namespace>", "-c", "<container-name>", "-p", "--tail=100"])`
