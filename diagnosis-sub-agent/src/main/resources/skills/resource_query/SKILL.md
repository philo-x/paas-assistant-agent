---
name: resource_query
version: "3.0"
category: Query & Retrieval
description: >
  K8s 资源查询的智能路由与数据检索框架。负责将用户的自然语言意图（资源发现、状态概览、配置核对等）翻译为最优的工具调用序列。
  涵盖 Workload、Node、Storage、Network 等资源的高效检索、关联追踪、性能保障（chunk-size）与结构化输出规范。
scope: read-only
triggers:
  - "需要查找符合特定条件的 Kubernetes 资源"
  - "需要跨命名空间搜索特定名称或标签的资源"
  - "需要追踪工作负载的拓扑链路（如 Deployment -> Pod，Service -> Endpoint）"
  - "需要查看 Node、PV/PVC、RBAC、NetworkPolicy 等集群级资源的配置或状态"
references:
  - ../kubectl_query/SKILL.md       # kubectl 兜底查询的 args/outputFields/jsonpath 语法与分页约束
---

# resource_query — K8s 资源查询与检索智能路由

> **💡 核心职责**：本 Skill 是**纯粹的资源检索与查询框架**。你的任务是根据用户意图（了解现状、核对配置等），匹配下方定义的 `query_patterns`，并严格遵守 `expected_output` 规范生成客观、结构化的查询结果，**切勿预设立场认为资源发生了故障**。

## 核心架构与原则

1. **客观的数据搬运工**：只负责精准、高效地获取数据并结构化展示。除非用户明确要求诊断异常，否则不要在结论中强加“诊断”、“排查”或“修复建议”。
2. **缩小查询爆炸半径 (APIServer 防雪崩)**：严禁无限制的全局查询。当查询高基数资源（如 pods, events, endpointslices, rolebindings, crds）且缺少明确的 `namespace`, `labelSelector`, `fieldSelector` 或具体名称时，**必须直接拒绝查询**。分页和字段投影仅作为已有范围约束后的附加安全保护，不能代替过滤条件。
3. **性能与安全**：
   - 对于大型负载的查询，**若专有 MCP 工具 Schema 支持字段投影（如 `outputFields`），必须使用**；若不支持，则由 Agent 在返回结果中摘取关键字段；若是 `kubectl` 兜底查询，则必须严格遵守 `kubectl_query` 规范使用 `args + outputFields`。
   - **Secret/环境变量严格脱敏**：绝不能打印明文密码。
   - **ConfigMap 差异化处理**：默认只列出 ConfigMap 的 Keys。只有当用户明确要求查看内容且 Key 名称不匹配敏感正则 `(?i)(password|token|secret|key|cert)` 时，才能输出值，否则必须打码脱敏。
4. **工具优先级与专属清单**：必须优先使用下方列出的专用 MCP 工具。仅当专用工具完全无法覆盖或返回字段不足时，才允许降级使用 `kubectl`。
   - **通用动态检索**：`get_k8s_resource`, `list_k8s_resource`, `describe_k8s_resource`
   - **Node (节点)**：`list_k8s_node`, `get_k8s_node_resource_usage`, `get_k8s_pod_count_running_on_node`, `get_k8s_top_node`
   - **Pod / Workload**：`list_k8s_pod`, `describe_k8s_pod`, `get_k8s_pod_resource_usage`, `get_k8s_top_pod`
   - **Network (网络追踪)**：`get_k8s_pod_linked_services`, `get_pod_linked_endpoints`, `list_alb2_resources`, `list_alb2_routing_rules`, `find_alb2_resources_by_service`
   - **Storage (存储追踪)**：`get_k8s_pod_linked_pv`, `get_k8s_pod_linked_pvc`
   - **Event (事件)**：`list_k8s_event`, `list_k8s_pod_event`
5. **被动异常嗅探 (Passive Anomaly Sniffing)**：在进行客观资源获取时，如果你观察到资源具有明显的故障状态（如 Pod 处于 CrashLoopBackOff、Node 处于 NotReady、PVC 处于 Pending 等），你必须在 `Summary` 和 `Next Steps` 中高亮发出警报（例如：“⚠️ 注意：发现 3 个 Pod 处于 CrashLoopBackOff 状态。”），并询问用户是否需要切换到诊断模式进行深入排查。严禁在当前查询回合直接输出诊断推测或修复建议。

## 与 kubectl_query 的协作边界

`resource_query` 负责决定**查询意图、资源链路、选择器与输出结构**；`kubectl_query` 负责约束**kubectl 兜底查询的参数格式、字段投影、jsonpath 转义与分页**。

当任何 `query_patterns.steps` 需要降级到 `kubectl` 时，必须先应用以下路由规则：

1. **结构化字段提取优先**：使用 `kubectl(cluster, args=[...], outputFields=[...])`，只读取名称、命名空间、状态、ownerReferences、selector、labels、ports、conditions 等当前任务必要字段。
2. **仅格式化展示时使用 jsonpath**：只有当用户需要 Markdown 表格、制表符分隔列表或紧凑文本时，才使用 `args + -o jsonpath=...`；包含点号或斜杠的 label/annotation key 必须按 `kubectl_query` 的 JSONPath 转义规则处理。
3. **禁止 shell 拼接**：不得用管道、重定向、`grep`、`awk`、`jq` 等外部命令过滤 kubectl 输出；所有过滤必须通过 namespace、labelSelector、fieldSelector、`outputFields`、jsonpath 或分页完成。
4. **高基数资源必须分页/过滤**：Pod、Event、EndpointSlice、RoleBinding、ClusterRoleBinding、CRD 实例等资源不得无条件全量拉取；缺少 namespace、labelSelector、fieldSelector 或明确名称时，应要求用户缩小范围。

---

## 模式匹配引擎 (Query Patterns)

当你收到查询指令时，请在以下 YAML 模式中寻找匹配项并执行相应的 `steps` 与 `selectors`：

```yaml
query_patterns:
  - resource_type: "Workloads (Pod/Deployment/StatefulSet/DaemonSet/Job/CronJob)"
    priority_selectors:
      - label_selector: "-l app=nginx" # 业务标签优先
      - field_selector: "--field-selector spec.nodeName=xxx" # 特定条件过滤
      - exact_match: "-n <namespace> <name>" # 精确匹配
    fallback: "拒绝全局 List，要求补充过滤条件，或强制分页+投影"
    scenario: "工作负载链路检索与概览"
    steps:
      - action: "查询顶层资源基本信息与选择器配置（若工具支持则强制字段投影）"
        tool: "get_k8s_resource(cluster, namespace=\"<ns>\", kind=\"<type>\", name=\"<name>\") 或 list_k8s_resource(cluster, ...)"
      - action: "溯源链路：通过 ownerReferences 向上溯源，或通过 matchLabels 向下寻找子资源"
      - action: "顺藤摸瓜查询下属 Pod 列表"
        tool: "list_k8s_pod(cluster, namespace=\"<ns>\", labelSelector=\"...\")"
      - action: "获取资源事件（仅在用户要求或状态明显异常时调用）"
        tool: "list_k8s_event(cluster, namespace=\"<ns>\", involvedObjectName=\"<name>\", involvedObjectKind=\"<kind>\")"

  - resource_type: "Nodes"
    priority_selectors:
      - label_selector: "-l node-role.kubernetes.io/worker="
      - field_selector: "--field-selector spec.unschedulable=true"
    scenario: "节点信息与资源分布查询"
    steps:
      - action: "查询符合条件的节点列表及状态"
        tool: "list_k8s_node(cluster, labelSelector=\"...\")"
      - action: "查看特定 Node 上的 Pod 数量分布（防溢出）"
        tool: "get_k8s_pod_count_running_on_node(cluster, nodeName=\"<node-name>\")"
      - action: "提取 Node 资源使用率及配额"
        tool: "get_k8s_node_resource_usage(cluster, nodeName=\"<node-name>\")"

  - resource_type: "Storage (PV/PVC/StorageClass)"
    priority_selectors:
      - exact_match: "-n <namespace> <pvc-name>"
    scenario: "存储卷绑定与配置查询"
    steps:
      - action: "若从 Pod 出发，直接查询关联存储"
        tool: "get_k8s_pod_linked_pvc(cluster, podName=\"<name>\", namespace=\"<ns>\") 或 get_k8s_pod_linked_pv(cluster, ...)"
      - action: "常规查看 PVC 当前状态及绑定的 PV 名称"
        tool: "get_k8s_resource(cluster, namespace=\"<ns>\", kind=\"pvc\", name=\"<name>\")"
      - action: "查看 PV 详情及使用的 StorageClass"
        tool: "get_k8s_resource(cluster, kind=\"pv\", name=\"<pv-name>\")"

  - resource_type: "Network & Traffic (Service/Ingress/Gateway/NetworkPolicy)"
    scenario: "网络拓扑与流量策略检索"
    steps:
      - action: "查看 ALB2 或 Ingress 的路由转发规则"
        tool: "list_alb2_routing_rules(cluster, ...) 或 list_alb2_resources(cluster, ...)"
      - action: "获取 Service 及其 Selector 标签"
        tool: "get_k8s_resource(cluster, namespace=\"<ns>\", kind=\"Service\", name=\"<name>\")"
      - action: "若从 Pod 出发，快速查询关联网络"
        tool: "get_k8s_pod_linked_services(cluster, ...) 或 get_pod_linked_endpoints(cluster, ...)"
      - action: "查看 Service 关联的 Endpoints/EndpointSlices (注意显式声明 Group Version)"
        tool: "list_k8s_resource(cluster, namespace=\"<ns>\", kind=\"EndpointSlice\", group=\"discovery.k8s.io\", version=\"v1\", labelSelector=\"kubernetes.io/service-name=<name>\") 或 get_pod_linked_endpoints(cluster, ...)"
      - action: "查看网络访问策略限制 (显式声明 Group Version)"
        tool: "list_k8s_resource(cluster, namespace=\"<ns>\", kind=\"NetworkPolicy\", group=\"networking.k8s.io\", version=\"v1\")"

  - resource_type: "Cluster & RBAC (ClusterRole/RoleBinding/CRD)"
    scenario: "集群级配置与鉴权资源检索"
    steps:
      - action: "列出或查看 CRD 详情 (使用完整 GVK)"
        tool: "get_k8s_resource(cluster, kind=\"CustomResourceDefinition\", group=\"apiextensions.k8s.io\", version=\"v1\", name=\"<name>\") 或 list_k8s_resource(cluster, ...)"
      - action: "按命名空间安全查询 RoleBinding"
        tool: "list_k8s_resource(cluster, namespace=\"<ns>\", kind=\"RoleBinding\") -> Agent自行过滤"
      - action: "查询 ClusterRoleBinding (必须配置极简投影和分页)"
        tool: "list_k8s_resource(cluster, kind=\"ClusterRoleBinding\", page=1, pageSize=50) -> Agent自行过滤"
```

---

## 输出规范 (Expected Output)

**默认结构化 Markdown 输出**。完成查询后，你**应当优先**使用人类友好的**结构化 Markdown 表格**和**自然语言摘要**呈现结果。若用户或外部调用方明确要求机器可读（如 JSON 格式），则按要求输出 JSON。

**输出模板要求**：
1. **Summary（摘要概览）**：一句自然的、非诊断性的客观总结（例如：“共查询到 5 个 app=nginx 的 Pod，当前均为 Running 状态。”）。
2. **核心资源表格 (Markdown Tables)**：根据查询的对象，用表格罗列最关键字段（如 Name, Ready, Status, Restarts, Age, Node 等）。
3. **关键配置截取 (Code Blocks)**：对于非常特定且复杂的配置片段（如复杂的 NetworkPolicy 规则或特定的 `env`），使用小的 `yaml` 或 `json` 代码块做精确展示，严禁直接贴上百行无用 YAML。
4. **Next Steps（后续建议）**：根据当前状态，提供用户可能感兴趣的下一步只读操作建议（例如：“是否需要查看 `nginx-xyz` Pod 的末尾日志？”）。

请确保返回内容高度可读、信息密度高、结果前置，坚决过滤掉底座返回的大段冗余 YAML 元数据（如 `managedFields`）。
