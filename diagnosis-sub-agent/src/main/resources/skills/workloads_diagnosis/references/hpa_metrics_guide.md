# HPA 指标配置与 metrics-server 排查

本文档为 `workloads_diagnosis` Skill 的参考资料，说明 HPA 指标类型和 metrics-server 常见问题。

---

## HPA 指标类型

| 指标类型 | 来源 | 配置字段 |
|---------|------|---------|
| Resource（资源指标） | metrics-server | `spec.metrics[].type: Resource` |
| Custom（自定义指标） | Prometheus Adapter 等 | `spec.metrics[].type: Pods` / `Object` |
| External（外部指标） | 外部系统 | `spec.metrics[].type: External` |

**TARGETS 列显示 `<unknown>` 的原因**：
- Resource 指标：metrics-server 不可用
- Custom/External 指标：对应的 Metrics API 实现不可用

---

## metrics-server 常见问题

### 问题 1：TLS 证书验证失败（本地集群）
```
日志关键字：
  "x509: certificate signed by unknown authority"
  "failed to verify certificate"

原因：metrics-server 尝试通过 HTTPS 访问 kubelet，但 kubelet 使用自签名证书
适用场景：Minikube、Kind、kubeadm 自建集群

排查工具：
  get_k8s_pod_logs(cluster, namespace=kube-system, name=<metrics-server-pod>)
```

### 问题 2：metrics-server 未安装
```
现象：kube-system 中没有 metrics-server Deployment

排查工具：
  list_k8s_resource(cluster, namespace=kube-system, kind=Deployment)
  → 没有 metrics-server → 未安装
```

### 问题 3：metrics-server 运行但 API 不可达
```
日志关键字：
  "unable to fully scrape metrics from node"
  "connection refused"

原因：节点 kubelet 端口（10250）被防火墙或 NetworkPolicy 阻断
```

---

## HPA 状态字段说明

```yaml
status:
  currentReplicas: 3          # 当前副本数
  desiredReplicas: 5          # HPA 计算的目标副本数
  currentMetrics:             # 当前指标值
    - type: Resource
      resource:
        name: cpu
        current:
          averageUtilization: 80   # 当前 CPU 利用率
  conditions:                 # HPA 状态条件
    - type: ScalingActive     # False = HPA 无法获取指标
    - type: AbleToScale       # False = 无法扩缩容（副本数已达 min/max）
    - type: ScalingLimited    # True = 副本数被 min/max 限制
```

> 诊断工具：`get_k8s_resource(cluster, kind=HorizontalPodAutoscaler, group=autoscaling, version=v2, name=<hpa>)` 读取 status 字段
