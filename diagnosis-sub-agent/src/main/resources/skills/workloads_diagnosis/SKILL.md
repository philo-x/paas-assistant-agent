---
name: workloads_diagnosis
version: "1.1"
category: Workloads
description: >
  诊断 Deployment、StatefulSet、DaemonSet 等工作负载的生命周期问题：副本数为零、滚动更新卡住、HPA 无法获取指标、蓝绿/金丝雀发布流量异常、有状态应用数据不一致、DaemonSet 节点分布异常等。本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Deployment / StatefulSet / DaemonSet AVAILABLE 副本数为 0，服务不可用，且用户要求排查"
  - "kubectl rollout status 长时间未完成"
  - "新版本发布期间服务完全中断或部分节点异常"
  - "蓝绿发布切换后用户仍访问旧版本"
  - "金丝雀版本流量占比与预期不符"
  - "HPA TARGETS 列显示 unknown，不触发扩缩容，且作为故障现象输入"
  - "StatefulSet Pod 重启后数据丢失，Pod 名称随机变化"
  - "DaemonSet 某些节点上无 Pod 或分布不均，且用户询问原因"
references:
  - references/rollout_strategy_guide.md   # 滚动更新策略参数说明
  - references/hpa_metrics_guide.md        # HPA 指标配置与 metrics-server 排查
---

# workloads_diagnosis — 工作负载诊断 (v1.1)

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 诊断工作流（v1.1）

### Step 0：自动化工作负载异常扫描（K8sGPT）

> **💡 最佳实践**：在逐步排查控制器和 Pod 之前，优先让 K8sGPT 扫描常见的副本/调度/HPA错误。
> **💡 最佳实践**：在逐步排查控制器和 Pod 之前，优先让 K8sGPT 扫描常见的副本/调度/HPA错误。

```
工具：analyze(cluster, namespace=<ns>, name=<workload-name>, filters=["Deployment", "StatefulSet", "DaemonSet", "ReplicaSet", "HorizontalPodAutoscaler"])
→ 快速识别 AVAILABLE 副本数为零、HPA 找不到 Metrics，或滚动更新卡住等常见问题。
```

---

### Step 1：确认工作负载状态

```
工具：list_k8s_resource(cluster, namespace, kind=<workload-kind>, group=apps, version=v1)
→ 观察 READY / UP-TO-DATE / AVAILABLE 三列

AVAILABLE = 0 且 UP-TO-DATE = 0 → Step 2A（副本数为零）
AVAILABLE = 0 且 UP-TO-DATE 持续不变（发布中） → Step 2B（滚动更新卡住）
HPA 相关 → Step 2C（HPA 指标问题）
流量分配异常（蓝绿/金丝雀） → Step 2D（发布策略诊断）
StatefulSet 问题 → Step 2E（有状态应用诊断）
DaemonSet 问题 → Step 2F（DaemonSet 节点分布异常）
```

---

### Step 2A：副本数为零诊断

```
① 确认当前副本配置
   工具：get_k8s_resource(cluster, namespace, kind=<workload-kind>, name=<name>,
         group=apps, version=v1)
   → 读取 spec.replicas 字段（若为 DaemonSet，则读取 status.desiredNumberScheduled）

② 确认是否有历史停止注解
   → 读取 metadata.annotations 中是否存在副本数记录

③ 查看工作负载相关事件
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<name>, involvedObjectKind="<workload-kind>")
   → 判断是人为操作还是异常触发
```

---

### Step 2B：滚动更新卡住诊断

```
① 查看发布状态
   工具：kubectl(cluster, args=["rollout", "status", "<workload-kind>/<name>", "-n", "<namespace>"])
   → 若长时间未完成 → 卡住（注：对 Deployment，也可优先用 get_k8s_deployment_rollout_status 工具）

② 查看发布历史（识别新旧版本信息）
   工具：kubectl(cluster, args=["rollout", "history", "<workload-kind>/<name>", "-n", "<namespace>"])
   → 记录当前版本号（revision）

③ 查看工作负载详细信息（关键：确认是否超时）
   工具：get_k8s_resource(cluster, namespace, kind=<workload-kind>, name=<name>, group=apps, version=v1)
   → 读取 status.conditions，确认是否有明确的超时或异常状况（注：Deployment 默认 600s 标记 ProgressDeadlineExceeded）

④ 查看 Events
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<name>, involvedObjectKind="<workload-kind>")
   → 找 FailedCreate / ImagePullBackOff / Insufficient 等事件

⑤ 查看新版本 Pod 的具体错误
   工具：list_k8s_pod(cluster, namespace)
   → 找 STATUS 不为 Running 的新版本 Pod
   工具：list_k8s_event(cluster, namespace, involvedObjectName=<new-pod>, involvedObjectKind="Pod")
   → 确认卡住原因（镜像错误 / 资源不足 / 配置错误）

⑥ 检查更新策略配置
   → 读取 spec.strategy（或 spec.updateStrategy）的 maxUnavailable 和 maxSurge
```

> 参考：[滚动更新策略说明](references/rollout_strategy_guide.md)

---

### Step 2C：HPA 指标异常与扩缩容延迟诊断

```
① 查看 HPA 配置和状态
   工具：get_k8s_deployment_hpa_list(cluster, namespace, name=<deployment>)
   → TARGETS 列显示 <unknown>/50% → metrics-server 无法提供数据，继续排查 metrics-server（Step ②）
   → TARGETS 显示正常（例如 90%/50%）但没有扩容 → 可能是处于扩缩容冷却窗口（Stabilization Window），跳转 Step ④

② 检查 metrics-server 是否运行
   工具：list_k8s_resource(cluster, namespace=kube-system, kind=Deployment,
         group=apps, version=v1)
   → 找 metrics-server Deployment，检查 AVAILABLE 列

③ 若 metrics-server 不可用，查看其 Pod 日志
   工具：list_k8s_pod(cluster, namespace=kube-system)
   → 找 metrics-server Pod
   工具：get_k8s_pod_logs(cluster, namespace=kube-system,
         name=<metrics-server-pod>, tail=100)
   → 查找 "certificate" / "TLS" / "connection refused" 等错误

④ 查看 HPA 详细配置与 Events（验证冷却时间）
   工具：get_k8s_resource(cluster, namespace, kind=HorizontalPodAutoscaler,
         group=autoscaling, version=v2, name=<hpa>)
   → 读取 spec.metrics 字段，确认是 Resource / Custom / External 指标
   → 查看 HPA 的 Events 记录（通过 describe_k8s_resource(cluster, kind=HorizontalPodAutoscaler) 或 list_k8s_event）
   ⚠️ SRE 提醒：HPA 缩容有默认 5 分钟（缩容）或根据 behavior 配置的冷却期。如果指标满足条件但未发生动作，检查 Events 中是否提示由于 cooldown 窗口未过而限制了操作，不可仅判定 metrics-server 故障。

⑤ 查看历史监控指标趋势 (以验证 HPA 扩缩容判定是否合理)
   工具：get_k8s_resource_metrics_history(cluster, query=<promql>)
```

> 参考：[HPA 指标配置与 metrics-server 排查](references/hpa_metrics_guide.md)

---

### Step 2D：蓝绿/金丝雀发布流量诊断

```
① 查看 Service 的当前 selector
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.selector

② 查看各版本工作负载的 Pod Labels
   工具：list_k8s_resource(cluster, namespace, kind=<workload-kind>,
         group=apps, version=v1)
   → 对比 Blue 和 Green 的 Pod template labels

③ 确认当前流量指向哪个版本
   → Service selector 中的 label 匹配哪个版本的 Pod

④ 查看各版本副本数（金丝雀分流比例）
   工具：list_k8s_resource(cluster, namespace, kind=<workload-kind>,
         group=apps, version=v1)
   → 计算实际流量比
```

---

### Step 2E：StatefulSet 有状态应用诊断

```
① 确认工作负载类型和 Pod 命名规律
   工具：list_k8s_pod(cluster, namespace)
   → 若 Pod 名称格式为 <name>-<random>（非 name-0/name-1）→ 使用了 Deployment 而非 StatefulSet

② 检查存储绑定方式
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod-0>)
   工具：get_k8s_pod_linked_pvc(cluster, namespace, name=<pod-1>)
   → 若多个 Pod 绑定同一 PVC → 共用存储（数据损坏风险）

③ 查看 StatefulSet spec（检查是否有 volumeClaimTemplates）
   工具：get_k8s_resource(cluster, namespace, kind=StatefulSet,
         group=apps, version=v1, name=<name>)
   → 读取 spec.volumeClaimTemplates 字段
   → 若为空但使用了固定 PVC → 配置错误
```

---

### Step 2F：DaemonSet 节点分布异常诊断

```
① 确认预期调度节点数与实际就绪节点数
   工具：get_k8s_resource(cluster, namespace, kind=DaemonSet, name=<name>, group=apps, version=v1)
   → 比较 status.desiredNumberScheduled 与 status.numberReady
   → 若 desiredNumberScheduled < 预期集群或节点组节点数，说明 NodeSelector 或 Affinity 未匹配足够节点

② 检查容忍度 (Tolerations) 配置
   → 读取 spec.template.spec.tolerations
   → 若节点存在污点 (Taints) 但没有相应容忍度，Pod 将无法调度到该节点

③ 结合节点状态排查未调度原因
   工具：list_k8s_node(cluster)
   工具：describe_k8s_resource(cluster, kind="Node", name="<node>")
   → 分析 node 状态为何该节点没有运行对应的 DaemonSet Pod
```

---


**示例（HPA metrics-server 缺失）**：
> **Root Cause:** 集群未安装 metrics-server，HPA 控制器无法从 metrics API 获取 Pod 的 CPU 使用率。  
> **Evidence Chain:** metrics-server 缺失 → HPA 调用 `metrics.k8s.io` API 失败 → HPA `status.currentMetrics` 为空，TARGETS 显示 `<unknown>` → HPA 无法计算目标副本数，replicas 始终停留在 minReplicas。  
> **Confidence:** High — `list_k8s_resource(cluster, kube-system, kind=Deployment)` 未找到 metrics-server；`get_k8s_deployment_hpa_list` 返回 `TARGETS=<unknown>/50%`。  
> **Recommended Fix:** 建议运维人员在 kube-system 命名空间安装 metrics-server（官方 components.yaml），本地集群需额外添加 `--kubelet-insecure-tls` 启动参数。

---

## 兜底排查机制（kubectl）

在排查各类应用工作负载（Deployment, StatefulSet, DaemonSet）或 HPA 遇到特定自动化或专用 MCP 工具失效时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查工作负载副本就绪及配置详情：
  `工具：kubectl(cluster, args=["get", "deploy,sts,ds", "-n", "<namespace>", "-o", "wide"])`
- 详细排查特定工作负载滚动更新状态与版本历史：
  `工具：kubectl(cluster, args=["rollout", "status", "<workload-kind>/<workload-name>", "-n", "<namespace>"])`
  `工具：kubectl(cluster, args=["rollout", "history", "<workload-kind>/<workload-name>", "-n", "<namespace>"])`
- 排查 HPA 自动缩容状态及限制阈值：
  `工具：kubectl(cluster, args=["get", "hpa", "-n", "<namespace>", "-o", "yaml"])`
