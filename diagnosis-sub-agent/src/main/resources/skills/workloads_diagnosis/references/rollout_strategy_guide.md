# 滚动更新策略参数说明

本文档为 `workloads_diagnosis` Skill 的参考资料，说明 Deployment 滚动更新策略字段含义。

---

## RollingUpdate 策略参数

| 字段 | 默认值 | 含义 |
|------|--------|------|
| `maxUnavailable` | 25% | 更新期间最多允许多少个 Pod 不可用（整数或百分比） |
| `maxSurge` | 25% | 更新期间最多允许超出 `replicas` 多少个额外 Pod |

---

## 常见策略配置与效果

### 零停机更新（推荐）
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0    # 始终保持全部副本可用
    maxSurge: 1          # 每次新起 1 个新版本 Pod，确认 Ready 后再停旧版本
```

### 快速更新（可接受短暂减少容量）
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 1    # 最多停 1 个旧版本
    maxSurge: 1          # 最多多跑 1 个新版本
```

### 危险配置（造成发布期间服务中断）
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 100%  # 所有旧 Pod 同时停止！
    maxSurge: 0           # 不允许超出 replicas → 先全部停止旧版本，再启动新版本
```

---

## 发布卡住的常见原因

| 现象 | 可能原因 | 诊断工具 |
|------|---------|---------|
| 新 Pod ImagePullBackOff | 镜像 Tag 不存在 | `list_k8s_pod_event` |
| 新 Pod Pending | 资源不足（配合 nodes_diagnosis） | `list_k8s_pod_event` |
| 新 Pod CrashLoopBackOff | 新版本应用崩溃 | `get_k8s_pod_logs(previous=true)` |
| 新 Pod Readiness 一直 false | Readiness Probe 失败 | `list_k8s_pod_event` + `diagnose_k8s_pod_network` / `run_command_in_k8s_pod` |
| Deployment Progress Deadline 超时 | `progressDeadlineSeconds` 配置过短 | `get_k8s_resource(kind=Deployment)` 读取该字段 |
