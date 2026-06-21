---
name: kubectl_query
version: "1.1"
category: Kubectl
description: >
  在诊断过程中使用 kubectl MCP 工具查询和检索 K8s 资源时的参数、语法与限制规范。
  引导 Agent 优先使用 outputFields 进行高鲁棒、防溢出、支持分页的字段投影；
  仅在需要格式化成非 JSON 文本（如 Markdown 表格、特定分隔符列表）时，才降级使用 args + jsonpath。
scope: read-only
triggers:
  - "需要通过只读 kubectl 工具查询特定 Kubernetes 资源详情"
  - "需要对大规模资源结果进行字段裁剪或过滤输出"
  - "调用 kubectl 工具因包含管道符、重定向或外部命令报错"
  - "需要精确提取带有特殊字符/点号的 Label 或 Annotation 字段"
references:
  - references/jsonpath_syntax_guide.md       # Kubectl 查询投影与 JSONPath 语法指南
---

# kubectl_query — Kubectl 查询投影与规范

> **⚠️ 只读模式**：本 Skill 仅用于规范只读查询操作，不涉及任何集群变更或状态修改。

## 核心原则与选择矩阵

为了确保 Agent 调用的高鲁棒性、防 Token 溢出并支持服务端分页，请严格按照下表策略选择 kubectl 数据过滤方式：

| 过滤要求 | 推荐调用格式 | 方案优先级 | 决策 rationale |
| :--- | :--- | :--- | :--- |
| **提取结构化数据**（用于分析、判断、提取 IP/Name 等） | **使用 `args` + `outputFields` 数组** | **第一优先级（最佳实践 Gold Standard）** | 1. 鲁棒性高（无语法拼接出错风险）<br>2. 节省 Token（服务端自动过滤 managedFields）<br>3. 支持分页。 |
| **需要将输出格式化为非 JSON 文本**（如拼接成制表符分隔行、Markdown 表格数据） | **使用 `args` + `-o jsonpath='...'`** | **第二优先级（降级备选 Fallback）** | 仅在有明确的非 JSON 文本格式拼接要求时才允许降级使用。必须使用 `args` 以免引号混淆。 |
| **无过滤要求，仅需宏观预览** | **使用 `cmd`** | 仅限极简预览（如 `get ns`） | 结构化查询严禁使用此方案。 |

---

## 规范查询工作流

### Step 1：确定查询目标与优先级路线
根据诊断逻辑，选择相应的参数组合：

#### 路线 A：使用 `outputFields` 进行结构化投影（首选）
若只需获取 Pod 的状态、Service 的 Selector 或 Ingress 的端口等字段进行逻辑推理：
- 在 `args` 中指定 `get` 命令；
- 在 `outputFields` 中传入以 `.` 开头的字段路径列表。
```json
// 示例：查询 Ingress 规则的名称及后端域名
工具：kubectl(cluster, cluster,
  args=["get", "ingress", "-n", "default"],
  outputFields=[".metadata.name", ".spec.rules[*].host"]
)
```

#### 路线 B：使用 `jsonpath` 文本格式化（仅限特殊文本格式）
若需要将输出渲染为 Markdown 列表或带有制表符的紧凑输出以呈现给用户：
- 必须将所有参数放在 `args` 数组中；
- 避免使用 `cmd` 传递参数，以规避 shell 转义错误。
```json
// 示例：以制表符分隔获取 Pod IP 与名称
工具：kubectl(cluster, cluster, 
  args=["get", "pods", "-n", "default", "-o", "jsonpath={range .items[*]}{.metadata.name}{\t}{.status.podIP}{\n}{end}"]
)
```

---

### Step 2：特殊字段转义规范
当需要提取或过滤的键名中包含特殊字符或点号（`.`）时（如 Alauda ALB2 的 Label `alb2.cpaas.io/name`）：
- **若使用 `outputFields`**：无需转义，直接以路径形式写入。例如：`.metadata.labels.alb2.cpaas.io/name`。
- **若使用 `jsonpath`**：**必须**用单引号括起键名，并对其中的点号加反斜杠转义：`['alb2\.cpaas\.io/name']`。

---

### Step 3：处理分页与数据溢出
在拉取列表资源（如 `get pods`）时：
- 默认分页为 `page=1, pageSize=100`。
- 严禁一次性设置过大的 `pageSize`（最大支持 500）。若有更多数据，请通过 `page` 参数按页迭代读取，以防 Token 溢出崩溃。
