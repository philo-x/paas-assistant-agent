# PaaS Agent Assistant

<p align="center">
  <strong>基于 AgentScope Java 构建的 PaaS 平台多智能体运维助手</strong>
</p>

<p align="center">
  <a href="#项目概览">项目概览</a> •
  <a href="#功能架构">功能架构</a> •
  <a href="#系统架构">系统架构</a> •
  <a href="#工具目录">工具目录</a> •
  <a href="#技术架构">技术架构</a> •
  <a href="#快速开始">快速开始</a>
</p>

## 项目概览

PaaS Agent Assistant 将原有示例重构为面向平台场景的 Kubernetes 诊断与运维助手。

当前 V1 保留既有的 `Supervisor -> Sub Agent -> MCP -> Storage` 骨架，主链路聚焦在一条更稳的路径上：

1. 读取集群与资源状态
2. 诊断问题
3. 生成变更计划
4. 显式确认后执行
5. 将审批与执行审计落到 MySQL

对外聊天入口是结构化的 Server-Sent Events 流：

`POST /api/assistant/chat/structured`，请求体 JSON `{chat_id, user_id, user_query}`

## 功能架构

### Supervisor Agent

`supervisor-agent` 继续作为统一入口和调度层。

- 保留 SSE 流式输出与 MySQL 会话装载能力
- 将诊断、修复、重启、扩缩容、patch、恢复类请求路由到 `diagnosis-sub-agent`
- 将 YAML 字段解释、`describe` 解读、命令推荐、资源使用指南类请求路由到 `guide-sub-agent`

### Diagnosis Sub Agent

`diagnosis-sub-agent` 负责 Kubernetes 诊断与受控变更编排。

- 先调用只读 MCP 工具获取资源、事件、日志和诊断结果
- 输出现象、可能原因、风险和建议动作
- 对所有变更强制执行两阶段协议：
  - `change-plan-*`
  - `change-execute(approval_id, confirmed=true)`

V1 支持的资源范围：

`Pod`、`Deployment`、`StatefulSet`、`DaemonSet`、`Job`、`Service`、`Ingress`、`Node`、`Event`、`PVC`、`Namespace`

V1 支持的变更动作：

- 重启工作负载
- 扩缩容工作负载
- 删除 Pod
- Patch 白名单字段

### Guide Sub Agent

`guide-sub-agent` 负责只读咨询增强能力。

- 通过 RAG 回答资源指南类问题
- 解释 YAML 字段和 `kubectl describe` 输出
- 推荐保守的排障命令
- 复用 `platform-mcp-server` 的只读工具，不额外建立一条集群访问链路
- 不执行变更

### Platform MCP Server

`platform-mcp-server` 是变更执行门面。

- 使用 Fabric8 Kubernetes Client 实现受控执行能力
- 在 MySQL 中维护审批记录与执行审计
- 诊断工具由 `k8sgpt-server` 直接提供给 Agent

### 数据层

旧的示例业务表已经退出当前应用主流程。

保留：

- MySQL 会话持久化
- `operation_approval`
- `operation_execution`

移除：

- `users`
- `products`
- `orders`
- `feedback`

## 系统架构

```mermaid
flowchart LR
  flowchart LR
  User["用户 / 聊天界面"] --> Supervisor["supervisor-agent"]
  Supervisor --> Diagnosis["diagnosis-sub-agent"]
  Supervisor --> Guide["guide-sub-agent"]

  Diagnosis --> K8sGPT["k8sgpt-server (MCP HTTP)"]
  Guide --> RAG
  Guide --> MySQL["MySQL"]
```

## 工具目录

### 诊断工具 (via k8sgpt MCP Server)

- `analyze`
- `cluster-info`
- `list-resources`
- `get-resource`
- `list-namespaces`
- `list-events`
- `get-logs`
- `config`
- `list-filters` / `add-filters` / `remove-filters`
- `list-integrations`

### 变更工具

- `change-plan-restart`
- `change-plan-scale`
- `change-plan-delete-pod`
- `change-plan-patch`
- `change-execute`
- `change-get-status`

### 变更协议

所有变更请求都必须遵循同一条协议：

1. 先调用某个 `change-plan-*` 生成计划
2. 向用户展示 `approval_id`、目标资源、影响范围和回滚提示
3. 等待用户明确确认
4. 再调用 `change-execute`，并带上 `approval_id`

所有执行结果统一返回这些字段：

- `approval_id`
- `execution_id`
- `resource_ref`
- `action`
- `status`
- `summary`
- `rollback_hint`

## 技术架构

| 层次 | 技术 |
|---|---|
| Agent 框架 | AgentScope Java |
| 后端运行时 | Spring Boot |
| 工具协议 | MCP Spring WebFlux |
| 智能体协同 | 基于 Nacos 的 A2A |
| 诊断上游 | k8sgpt MCP HTTP |
| 变更执行 | Fabric8 Kubernetes Client |
| 持久化 | MySQL |
| 前端 | Vue 3、TypeScript、Pinia、Vue Router |
| 模型提供商 | DashScope、OpenAI 兼容接口 |
| 知识检索 | Dify RAG |
| 记忆能力 | AutoContextMemory、Mem0 |
| 部署方式 | Docker Compose、Helm |

## 仓库结构

```text
.
├── platform-mcp-server
├── diagnosis-sub-agent
├── guide-sub-agent
├── supervisor-agent
├── frontend
├── helm
├── mysql-image
└── nacos-image
```

## 快速开始

### 1. 准备配置

关键运行参数如下：

- 模型：`MODEL_PROVIDER`、`MODEL_API_KEY`、`MODEL_NAME`、`MODEL_BASE_URL`
- Nacos：`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD`
- MySQL：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`
- k8sgpt：`K8SGPT_MCP_URL`
- Kubernetes 接入：
  - `K8S_AUTH_MODE=incluster|kubeconfig`
  - `KUBECONFIG_PATH`
  - `K8S_CONTEXT`
- 审批有效期：`EXECUTION_APPROVAL_TTL_SECONDS`

### 2. 使用 Docker Compose 启动

```bash
docker compose up -d
```

编排中包含这些服务：

- `mysql`
- `nacos-server`
- `k8sgpt-server`
- `platform-mcp-server`
- `guide-sub-agent`
- `diagnosis-sub-agent`
- `supervisor-agent`

### 3. 访问应用

默认由 `supervisor-agent` 提供前端与聊天接口：

- UI：`http://localhost:10008`
- SSE 聊天接口：`POST http://localhost:10008/api/assistant/chat/structured`

示例：

```bash
curl -N -X POST http://localhost:10008/api/assistant/chat/structured \
     -H 'Content-Type: application/json' \
     -H 'Accept: text/event-stream' \
     -d '{"chat_id":"demo-chat","user_id":"ops-user","user_query":"某个 Pod CrashLoopBackOff，帮我定位原因"}'
```

## V1 范围

这次重构已纳入：

- 单集群 Kubernetes 诊断
- 显式确认后的变更执行
- 审批与执行审计落库
- YAML、describe、命令推荐类咨询能力
- `k8sgpt-server` 的 Compose 与 Helm 编排

这次重构暂不覆盖：

- 多集群路由
- 多租户隔离
- 平台 CRD 诊断
- 未经确认的自动执行
