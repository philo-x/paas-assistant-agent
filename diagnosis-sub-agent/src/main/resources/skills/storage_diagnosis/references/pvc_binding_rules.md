# PVC/PV 绑定三要素与诊断规则

本文档为 `storage_diagnosis` Skill 的参考资料，说明 PVC 绑定到 PV 的必要条件。

---

## 绑定三要素

PVC 能绑定到 PV，必须同时满足以下三个条件：

| 要素 | PVC 字段 | PV 字段 | 匹配规则 |
|------|---------|---------|---------|
| StorageClass | `spec.storageClassName` | `spec.storageClassName` | 必须完全一致 |
| AccessModes | `spec.accessModes` | `spec.accessModes` | PV 的 accessModes 必须包含 PVC 要求的所有模式 |
| 容量 | `spec.resources.requests.storage` | `spec.capacity.storage` | PV 容量 ≥ PVC 请求容量 |

---

## AccessModes 含义

| 模式 | 缩写 | 含义 |
|------|------|------|
| `ReadWriteOnce` | RWO | 只允许**一个节点**以读写模式挂载 |
| `ReadOnlyMany` | ROX | 允许**多个节点**以只读模式挂载 |
| `ReadWriteMany` | RWX | 允许**多个节点**以读写模式挂载 |
| `ReadWriteOncePod` | RWOP | 只允许**一个 Pod** 以读写模式挂载（K8s 1.22+） |

> ⚠️ **StatefulSet 多副本注意**：若多个 Pod 运行在不同节点且需要独立存储，不能使用 RWO 模式共享同一个 PV，必须使用 `volumeClaimTemplates` 为每个 Pod 动态制备独立的 PVC。

---

## 常见绑定失败场景

### 场景 1：StorageClass 不存在
```
PVC storageClassName = "fast-ssd"
集群中存在的 SC = ["standard", "gp2"]

→ 无法找到匹配的 StorageClass → 无法触发动态制备 → PVC 永久 Pending
```

### 场景 2：没有 Default StorageClass
```
PVC storageClassName 未指定（空值）
集群没有标注 is-default-class=true 的 StorageClass

→ K8s 无法自动选择 SC → PVC Pending
```

### 场景 3：PV 容量不足
```
PVC requests.storage = "100Gi"
可用 PV capacity.storage = "50Gi"

→ PV 容量不满足 → 无法绑定（即使其他条件都满足）
```

### 场景 4：AccessMode 不兼容
```
PVC accessModes = ["ReadWriteMany"]
PV accessModes = ["ReadWriteOnce"]

→ PV 不支持 RWX → 无法绑定
```

---

## PVC 状态机

```
PVC 创建 → Pending（等待绑定）
         → Bound（成功绑定到 PV）
         → Lost（PV 被删除，PVC 还存在）

PV 状态机（回收策略相关）：
Available → Bound（绑定到 PVC）
Bound → Released（PVC 被删除，Retain 策略）
Released → Available（手动清除 claimRef 后重新可用）
Bound → Deleted（PVC 被删除，Delete 策略，存储被物理删除）
```
