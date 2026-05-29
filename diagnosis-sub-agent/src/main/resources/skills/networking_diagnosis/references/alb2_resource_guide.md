# ALB2、Frontend 与 Rule 资源关系与调试指南

本文档为 `networking_diagnosis` Skill 的参考资料，说明私有化 K8s 集群中 ALB2 负载均衡组件（由 `alb2.cpaas.io` 定义的 CustomResourceDefinition）的排查逻辑与常用命令。

---

## 1. 资源拓扑关系

```
[ ALB2 实例 ] (代表负载均衡器服务，如 center5-100-115-99-170)
       │
       └───► [ Frontend 前端监听 ] (代表端口与协议，如 80 端口)
                   │
                   └───► [ Rule 转发规则 ] (代表具体 Host/Path 转发与后端 Service 映射)
```

---

## 2. 命名规则与绑定关系

### ALB2 实例
* **资源类型**：`alb2` (或者 `alb2s.crd.alauda.io`)
* **命名示例**：`center5-100-115-99-170`

### Frontend 监听
* **资源类型**：`frontends.crd.alauda.io`
* **命名规范**：`[ALB2 名称]-[五位端口号]`
* **示例**：`center5-100-115-99-170-00080` (监听 80 端口)
* **绑定声明**：
  * `metadata.ownerReferences` 的 `kind` 为 `ALB2`。
  * `metadata.labels` 中包含 `alb2.cpaas.io/name: [ALB2 名称]`。

### Rule 规则
* **资源类型**：`rules.crd.alauda.io`
* **命名规范**：`[Frontend 名称]-[UUID/Hash]`
* **示例**：`center5-100-115-99-170-00080-03768774-dff8-468c-8e27-13e8ee38fae1`
* **绑定声明**：
  * `metadata.ownerReferences` 的 `kind` 为 `Frontend`。
  * `metadata.labels` 中包含 `alb2.cpaas.io/frontend: [Frontend 名称]` 和 `alb2.cpaas.io/name: [ALB2 名称]`。
* **关键 spec 字段**：
  * `spec.priority`：匹配优先级（数值越小，优先级越高）。
  * `spec.domain`：匹配的域名（如 `app.example.com`），可为空字符串 `""`。
  * `spec.url`：URL 路径简写（如 `/eBanks-manager`），便于展示，实际匹配以 `dsl`/`dslx` 为准。
  * `spec.dsl`：DSL 匹配表达式字符串（如 `(AND (STARTS_WITH URL /eBanks-manager) (EQ HEADER ENVIRONMENT_GROUP H5))`）。
  * `spec.dslx`：结构化 DSL 条件（支持 `HOST`、`URL`、`HEADER` 三种类型，操作符包括 `IN`、`STARTS_WITH`、`REGEX`、`EQ`；`HEADER` 类型额外包含 `key` 字段指定 Header 名称）。
  * `spec.backendProtocol`：后端协议（`HTTP` 或 `HTTPS`）。
  * `spec.serviceGroup.services`：后端 Service 列表（含 `name`、`namespace`、`port`、`weight` 字段）。注意路径是 `serviceGroup.services`，不是 `services`。

---

## 3. 常见故障诊断与分析路径

### A. 访问返回 `502 Bad Gateway` (网关错误)
* **可能原因**：
  1. Frontend 和 Rule 资源配置正常，但 Rule 绑定的后端 K8s Service 的 Endpoints 列表为空。
  2. 后端 Pod 的服务未正常监听或发生 Crash。
* **排查命令**：
  1. 获取对应 Rule 绑定的后端 Service：
     `kubectl get rule <rule-name> -n <ns> -o jsonpath='{.spec.serviceGroup.services}'`
  2. 检查对应 Service 的 Endpoints 状态：
     `kubectl get ep <service-name> -n <service-ns>`

### B. 访问返回 `404 Not Found` (未找到路由)
* **可能原因**：
  1. 访问使用的域名（Host）或路径（Path）没有在任何 Rule 资源的 `spec.domain` 或 `spec.dsl` 中定义。
  2. 对应的 Rule 未激活或未被绑定的 Frontend/ALB2 加载。
* **排查命令**：
  1. 查询指定 Frontend 下的所有 Rule，检查是否有匹配的 `domain` 或 `dsl` 条件：
     `kubectl get rules -n <ns> -l alb2.cpaas.io/frontend=<frontend-name> -o yaml`
  2. 确认 ALB2 Controller 的 Pod 是否正常运行且无报错日志。

### C. 访问返回 `504 Gateway Timeout` (超时)
* **可能原因**：
  1. 后端 Pod 处理缓慢或卡死。
  2. Pod 与 ALB2 节点之间的网络连通性被 NetworkPolicy 拦截（见 `networkpolicy_syntax_guide.md`）。
* **排查命令**：
  1. 检查后端 Pod 日志及 CPU/内存占用。
  2. 验证 ALB2 所在节点/Pod 到后端 Pod IP 的网络连通性。

### D. 多 ALB2 隔离环境下，配置了 Rules 但流量未达后端 Pod (配置配错或路由不匹配)
* **可能原因**：
  1. **ALB2 实例绑定错误**：用户配置了 Rule，但是将其错误地关联到了其他业务中心的 ALB2 实例上（例如，业务中心 A 的流量实际由 `center5` 承载，但 Rule 被关联到了 `center1`，其 `alb2.cpaas.io/name` 标签不匹配）。
  2. **Frontend 端口绑定错误**：Rule 挂载的 Frontend 监听端口（如 80）与客户端发起请求的协议端口（如 443 HTTPS）不对应。
  3. **流量路由解析错误**：域名的 DNS/VIP 解析未指向负责该业务中心的 ALB2 的 IP/NodeIP，导致流量流向了另一个未配置该规则的 ALB2 实例。
  4. **控制面同步失效**：对应的 ALB2 控制器 Pod 发生故障，未能及时加载/Reconcile 该 Rule。
  5. **`spec.serviceGroup.services[].namespace` 跨 Namespace 错误**：Rule 的后端 Service 配置中 `namespace` 字段指向了错误的命名空间，导致 ALB2 找不到目标 Service。
* **排查命令**：
  1. 确认该业务中心正常工作的 ALB2 实例名称（如 `center5-100-115-99-170`）。
  2. 确认配置的 Rule 资源所绑定的 ALB2 实例标签：
     `kubectl get rule <rule-name> -n <ns> -o jsonpath='{.metadata.labels.alb2\.cpaas\.io/name}'`
     → 确认该标签是否与正确的 ALB2 一致。
  3. 确认当前 Rule 所关联的 Frontend 端口：
     `kubectl get rule <rule-name> -n <ns> -o jsonpath='{.metadata.labels.alb2\.cpaas\.io/frontend}'`
  4. 确认 Rule 的后端 Service namespace 是否正确：
     `kubectl get rule <rule-name> -n <ns> -o jsonpath='{.spec.serviceGroup.services}'`
     → 检查返回的 `namespace` 字段是否与目标 Service 实际所在 Namespace 一致。
  5. 检查 ALB2 控制器 Pod 的同步日志是否有错误（如证书配置、DSL 解析错误等）：
     `kubectl logs -n cpaas-system -l alb2.cpaas.io/name=<correct-alb2-name> -c alb2`

### E. Rule 优先级冲突导致路由被抢占
* **可能原因**：
  1. **宽泛路径覆盖精确路径**：用户 A 配置了 `STARTS_WITH /`（priority=1），用户 B 配置了 `REGEX /manager/(.*)`（priority=5），所有请求被优先匹配到用户 A 的 Rule。
  2. **Priority 值设置不合理**：精确路径的 Rule 的 priority 数值大于宽泛路径的 Rule，导致宽泛路径先匹配。
  3. **同域名下多租户/多用户 Rule 未做路径隔离**：多个用户/业务共享同一域名，各自创建的 Rule 路径存在交叉覆盖关系。
* **排查命令**：
  1. 列出同一 Frontend 下所有共享相同域名的 Rule 及其优先级：
     `kubectl get rules -n <ns> -l alb2.cpaas.io/frontend=<frontend-name> -o custom-columns='NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,DSL:.spec.dsl'`
  2. 按 priority 从小到大排序，检查是否有宽泛路径（如 `/`）排在精确路径（如 `/manager/(.*)`）之前。
  3. 查看被怀疑抢占流量的 Rule 的后端 Service：
      `kubectl get rule <suspect-rule-name> -n <ns> -o jsonpath='{.spec.serviceGroup.services}'`
     → 若该 Rule 的后端 Service 不是用户期望的目标，则确认为优先级冲突。

> 参考：[ALB2 Rule 优先级机制与路径冲突排查指南](alb2_rule_priority_guide.md)

---

## 4. 诊断常用命令

```bash
# 1. 查找指定 ALB2 实例下的所有监听端口 (Frontend)
kubectl get frontends -n cpaas-system -l alb2.cpaas.io/name=<alb2-name>

# 2. 查看具体 Frontend 监听配置 (如协议、端口)
kubectl get frontend <frontend-name> -n cpaas-system -o yaml

# 3. 查找特定监听端口 (Frontend) 下挂载的所有路由规则 (Rule)
kubectl get rules -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name>

# 4. 查看具体 Rule 转发的主机域名、DSL 表达式及指向的后端服务 (Service)
kubectl get rule <rule-name> -n cpaas-system -o yaml

# 5. 按优先级查看同一 Frontend 下所有 Rule 的域名、路径与优先级
kubectl get rules -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name> \
  -o custom-columns='NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,DSL:.spec.dsl'

# 6. 查看 Rule 的后端 Service 配置（含跨 Namespace 信息）
kubectl get rule <rule-name> -n cpaas-system -o jsonpath='{.spec.serviceGroup.services}'
```

