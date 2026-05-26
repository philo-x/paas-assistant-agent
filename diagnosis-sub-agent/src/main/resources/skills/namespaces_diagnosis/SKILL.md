---
name: namespaces_diagnosis
version: "1.0"
category: Namespaces
description: >
  诊断 Kubernetes Namespace 层面的问题：ResourceQuota 配额超限导致 Pod 创建失败、
  Namespace 隔离性问题（资源查询到了错误的命名空间），以及跨 Namespace 访问的
  DNS 格式错误。本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Pod 创建失败（未生成 Pod 对象），其上级控制器（ReplicaSet/StatefulSet）Events 含 exceeded quota 或 FailedCreate"
  - "kubectl get pods 查不到资源，怀疑 Namespace 错误"
  - "跨 Namespace 访问 Service 失败（配合 networking_diagnosis 使用）"
  - "某个 Namespace 资源配额使用率接近上限"
references:
  - references/quota_resource_types.md    # ResourceQuota 支持的资源类型速查
---

# namespaces_diagnosis — 命名空间诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 诊断工作流

### Step 0：命名空间自动化初步巡检（K8sGPT）

> **💡 最佳实践**：怀疑 Namespace 配额或系统异常时，直接调用 K8sGPT 获取顶层错误信息。

```
工具：analyze(namespace=<ns>, filters=["Pod", "ReplicaSet", "StatefulSet"])
→ 当怀疑 Namespace 的 Quota 满导致 Pod 创建失败时，直接针对该 namespace 运行（无需指定特定 name），K8sGPT 会自动抓取到控制器因超出 ResourceQuota 而报的 FailedCreate 事件。
```

---

### Step 1：确认 Namespace 列表

```
工具：list_k8s_namespace(cluster)
→ 确认目标 Namespace 存在且 STATUS = Active
→ 若用户描述的 Namespace 不在列表中 → 引导用户确认正确的命名空间名称
```

---

### Step 2：ResourceQuota 配额超限诊断

```
① 查看 Namespace 的 ResourceQuota 列表
   工具：list_k8s_resource(cluster, namespace, kind=ResourceQuota, version=v1)

② 查看具体配额的使用情况
   工具：get_k8s_resource(cluster, namespace, kind=ResourceQuota,
         name=<quota>, version=v1)
   或使用 kubectl 兜底命令查看 Quota 详情：
   工具：kubectl(cluster, cmd="describe resourcequota <quota> -n <namespace>")
   → 对比 status.used vs status.hard
   → Used 接近或等于 Hard → 配额即将/已耗尽

③ 查看 Namespace 内各 Pod 的资源用量
   工具：get_k8s_top_pod(cluster, namespace)
   → 识别哪些 Pod 占用了大量资源（可能是不合理的高耗资源 Pod）
   ⚠️ SRE 提醒：如果新 Pod 因配额超限未被创建，此时无法通过 list_k8s_pod 或 get_k8s_top_pod 获取该 Pod 的信息。应直接跳至下一步去排查上级控制器。

④ 查看导致 exceeded quota 的具体错误原因（由于 Quota 被 Admission Controller 拦截时，Pod 对象不会生成，因此需查询上级控制器的事件）
   工具：list_k8s_resource(cluster, namespace, kind=ReplicaSet, group=apps, version=v1)
   工具：list_k8s_resource(cluster, namespace, kind=StatefulSet, group=apps, version=v1)
   → 获取控制器名称

   工具：list_k8s_event(cluster, namespace, involvedObjectName=<replicaset-or-statefulset-name>)
   → 查找名为 `FailedCreate` 的事件，读取 Message 中的 "exceeded quota" 详情
   → 记录是哪种资源类型超限（cpu / memory / pods 数量等）
```

> 参考：[ResourceQuota 支持的资源类型](references/quota_resource_types.md)

---

### Step 3：Namespace 隔离问题诊断

```
① 跨 Namespace 查询 Pod（确认资源实际所在位置）
   工具：list_k8s_namespace(cluster)
   → 获取所有 Namespace 列表

   → 对每个相关 Namespace 执行：
   工具：list_k8s_pod(cluster, namespace=<ns>)
   或使用 kubectl 兜底命令全局检索 Pod（全命名空间）：
   工具：kubectl(cluster, cmd="get pods -A -o wide")
   → 找到实际运行的 Pod

② 确认跨 Namespace 访问使用了正确的 FQDN 格式
   → 短名称仅在同一 Namespace 内有效
   → 跨 Namespace 必须使用：<service-name>.<namespace>.svc.cluster.local
   → 参考 networking_diagnosis 的 DNS 诊断流程
```

---

## 诊断报告格式

```
**Root Cause:** [因果链中最早的失败事件或错误配置]
**Evidence Chain:** [事件] → [触发] → [影响] → [用户观察到的症状]
**Confidence:** [High / Medium / Low — 置信度依据]
**Recommended Fix:** [面向运维人员的修复建议]
```

⚠️ **SRE 提醒**：K8s Events 默认只有 1 小时 TTL。如果未查询到相关 Events，不能断定没发生过故障，需在报告中声明 Events 可能已过 TTL 自动清理，并将 Confidence（置信度）适当降级。

**示例（ResourceQuota 超限）**：
> **Root Cause:** Namespace "production" 的 ResourceQuota 将 `requests.cpu` 限制为 "4"，当前 `used=3.8`，新 Pod 申请 `requests.cpu=500m` 导致总量超限（3.8+0.5=4.3 > 4）。  
> **Evidence Chain:** ResourceQuota `hard.requests.cpu="4"` 配置 → 当前 `used.requests.cpu="3.8"` → 新 Pod 申请 500m → Admission Controller 检测到超限，拒绝创建 → Pod 无法启动，上级控制器 ReplicaSet 的 Events 显示 `FailedCreate` 且包含 "exceeded quota"。  
> **Confidence:** High — `get_k8s_resource(ResourceQuota)` 返回 `used=3.8/hard=4`；对 ReplicaSet 的 `list_k8s_event` 返回 `FailedCreate` 且 Message 包含 "forbidden: exceeded quota"。  
> **Recommended Fix:** 建议运维人员选择以下方案之一：① 将新 Deployment 的 `resources.requests.cpu` 减小至剩余可用量（如 100m）；② 联系平台管理员申请扩大该 Namespace 的 ResourceQuota（需说明业务诉求）；③ 排查并清理 Namespace 内已不再使用的 Deployment/Pod 以释放配额。

---

## 兜底排查机制（kubectl）

在进行命名空间层面的资源或隔离诊断遇到工具受限时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查全局 Namespace 列表及状态：
  `工具：kubectl(cluster, cmd="get namespaces")`
- 全局跨 Namespace 查看 Pod 信息：
  `工具：kubectl(cluster, cmd="get pods -A -o wide")`
- 查看资源配额（ResourceQuota）详细约束与实际额度：
  `工具：kubectl(cluster, cmd="describe resourcequota <quota-name> -n <namespace>")`
