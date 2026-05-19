---
name: workloads_diagnosis
version: "1.0"
category: Workloads
description: >
  诊断 Deployment、StatefulSet、DaemonSet 等工作负载的生命周期问题：副本数为零、
  滚动更新卡住、HPA 无法获取指标、蓝绿/金丝雀发布流量异常，以及有状态应用
  误用 Deployment 导致数据不一致。本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Deployment AVAILABLE 副本数为 0，服务不可用"
  - "kubectl rollout status 长时间未完成"
  - "新版本发布期间服务完全中断"
  - "蓝绿发布切换后用户仍访问旧版本"
  - "金丝雀版本流量占比与预期不符"
  - "HPA TARGETS 列显示 unknown，不触发扩缩容"
  - "StatefulSet Pod 重启后数据丢失，Pod 名称随机变化"
references:
  - references/rollout_strategy_guide.md   # 滚动更新策略参数说明
  - references/hpa_metrics_guide.md        # HPA 指标配置与 metrics-server 排查
---

# workloads_diagnosis — 工作负载诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 诊断工作流

### Step 1：确认工作负载状态

```
工具：list_k8s_resource(cluster, namespace, kind=Deployment, group=apps, version=v1)
→ 观察 READY / UP-TO-DATE / AVAILABLE 三列

AVAILABLE = 0 且 UP-TO-DATE = 0 → Step 2A（副本数为零）
AVAILABLE = 0 且 UP-TO-DATE 持续不变（发布中） → Step 2B（滚动更新卡住）
HPA 相关 → Step 2C（HPA 指标问题）
流量分配异常（蓝绿/金丝雀） → Step 2D（发布策略诊断）
StatefulSet 问题 → Step 2E（有状态应用诊断）
```

---

### Step 2A：副本数为零诊断

```
① 确认当前副本配置
   工具：get_k8s_resource(cluster, namespace, kind=Deployment, name=<name>,
         group=apps, version=v1)
   → 读取 spec.replicas 字段

② 确认是否有历史停止注解
   → 读取 metadata.annotations 中是否存在副本数记录
   （stop_k8s_deployment 会将原始副本数写入注解）

③ 查看 Deployment 相关事件
   工具：list_k8s_deploy_event(cluster, namespace, name=<name>)
   → 判断是人为操作还是异常触发
```

---

### Step 2B：滚动更新卡住诊断

```
① 查看发布状态
   工具：get_k8s_deployment_rollout_status(cluster, namespace, name=<name>)
   → 若长时间显示 "Waiting for deployment xxx rollout to finish" → 卡住

② 查看发布历史（识别新旧版本信息）
   工具：get_k8s_deployment_rollout_history(cluster, namespace, name=<name>)
   → 记录当前版本号（revision）

③ 查看 Deployment 详细信息（关键：确认是否超时）
   工具：get_k8s_resource(cluster, namespace, kind=Deployment, name=<name>, group=apps, version=v1)
   → 读取 status.conditions，确认是否存在 Type=Progressing 且 Reason=ProgressDeadlineExceeded 状况。
   （K8s 默认 600s 未完成滚动更新将标记此超时错误）

④ 查看 Deployment Events
   工具：list_k8s_deploy_event(cluster, namespace, name=<name>)
   → 找 FailedCreate / ImagePullBackOff / Insufficient 等事件

⑤ 查看新版本 Pod 的具体错误
   工具：list_k8s_pod(cluster, namespace)
   → 找 STATUS 不为 Running 的新版本 Pod
   工具：list_k8s_pod_event(cluster, namespace, name=<new-pod>)
   → 确认卡住原因（镜像错误 / 资源不足 / 配置错误）

⑥ 检查更新策略配置
   → 读取 spec.strategy.rollingUpdate.maxUnavailable 和 maxSurge
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
   → 查看 HPA 的 Events 记录（通过 describe_k8s_resource(HPA) 或 list_k8s_event）
   ⚠️ SRE 提醒：HPA 缩容有默认 5 分钟（缩容）或根据 behavior 配置的冷却期。如果指标满足条件但未发生动作，检查 Events 中是否提示由于 cooldown 窗口未过而限制了操作，不可仅判定 metrics-server 故障。
```

> 参考：[HPA 指标配置与 metrics-server 排查](references/hpa_metrics_guide.md)

---

### Step 2D：蓝绿/金丝雀发布流量诊断

```
① 查看 Service 的当前 selector
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.selector

② 查看各版本 Deployment 的 Pod Labels
   工具：list_k8s_resource(cluster, namespace, kind=Deployment,
         group=apps, version=v1)
   → 对比 Blue 和 Green 的 Pod template labels

③ 确认当前流量指向哪个版本
   → Service selector 中的 label 匹配哪个 Deployment 的 Pod

④ 查看各版本副本数（金丝雀分流比例）
   工具：list_k8s_resource(cluster, namespace, kind=Deployment,
         group=apps, version=v1)
   → 计算实际流量比 = canary.replicas / (stable.replicas + canary.replicas)
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

## 诊断报告格式

```
**Root Cause:** [因果链中最早的失败事件或错误配置]
**Evidence Chain:** [事件] → [触发] → [影响] → [用户观察到的症状]
**Confidence:** [High / Medium / Low — 置信度依据]
**Recommended Fix:** [面向运维人员的修复建议]
```

⚠️ **SRE 提醒**：K8s Events 默认只有 1 小时 TTL。如果未查询到相关 Events，不能断定没发生过故障，需在报告中声明 Events 可能已过 TTL 自动清理，并将 Confidence（置信度）适当降级。

**示例（HPA metrics-server 缺失）**：
> **Root Cause:** 集群未安装 metrics-server，HPA 控制器无法从 metrics API 获取 Pod 的 CPU 使用率。  
> **Evidence Chain:** metrics-server 缺失 → HPA 调用 `metrics.k8s.io` API 失败 → HPA `status.currentMetrics` 为空，TARGETS 显示 `<unknown>` → HPA 无法计算目标副本数，replicas 始终停留在 minReplicas。  
> **Confidence:** High — `list_k8s_resource(kube-system, kind=Deployment)` 未找到 metrics-server；`get_k8s_deployment_hpa_list` 返回 `TARGETS=<unknown>/50%`。  
> **Recommended Fix:** 建议运维人员在 kube-system 命名空间安装 metrics-server（官方 components.yaml），本地集群需额外添加 `--kubelet-insecure-tls` 启动参数。
