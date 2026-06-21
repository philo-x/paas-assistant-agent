# ResourceQuota 支持的资源类型速查

本文档为 `namespaces_diagnosis` Skill 的参考资料，列出 ResourceQuota 可以限制的资源类型。

---

## 计算资源配额

| 字段 | 含义 |
|------|------|
| `requests.cpu` | 所有 Pod 的 CPU Request 总量上限 |
| `limits.cpu` | 所有 Pod 的 CPU Limit 总量上限 |
| `requests.memory` | 所有 Pod 的内存 Request 总量上限 |
| `limits.memory` | 所有 Pod 的内存 Limit 总量上限 |
| `requests.ephemeral-storage` | 临时存储 Request 总量上限 |
| `limits.ephemeral-storage` | 临时存储 Limit 总量上限 |

---

## 对象数量配额

| 字段 | 含义 |
|------|------|
| `count/pods` | Pod 总数上限 |
| `count/deployments.apps` | Deployment 数量上限 |
| `count/replicasets.apps` | ReplicaSet 数量上限 |
| `count/statefulsets.apps` | StatefulSet 数量上限 |
| `count/services` | Service 数量上限 |
| `count/services.loadbalancers` | LoadBalancer 类型 Service 数量上限 |
| `count/configmaps` | ConfigMap 数量上限 |
| `count/secrets` | Secret 数量上限 |
| `count/persistentvolumeclaims` | PVC 数量上限 |

---

## 诊断关键点

```
用户看到 "exceeded quota" 时，Events 通常会包含：
  "forbidden: [maximum number of objects: pods.count]"
  "forbidden: [cpu request is over quota]"

查看具体超出的资源：
  get_k8s_resource(cluster, kind=ResourceQuota, name=<quota>)
  → status.used.<resource> >= status.hard.<resource>

计算剩余可用量：
  剩余 = hard - used
  若新 Pod 的 request > 剩余 → 创建失败
```

---

## LimitRange 与 ResourceQuota 的区别

| 对象 | 作用 |
|------|------|
| `LimitRange` | 限制**单个 Pod/Container** 的资源范围（min/max/default） |
| `ResourceQuota` | 限制**整个 Namespace** 的资源总量 |

> 注意：若 Namespace 有 LimitRange 设置了 `default limits`，而 Pod 未显式设置 limits，则 LimitRange 会自动注入 limits。这会影响 ResourceQuota 的 limits 配额计算。
