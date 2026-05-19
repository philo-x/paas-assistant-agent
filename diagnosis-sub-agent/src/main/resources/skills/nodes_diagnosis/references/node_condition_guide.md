# Node Condition 类型与诊断指南

本文档为 `nodes_diagnosis` Skill 的参考资料，描述节点状态条件（Conditions）的含义和对应诊断思路。

---

## Node Conditions 一览

通过 `describe_k8s_resource(kind=Node, name=<node>)` 可查看节点的 Conditions 列表。

| Condition | Status=True 的含义 | 严重性 |
|-----------|------------------|--------|
| `Ready` | 节点健康，可接受 Pod 调度 | — |
| `MemoryPressure` | 节点内存不足（触发了 eviction threshold） | 🔴 高 |
| `DiskPressure` | 节点磁盘（镜像/日志）空间不足 | 🔴 高 |
| `PIDPressure` | 节点进程数接近操作系统上限 | 🟡 中 |
| `NetworkUnavailable` | 节点网络插件未就绪 | 🔴 高 |

> **诊断规则**：`Ready=False` 或 `Ready=Unknown` 均表示节点异常，需结合其他 Condition 判断根因。

---

## MemoryPressure 诊断路径

```
现象：节点 MemoryPressure=True
→ kubelet 检测到节点内存使用超过 eviction.hard 阈值（默认 memory.available<100Mi）

诊断步骤：
1. get_k8s_node_resource_usage(node) → 确认内存使用率
2. get_k8s_top_pod(namespace) → 找到内存占用最高的 Pod
3. get_k8s_pod_resource_usage(namespace, name=<pod>) → 确认是否存在内存超限

后果：
- kubelet 开始驱逐内存占用最高的 Pod（QoS 等级 BestEffort 优先）
- 被驱逐 Pod 会出现 Evicted 状态

修复建议（运维人员执行）：
- 为高耗内存 Pod 设置合理的 limits.memory
- 清理无用的 Docker 镜像（节点磁盘）
- 升级节点规格或添加节点
```

---

## DiskPressure 诊断路径

```
现象：节点 DiskPressure=True
→ 节点 /var/lib/kubelet（镜像/容器层）或 /var/lib/docker 磁盘使用超阈值

诊断步骤：
1. describe_k8s_resource(kind=Node, name=<node>) → 读取 DiskPressure Condition 的 Message
2. list_k8s_pod(namespace) + list_k8s_pod_event(pod) → 确认是否有 Pod 因 Evicted 被驱逐
3. get_k8s_pod_logs(pod) → 确认应用是否大量写日志到容器内

后果：
- 镜像拉取可能失败（无空间存储新镜像）
- Pod 可能被驱逐

修复建议（运维人员执行）：
- 清理未使用的镜像（docker image prune）
- 修改应用日志输出位置（从 /var/log 改为挂载的外部卷）
- 扩容节点磁盘
```

---

## NotReady 综合诊断流程

```
节点 Ready=False，按以下顺序排查：

1. 检查其他 Conditions（MemoryPressure / DiskPressure / PIDPressure）
   → 若有压力条件 True → 按上述流程处理

2. 检查网络（NetworkUnavailable）
   → NetworkUnavailable=True → CNI 插件问题
   → 检查 kube-system 命名空间中的 CNI DaemonSet Pod 状态

3. 检查 kubelet 是否正常
   → 通过节点 Events 查看（list_k8s_event involvedObjectName=<node>）
   → 找 "NodeNotReady" / "Kubelet stopped posting node status" 等事件

4. 检查节点资源是否耗尽（CPU/内存/IP）
   → get_k8s_node_resource_usage / get_k8s_node_ip_usage
```

---

## Node IP 耗尽

```
现象：新 Pod 创建成功但一直处于 Pending，Events 含 "no IP address available in range"

诊断：
工具：get_k8s_node_ip_usage(cluster, name=<node>)
→ 查看 IP 池使用情况（已用 / 总量）

节点 IP 来源：
- 每个节点从集群 CIDR 分配一个 Pod CIDR（如 /24 = 254 个可用 IP）
- maxPods 配置（默认 110）也会限制同一节点的 Pod 数上限

修复建议（运维人员执行）：
- 清理 Terminating 状态的僵尸 Pod
- 调整 node.podCIDR 分配更大的子网（需重建集群节点）
- 添加新节点
```
