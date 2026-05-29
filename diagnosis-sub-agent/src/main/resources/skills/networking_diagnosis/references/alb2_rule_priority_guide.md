# ALB2 Rule 优先级机制与路径冲突排查指南

本文档为 `networking_diagnosis` Skill 的参考资料，专门说明 ALB2 Rule 资源的优先级匹配机制、DSL 匹配表达式语法，以及多 Rule 路径冲突的排查方法。

---

## 1. Rule 优先级机制

### `spec.priority` 字段

每个 Rule 资源的 `spec.priority` 字段决定了该规则在同一 Frontend 下的匹配优先级。

| 字段 | 含义 | 示例 |
|------|------|------|
| `spec.priority` | 优先级数值，**数值越小，优先级越高**，越先被匹配 | `priority: 1` 优先于 `priority: 5` |

**匹配顺序**：当同一 Frontend 下存在多个 Rule 且请求同时匹配多个 Rule 的条件时，ALB2 控制器按 `priority` 从小到大排序，**第一个匹配的 Rule 生效**，后续 Rule 不再评估。

---

## 2. DSL/DSLX 匹配表达式

Rule 使用 `spec.dsl` 和 `spec.dslx` 字段定义请求匹配条件。多个条件之间为 **AND** 关系（必须同时满足）。

### DSL 字符串格式

```
# 仅匹配域名
spec.dsl: "(AND (IN HOST example.com))"

# 仅匹配 URL 前缀
spec.dsl: "(AND (STARTS_WITH URL /api/))"

# 匹配域名 + URL 路径
spec.dsl: "(AND (IN HOST example.com) (REGEX URL /manager/(.*)))"

# 匹配 URL 前缀 + 请求头（Header）条件
spec.dsl: "(AND (STARTS_WITH URL /eBanks-manager) (EQ HEADER ENVIRONMENT_GROUP H5))"
```

### DSLX 结构化格式

DSLX 是 DSL 的结构化 YAML 表达，支持三种匹配类型：`HOST`、`URL`、`HEADER`。

**示例 1：仅匹配域名**
```yaml
spec.dslx:
  - type: HOST
    values:
      - - IN
        - example.com
```

**示例 2：匹配 URL 前缀 + 请求头**
```yaml
spec.dslx:
  - type: URL
    values:
      - - STARTS_WITH
        - /eBanks-manager
  - key: ENVIRONMENT_GROUP        # HEADER 类型需要额外的 key 字段指定 Header 名称
    type: HEADER
    values:
      - - EQ
        - H5
```

### 匹配类型

| 类型 | 含义 | DSLX 是否需要 key 字段 | 说明 |
|------|------|----------------------|------|
| `HOST` | 匹配请求的域名 | 否 | 如 `(IN HOST example.com)` |
| `URL` | 匹配请求的 URL 路径 | 否 | 如 `(STARTS_WITH URL /api/)` |
| `HEADER` | 匹配请求的 HTTP Header | **是**，`key` 指定 Header 名称 | 如 `(EQ HEADER ENVIRONMENT_GROUP H5)` |

### 匹配操作符

| 操作符 | 含义 | 示例 | 匹配范围 |
|-------|------|------|---------|
| `IN` | 精确等于（可多值） | `(IN HOST example.com)` | 仅匹配 `example.com` |
| `STARTS_WITH` | 前缀匹配 | `(STARTS_WITH URL /eBanks-manager)` | 匹配 `/eBanks-manager`、`/eBanks-manager/login` 等 |
| `REGEX` | 正则匹配 | `(REGEX URL /manager/(.*))` | 匹配 `/manager/` 下所有路径 |
| `EQ` | 精确匹配 | `(EQ HEADER ENVIRONMENT_GROUP H5)` | 仅匹配 Header `ENVIRONMENT_GROUP` 值为 `H5` |

### 关键注意事项

* **`domain` 为空时**：当 `spec.domain: ""` 时，表示该 Rule 不基于域名匹配，而是通过 DSL 中的 URL/HEADER 条件进行匹配。
* **`spec.url` 字段**：Rule 还有一个 `spec.url` 字段（如 `url: /eBanks-manager`），是 URL 路径的简写便于展示，实际匹配逻辑以 `dsl`/`dslx` 为准。
* **AND 语义**：DSL 中多个条件用 `(AND ...)` 包裹，必须**全部满足**才能匹配。

---

## 3. Rule 优先级冲突的典型场景

### 场景 A：宽泛路径抢占精确路径

这是生产环境中最常见的 Rule 冲突问题。

**示例**：

| Rule | 所属用户 | Domain | Path 条件 | Priority | 后端 Service |
|------|---------|--------|----------|----------|-------------|
| Rule A | 用户 A | `app.example.com` | `(STARTS_WITH URL /)` | `1` | `svc-a` |
| Rule B | 用户 B | `app.example.com` | `(STARTS_WITH URL /eBanks-manager)` | `5` | `svc-b` |

**结果**：当请求 `app.example.com/eBanks-manager/login` 时：
1. ALB2 按 priority 排序，先评估 Rule A（priority=1）
2. Rule A 的 `STARTS_WITH /` 匹配**所有路径**（包括 `/eBanks-manager/login`）
3. Rule A 匹配成功，流量被路由到 `svc-a`
4. Rule B **永远不会被匹配到**，用户 B 的应用不可访问

### 场景 B：相同路径但不同 HEADER 条件未被区分

当两个 Rule 具有相同 URL 前缀但通过不同 HEADER 区分（如灰度环境），优先级设置不当也会导致冲突。

**示例**：

| Rule | Domain | DSL 条件 | Priority | 后端 Service |
|------|--------|----------|----------|-------------|
| Rule A | `""` | `(AND (STARTS_WITH URL /eBanks-manager))` | `1` | `svc-prod` |
| Rule B | `""` | `(AND (STARTS_WITH URL /eBanks-manager) (EQ HEADER ENVIRONMENT_GROUP H5))` | `5` | `svc-h5` |

**结果**：Rule B 虽然增加了 HEADER 条件限制，但因为 Rule A 的 priority 更高且 URL 条件已满足，所有请求（无论 Header 值）都被 Rule A 截获。

### 识别方法

```
当用户报告 "我的应用 URL 访问异常，但 Rule 配置明明正确" 时，应怀疑优先级冲突。
关键信号：
  - 请求被路由到了非预期的后端 Service
  - 同 Frontend 下存在多个 Rule，且其中一个使用了宽泛匹配条件
  - 宽泛条件 Rule 的 priority 值 < 精确条件 Rule 的 priority 值
  - 特别注意 domain 为空的 Rule，它们可能通过 URL/HEADER 匹配与其他 Rule 发生交叉
```

---

## 4. 冲突排查命令

```bash
# 1. 列出同一 Frontend 下所有 Rule（可以看到同域名/同端口的所有规则）
kubectl get rules -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name> -o wide

# 2. 按 priority 排序查看所有 Rule 的关键字段（域名、DSL、优先级）
kubectl get rules -n cpaas-system -l alb2.cpaas.io/frontend=<frontend-name> \
  -o custom-columns='NAME:.metadata.name,PRIORITY:.spec.priority,DOMAIN:.spec.domain,DSL:.spec.dsl'

# 3. 查看特定 Rule 的完整 DSL 表达式和优先级
kubectl get rule <rule-name> -n cpaas-system \
  -o jsonpath='{.spec.priority}{"\t"}{.spec.domain}{"\t"}{.spec.dsl}{"\n"}'

# 4. 查看特定 Rule 的后端 Service 配置（注意路径是 serviceGroup.services）
kubectl get rule <rule-name> -n cpaas-system -o jsonpath='{.spec.serviceGroup.services}'
```

---

## 5. 冲突解决建议

| 冲突类型 | 解决方案 |
|---------|---------|
| 宽泛路径（`/`）priority 过高 | 调高（增大数值）宽泛路径 Rule 的 priority，使其排在精确路径之后 |
| 两个 Rule 路径有交集 | 为精确路径 Rule 设置更小的 priority 值，确保优先匹配 |
| 同路径不同 HEADER 条件被覆盖 | 将带有 HEADER 精确条件的 Rule 的 priority 设为更小值，优先匹配精确条件 |
| 同域名下不同用户的 Rule 冲突 | 建议为不同用户分配不同的域名前缀或子路径，避免共用根路径 |
