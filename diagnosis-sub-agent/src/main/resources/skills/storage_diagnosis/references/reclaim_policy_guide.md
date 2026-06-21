# PV 回收策略说明与数据风险

本文档为 `storage_diagnosis` Skill 的参考资料，说明 PV 回收策略（ReclaimPolicy）的含义和数据安全影响。

---

## 回收策略对比

| 策略 | 触发条件 | 数据结果 | 风险等级 |
|------|---------|---------|---------|
| `Retain` | PVC 被删除 | **数据保留**，PV 进入 Released 状态，需手动清理 | 🟢 安全 |
| `Delete` | PVC 被删除 | **物理存储同步删除**（如 EBS/GCE Disk 被彻底删除） | 🔴 极危险 |
| `Recycle`（已废弃） | PVC 被删除 | 对卷执行 `rm -rf`，然后标记为可重用 | 🟡 中等风险 |

---

## Retain 策略下的 PV 生命周期

```
PVC 被删除
    ↓
PV 进入 Released 状态（数据保留）
    ↓
需手动处理（运维操作）：
  1. 备份/迁移数据
  2. 清除 PV 的 claimRef 字段（让 PV 重新变为 Available）
  3. 或直接删除 PV（此时才真正影响底层存储）
```

---

## 诊断时的风险提示

当发现 PV 使用 `Delete` 策略时，**必须在诊断报告中明确标注**：

```
⚠️ 数据风险警告：
当前 PV "<pv-name>" 的 reclaimPolicy=Delete。
若用户操作导致关联 PVC "<pvc-name>" 被删除，底层存储将被永久删除，数据无法恢复。
建议运维人员：
  1. 执行任何存储变更前，先完成数据备份
  2. 评估是否将 reclaimPolicy 修改为 Retain（需运维人员执行）
  3. 确认应用是否有定期快照或外部备份机制
```

---

## StorageClass 默认回收策略

不同 StorageClass 的默认 reclaimPolicy 不同：

| StorageClass 类型 | 默认 reclaimPolicy |
|----------------|-------------------|
| 云厂商（EBS/GCE Disk/Azure Disk） | Delete（危险！） |
| NFS 类 | Retain |
| Local Storage | Delete |
| 自定义 StorageClass | 由 `reclaimPolicy` 字段决定 |

> 诊断时查看 StorageClass 详情：`get_k8s_resource(cluster, kind=StorageClass, name=<sc>)` → 读取 `reclaimPolicy` 字段
