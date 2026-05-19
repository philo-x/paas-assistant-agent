# Node 调度约束字段参考

本文档为 `nodes_diagnosis` Skill 的深度参考资料，包含亲和性、污点/容忍和 PDB 的字段说明与决策逻辑。

---

## Node Affinity

节点亲和性通过 `spec.affinity.nodeAffinity` 配置，分为两类：

| 类型 | 字段路径 | 语义 |
|------|---------|------|
| 强制要求 | `requiredDuringSchedulingIgnoredDuringExecution` | 节点**必须**满足条件，否则 Pod 不会被调度 |
| 优先偏好 | `preferredDuringSchedulingIgnoredDuringExecution` | 节点**尽量**满足，不满足时仍可调度 |

**常见诊断场景**：

```
Events 含 "didn't match node affinity rules"
→ 原因：required 规则中的 matchExpressions 在集群中没有节点满足

诊断步骤：
1. describe_k8s_pod → 读取 nodeAffinity.required.nodeSelectorTerms[].matchExpressions
   → 记录要求的 key、operator、values
2. list_k8s_node → 检查是否有节点具备对应 Label
```

**matchExpressions operator 含义**：

| Operator | 语义 |
|---------|------|
| `In` | 节点 Label 的值在指定列表中 |
| `NotIn` | 节点 Label 的值不在指定列表中 |
| `Exists` | 节点有该 Label Key（不管值） |
| `DoesNotExist` | 节点没有该 Label Key |
| `Gt` / `Lt` | 节点 Label 值大于/小于指定数值 |

---

## Taints and Tolerations

污点（Taint）加在 Node 上，容忍（Toleration）加在 Pod 上。

### Taint Effect 含义

| Effect | 含义 |
|--------|------|
| `NoSchedule` | 不容忍该污点的 Pod 不会被调度到此节点（已在此节点上的 Pod 不受影响） |
| `PreferNoSchedule` | 尽量不调度到此节点，但非强制 |
| `NoExecute` | 不容忍的 Pod 不会被调度，且**已在此节点上的 Pod 会被驱逐** |

### 匹配规则

Toleration 与 Taint 匹配需要同时满足：

```
toleration.key     == taint.key      （或 operator=Exists 时不需要匹配 value）
toleration.value   == taint.value
toleration.effect  == taint.effect   （或 effect 为空时匹配所有 effect）
```

### 诊断对比表

```
Node Taint:       key=dedicated, value=gpu, effect=NoSchedule
Pod Toleration:   key=dedicated, operator=Equal, value=cpu, effect=NoSchedule

→ value 不匹配（gpu ≠ cpu）→ Pod 无法被调度到此节点
```

---

## PodDisruptionBudget (PDB)

PDB 用于保护应用在维护期间的最小可用副本数。

### 关键字段

| 字段 | 含义 |
|------|------|
| `spec.minAvailable` | 维护期间至少保持可用的 Pod 数量（整数或百分比） |
| `spec.maxUnavailable` | 维护期间允许不可用的最大 Pod 数量 |
| `status.disruptionsAllowed` | 当前允许驱逐的 Pod 数量（= 当前可用数 - minAvailable） |
| `status.currentHealthy` | 当前健康（Ready）的 Pod 数量 |
| `status.desiredHealthy` | 期望健康的 Pod 数量 |

### 诊断逻辑

```
disruptionsAllowed = 0 的原因：
  → 当前 currentHealthy == minAvailable（再驱逐一个就低于最小值）

若 minAvailable=3，currentHealthy=3 → disruptionsAllowed=0 → drain 完全阻塞

修复建议方向（运维人员执行）：
  1. 临时调整 PDB 的 minAvailable（维护窗口期）
  2. 先扩容至更多副本，再执行 drain
  3. 确认是否有 Pod 处于 NotReady 状态导致 currentHealthy 偏低
```

---

## 节点资源计算公式

```
可用于新 Pod 调度的资源 = Allocatable - 已分配 Request 总量

Allocatable = Capacity - Node 系统预留（kube-reserved + system-reserved）

调度失败条件：
  Pod Request.cpu  > max(所有节点的可用 CPU)
  Pod Request.mem  > max(所有节点的可用 Memory)
```
