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
* **资源类型**：`alb2` (或运行 `kubectl api-resources` 手动确认包含 `alb`、`frontend`、`rule` 的资源，或直接获取相关 CRD 以确认集群中实际注册的自定义资源名，注意 MCP 环境下不支持管道 `| grep` 过滤)
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

> 💡 **最佳实践**：在诊断 ALB2 问题时，应优先使用专用 MCP 诊断工具，避免直接编写复杂的 `kubectl` custom-columns/jsonpath 提取命令（以防由于转义或引号问题导致执行失败）。

### A. 访问返回 `502 Bad Gateway` (网关错误)
* **可能原因**：
  1. Frontend 和 Rule 资源配置正常，但 Rule 绑定的后端 K8s Service 的 Endpoints 列表为空。
  2. 后端 Pod 的服务未正常监听或发生 Crash。
* **排查路径**：
  * **首选 MCP 工具**：
    `list_alb2_routing_rules(cluster, namespace, alb2_name, frontend_name)`
    → 直接在返回的 `backends` 列表中，查看对应 Rule 绑定的 Service 及其 `serviceExists`（Service是否存在）和 `endpointsCount`（就绪 Endpoints 数量）。
  * **备用 kubectl 命令**：
    1. 获取对应 Rule 绑定的后端 Service：
       `kubectl get rules.crd.alauda.io <rule-name> -n <ns> -o jsonpath='{.spec.serviceGroup.services}'`
    2. 检查对应 Service 的 Endpoints 状态：
       `kubectl get ep <service-name> -n <service-ns>`

### B. 访问返回 `404 Not Found` (未找到路由)
* **可能原因**：
  1. 访问使用的域名（Host）或路径（Path）没有在任何 Rule 资源的 `spec.domain` 或 `spec.dsl` 中定义。
  2. 对应的 Rule 未激活或未被绑定的 Frontend/ALB2 加载。
* **排查路径**：
  * **首选 MCP 工具**：
    `list_alb2_routing_rules(cluster, namespace, alb2_name, frontend_name)`
    → 检查返回列表中是否有匹配请求 Host/Path 的 Rule 条件。
  * **备用 kubectl 命令**：
    `kubectl get rules.crd.alauda.io -n <ns> -l alb2.cpaas.io/frontend=<frontend-name> -o yaml`

### C. 访问返回 `504 Gateway Timeout` (超时)
* **可能原因**：
  1. 后端 Pod 处理缓慢或卡死。
  2. Pod 与 ALB2 节点之间的网络连通性被 NetworkPolicy 拦截（见 `networkpolicy_syntax_guide.md`）。
* **排查路径**：
  1. 检查后端 Pod 日志及 CPU/内存占用。
  2. 验证 ALB2 所在节点/Pod 到后端 Pod IP 的网络连通性。

### D. 多 ALB2 隔离环境下，配置了 Rules 但流量未达后端 Pod (配置配错或路由不匹配)
* **可能原因**：
  1. **ALB2 实例绑定错误**：用户配置了 Rule，但是将其错误地关联到了其他业务中心的 ALB2 实例上（其 `alb2.cpaas.io/name` 标签不匹配）。
  2. **Frontend 端口绑定错误**：Rule 挂载的 Frontend 监听端口（如 80）与客户端发起请求的协议端口（如 443 HTTPS）不对应。
  3. **流量路由解析错误**：域名的 DNS/VIP 解析未指向负责该业务中心的 ALB2 的 IP/NodeIP，导致流量流向了另一个未配置该规则的 ALB2 实例。
  4. **控制面同步失效**：对应的 ALB2 控制器 Pod 发生故障，未能及时加载/Reconcile 该 Rule。
  5. **`spec.serviceGroup.services[].namespace` 跨 Namespace 错误**：Rule 的后端 Service 配置中 `namespace` 字段指向了错误的命名空间，导致 ALB2 找不到目标 Service。
* **排查路径**：
  * **首选 MCP 工具**：
    1. 反向查找 Service 关联的负载均衡资源：
       `find_alb2_resources_by_service(cluster, service_name, service_namespace)`
       → 确认返回 mappings 中的 `alb2Name` 是否与客户端请求的目标 ALB2 实例相匹配，且 mapping 中绑定的 `port`、`protocol` 及 `proxyType` 是否正确。
    2. 检查控制器同步日志：
       `get_alb2_controller_logs(cluster, alb2_name, tail_lines)`
       → 自动定位控制器 Pod 并过滤拉取 `reload`、`fail`、`error` 等相关警告。
  * **备用 kubectl 命令**：
    1. 确认配置 of Rule 资源所绑定的 ALB2 实例：
       `kubectl get rules.crd.alauda.io <rule-name> -n <ns> -o jsonpath="{.metadata.labels['alb2.cpaas.io/name']}"`
    2. 确认 Rule 的后端 Service namespace 是否正确：
       `kubectl get rules.crd.alauda.io <rule-name> -n <ns> -o jsonpath='{.spec.serviceGroup.services}'`

### E. Rule 优先级冲突导致路由被抢占
* **可能原因**：
  1. **宽泛路径覆盖精确路径**：用户 A 配置了 `STARTS_WITH /`（priority=1），用户 B 配置了 `REGEX /manager/(.*)`（priority=5），所有请求被优先匹配到用户 A 的 Rule。
  2. **Priority 值设置不合理**：精确路径的 Rule 的 priority 数值大于宽泛路径的 Rule，导致宽泛路径先匹配。
  3. **同域名下多租户/多用户 Rule 未做路径隔离**：多个用户/业务共享同一域名，各自创建的 Rule 路径存在交叉覆盖关系。
* **排查路径**：
  * **首选 MCP 工具**：
    `diagnose_alb2_rule_conflict(cluster, namespace, frontend_name)`
    → 静态分析指定 Frontend 下的所有规则冲突，直接查看返回的 warnings 列表。
  * **备用 kubectl 命令**：
    `kubectl get rules.crd.alauda.io -n <ns> -l alb2.cpaas.io/frontend=<frontend-name> --sort-by=.spec.priority -o custom-columns="NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,DSL:.spec.dsl"`

> 参考：[ALB2 Rule 优先级机制与路径冲突排查指南](alb2_rule_priority_guide.md)

---

## 4. 诊断常用命令

### 4.1 首选专用 MCP 工具
在诊断中，**强烈建议优先使用以下专用 MCP 工具**，能够极大提升排查的速度和稳定性：
* 列出所有 ALB2 实例：`list_alb2_resources(cluster, namespace)`
* 反向查 Service 绑定的 ALB2/Frontend/Rule 关系：`find_alb2_resources_by_service(cluster, service_name, service_namespace)`
* 排序获取 Frontend 下所有 Rule（带后端诊断）：`list_alb2_routing_rules(cluster, namespace, alb2_name, frontend_name)`
* 自动诊断 Frontend 下规则优先级与遮蔽冲突：`diagnose_alb2_rule_conflict(cluster, namespace, frontend_name)`
* 拉取并按关键字过滤控制器日志：`get_alb2_controller_logs(cluster, alb2_name, tail_lines)`

### 4.2 备用 kubectl 命令
在专用 MCP 工具受限或不可用时，可使用 `kubectl` 进行底层探查。
> 💡 **MCP 工具使用提示**：在 MCP 环境下调用 `kubectl` 工具时，若执行带有 jsonpath、custom-columns 等包含特殊符号/引号的复杂查询，请**务必将参数以数组形式传递给 `args` 参数**（例如 `args=["get", "rules.crd.alauda.io", "-o", "jsonpath={...}"]`）以避免引号解析和反斜杠转义错误，不能直接使用 `cmd` 参数。此外，MCP 模式下不支持管道符 `|` 及外部命令。

```bash
# 1. 查找指定 ALB2 实例下的所有监听端口 (Frontend)
kubectl get frontends.crd.alauda.io -n cpaas-system -l alb2.cpaas.io/name=<alb2-name> -o custom-columns="NAME:.metadata.name,PORT:.spec.port,PROTOCOL:.spec.protocol,BACKEND_PROTOCOL:.spec.backendProtocol"

# 2. 查看具体 Frontend 监听配置 (如协议、端口)
kubectl get frontends.crd.alauda.io <frontend-name> -n cpaas-system -o yaml

# 3. 查找特定监听端口 (Frontend) 下挂载的所有路由规则 (Rule)
kubectl get rules.crd.alauda.io -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name>

# 4. 查看具体 Rule 转发的主机域名、DSL 表达式及指向的后端服务 (Service)
kubectl get rules.crd.alauda.io <rule-name> -n cpaas-system -o yaml

# 5. 按优先级排序列出同一 Frontend 下所有 Rule 的信息
kubectl get rules.crd.alauda.io -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name> \
  --sort-by=.spec.priority \
  -o custom-columns="NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,URL:.spec.url,DSL:.spec.dsl,SVC_NS:.spec.serviceGroup.services[*].namespace,SVC_NAME:.spec.serviceGroup.services[*].name,SVC_PORT:.spec.serviceGroup.services[*].port"

# 6. 查看 Rule 的后端 Service 配置（含跨 Namespace 信息）
kubectl get rules.crd.alauda.io <rule-name> -n cpaas-system -o jsonpath='{.spec.serviceGroup.services}'

# 7. 反查指向特定 Service 的所有 Rule（注意：通过 MCP kubectl 工具执行时不支持管道 grep，此格式用于 custom-columns 全量输出由调用端做结果过滤）
kubectl get rules.crd.alauda.io -n cpaas-system -o custom-columns="NAME:.metadata.name,ALB2:metadata.labels['alb2.cpaas.io/name'],FRONTEND:metadata.labels['alb2.cpaas.io/frontend'],SVC_NS:.spec.serviceGroup.services[*].namespace,SVC_NAME:.spec.serviceGroup.services[*].name,SVC_PORT:.spec.serviceGroup.services[*].port"

# 8. 结合指定 ALB2 实例，反查该实例下指向特定后端 Service 的 Rule 及其挂载的前端监听 (Frontend/Port)
kubectl get rules.crd.alauda.io -n cpaas-system -l alb2.cpaas.io/name=<alb2-name> \
  -o custom-columns="NAME:.metadata.name,FRONTEND:metadata.labels['alb2.cpaas.io/frontend'],SVC_NS:.spec.serviceGroup.services[*].namespace,SVC_NAME:.spec.serviceGroup.services[*].name,SVC_PORT:.spec.serviceGroup.services[*].port,WEIGHT:.spec.serviceGroup.services[*].weight"
```

