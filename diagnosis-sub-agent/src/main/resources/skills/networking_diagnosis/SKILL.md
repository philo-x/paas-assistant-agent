---
name: networking_diagnosis
version: "1.0"
category: Networking
description: >
  诊断 Kubernetes Service 连通性、DNS 解析、Ingress 路由及 NetworkPolicy 流量控制问题。
  包括 Selector 不匹配、targetPort 错误、跨 Namespace DNS 失败、
  Ingress 路径 404 及 NetworkPolicy 静默丢包等场景。
  本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Service 的 Endpoints 为空（none）"
  - "访问 Service 返回 connection refused"
  - "Pod 间通信返回 connection timed out（非 refused）"
  - "DNS 解析失败（nslookup 无结果）"
  - "Ingress 路由返回 404 Not Found"
  - "LoadBalancer Service 的 EXTERNAL-IP 长期为 pending"
  - "有状态应用 Pod 之间无法通过 DNS 互相发现"
  - "用户 Session 频繁丢失（请求被路由到不同 Pod）"
references:
  - references/dns_naming_conventions.md      # K8s DNS 命名规则与 FQDN 格式
  - references/networkpolicy_syntax_guide.md  # NetworkPolicy 规则语法与调试
---

# networking_diagnosis — 网络与服务诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 关键诊断线索

| 错误表现 | 区分含义 |
|---------|---------|
| `connection refused` | 连接被对端**主动拒绝**（Port 不匹配/服务未监听） |
| `connection timed out` | 连接被**静默丢弃**（NetworkPolicy 拦截） |
| DNS 解析失败 | 名称错误 / Namespace 不匹配 / CoreDNS 异常 |

## 诊断工作流

### Step 1：确认 Endpoints 状态

```
① 查看 Pod 关联的 Service 和 Endpoints
   工具：get_k8s_pod_linked_services(cluster, namespace, name=<pod>)
   → 获取 Service 名称列表

   工具：get_pod_linked_endpoints(cluster, namespace, name=<pod>)
   → 若 Endpoints 为空 → 跳转 Step 2A（Selector 问题）
   → 若 Endpoints 不空但连接失败 → 跳转 Step 2B（Port 问题）

② 确认是否有 NetworkPolicy
   工具：list_k8s_resource(cluster, namespace, kind=NetworkPolicy,
         group=networking.k8s.io, version=v1)
   → 若有 NetworkPolicy → 结合 Step 2D 一起排查
```

---

### Step 2A：Service Endpoints 为空诊断

⚠️ **SRE 提醒：Endpoints 为空在生产中有三种常见原因**：
1. **Selector 彻底不匹配**：Service 声明的 labels 没有任何 Pod 匹配。
2. **Pod 状态全为 NotReady**：有 Pod 匹配，但它们因探针失败等原因均不处于 Ready 状态。此时，如果 Service 没有配置 `publishNotReadyAddresses: true`，Endpoints 将依旧为空。
3. **部分 Labels 缺失**：Pod 虽然 Ready，但未打齐 Service 要求的全部 Selector Labels。

```
① 获取 Service 的 selector
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.selector 字段

② 查看 Pod 实际 Labels 和就绪状态（检查 ready 字段与状态）
   工具：list_k8s_pod(cluster, namespace)
   → 对比所有 Pod 的 Labels 与 Service selector：
     - 若没有任何 Pod Labels 匹配 Service selector → 原因 1（Selector 不匹配）
     - 若 Pod labels 匹配，但所有 matching Pods 的 READY 均为 0/1（或 Status 不为 Running） → 原因 2（Pod 未就绪，探针挂了）
     - 若 Pod 只有部分 label 匹配（如少了一个键值对） → 原因 3（Labels 缺失）
```

**判断对比**：
```
Service spec.selector:  {app: "frontend", env: "prod"}
Pod metadata.labels:    {app: "frontend", env: "staging"}
                                                    ↑ 不一致 → Endpoints 为空
```

---

### Step 2B：targetPort 与容器端口不一致

```
① 查看 Service 端口配置
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.ports[].targetPort

② 查看 Pod 容器声明的端口
   工具：describe_k8s_pod(cluster, namespace, name=<pod>)
   → 读取 Containers.Ports

③ 在 Pod 内直接测试应用监听端口
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod>, container=<c>,
         command="wget", args=["-qO-", "--timeout=3", "http://localhost:<port>/"])
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，表明镜像为 scratch/distroless 等精简镜像。这不是应用错误，请使用间接证据（如容器声明的端口、容器日志中是否正常 bind 端口）进行推断。
   → 逐一测试不同端口，找到实际监听端口
```

---

### Step 2C：DNS 解析失败

```
① 在 Source Pod 内测试短名称解析
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod>, container=<c>,
         command="nslookup", args=["<service-name>"])
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，说明镜像精简，不可直接调用 nslookup 测试。应改用 Service IP 的直连连通性进行推断。

② 若失败，测试 FQDN 格式
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod>, container=<c>,
         command="nslookup", args=["<service-name>.<target-ns>.svc.cluster.local"])
   → 若 FQDN 成功但短名称失败 → 跨 Namespace 访问使用了短名称

③ 测试 CoreDNS 是否健康
   工具：list_k8s_pod(cluster, namespace=kube-system)
   → 找 coredns Pod
   工具：get_k8s_pod_logs(cluster, namespace=kube-system, name=<coredns-pod>, tail=50)
   → 查找错误日志
```

> 参考：[DNS 命名规则与 FQDN 格式](references/dns_naming_conventions.md)

---

### Step 2D：NetworkPolicy 流量拦截

```
① 确认错误类型（timeout vs refused）
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod>, container=<c>,
         command="wget", args=["--timeout=5", "http://<target-ip>:<port>/"])
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，说明镜像为 scratch/distroless 等精简镜像。这不代表服务异常，请根据 NetworkPolicy 规则定义和 Labels 匹配进行逻辑判断，不要盲目断定超时。
   → timeout → NetworkPolicy 静默丢弃
   → refused → 端口问题，回到 Step 2B

② 查看 Target Namespace 的所有 NetworkPolicy
   工具：list_k8s_resource(cluster, namespace=<target-ns>, kind=NetworkPolicy,
         group=networking.k8s.io, version=v1)

③ 查看具体 NetworkPolicy 规则
   工具：get_k8s_resource(cluster, namespace, kind=NetworkPolicy,
         group=networking.k8s.io, version=v1, name=<policy>)
   → 读取 spec.podSelector / spec.ingress / spec.egress
   → 判断是否存在 deny-all（podSelector:{} + 无规则）
   → 判断允许规则的 podSelector 是否覆盖 Source Pod 的 Labels
```

> 参考：[NetworkPolicy 规则语法](references/networkpolicy_syntax_guide.md)

---

### Step 2E：Ingress 路径 404

```
① 查看 Pod 关联的 Ingress 规则
   工具：get_pod_linked_ingresses(cluster, namespace, name=<pod>)
   → 读取 rules[].http.paths[].path / pathType / backend

② 验证后端服务实际路由前缀
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod>, container=<c>,
         command="wget", args=["-qO-", "http://localhost:<port>/<actual-path>"])
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，说明镜像缺失 wget，请改用间接指标（如 Endpoints 状态或 Readiness 记录）判断。
   → 与 Ingress path 对比

③ 确认 IngressClass 是否正确
   工具：get_k8s_resource(cluster, namespace, kind=Ingress,
         group=networking.k8s.io, version=v1, name=<ingress>)
   → 读取 metadata.annotations["kubernetes.io/ingress.class"]
   工具：list_k8s_resource(cluster, kind=IngressClass,
         group=networking.k8s.io, version=v1)
   → 确认是否有 default IngressClass
```

---

### Step 2F：StatefulSet Headless Service 诊断

```
① 确认 Service 类型
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.clusterIP 字段
   → 若非 None → 不是 Headless Service，Pod 间 DNS 无法直接解析

② 验证 Pod 间 DNS 解析（Headless Service 模式）
   工具：run_command_in_k8s_pod(cluster, namespace, name=<pod-0>, container=<c>,
         command="nslookup",
         args=["<pod-1>.<headless-svc>.<ns>.svc.cluster.local"])
   ⚠️ SRE 提醒：如果执行返回 "executable file not found"，说明镜像缺失 nslookup，请用 Service IP 连通性进行推断。
   → 若解析失败且 Service 非 Headless → 确认问题根因
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

**示例（Selector 不匹配）**：
> **Root Cause:** Service `spec.selector` 配置为 `{app: frontend}`，而 Deployment Pod template 的 labels 为 `{app: backend}`，Selector 无法匹配任何 Pod。  
> **Evidence Chain:** Service selector `{app:frontend}` 与 Pod labels `{app:backend}` 不匹配 → Endpoints 控制器找不到符合条件的 Pod → Service Endpoints 列表为空 → 所有到 Service 的请求无目标，返回 connection refused。  
> **Confidence:** High — `get_k8s_resource(Service)` 返回 selector={app:frontend}；`list_k8s_pod` 返回 Pod labels={app:backend}；`get_pod_linked_endpoints` 返回 Endpoints 为空。  
> **Recommended Fix:** 建议运维人员将 Service 的 `spec.selector` 修改为 `{app: backend}` 与 Pod labels 一致，或将 Deployment Pod template labels 修改为 `{app: frontend}`。
