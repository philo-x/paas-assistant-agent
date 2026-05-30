# Kubectl 查询投影与 JSONPath 语法指南

本文档为 `kubectl_query` Skill 的参考资料，旨在指导 Agent 在通过 MCP 接口调用 `kubectl` 工具时，遵循高鲁棒性、可分页、防溢出的最佳实践，合理选择过滤提取方式。

---

## 1. 字段提纯最佳实践：优先使用 `outputFields` 投影

在 K8s 诊断场景下，`kubectl get -o json` 通常会返回极其庞大且包含大量噪音（如 `managedFields`）的 JSON 文本，极易造成大模型上下文 Token 溢出。

**第一优先级（最佳实践）：使用 `outputFields` 投影**。这是获取结构化数据的首选方案。

### 🌟 为什么优先使用 `outputFields`？
1. **防溢出与 Token 节省**：MCP 服务端接收到该参数后，会自动剥离无关字段并仅保留投影路径中的字段，Token 消耗量可降低 80% 以上。
2. **高鲁棒性**：避免因手工拼写复杂的 JSONPath 语法括号或反斜杠转义失败而导致命令报错。
3. **原生分页支持**：配合 MCP 工具的 `page` 和 `pageSize` 参数，能对投影过滤后的列表进行完美的服务端分页展示。

### 💻 字段投影调用示例
查询 Service 的名称、端口及 Selector 标签：
```json
{
  "cluster": "prod-cluster",
  "args": ["get", "service", "-n", "default"],
  "outputFields": [
    ".metadata.name",
    ".spec.ports",
    ".spec.selector"
  ]
}
```

---

## 2. 备选方案（降级）：使用 `args` + `jsonpath`

**第二优先级（仅在特殊格式场景下允许降级使用）**。

### ⚠️ 降级使用条件
仅在需要将输出结果格式化为**非 JSON 文本**时，才允许使用 `jsonpath`。典型场景包括：
- 必须将输出拼接为特定格式的文本列表（如用制表符 `\t` 分隔每一列）。
- 需直接生成 Markdown 表格所需的数据。

> [!IMPORTANT]
> **SRE 安全建议**：只要决定降级使用 `jsonpath` 提取，**必须**使用 `args` 参数数组传递参数，绝对不要使用 `cmd` 字符串，以防 Shell 引号嵌套被解析器错误剥离导致报错。

---

## 3. Kubectl JSONPath 语法与特殊字符处理

若符合上述降级条件，请务必遵守以下 JSONPath 书写规范：

### 3.1 基础语法规则
* **大括号包裹**：整个表达式必须用花括号 `{...}` 包裹。例如：`{.items[*].metadata.name}`。
* **数组迭代**：用 `[*]` 遍历列表，用 `[0]` 访问单个元素。
* **空白与格式控制字符**：
  - 空格：`{" "}`
  - 制表符：`{"\t"}`
  - 换行符：`{"\n"}`
  - 范围循环遍历：`{range .items[*]}...{end}`。

### 3.2 标签与注解（特殊字符/点号）转义规则
K8s 资源的 Label 或 Annotation 键名若包含 `.` 或 `/`（如 `alb2.cpaas.io/name`），直接书写会导致解析器识别为层级对象。**必须使用单引号并对点号进行反斜杠转义**：

| 目标字段 | 推荐的 JSONPath 书写形式（在 `args` 中传递） |
| :--- | :--- |
| 普通 Label (`app`) | `{.metadata.labels.app}` |
| 带点号标签 (`alb2.cpaas.io/name`) | `{.metadata.labels['alb2\.cpaas\.io/name']}` |
| 带点号注解 (`kubernetes.io/ingress.class`) | `{.metadata.annotations['kubernetes\.io/ingress\.class']}` |

---

## 4. 经典查询模板对照表

### 示例 A：查询所有 Pod 的名称与 IP（非 JSON 文本列表格式）
* **允许降级使用 JSONPath 的场景**：需要以制表符和换行符分隔每一行，供文本日志记录。
* **参数构造**：
  ```json
  {
    "cluster": "prod-cluster",
    "args": [
      "get", "pods", "-n", "default", 
      "-o", "jsonpath={range .items[*]}{.metadata.name}{\t}{.status.podIP}{\n}{end}"
    ]
  }
  ```

### 示例 B：查询特定 Node 上的所有 Pod 名称（列表筛选）
* **推荐做法（最佳实践）**：使用 `--field-selector` 服务端过滤，并用 `outputFields` 投影提取名称。
* **参数构造**：
  ```json
  {
    "cluster": "prod-cluster",
    "args": [
      "get", "pods", "-n", "default", 
      "--field-selector", "spec.nodeName=node-01"
    ],
    "outputFields": [".metadata.name"]
  }
  ```

### 示例 C：查询 Pod 内指定容器的等待原因（如 CrashLoopBackOff）
* **推荐做法（最佳实践）**：使用 `outputFields` 提取状态，由 Agent 进行逻辑判断。
* **参数构造**：
  ```json
  {
    "cluster": "prod-cluster",
    "args": ["get", "pod", "my-pod", "-n", "default"],
    "outputFields": [
      ".status.containerStatuses[*].name",
      ".status.containerStatuses[*].state.waiting.reason"
    ]
  }
  ```

---

## 5. MCP 报错与排查速查

| 报错关键字 | 常见根因 | 解决方案 |
| :--- | :--- | :--- |
| `不支持 Shell 运算符 ...` | 试图使用管道符 `|` 或外部命令过滤。 | 1. 优先改用 `outputFields` 获取 JSON 在代码内过滤。<br>2. 改用 `--field-selector` 或 `-l`。 |
| `expected '{'` / `unrecognized character` | JSONPath 漏掉了外层的 `{}`，或引号在 `cmd` 参数解析中发生混淆。 | 1. 确认大括号闭合。<br>2. **立即弃用 `cmd`，改用 `args` 参数数组传递。** |
| 点号字段提取为空或报错 | 未对 Label/Annotation 的点号添加 `\.` 转义。 | 按规范改写为 `['domain\.key']` 格式。 |
