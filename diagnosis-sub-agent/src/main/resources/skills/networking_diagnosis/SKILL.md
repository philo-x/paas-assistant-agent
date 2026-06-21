---
name: networking_diagnosis
version: "1.2"
category: Networking
description: >
  诊断 Kubernetes Service 连通性、DNS 解析、ALB2 负载分流、ALB2 Rule 优先级冲突及 NetworkPolicy 流量控制问题。
  包括 Selector 不匹配、targetPort 错误、跨 Namespace DNS 失败、
  ALB2 路径 404/502、ALB2 监听配置错误、Rule 优先级抢占及 NetworkPolicy 等静默丢包场景。
  本 Skill 为只读诊断，不执行任何变更。
scope: read-only
triggers:
  - "Service 的 Endpoints 为空（none）"
  - "访问 Service 返回 connection refused"
  - "Pod 间通信返回 connection timed out（非 refused）"
  - "DNS 解析失败（nslookup 无结果）"
  - "LoadBalancer Service 的 EXTERNAL-IP 长期为 pending"
  - "有状态应用 Pod 之间无法通过 DNS 互相发现"
  - "用户 Session 频繁丢失（请求被路由到不同 Pod）"
  - "ALB2 路由返回 502 Bad Gateway"
  - "ALB2 路由返回 404 Not Found"
  - "ALB2 路由返回 504 Gateway Timeout"
  - "ALB2 Rule 优先级冲突导致路由被抢占"
  - "同域名下不同用户的 Rule 路径匹配冲突"
  - "请求被路由到错误的后端 Service（非预期 Rule 匹配）"
references:
  - references/dns_naming_conventions.md       # K8s DNS 命名规则与 FQDN 格式
  - references/networkpolicy_syntax_guide.md   # NetworkPolicy 规则语法与调试
  - references/alb2_resource_guide.md          # ALB2、Frontend 与 Rule 资源调试
  - references/alb2_rule_priority_guide.md     # ALB2 Rule 优先级机制与路径冲突
---

# networking_diagnosis — 网络与服务诊断

> **⚠️ 只读模式**：本 Skill 仅输出诊断报告，不执行任何修复变更。

## 关键诊断线索

| 错误表现 | 区分含义 |
|---------|---------|
| `connection refused` | 连接被对端**主动拒绝**（Port 不匹配/服务未监听） |
| `connection timed out` | 连接被**静默丢弃**（网络丢包，如 NetworkPolicy 拦截、安全组/防火墙阻断、CNI 路由异常等） |
| DNS 解析失败 | 名称错误 / Namespace 不匹配 / CoreDNS 异常 |

## 诊断工作流

### Step 0：自动化服务与路由分析（K8sGPT）

> **💡 最佳实践**：在深入追踪 Endpoints 或手工测试网络连通性前，优先进行自动化分析。

```
工具：analyze(cluster, namespace=<ns>, name=<svc>, filters=["Service", "NetworkPolicy"])
→ 若 K8sGPT 提示 Selector mismatch（如标签匹配错误），可直接修复，跳过底层探测。
```

---

### Step 1：确认 Endpoints 状态

```
① 查看 Pod 关联的 Service 和 Endpoints
   工具：get_k8s_pod_linked_services(cluster, namespace, name=<pod>)
   → 获取 Service 名称列表

   工具：get_pod_linked_endpoints(cluster, namespace, name=<pod>)
   或使用 kubectl 兜底命令查看 Service 与 Endpoints 列表：
   工具：kubectl(cluster, args=["get", "svc,ep", "-n", "<namespace>", "-o", "wide"])
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

③ 测试应用监听端口
   工具：diagnose_k8s_pod_network(cluster, namespace, pod=<pod>, targetIP="127.0.0.1", targetPort=<port>)
   ⚠️ SRE 提醒：该工具会默认使用 curl 运行端口连接测试。若目标镜像中缺少 curl 等基础命令（抛出 command not found），该工具会自动启动一个临时 busybox Pod 进行连通性探测，能彻底解决 scratch/distroless 等极简镜像无法执行探测的问题。不需要使用 run_command_in_k8s_pod。
   → 逐一测试不同端口，找到实际监听端口
```

---

### Step 2C：DNS 解析失败

```
① 在 Source Pod 内测试 DNS 解析与 CoreDNS 状态
   工具：test_k8s_dns_resolve(cluster, namespace, pod=<pod>, host=<service-name>)
   ⚠️ SRE 提醒：该工具不仅测试直接解析，还会自动读取 resolv.conf 并检测 CoreDNS 状态，能有效规避 distroless 镜像无 nslookup 工具的限制。

② 测试 FQDN 格式解析
   工具：test_k8s_dns_resolve(cluster, namespace, pod=<pod>, host=<service-name>.<target-ns>.svc.cluster.local)
   → 若 FQDN 成功但短名称失败 → 跨 Namespace 访问使用了短名称

③ 若 DNS 解析依然失败，查看 CoreDNS 日志
   工具：list_k8s_pod(cluster, namespace=kube-system)
   → 找 coredns Pod
   工具：get_k8s_pod_logs(cluster, namespace=kube-system, name=<coredns-pod>, tail=50)
   → 查找错误日志
```

---

### Step 2D：NetworkPolicy 流量拦截

```
① 确认网络连通性与错误类型
   工具：diagnose_k8s_pod_network(cluster, namespace, pod=<pod>, targetIP=<target-ip>, targetPort=<port>)
   ⚠️ SRE 提醒：该工具会默认使用 curl 运行连接测试，若镜像中缺少 curl 等命令（抛出 command not found），它会自动启动一个临时 busybox Pod 进行连通性探测，能彻底解决 scratch/distroless 等极简镜像无法执行探测的问题。
   → timeout/丢包 → 网络静默丢包（需考虑 NetworkPolicy 拦截、云安全组/防火墙阻断或 CNI 路由异常等原因）
   → refused/拒绝 → 端口服务未监听，回到 Step 2B

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


---

### Step 2E：StatefulSet Headless Service 诊断

```
① 确认 Service 类型
   工具：get_k8s_resource(cluster, namespace, kind=Service, name=<svc>, version=v1)
   → 读取 spec.clusterIP 字段
   → 若非 None → 不是 Headless Service，Pod 间 DNS 无法直接解析

② 验证 Pod 间 DNS 解析（Headless Service 模式）
   工具：test_k8s_dns_resolve(cluster, namespace, pod=<pod-0>, host="<pod-1>.<headless-svc>.<ns>.svc.cluster.local")
   ⚠️ SRE 提醒：该工具会自动读取 resolv.conf 并检测 CoreDNS 状态，不依赖 Pod 内的 nslookup 命令行工具，可彻底解决 scratch/distroless 镜像限制。
   → 若解析失败且 Service 非 Headless → 确认问题根因
```

---

### Step 2F：多 ALB2 隔离监听与路由诊断

> **💡 最佳实践**：请优先使用专用的 `alb2` MCP 诊断工具，仅在工具不可用时，使用底部的 `kubectl` 兜底机制。

```
① 确认当前业务中心或服务所属的位置/ALB2 实例名称
   例如，确认该业务服务应由哪个 ALB2 实例（如 `center5-100-115-99-170`）承载。
   工具：list_alb2_resources(cluster, namespace="cpaas-system")
   → 确认目标 ALB2 实例是否存在、Ready 字段是否为 true。若 Ready 为 false，查看 message 获取异常原因。

② 反查 Service 暴露关系（识别 TCP 直连模式与 HTTP 路由规则模式）
   ⚠️ SRE 提醒：服务在 ALB2 上暴露有两种可能模式：
   - TCP/UDP 直连模式：Frontend 资源可能直接包含端口映射，或 Frontend 直接关联 Service；
   - HTTP/HTTPS 反向代理模式：多个后端 Service 共享 80/443 端口，通过自定义路由规则（Rule）转发。

   反查方法：
   工具：find_alb2_resources_by_service(cluster, service_name=<svc-name>, service_namespace=<svc-ns>)
   → 分析返回 of mappings 列表：
     - 若 `foundCount == 0` ➔ 根因：此 Service 尚未在任何 ALB2 实例中配置暴露规则。
     - 检查匹配项的 `alb2Name` 是否与实际承载业务的负载均衡器名称一致。若不一致 ➔ 根因：Rule 被错误绑定到了其他 ALB2 实例（多 ALB 隔离环境下的绑定偏离）。
     - 记录正确的 `frontendName`（如 `center5-100-115-99-170-00080`）、`port`、`protocol` 及 `proxyType` (L4 或 L7)。

③ 确认当前 Rule 所绑定的 Frontend 及端口配置
   （已在 Step ② 自动验证。如果客户端请求的协议/端口与 mapping 中的 port/protocol 不匹配，说明端口绑定错误或 Frontend 未开启相应监听）

④ 查看监听端口挂载的路由转发规则（Rule）及后端健康状态
   工具：list_alb2_routing_rules(cluster, namespace="cpaas-system", alb2_name=<alb2-name>, frontend_name=<frontend-name>)
   → 分析列表中与客户端 Host/Path 匹配 of Rule 的 backends 诊断数据：
     - 检查 `serviceExists` 是否为 true。若为 false ➔ 根因：Rule 绑定的 Service 在 K8s 中已被删除。
     - 检查 `endpointsCount`。若为 0 ➔ 根因：后端 Pod 未就绪，无可用 Endpoints (ALB2 会返回 502 Bad Gateway)。
     - 若后端状态正常，但仍未匹配 ➔ 跳转 Step 2G 静态分析规则优先级冲突。

⑤ 检查 ALB2 控制器日志 ( Nginx Reload 异常、证书加载异常等 )
   工具：get_alb2_controller_logs(cluster, alb2_name=<alb2-name>, tail_lines=200)
   → 分析过滤后的 Reload 报错、证书加载或 invalid config 等告警信息。
```

> 参考：[ALB2、Frontend 与 Rule 调试指南](references/alb2_resource_guide.md)

---

### Step 2G：ALB2 Rule 优先级与路径冲突诊断

⚠️ **SRE 提醒：这是多业务中心共享 ALB2 Frontend 时最常见的隐性故障**。当同一 Frontend 下存在多个 Rule 匹配同一域名时，`spec.priority` 数值较小的 Rule 会优先匹配。如果宽泛路径（如 `/`）的 Rule 优先级高于精确路径（如 `/manager/(.*)`），则精确路径的流量会被宽泛路径截获。

```
① 执行规则冲突自动静态分析
   工具：diagnose_alb2_rule_conflict(cluster, namespace="cpaas-system", frontend_name=<frontend-name>)
   → 分析返回 of warnings 列表：
     - 若检测到 `DuplicateMatching` ➔ 关键冲突：两个 Rule 拥有完全相同的匹配条件，低优先级规则永远失效。
     - 若检测到 `WildcardShadowing` 或 `PrefixShadowing` ➔ 抢占冲突：有高优先级（数值较小）的宽泛路径规则（如 `/` 或 `/parent`）劫持了子路径流量，导致目标精确路径规则失效。

② 确认被抢占流量的去向与修复建议
   - 读取诊断信息中 `highPriorityRule` 对应的后端 Service 去向。
   - 修改建议：将目标精确 Rule 的 `spec.priority` 值修改得比抢占 Rule 更小（如设为 0），或调大抢占 Rule 的 `spec.priority` 值。
```

> 参考：[ALB2 Rule 优先级机制与路径冲突排查指南](references/alb2_rule_priority_guide.md)

---


**示例（Selector 不匹配）**：
> **Root Cause:** Service `spec.selector` 配置为 `{app: frontend}`，而 Deployment Pod template 的 labels 为 `{app: backend}`，Selector 无法匹配任何 Pod。  
> **Evidence Chain:** Service selector `{app:frontend}` 与 Pod labels `{app:backend}` 不匹配 → Endpoints 控制器找不到符合条件的 Pod → Service Endpoints 列表为空 → 所有到 Service 的请求无目标，返回 connection refused。  
> **Confidence:** High — `get_k8s_resource(cluster, kind=Service)` 返回 selector={app:frontend}；`list_k8s_pod` 返回 Pod labels={app:backend}；`get_pod_linked_endpoints` 返回 Endpoints 为空。  
> **Recommended Fix:** 建议运维人员将 Service 的 `spec.selector` 修改为 `{app: backend}` 与 Pod labels 一致，或将 Deployment Pod template labels 修改为 `{app: frontend}`。

**示例（ALB2 路由规则指向了空 Endpoints）**：
> **Root Cause:** Rule `center5-100-115-99-170-00080-xx-hash` 配置的后端服务 `nupp-xxl-job` 的 Endpoints 列表为空（所有后端 Pod 处于未就绪状态或探针失败）。  
> **Evidence Chain:** 访问特定 Host 路由到 ALB2 端口 80 (Frontend) → 匹配到 Rule → Rule 查找后端服务 `nupp-xxl-job` → 服务无可用 Endpoints → 转发失败，ALB2 返回 502 Bad Gateway。  
> **Confidence:** High — `kubectl get rule` 返回绑定的 Service 为 `nupp-xxl-job`；`kubectl get ep nupp-xxl-job` 返回 Endpoints 为空；Pod 描述显示其 Readiness 探针失败。  
> **Recommended Fix:** 建议排查后端服务 `nupp-xxl-job` 对应的 Pod 为什么无法通过 Readiness 探针。

**示例（多 ALB 隔离场景下 Rule 配错 ALB2 实例）**：
> **Root Cause:** 业务流量实际流向的负载均衡器是 `center5-100-115-99-170`，但用户配置的路由规则 `center1-100-115-99-116-00080-xx-hash` 被错误绑定到了 `center1-100-115-99-116` 实例（即 `alb2.cpaas.io/name` 标签关联错误）。  
> **Evidence Chain:** 用户请求发送给 `center5` 绑定的外部 IP ➔ `center5` 控制器解析其下所有的 rules ➔ 未找到与请求 Host/Path 相匹配的 Rule ➔ 流量被 `center5` 丢弃或返回 404，未送达后端 Pod。  
> **Confidence:** High — `kubectl get rules` 表明该匹配 Rule 的 `alb2.cpaas.io/name` 标签指向了 `center1`，而当前域名的 VIP/Service 实际对应的负载均衡器是 `center5`。  
> **Recommended Fix:** 建议用户修改该 Rule 资源或重新创建，使其 `metadata.labels["alb2.cpaas.io/name"]` 关联正确的 ALB2 实例 `center5-100-115-99-170`。

**示例（Rule 优先级冲突导致路由被宽泛路径抢占）**：
> **Root Cause:** 同一 Frontend (`center5-100-115-99-170-00080`) 下，用户 A 的 Rule（`spec.dsl: (STARTS_WITH URL /)`，`priority: 1`）优先级高于用户 B 的 Rule（`spec.dsl: (REGEX URL /manager/(.*))`，`priority: 5`）。由于 ALB2 按 priority 从小到大匹配，所有请求（包括 `/manager/*` 路径）均被 Rule A 优先截获并路由到用户 A 的后端 Service。  
> **Evidence Chain:** 用户 B 访问 `app.example.com/manager/dashboard` ➔ ALB2 按 priority 排序评估 Rules ➔ Rule A（priority=1，`STARTS_WITH /`）首先匹配成功 ➔ 请求被路由到用户 A 的 `svc-a` ➔ 用户 B 的 Rule B（priority=5）从未被评估 ➔ 用户 B 观察到应用访问异常（返回非预期内容或 404）。  
> **Confidence:** High — `kubectl get rules` 按 priority 排序显示 Rule A（priority=1）在 Rule B（priority=5）之前；两者 domain 相同；Rule A 的 `STARTS_WITH /` 覆盖了 Rule B 的 `/manager/(.*)` 路径。  
> **Recommended Fix:** 建议将用户 B 的精确路径 Rule 的 `spec.priority` 调低至小于 1（如设为 0），使其优先于宽泛路径 Rule 被匹配；或将用户 A 的宽泛路径 Rule 的 `spec.priority` 调高（如设为 10），降低其匹配优先级。

---

## 兜底排查机制（kubectl）

在专用 MCP 工具（如 `get_pod_linked_endpoints` 等）受限或无法满足深度排查时，可使用只读 `kubectl` 兜底工具执行命令：
- 宏观排查 Service、Endpoints 端口与 IP 映射：
  `工具：kubectl(cluster, args=["get", "svc,ep", "-n", "<namespace>", "-o", "wide"])`
- 检查 Service 的详细配置及 Selector 匹配规则：
  `工具：kubectl(cluster, args=["describe", "service", "<service-name>", "-n", "<namespace>"])`
- 查看网络策略（NetworkPolicy）的实际应用情况：
  `工具：kubectl(cluster, args=["get", "networkpolicy", "-n", "<namespace>", "-o", "yaml"])`
- 排查 ALB2、Frontend 和 Rule 自定义资源配置关系：
  `工具：kubectl(cluster, args=["get", "alb2,frontends.crd.alauda.io,rules.crd.alauda.io", "-n", "cpaas-system", "-o", "wide"])`
  `工具：kubectl(cluster, args=["get", "rules.crd.alauda.io", "<rule-name>", "-n", "cpaas-system", "-o", "yaml"])`
- 按优先级排序查看同一 Frontend 下所有 Rule 的域名、路径与优先级（用于排查优先级冲突）：
  `工具：kubectl(cluster, args=["get", "rules.crd.alauda.io", "-n", "cpaas-system", "-l", "alb2.cpaas.io/frontend=<frontend-name>", "--sort-by=.spec.priority", "-o", "custom-columns=NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,URL:.spec.url,DSL:.spec.dsl,SVC_NS:.spec.serviceGroup.services[*].namespace,SVC_NAME:.spec.serviceGroup.services[*].name,SVC_PORT:.spec.serviceGroup.services[*].port"])`
- 查看 Rule 的后端 Service 配置（含跨 Namespace 信息）：
  `工具：kubectl(cluster, args=["get", "rules.crd.alauda.io", "<rule-name>", "-n", "cpaas-system", "-o", "jsonpath={.spec.serviceGroup.services}"])`

