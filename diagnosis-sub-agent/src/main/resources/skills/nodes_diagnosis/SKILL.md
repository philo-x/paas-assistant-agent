---
name: nodes_diagnosis
version: "1.0"
category: Nodes
description: >
  诊断 Kubernetes 节点层问题：Pod 调度失败（资源不足、亲和性/污点不匹配）、
  节点维护被 PDB 阻塞、节点健康状态异常（NotReady）及 IP 资源耗尽。
  本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Pod 状态长期 Pending，Events 含 Insufficient cpu/memory"
  - "Pod Events 含 didn't match node affinity 或 had taints that pod didn't tolerate"
  - "节点 drain 操作挂起，提示 Cannot evict pod as it would violate PDB"
  - "节点状态为 NotReady 或 Unknown"
  - "节点含 MemoryPressure / DiskPressure / PIDPressure 条件"
  - "节点 IP 资源耗尽，新 Pod 无法获取 IP"
references:
  - references/scheduling_constraints.md   # 亲和性/污点/PDB 字段说明
  - references/node_condition_guide.md     # Node Condition 类型与含义
---

# nodes_diagnosis — 节点诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 诊断工作流

### Step 0：节点与调度约束自动化检查（K8sGPT）

> **💡 最佳实践**：在梳理复杂的亲和性、污点和 PDB 前，先让 K8sGPT 给出高度概括的诊断结论。

```
工具：analyze(filters=["Node", "PodDisruptionBudget", "Pod"])
→ 利用 K8sGPT 一键分析 Node 的 NotReady 原因，以及 PDB 阻塞和 Pod 调度失败（资源/污点不满足）的综合情况。（通常节点故障不需要指定 namespace，若针对特定未调度 Pod 可带上 namespace 参数）
```

---

### Step 1：获取节点全局视图

```
工具：list_k8s_node(cluster)
或使用 kubectl 兜底命令查看节点宽表信息（如内网/外网 IP、操作系统内核）：
工具：kubectl(cluster, cmd="get nodes -o wide")
→ 观察所有节点的 STATUS / ROLES / VERSION
→ 若有节点 NotReady → 跳转 Step 4（节点健康诊断）
→ 若所有节点 Ready → 问题在调度层 → 继续 Step 2
```

### Step 2：识别 Pending Pod 的调度失败原因

```
工具：list_k8s_event(cluster, namespace, involvedObjectName=<pending-pod>, involvedObjectKind="Pod")
→ 读取 Events 区块，找到调度失败关键字

"Insufficient cpu" 或 "Insufficient memory"
  → 跳转 Step 3A（资源不足）

"didn't match node affinity" 或 "match pod affinity rules"
  → 跳转 Step 3B（亲和性不匹配）

"had taints that pod didn't tolerate"
  → 跳转 Step 3C（污点/容忍不匹配）

"Cannot evict pod" / "would violate the pod's disruption budget"
  → 跳转 Step 3D（PDB 阻塞诊断）
```

---

### Step 3A：资源不足诊断

```
① 确认 Pod 资源请求量
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers.Resources.Requests 字段

② 获取集群节点资源用量排名
   工具：get_k8s_top_node(cluster)
   或使用 kubectl top node/pod 兜底监控：
   工具：kubectl(cluster, cmd="top nodes")
   工具：kubectl(cluster, cmd="top pods -A")
   → 识别哪些节点资源已接近饱和

③ 查看单节点详细可分配资源
   工具：get_k8s_node_resource_usage(cluster, name=<node-name>)
   → 对比 Allocatable（可分配）vs Requested（已申请）

④ 确认节点 Pod 密度
   工具：get_k8s_pod_count_running_on_node(cluster, name=<node-name>)
   → 确认是否已达 maxPods 上限。
   ⚠️ SRE 提醒：在大规模云厂商环境中（例如 AWS/阿里云使用 VPC-CNI 模式），节点的最大 Pod 数量不仅受限于 `maxPods` 参数（默认 110），更受限于节点实例规格的 ENI 弹性网卡数量和单网卡 IP 配额，实际可用上限可能远低于 110。如果 `top` 返回资源充足但依然调度失败，必须通过 `get_k8s_node_ip_usage` 排查节点 IP 配额是否用尽。
```

**诊断结论方向**：
- Pod Request 超出所有节点单节点可分配量 → 资源规格问题
- 集群总量足够但碎片化 → 资源碎片化问题
- 所有节点 Pod 数量已达上限 → Pod 密度上限问题

---

### Step 3B：节点亲和性不匹配诊断

```
① 查看 Pod 的 nodeAffinity / nodeSelector 配置
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Node-Selectors / Node Affinity 字段

② 查看所有节点的 Labels
   工具：list_k8s_node(cluster)
   → 核对节点是否有 Pod 亲和性要求的 key=value Label

③ 若需查看特定节点的详细 Labels
   工具：describe_k8s_resource(cluster, kind=Node, name=<node>, version=v1)
   → 读取 Labels 完整列表
```

> 参考：[亲和性字段说明](references/scheduling_constraints.md#node-affinity)

---

### Step 3C：污点/容忍不匹配诊断

```
① 查看各节点的 Taint
   工具：describe_k8s_resource(cluster, kind=Node, name=<node>, version=v1)
   → 读取 Taints 字段（key=value:effect）

② 查看 Pod 的 Tolerations
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Tolerations 字段

③ 对比 Node Taint 与 Pod Toleration 是否完全匹配
   → key / value / effect 三者须一致，或使用 operator=Exists
```

> 参考：[污点/容忍字段说明](references/scheduling_constraints.md#taints-and-tolerations)

---

### Step 3D：PDB 阻塞 drain 诊断

```
① 查看受影响 Namespace 的所有 PDB
   工具：list_k8s_resource(cluster, namespace, kind=PodDisruptionBudget,
         group=policy, version=v1)

② 查看具体 PDB 状态（关键字段：disruptionsAllowed）
   工具：get_k8s_resource(cluster, namespace, kind=PodDisruptionBudget,
         name=<pdb>, group=policy, version=v1)
   → 若 status.disruptionsAllowed = 0 → PDB 完全锁住驱逐

③ 查看当前该 PDB 覆盖的 Pod 情况
   工具：list_k8s_pod(cluster, namespace)
   → 结合 PDB 的 selector 判断哪些 Pod 受约束
```

> 参考：[PDB 字段说明与数学约束](references/scheduling_constraints.md#pdb)

---

### Step 4：节点健康状态诊断

```
① 查看节点详细状态和 Conditions
   工具：describe_k8s_resource(cluster, kind=Node, name=<node>, version=v1)
   → 读取 Conditions 区块（MemoryPressure / DiskPressure / PIDPressure / Ready）
   → 读取 Allocatable vs Capacity 差值（已驱逐资源量）
   ⚠️ SRE 提醒：节点状态若为 `NotReady`，在生产中极有可能是系统级负载过高（如 CPU / 磁盘 IO 打满到 100%）导致 Kubelet 进程无法及时向 APIServer 上报心跳（Heartbeat）。此时通过 `get_k8s_node_resource_usage` 获取的数据可能是过期的历史数据，应结合节点 Events 中的 "Kubelet stopped posting node status" 事件做综合研判。

② 查看节点资源用量
   工具：get_k8s_node_resource_usage(cluster, name=<node>)

③ 查看节点 IP 使用情况
   工具：get_k8s_node_ip_usage(cluster, name=<node>)
   → 若 IP 池耗尽 → 新 Pod 无法调度

④ 查看节点上的 Events
   工具：list_k8s_event(cluster, namespace=<namespace>, involvedObjectName=<node>, involvedObjectKind="Node")

⑤ 查看节点系统日志或 OOM 日志 (特别是 NotReady 节点)
   工具：get_k8s_node_system_logs(cluster, name=<node>, pattern="error|fail|kubelet")
   工具：get_k8s_node_dmesg_oom(cluster, name=<node>)
```

> 参考：[Node Condition 类型与处置指南](references/node_condition_guide.md)

---

## 诊断报告格式

> 收集充分证据后，**必须**输出以下结构化报告：

```
**Root Cause:** [因果链中最早的失败事件或错误配置]
**Evidence Chain:** [事件] → [触发] → [影响] → [用户观察到的症状]
**Confidence:** [High / Medium / Low — 置信度依据]
**Recommended Fix:** [面向运维人员的修复建议]
```

⚠️ **SRE 提醒**：K8s Events 默认只有 1 小时 TTL。如果未查询到相关 Events，不能断定没发生过故障，需在报告中声明 Events 可能已过 TTL 自动清理，并将 Confidence（置信度）适当降级。

**示例（资源不足场景）**：
> **Root Cause:** Deployment spec 将 `requests.cpu` 设置为 `8000m`，超出集群最大节点的可分配 CPU（`7800m`）。  
> **Evidence Chain:** `spec.resources.requests.cpu=8000m` 写入 → Scheduler 遍历所有节点均返回 Insufficient cpu → Pod 无法完成调度 → Pod 持续处于 Pending 状态。  
> **Confidence:** High — `get_k8s_node_resource_usage` 返回最大节点 Allocatable CPU = 7800m；`describe_k8s_pod` 返回 Pod requests.cpu = 8000m，差值直接导致调度失败。  
> **Recommended Fix:** 建议运维人员将 Deployment `spec.template.spec.containers[].resources.requests.cpu` 修改为节点可分配量以内的值（如 `2000m`），或向基础设施团队申请扩容节点。

---

## 兜底排查机制（kubectl）

当特定的节点或资源指标 MCP 工具受限或无法上报最新状态时，可使用只读 `kubectl` 兜底工具执行命令：
- 查看节点宽表及全局网络/操作系统属性：
  `工具：kubectl(cluster, cmd="get nodes -o wide")`
- 监控集群实时的节点 and Pod 资源开销：
  `工具：kubectl(cluster, cmd="top nodes")`
  `工具：kubectl(cluster, cmd="top pods -A")`
- 查看节点的详细配置与脏污/条件详情：
  `工具：kubectl(cluster, cmd="describe node <node-name>")`
